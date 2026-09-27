package com.forbidad4tieba.hook.config

import android.content.Context
import com.forbidad4tieba.hook.core.XposedCompat
import java.io.File

internal object ModuleUserDataCleaner {
    private const val SHARED_PREFS_DIR = "shared_prefs"
    private const val LEGACY_ABOUT_CACHE_FILE_NAME = "tbhook_about_cache.json"
    private const val ABOUT_CACHE_DIR_NAME = "tbhook"
    private const val ABOUT_CACHE_FILE_NAME = "about_info_cache.json"
    private const val COLLECTION_CACHE_DIR_NAME = "tbhook_cache"
    private const val SHARE_STAGING_PARENT_DIR = "com_qq_e_download"
    private const val SHARE_STAGING_DIR_NAME = "tbhook_share"

    data class Result(
        val deletedTargets: List<String>,
        val failedTargets: List<String>,
    ) {
        val success: Boolean
            get() = failedTargets.isEmpty()
    }

    fun clearBeforeManualScan(context: Context): Result {
        return clearAllModuleData(context, resetRuntime = true)
    }

    fun clearAllModuleData(context: Context, resetRuntime: Boolean): Result {
        val appCtx = context.applicationContext ?: context
        val deleted = ArrayList<String>()
        val failed = ArrayList<String>()

        deleteSharedPrefs(appCtx, ConfigManager.MODULE_STATE_PREFS_NAME, deleted, failed)
        deleteSharedPrefs(appCtx, ConfigManager.SYMBOL_CACHE_PREFS_NAME, deleted, failed)
        deleteSharedPrefs(appCtx, ConfigManager.LEGACY_MIXED_PREFS_NAME, deleted, failed)
        deleteOwnedFile(
            parent = appCtx.filesDir,
            name = LEGACY_ABOUT_CACHE_FILE_NAME,
            label = "files/$LEGACY_ABOUT_CACHE_FILE_NAME",
            deleted = deleted,
            failed = failed,
        )
        deleteOwnedFile(
            parent = File(appCtx.filesDir, ABOUT_CACHE_DIR_NAME),
            name = ABOUT_CACHE_FILE_NAME,
            label = "files/$ABOUT_CACHE_DIR_NAME/$ABOUT_CACHE_FILE_NAME",
            deleted = deleted,
            failed = failed,
        )
        deleteOwnedFile(
            parent = appCtx.filesDir,
            name = ConfigManager.MODEL_SCORE_STATS_FILE_NAME,
            label = "files/${ConfigManager.MODEL_SCORE_STATS_FILE_NAME}",
            deleted = deleted,
            failed = failed,
        )
        deleteOwnedTree(
            parent = appCtx.filesDir,
            name = COLLECTION_CACHE_DIR_NAME,
            label = "files/$COLLECTION_CACHE_DIR_NAME/",
            deleted = deleted,
            failed = failed,
        )
        deleteOwnedTree(
            parent = File(appCtx.cacheDir, SHARE_STAGING_PARENT_DIR),
            name = SHARE_STAGING_DIR_NAME,
            label = "cache/$SHARE_STAGING_PARENT_DIR/$SHARE_STAGING_DIR_NAME/",
            deleted = deleted,
            failed = failed,
        )

        if (resetRuntime) {
            ConfigManager.resetRuntimeAfterUserDataClear(appCtx)
        }

        val result = Result(deletedTargets = deleted.distinct(), failedTargets = failed.distinct())
        XposedCompat.log(
            "[ModuleUserDataCleaner] clearAllModuleData " +
                "success=${result.success}, deleted=${deleted.joinToString(",").ifEmpty { "-" }}, " +
                "failed=${failed.joinToString(",").ifEmpty { "-" }}"
        )
        return result
    }

    private fun deleteSharedPrefs(
        context: Context,
        prefsName: String,
        deleted: MutableList<String>,
        failed: MutableList<String>,
    ) {
        if (prefsName == ConfigManager.USER_SETTINGS_PREFS_NAME) {
            XposedCompat.logW("[ModuleUserDataCleaner] skip user settings prefs: $prefsName")
            return
        }
        val label = "$SHARED_PREFS_DIR/$prefsName.xml"
        val prefsDir = File(context.applicationInfo.dataDir, SHARED_PREFS_DIR)
        val prefsFile = File(prefsDir, "$prefsName.xml")
        val backupFile = File(prefsDir, "$prefsName.xml.bak")
        val existed = prefsFile.exists() || backupFile.exists()

        runCatching {
            context.deleteSharedPreferences(prefsName)
        }.onFailure { t ->
            failed.add(label)
            XposedCompat.logW("[ModuleUserDataCleaner] delete shared prefs failed: $prefsName ${t.message}")
        }

        var remaining = false
        for (file in arrayOf(prefsFile, backupFile)) {
            if (file.exists() && !file.delete()) {
                remaining = true
            }
        }
        when {
            remaining -> failed.add(label)
            existed -> deleted.add(label)
        }
    }

    private fun deleteOwnedFile(
        parent: File,
        name: String,
        label: String,
        deleted: MutableList<String>,
        failed: MutableList<String>,
    ) {
        val file = resolveOwnedChild(parent, name, label, failed) ?: return
        if (!file.exists()) return
        if (file.isFile && file.delete()) {
            deleted.add(label)
        } else {
            failed.add(label)
        }
    }

    private fun deleteOwnedTree(
        parent: File,
        name: String,
        label: String,
        deleted: MutableList<String>,
        failed: MutableList<String>,
    ) {
        val dir = resolveOwnedChild(parent, name, label, failed) ?: return
        if (!dir.exists()) return
        if (dir.isDirectory && dir.deleteRecursively()) {
            deleted.add(label)
        } else {
            failed.add(label)
        }
    }

    private fun resolveOwnedChild(
        parent: File,
        name: String,
        label: String,
        failed: MutableList<String>,
    ): File? {
        return runCatching {
            val ownedParent = parent.canonicalFile
            val child = File(ownedParent, name).canonicalFile
            if (child.parentFile != ownedParent || child.name != name) {
                failed.add(label)
                null
            } else {
                child
            }
        }.getOrElse { t ->
            failed.add(label)
            XposedCompat.logW("[ModuleUserDataCleaner] resolve target failed: $label ${t.message}")
            null
        }
    }
}
