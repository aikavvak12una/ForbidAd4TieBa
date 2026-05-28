package com.forbidad4tieba.hook.feature.perf

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 禁用百度 Titan SDK 热补丁框架�? *
 * 热补丁框架启动时通过 LoaderManager.load() �?loadInTime() 加载补丁 dex�? * 再把 Interceptable 实例注入到每个类的静�?$ic 字段�? * 这里�?load() 返回 -1，也就是 LOAD_STATE_ERROR_NOPATCH，阻止补丁加载，
 * 同时清空 ClassClinitInterceptorStorage，避免残留拦截�? *
 * 还会删除磁盘上的已有补丁文件，避�?hook 未生效时继续使用残留补丁数据�? *
 * 作用�? * - 去掉每个方法里的 Interceptable 空值检查开销
 * - 阻止服务器下发补丁修改应用行�? * - 避免 TreltChu �?Titan 运行时类进入执行路径
 * - 减少启动阶段的签名校验、dex 加载和类补丁耗时
 *
 * 这个 hook �?onPackageLoaded 中安装，也就�?Application.attach 之前�? * 目标应用会在 TiebaBaseApplication.attachBaseContext() 里调�?LoaderManager.load()�? */
object TitanPatchBlockHook {
    private const val TAG = "[TitanPatchBlockHook]"
    private const val LOADER_MANAGER_CLASS = "com.baidu.titan.sdk.loader.LoaderManager"
    private const val CLINIT_STORAGE_CLASS = "com.baidu.titan.sdk.runtime.ClassClinitInterceptorStorage"
    private const val TITAN_DIR = "titan"
    private const val LOAD_STATE_NOPATCH = -1

    private val installed = AtomicBoolean(false)
    private val patchesCleaned = AtomicBoolean(false)

    fun hook(cl: ClassLoader) {
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        val loaderClass = XposedCompat.findClassOrNull(LOADER_MANAGER_CLASS, cl)
        if (loaderClass == null) {
            installed.set(false)
            XposedCompat.logD("$TAG class NOT FOUND: $LOADER_MANAGER_CLASS")
            return
        }

        var hooked = 0

        // Intercept load() and return no-patch when enabled.
        val loadMethod = try {
            loaderClass.getDeclaredMethod("load")
        } catch (_: Throwable) { null }

        if (loadMethod != null) {
            try {
                mod.hook(loadMethod).intercept { chain ->
                    if (isEnabled(chain.thisObject)) {
                        clearClinitInterceptor(cl)
                        XposedCompat.log("$TAG load() BLOCKED by config")
                        return@intercept LOAD_STATE_NOPATCH
                    }
                    chain.proceed()
                }
                hooked++
            } catch (t: Throwable) {
                XposedCompat.logD("$TAG failed to hook load(): ${t.message}")
            }
        }

        // Intercept loadInTime() and return no-patch when enabled.
        val loadInTimeMethod = try {
            loaderClass.getDeclaredMethod("loadInTime")
        } catch (_: Throwable) { null }

        if (loadInTimeMethod != null) {
            try {
                mod.hook(loadInTimeMethod).intercept { chain ->
                    if (isEnabled(chain.thisObject)) {
                        clearClinitInterceptor(cl)
                        XposedCompat.log("$TAG loadInTime() BLOCKED by config")
                        return@intercept LOAD_STATE_NOPATCH
                    }
                    chain.proceed()
                }
                hooked++
            } catch (t: Throwable) {
                XposedCompat.logD("$TAG failed to hook loadInTime(): ${t.message}")
            }
        }

        if (hooked > 0) {
            XposedCompat.logD("$TAG interceptors registered: $hooked methods")
        } else {
            installed.set(false)
            XposedCompat.logD("$TAG no Titan load methods found")
        }
    }

    /**
     * 直接读取 SharedPreferences，判�?Titan 阻断是否启用�?     * �?attachBaseContext 阶段调用 LoaderManager.load() 时，
     * 此时 ConfigManager 可能还没初始化�?     */
    private fun isEnabled(loaderManager: Any?): Boolean {
        // Fast path after ConfigManager is initialized.
        if (ConfigManager.getAppContext() != null) {
            return ConfigManager.isTitanPatchBlockEnabled
        }
        // Slow path reads prefs from Titan loader context before attach completes.
        try {
            val context = getContextFromLoaderManager(loaderManager) ?: return false
            if (ConfigManager.isRestrictedFeatureUnlockBlocked(context)) return false
            val prefs = context.getSharedPreferences(
                ConfigManager.USER_SETTINGS_PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )
            val unlocked = prefs.getBoolean(ConfigManager.KEY_RESTRICTED_FEATURES_UNLOCKED, false)
            val performanceEnabled = prefs.getBoolean(ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION, false)
            return unlocked &&
                performanceEnabled &&
                prefs.getBoolean(ConfigManager.KEY_BLOCK_TITAN_PATCH, false)
        } catch (_: Throwable) {
            return false
        }
    }

    private fun getContextFromLoaderManager(loaderManager: Any?): android.content.Context? {
        if (loaderManager == null) return null
        return try {
            val field = loaderManager.javaClass.getDeclaredField("mContext")
            field.isAccessible = true
            field.get(loaderManager) as? android.content.Context
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 从磁盘删�?Titan 补丁文件�?     * 需要在 Context 可用后调用，避免补丁数据跨重启保留�?     */
    fun deletePatchFiles(context: Context) {
        if (!ConfigManager.isTitanPatchBlockEnabled) return
        if (!patchesCleaned.compareAndSet(false, true)) return

        try {
            val titanDir = File(context.applicationInfo.dataDir, TITAN_DIR)
            if (!titanDir.exists()) {
                XposedCompat.logD("$TAG patch dir does not exist, nothing to delete")
                return
            }
            val deleted = deleteRecursive(titanDir)
            if (deleted > 0) {
                XposedCompat.log("$TAG deleted $deleted Titan patch files/dirs")
            } else {
                XposedCompat.logD("$TAG patch dir was empty")
            }
        } catch (t: Throwable) {
            XposedCompat.logD("$TAG failed to delete patch files: ${t.message}")
        }
    }

    private fun deleteRecursive(file: File): Int {
        var count = 0
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    count += deleteRecursive(child)
                }
            }
        }
        if (file.delete()) count++
        return count
    }

    private fun clearClinitInterceptor(cl: ClassLoader) {
        try {
            val storageClass = Class.forName(CLINIT_STORAGE_CLASS, false, cl)
            val icField = storageClass.getDeclaredField("\$ic")
            icField.isAccessible = true
            icField.set(null, null)
            XposedCompat.logD("$TAG ClassClinitInterceptorStorage.\$ic cleared")
        } catch (_: Throwable) {
            // Non-critical path; load() blocking is the primary mechanism.
        }
    }
}
