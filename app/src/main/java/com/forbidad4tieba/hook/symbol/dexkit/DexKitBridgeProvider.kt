package com.forbidad4tieba.hook.symbol.dexkit

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.symbol.scan.HookSymbolScanSession
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal class DexKitScanBridge(
    val sourcePath: String,
    val bridge: DexKitBridge,
) : AutoCloseable {
    override fun close() {
        bridge.close()
    }
}

internal object DexKitBridgeProvider {
    private const val TAG = "DexKitBridge"
    private const val MODULE_PACKAGE_NAME = "com.forbidad4tieba.hook"
    private const val DEXKIT_LIBRARY_NAME = "dexkit"
    private const val DEXKIT_LIBRARY_FILE_NAME = "libdexkit.so"
    private const val EXTRACTED_LIBRARY_DIR_NAME = "tbhook_native_libs"

    @Volatile
    private var nativeLibraryLoaded = false

    @Volatile
    private var nativeLibraryLoadError: String? = null

    fun openFirstAvailable(
        sourcePaths: List<String>,
        logger: ScanLogger?,
    ): DexKitScanBridge? {
        ensureNativeLibraryLoaded(logger)?.let { detail ->
            recordBridgeIssue(logger, detail)
            return null
        }

        var failed = 0
        var firstError: String? = null
        for (sourcePath in sourcePaths.distinct()) {
            if (!File(sourcePath).isFile) continue
            try {
                return DexKitScanBridge(
                    sourcePath = sourcePath,
                    bridge = DexKitBridge.create(sourcePath),
                )
            } catch (t: Throwable) {
                failed++
                if (firstError == null) {
                    firstError = HookSymbolScanDiagnostics.sanitizeScanStatusText(
                        HookSymbolScanDiagnostics.formatScanException(t),
                    )
                }
            }
        }
        if (failed > 0) {
            val detail = "open failed count=$failed" +
                (firstError?.let { ", firstException=$it" } ?: "")
            recordBridgeIssue(logger, detail)
        }
        return null
    }

    private fun ensureNativeLibraryLoaded(logger: ScanLogger?): String? {
        if (nativeLibraryLoaded) return null
        nativeLibraryLoadError?.let { return it }
        return synchronized(this) {
            if (nativeLibraryLoaded) return@synchronized null
            nativeLibraryLoadError?.let { return@synchronized it }

            val loadError = tryLoadNativeLibrary(logger)
            if (loadError == null) {
                nativeLibraryLoaded = true
                null
            } else {
                nativeLibraryLoadError = loadError
                loadError
            }
        }
    }

    private fun tryLoadNativeLibrary(logger: ScanLogger?): String? {
        try {
            System.loadLibrary(DEXKIT_LIBRARY_NAME)
            HookSymbolScanDiagnostics.log(logger, "$TAG: loaded by System.loadLibrary")
            return null
        } catch (t: Throwable) {
            val loadLibraryError = "System.loadLibrary failed: " + HookSymbolScanDiagnostics.sanitizeScanStatusText(
                HookSymbolScanDiagnostics.formatScanException(t),
            )
            val fallbackErrors = ArrayList<String>(3)
            fallbackErrors.add(loadLibraryError)

            tryLoadFromModuleNativeLibrary(logger)?.let(fallbackErrors::add) ?: return null
            tryLoadFromModuleApk(logger)?.let(fallbackErrors::add) ?: return null

            return fallbackErrors.joinToString("; ")
        }
    }

    private fun tryLoadFromModuleNativeLibrary(logger: ScanLogger?): String? {
        val moduleLibrary = resolveModuleNativeLibrary()
            ?: return "module nativeLibraryDir unavailable"
        if (!moduleLibrary.isFile) {
            return "module native library file missing: ${moduleLibrary.absolutePath}"
        }
        return try {
            System.load(moduleLibrary.absolutePath)
            HookSymbolScanDiagnostics.log(
                logger,
                "$TAG: loaded native library from ${moduleLibrary.parent}",
            )
            null
        } catch (t: Throwable) {
            "module native library load failed: ${moduleLibrary.absolutePath}, exception=" +
                HookSymbolScanDiagnostics.sanitizeScanStatusText(
                    HookSymbolScanDiagnostics.formatScanException(t),
                )
        }
    }

