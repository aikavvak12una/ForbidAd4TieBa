package com.forbidad4tieba.hook.config

import android.content.Context
import android.app.ActivityManager
import android.os.Process
import android.util.AtomicFile
import com.forbidad4tieba.hook.HookProcess
import com.forbidad4tieba.hook.contracts.ModuleDataPaths
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.diagnostic.DetailedLogExporter
import java.io.File
import java.util.Locale

internal object ModuleUserDataCleaner {
    private const val RESET_REQUEST = "full-module-reset-v1"

    data class Result(val deletedTargets: List<String>, val failedTargets: List<String>) {
        val success: Boolean get() = failedTargets.isEmpty()
    }

    /** A scan invalidates caches, preserving preferences, imported backgrounds and exports. */
    fun clearBeforeManualScan(context: Context): Result {
        val app = context.applicationContext ?: context
        val result = clearOwnedData(app, fullReset = false)
        ConfigManager.resetRuntimeAfterUserDataClear(app)
        return result
    }

    /** Persist the user's request; the next main-process bootstrap cleans before any writers start. */
    @Synchronized
    fun requestClearAllModuleData(context: Context): Boolean = try {
        val atomic = requestFile(context.applicationContext ?: context)
        val stream = atomic.startWrite()
        try {
            stream.write(RESET_REQUEST.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            true
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
    } catch (failure: Exception) {
        XposedCompat.logW("[ModuleUserDataCleaner] reset request failed: ${failure.message}")
        false
    }

    /** Called before the host's attachBaseContext in a fresh main process. Never clears a live session. */
    @Synchronized
    fun completePendingReset(context: Context): Result? {
        val app = context.applicationContext ?: context
        return try {
            val file = ModuleDataFiles.resolve(app.noBackupFilesDir, ModuleDataPaths.RESET_REQUEST_FILE)
            val backup = File(file.parentFile, file.name + ".bak")
            val temporary = File(file.parentFile, file.name + ".new")
            if (!file.exists() && !backup.exists()) {
                // An interrupted, uncommitted request is not permission to wipe data.
                if (temporary.exists()) runCatching { ModuleDataFiles.delete(file.parentFile!!, temporary.name) }
                    .onFailure { XposedCompat.logW("[ModuleUserDataCleaner] reset temporary cleanup failed: ${it.message}") }
                return null
            }
            val atomic = AtomicFile(file)
            val request = atomic.openRead().use { input -> input.readBytes().toString(Charsets.UTF_8) }
            // Consume once before deletion. A partial failure must not erase newly chosen settings next boot.
            atomic.delete()
            check(!file.exists() && !backup.exists() && !temporary.exists()) { "Reset request could not be consumed" }
            check(request == RESET_REQUEST) { "Invalid reset request" }
            if (!stopRemoteWriter(app)) return Result(emptyList(), listOf("process/image-viewer"))
            clearOwnedData(app, fullReset = true)
        } catch (failure: Exception) {
            XposedCompat.logW("[ModuleUserDataCleaner] pending reset failed: ${failure.message}")
            Result(emptyList(), listOf("no_backup/${ModuleDataPaths.RESET_REQUEST_FILE}"))
        }
    }

    private fun requestFile(context: Context) = AtomicFile(
        ModuleDataFiles.resolve(context.noBackupFilesDir, ModuleDataPaths.RESET_REQUEST_FILE),
    )

    /** The image process also holds settings and can finish a share write after the main process exits. */
    private fun stopRemoteWriter(context: Context): Boolean {
        if (context.packageName != Constants.TARGET_PACKAGE) return true
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: error("Activity service unavailable")
            val processes = manager.runningAppProcesses ?: error("Process list unavailable")
            val uid = context.applicationInfo.uid
            val self = Process.myPid()
            for (process in processes) {
                if (isResetWorker(process.processName, process.uid, uid, process.pid, self))
                    Process.killProcess(process.pid)
            }
            true
        } catch (failure: Exception) {
            XposedCompat.logW("[ModuleUserDataCleaner] remote writer could not be stopped: ${failure.message}")
            false
        }
    }

    internal fun isResetWorker(name: String, uid: Int, ownerUid: Int, pid: Int, selfPid: Int): Boolean =
        uid == ownerUid && pid > 0 && pid != selfPid && HookProcess.isImageViewerRemote(name)

    private fun clearOwnedData(context: Context, fullReset: Boolean): Result {
        val deleted = ArrayList<String>()
        val failed = ArrayList<String>()
        val preferencesRoot = File(context.applicationInfo.dataDir, "shared_prefs")
        for (name in ModuleDataPaths.preferenceNames(fullReset)) {
            val label = "shared_prefs/$name.xml"
            try {
                // Validate both paths before asking Android to remove its cached preferences instance.
                ModuleDataFiles.resolve(preferencesRoot, "$name.xml")
                ModuleDataFiles.resolve(preferencesRoot, "$name.xml.bak")
                val existed = File(preferencesRoot, "$name.xml").exists() || File(preferencesRoot, "$name.xml.bak").exists()
                context.deleteSharedPreferences(name)
                ModuleDataFiles.delete(preferencesRoot, "$name.xml")
                ModuleDataFiles.delete(preferencesRoot, "$name.xml.bak")
                if (existed) deleted.add(label)
            } catch (failure: Exception) {
                failed.add(label)
                XposedCompat.logW("[ModuleUserDataCleaner] $label: ${failure.message}")
            }
        }
        val roots = mapOf(
            ModuleDataPaths.Area.FILES to context.filesDir,
            ModuleDataPaths.Area.CACHE to context.cacheDir,
            ModuleDataPaths.Area.CODE_CACHE to (context.codeCacheDir ?: context.cacheDir),
        )
        for (entry in ModuleDataPaths.files) {
            if (entry.fullResetOnly && !fullReset) continue
            val label = entry.area.name.lowercase(Locale.ROOT) + "/" + entry.relativePath
            try {
                if (ModuleDataFiles.delete(checkNotNull(roots[entry.area]), entry.relativePath)) deleted.add(label)
            } catch (failure: Exception) {
                failed.add(label)
                XposedCompat.logW("[ModuleUserDataCleaner] $label: ${failure.message}")
            }
        }
        if (fullReset) {
            val exports = DetailedLogExporter.clearOwnedExports(context)
            deleted.addAll(exports.first)
            failed.addAll(exports.second)
        }
        return Result(deleted.distinct(), failed.distinct()).also {
            XposedCompat.log("[ModuleUserDataCleaner] full=$fullReset success=${it.success} deleted=${it.deletedTargets.size} failed=${it.failedTargets}")
        }
    }
}
