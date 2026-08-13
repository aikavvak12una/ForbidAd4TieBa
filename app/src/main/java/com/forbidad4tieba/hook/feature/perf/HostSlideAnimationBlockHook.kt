package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 关闭宿主全局滑动动画。
 *
 * 宿主滑动动画由云控 key 控制（TbSingleton.isSlideAnimationOn）：
 * - sync_slide_animation__switch（int，云控同步源）
 * - local_slide_animation__switch（boolean，本地生效值）
 * 两者都经 SharedPrefHelper 读取。本 hook 把两个读取都强制为"关闭"：
 * 即使云控开启滑动动画，全局滑动动画也保持关闭，列表/页面切换更跟手。
 *
 * 取舍：动画关闭后页面切换/列表滑动的过渡效果会消失（视觉变"硬"），
 * 但主线程动画编排开销下降，低端机滑动帧率更稳定。
 */
object HostSlideAnimationBlockHook {
    private const val TAG = "[HostSlideAnimationBlockHook]"
    private const val SHARED_PREF_HELPER_CLASS = "com.baidu.tbadk.core.sharedPref.SharedPrefHelper"
    private const val KEY_SYNC_SLIDE_ANIMATION = "sync_slide_animation__switch"
    private const val KEY_LOCAL_SLIDE_ANIMATION = "local_slide_animation__switch"

    private val installed = AtomicBoolean(false)

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isHostSlideAnimationDisabled) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        val clazz = XposedCompat.findClassOrNull(SHARED_PREF_HELPER_CLASS, cl)
        if (clazz == null) {
            installed.set(false)
            XposedCompat.log("$TAG class NOT FOUND: $SHARED_PREF_HELPER_CLASS")
            return
        }

        var installedCount = 0

        // 云控同步源：getInt("sync_slide_animation__switch") -> 0
        XposedCompat.findMethodOrNull(
            clazz,
            "getInt",
            String::class.java,
            Int::class.javaPrimitiveType!!,
        )?.let { method ->
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (key == KEY_SYNC_SLIDE_ANIMATION && ConfigManager.isHostSlideAnimationDisabled) {
                        return@intercept 0
                    }
                    chain.proceed()
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG getInt skipped: ${it.message}" } }
        }

        // 本地生效值：getBoolean("local_slide_animation__switch") -> false
        XposedCompat.findMethodOrNull(
            clazz,
            "getBoolean",
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (key == KEY_LOCAL_SLIDE_ANIMATION && ConfigManager.isHostSlideAnimationDisabled) {
                        return@intercept false
                    }
                    chain.proceed()
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG getBoolean skipped: ${it.message}" } }
        }

        if (installedCount > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$installedCount")
        } else {
            installed.set(false)
            XposedCompat.logD("$TAG no slide animation methods found")
        }
    }
}
