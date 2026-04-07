package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat

/**
 * 禁止首页自动刷新（多版本自适应方案）。
 *
 * ── 版本适配策略 ──
 *
 * PersonalizePageView 中的刷新方法（如 x1/r1）是混淆名，随版本变化。
 * 该类有 14+ 个签名完全相同的 public void() 方法，纯反射无法可靠区分。
 *
 * 本方案使用 **运行时探针** 自动发现刷新方法：
 *
 * 1. 在 BigdaySwipeRefreshLayout.setRefreshing(boolean) 安装探针。
 *    该类名和方法名均不受混淆影响（继承自 AndroidX SwipeRefreshLayout）。
 *
 * 2. 当 setRefreshing(true) 被调用时，通过调用栈定位 PersonalizePageView 中
 *    的直接调用者——该调用者即为刷新方法。
 *
 * 3. 首次发现后，在刷新方法上安装高效的直接拦截 hook，后续不再走探针路径。
 *
 * ── 拦截策略 ──
 *
 * 检查调用栈中是否存在 onClick → 有则放行（用户手动刷新），否则拦截（自动刷新）。
 * 手动下拉刷新由 BigdaySwipeRefreshLayout 内部处理，不经过刷新方法，天然不受影响。
 */
object AutoRefreshHook {

    private const val PPV_CLASS =
        "com.baidu.tieba.homepage.personalize.PersonalizePageView"
    private const val BIGDAY_SRL_CLASS =
        "com.baidu.tieba.homepage.personalize.bigday.BigdaySwipeRefreshLayout"

    @Volatile
    private var directHookInstalled = false

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isAutoRefreshDisabled) return
        val mod = XposedCompat.module ?: return

        val ppvClass = XposedCompat.findClassOrNull(PPV_CLASS, cl)
        if (ppvClass == null) {
            XposedCompat.log("[AutoRefreshHook] skipped: class $PPV_CLASS not found")
            return
        }

        val srlClass = XposedCompat.findClassOrNull(BIGDAY_SRL_CLASS, cl)
        if (srlClass == null) {
            XposedCompat.log("[AutoRefreshHook] skipped: BigdaySwipeRefreshLayout not found")
            return
        }

        val setRefreshing = findMethodInHierarchy(srlClass, "setRefreshing", Boolean::class.javaPrimitiveType!!)
        if (setRefreshing == null) {
            XposedCompat.log("[AutoRefreshHook] skipped: setRefreshing(boolean) not found")
            return
        }

        mod.hook(setRefreshing).intercept { chain ->
            // 直接 hook 已安装后，探针退出
            if (directHookInstalled) return@intercept chain.proceed()

            // 只关心 setRefreshing(true)
            val refreshing = chain.args[0] as? Boolean ?: return@intercept chain.proceed()
            if (!refreshing) return@intercept chain.proceed()

            // 分析调用栈
            val stack = Thread.currentThread().stackTrace
            var ppvMethodName: String? = null
            var hasUserClick = false

            for (frame in stack) {
                if (ppvMethodName == null &&
                    frame.className == PPV_CLASS &&
                    frame.methodName.length <= 3 &&
                    frame.methodName != "setRefreshing"
                ) {
                    ppvMethodName = frame.methodName
                }
                if (frame.methodName == "onClick") {
                    hasUserClick = true
                }
            }

            // 不是从 PPV 调用的 → 非自动刷新（下拉刷新等），放行
            if (ppvMethodName == null) return@intercept chain.proceed()

            // 有用户点击事件 → 用户主动刷新，放行
            if (hasUserClick) return@intercept chain.proceed()

            // ── 自动刷新路径命中 ──
            XposedCompat.log("[AutoRefreshHook] probe discovered refresh method: $ppvMethodName")

            // 安装直接 hook 以高效拦截后续调用
            val targetMethod = XposedCompat.findMethodOrNull(ppvClass, ppvMethodName)
            if (targetMethod != null) {
                installDirectHook(targetMethod)
            }

            // 阻断本次自动刷新
            XposedCompat.logD("[AutoRefreshHook] blocked via probe: $ppvMethodName()")
            null
        }

        XposedCompat.log("[AutoRefreshHook] probe INSTALLED on setRefreshing(boolean)")
    }

    /**
     * 在刷新方法上安装直接拦截 hook。
     * 策略：调用栈中有 onClick 则放行，否则拦截。
     */
    private fun installDirectHook(method: java.lang.reflect.Method) {
        if (directHookInstalled) return
        synchronized(this) {
            if (directHookInstalled) return
            val mod = XposedCompat.module ?: return

            mod.hook(method).intercept { chain ->
                val stack = Thread.currentThread().stackTrace
                for (frame in stack) {
                    if (frame.methodName == "onClick") {
                        return@intercept chain.proceed()
                    }
                }
                XposedCompat.logD("[AutoRefreshHook] blocked: ${method.name}()")
                null
            }

            directHookInstalled = true
            XposedCompat.log("[AutoRefreshHook] direct hook INSTALLED: $PPV_CLASS.${method.name}()")
        }
    }

    private fun findMethodInHierarchy(
        cls: Class<*>,
        name: String,
        vararg paramTypes: Class<*>,
    ): java.lang.reflect.Method? {
        var current: Class<*>? = cls
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, *paramTypes)
                    .apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }
}
