package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 禁止首页自动刷新。
 *
 * PersonalizePageView 的刷新方法名会随版本变化（如 x1 → r1），
 * 通过 HookSymbolResolver 动态解析后传入。
 *
 * 策略：检查调用栈中是否存在用户点击事件 (onClick)，有则放行，否则拦截。
 * 手动下拉刷新不经过此方法，天然无影响。
 */
object AutoRefreshHook {

    private const val PPV_CLASS = "com.baidu.tieba.homepage.personalize.PersonalizePageView"

    /**
     * @param cl 目标应用 ClassLoader
     * @param resolvedMethodName 通过 HookSymbolResolver 解析到的刷新方法名，为 null 则跳过
     */
    fun hook(cl: ClassLoader, resolvedMethodName: String?) {
        if (!ConfigManager.isAutoRefreshDisabled) return

        val methodName = resolvedMethodName
        if (methodName == null) {
            XposedBridge.log("${Constants.TAG}: AutoRefreshHook skipped: refresh method not resolved")
            return
        }

        try {
            val ppvClass = XposedHelpers.findClassIfExists(PPV_CLASS, cl)
            if (ppvClass == null) {
                XposedBridge.log("${Constants.TAG}: AutoRefreshHook skipped: class $PPV_CLASS not found")
                return
            }

            XposedHelpers.findAndHookMethod(
                ppvClass,
                methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val stack = Thread.currentThread().stackTrace
                        for (frame in stack) {
                            if (frame.methodName == "onClick") return // 用户交互，放行
                        }
                        param.result = null // 自动触发，拦截
                    }
                }
            )
            XposedBridge.log("${Constants.TAG}: AutoRefreshHook installed on $PPV_CLASS.$methodName()")
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: AutoRefreshHook failed: ${t.message}")
        }
    }
}
