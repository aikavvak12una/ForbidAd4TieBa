package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 阻断信息流视频预加载，减少网络、内存和 CPU 开销。
 *
 * 目标应用会通过这些入口预加载后续信息流视频：
 * 1. PreLoadVideoSwitchManager.isOpen() 做开关判断。
 * 2. DuMediaPrefetch.prefetch() 和 preConnect() 发起网络预取。
 *
 * 这两层都使用稳定类/方法签名，避免业务层扫描 PreLoadVideoHelper.load(...) 的混淆参数类型。
 *
 * 由视频组件开关控制。
 */
object VideoPreloadBlockHook {
    private const val TAG = "[VideoPreloadBlockHook]"

    private const val PRELOAD_SWITCH_MANAGER_CLASS =
        "com.baidu.tbadk.core.util.videoPreload.PreLoadVideoSwitchManager"
    private const val DU_MEDIA_PREFETCH_CLASS =
        "com.baidu.cyberplayer.sdk.DuMediaPrefetch"
    private const val DU_MEDIA_PREFETCH_CONFIG_CLASS =
        "com.baidu.cyberplayer.sdk.DuMediaPrefetch\$PrefetchConfig"

    private val installed = AtomicBoolean(false)

    fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        var totalInstalled = 0

        // Hook PreLoadVideoSwitchManager.isOpen() and return false.
        val switchClass = XposedCompat.findClassOrNull(PRELOAD_SWITCH_MANAGER_CLASS, cl)
        if (switchClass != null) {
            val isOpenMethod = XposedCompat.findMethodOrNull(switchClass, "isOpen")
            if (isOpenMethod != null) {
                try {
                    mod.hook(isOpenMethod).intercept { chain ->
                        if (isEnabled()) return@intercept false
                        chain.proceed()
                    }
                    totalInstalled++
                } catch (t: Throwable) {
                    XposedCompat.logD { "$TAG hook isOpen skipped: ${t.message}" }
                }
            }
        }

        // Hook DuMediaPrefetch network prefetch entry points.
        val prefetchClass = XposedCompat.findClassOrNull(DU_MEDIA_PREFETCH_CLASS, cl)
        val prefetchConfigClass = XposedCompat.findClassOrNull(DU_MEDIA_PREFETCH_CONFIG_CLASS, cl)
        if (prefetchClass != null && prefetchConfigClass != null) {
            totalInstalled += installStaticVoidMethod(
                prefetchClass,
                "prefetch",
                String::class.java,
                java.lang.Integer.TYPE,
                prefetchConfigClass,
            )
            totalInstalled += installStaticVoidMethod(
                prefetchClass,
                "preConnect",
                String::class.java,
                java.lang.Integer.TYPE,
                prefetchConfigClass,
            )
        } else if (prefetchClass != null) {
            XposedCompat.logD("$TAG prefetch config class not found")
        }

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled")
        } else {
            installed.set(false)
            XposedCompat.logD("$TAG no video preload methods found in this version")
        }
    }

    private fun isEnabled(): Boolean {
        return ConfigManager.isVideoComponentsDisabled
    }

    private fun installStaticVoidMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        val method = XposedCompat.findMethodOrNull(clazz, methodName, *parameterTypes)
            ?.takeIf { it.returnType == Void.TYPE }
            ?: run {
                XposedCompat.logD { "$TAG method not found: ${clazz.name}.$methodName" }
                return 0
            }
        return installShortCircuitHook(clazz, method)
    }

    private fun installShortCircuitHook(clazz: Class<*>, method: Method): Int {
        val mod = XposedCompat.module ?: return 0
        return try {
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (isEnabled()) return@intercept null
                chain.proceed()
            }
            1
        } catch (t: Throwable) {
            XposedCompat.logD { "$TAG hook ${clazz.name}.${method.name} skipped: ${t.message}" }
            0
        }
    }
}
