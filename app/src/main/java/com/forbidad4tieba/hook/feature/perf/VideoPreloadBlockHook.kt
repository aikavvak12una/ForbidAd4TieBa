package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 阻断信息流视频预加载，减少网络、内存和 CPU 开销。
 *
 * 目标应用会通过这些入口预加载后续信息流视频：
 * 1. PreLoadVideoSwitchManager.isOpen() 做开关判断。
 * 2. PreLoadVideoHelper.load() 做调度。
 * 3. DuMediaPrefetch.prefetch() 和 preConnect() 发起网络预取。
 *
 * 三层都 hook 后，不管哪条路径触发，都不会继续预取视频。
 *
 * 由视频组件开关或统一预加载运行期开关控制。
 */
object VideoPreloadBlockHook {
    private const val TAG = "[VideoPreloadBlockHook]"

    private const val PRELOAD_SWITCH_MANAGER_CLASS =
        "com.baidu.tbadk.core.util.videoPreload.PreLoadVideoSwitchManager"
    private const val PRELOAD_VIDEO_HELPER_CLASS =
        "com.baidu.tbadk.core.util.videoPreload.PreLoadVideoHelper"
    private const val DU_MEDIA_PREFETCH_CLASS =
        "com.baidu.cyberplayer.sdk.DuMediaPrefetch"

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

        // 1. hook PreLoadVideoSwitchManager.isOpen()，返回 false。
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

        // 2. hook PreLoadVideoHelper.load()，直接空返回。
        val helperClass = XposedCompat.findClassOrNull(PRELOAD_VIDEO_HELPER_CLASS, cl)
        if (helperClass != null) {
            val loadMethods = helperClass.declaredMethods.filter { method ->
                method.name == "load" && Modifier.isStatic(method.modifiers)
            }
            for (method in loadMethods) {
                try {
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) return@intercept null
                        chain.proceed()
                    }
                    totalInstalled++
                } catch (t: Throwable) {
                    XposedCompat.logD { "$TAG hook ${helperClass.name}.${method.name} skipped: ${t.message}" }
                }
            }
        }

        // 3. hook DuMediaPrefetch.prefetch() 和 preConnect()，直接空返回。
        val prefetchClass = XposedCompat.findClassOrNull(DU_MEDIA_PREFETCH_CLASS, cl)
        if (prefetchClass != null) {
            val targetMethods = prefetchClass.declaredMethods.filter { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    (method.name == "prefetch" || method.name == "preConnect")
            }
            for (method in targetMethods) {
                try {
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) return@intercept null
                        chain.proceed()
                    }
                    totalInstalled++
                } catch (t: Throwable) {
                    XposedCompat.logD { "$TAG hook ${prefetchClass.name}.${method.name} skipped: ${t.message}" }
                }
            }
        }

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled")
        } else {
            installed.set(false)
            XposedCompat.logD("$TAG no video preload methods found in this version")
        }
    }

    private fun isEnabled(): Boolean {
        return ConfigManager.isPreloadRuntimeDisabled || ConfigManager.isVideoComponentsDisabled
    }
}