    private fun tryLoadFromModuleApk(logger: ScanLogger?): String? {
        val app = recoverCurrentApplication()
            ?: return "module apk extraction unavailable: currentApplication=null"
        val moduleApkPaths = resolveModuleApkPaths(app)
        if (moduleApkPaths.isEmpty()) return "module apk path unavailable"

        val entryNames = Build.SUPPORTED_ABIS.orEmpty().map { abi ->
            "lib/$abi/$DEXKIT_LIBRARY_FILE_NAME"
        }
        if (entryNames.isEmpty()) return "device supported ABI list is empty"

        var sawApk = false
        var sawLibraryEntry = false
        var firstError: String? = null
        moduleApkPaths.forEach { apkPath ->
            val apkFile = File(apkPath)
            if (!apkFile.isFile) return@forEach
            sawApk = true
            try {
                ZipFile(apkFile).use { zip ->
                    val match = findNativeLibraryEntry(zip, entryNames) ?: return@use
                    sawLibraryEntry = true
                    val extracted = extractNativeLibrary(app, apkFile, zip, match)
                    System.load(extracted.absolutePath)
                    HookSymbolScanDiagnostics.log(
                        logger,
                        "$TAG: loaded native library from module apk entry ${match.name}",
                    )
                    return null
                }
            } catch (t: Throwable) {
                if (firstError == null) {
                    firstError = "${apkFile.absolutePath}: " +
                        HookSymbolScanDiagnostics.sanitizeScanStatusText(
                            HookSymbolScanDiagnostics.formatScanException(t),
                        )
                }
            }
        }

        if (!sawApk) return "module apk files missing: ${moduleApkPaths.joinToString(",")}"
        if (!sawLibraryEntry) return "module apk native entry missing for ABI: ${entryNames.joinToString(",")}"
        return "module apk native library load failed: ${firstError ?: "unknown"}"
    }

    private fun resolveModuleNativeLibrary(): File? {
        val appInfo = resolveModuleApplicationInfo() ?: return null
        val nativeLibraryDir = appInfo.nativeLibraryDir?.takeIf { it.isNotBlank() } ?: return null
        return File(nativeLibraryDir, DEXKIT_LIBRARY_FILE_NAME)
    }

    private fun resolveModuleApkPaths(app: Application): List<String> {
        val appInfo = resolveModuleApplicationInfo(app) ?: return emptyList()
        return buildList {
            appInfo.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            appInfo.splitSourceDirs?.forEach { path ->
                path?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()
    }

    private fun resolveModuleApplicationInfo(app: Application? = recoverCurrentApplication()): ApplicationInfo? {
        runCatching { XposedCompat.module?.moduleApplicationInfo }.getOrNull()?.let { return it }
        if (app == null) return null
        return try {
            @Suppress("DEPRECATION")
            app.packageManager.getApplicationInfo(MODULE_PACKAGE_NAME, 0)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findNativeLibraryEntry(
        zip: ZipFile,
        entryNames: List<String>,
    ): ZipEntry? {
        return entryNames.firstNotNullOfOrNull { entryName ->
            zip.getEntry(entryName)?.takeIf { !it.isDirectory }
        }
    }

    private fun extractNativeLibrary(
        app: Application,
        apkFile: File,
        zip: ZipFile,
        entry: ZipEntry,
    ): File {
        val abi = entry.name.substringAfter("lib/").substringBefore("/")
        val directory = File(app.codeCacheDir ?: app.cacheDir, EXTRACTED_LIBRARY_DIR_NAME)
        if (!directory.isDirectory && !directory.mkdirs()) {
            error("extract dir unavailable: ${directory.absolutePath}")
        }

        val stamp = "${apkFile.length()}-${apkFile.lastModified()}"
        val target = File(directory, "libdexkit-$abi-$stamp.so")
        if (target.isFile && (entry.size < 0L || target.length() == entry.size)) {
            return target
        }

        val temp = File(directory, "${target.name}.tmp")
        zip.getInputStream(entry).use { input ->
            temp.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (entry.size >= 0L && temp.length() != entry.size) {
            temp.delete()
            error("extract size mismatch: ${temp.absolutePath}")
        }
        if (target.exists() && !target.delete()) {
            temp.delete()
            error("replace extracted library failed: ${target.absolutePath}")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("rename extracted library failed: ${target.absolutePath}")
        }
        target.setReadable(true, false)
        target.setExecutable(true, false)
        return target
    }

    private fun recoverCurrentApplication(): Application? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThread.getDeclaredMethod("currentApplication").apply {
                isAccessible = true
            }
            currentApplication.invoke(null) as? Application
        } catch (_: Throwable) {
            null
        }
    }

    private fun recordBridgeIssue(logger: ScanLogger?, detail: String) {
        val errors = HookSymbolScanSession.get()?.scanErrors
        if (errors != null) {
            HookSymbolScanDiagnostics.recordScanIssue(
                logger = logger,
                tag = TAG,
                errors = errors,
                detail = detail,
            )
        } else {
            HookSymbolScanDiagnostics.log(logger, "$TAG: $detail")
        }
    }
}
