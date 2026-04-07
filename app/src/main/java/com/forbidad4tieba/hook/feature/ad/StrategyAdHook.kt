package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat

/**
 * 策略层广告拦截：伪造 VIP 免广告状态 / 关闭广告 SDK 开关。
 *
 * 分为两个阶段安装：
 * - hookStatic()  ：仅使用非混淆稳定类名，无需 symbols，在 handleLoadPackage 阶段执行。
 * - hookWithSymbols()：依赖反混淆扫描结果，在 Application.attach → symbols 加载后执行。
 */
object StrategyAdHook {
    @Volatile private var staticHooked = false
    @Volatile private var symbolHooked = false

    /**
     * Phase 1 — 静态 hook（非混淆类，可在 handleLoadPackage 阶段安装）。
     */
    fun hookStatic(cl: ClassLoader) {
        if (staticHooked) return
        staticHooked = true

        hookAccountData(cl)
        hookSwitchManager(cl)
        XposedCompat.log("[StrategyAdHook] static hooks dispatched")
    }

    /**
     * Phase 2 — 符号依赖 hook（依赖反混淆扫描结果，在 symbols 加载后安装）。
     */
    fun hookWithSymbols(cl: ClassLoader, symbols: HookSymbols) {
        if (symbolHooked) return
        symbolHooked = true

        val splashClass = symbols.splashAdHelperClass ?: "com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt"
        val splashMethod = symbols.splashAdHelperMethod ?: "a"
        hookReturnConstant(splashClass, cl, splashMethod, true)

        val closeAdClass = symbols.closeAdDataClass ?: "com.baidu.tbadk.data.CloseAdData"
        val closeAdG1 = symbols.closeAdDataMethodG1
        val closeAdJ1 = symbols.closeAdDataMethodJ1
        if (closeAdG1 != null && closeAdJ1 != null) {
            hookReturnConstant(closeAdClass, cl, closeAdG1, 1)
            hookReturnConstant(closeAdClass, cl, closeAdJ1, 1)
        } else {
            XposedCompat.logD("[StrategyAdHook] CloseAdData skipped: methods not resolved by scan")
        }

        val nd7Class = symbols.nd7Class
        val nd7MethodI0 = symbols.nd7MethodI0
        val nd7MethodL = symbols.nd7MethodL
        if (nd7Class != null && nd7MethodI0 != null && nd7MethodL != null &&
            nd7MethodI0 != "getSize" && nd7MethodI0 != "draw") {
            hookReturnConstant(nd7Class, cl, nd7MethodI0, true)
            hookReturnConstant(nd7Class, cl, nd7MethodL, 0)
        }

        hookZga(cl, symbols)
        XposedCompat.log("[StrategyAdHook] symbol-dependent hooks dispatched")
    }

    private fun hookAccountData(cl: ClassLoader) {
        val accountClass = XposedCompat.findClassOrNull(
            "com.baidu.tbadk.core.data.AccountData", cl
        ) ?: return

        val hooks = arrayOf(
            "getMemberCloseAdVipClose" to 1 as Any,
            "isMemberCloseAdVipClose" to true as Any,
            "getMemberCloseAdIsOpen" to 1 as Any,
            "isMemberCloseAdIsOpen" to true as Any,
            "getMemberType" to 2 as Any
        )

        for ((method, value) in hooks) {
            hookReturnConstant(accountClass, method, value)
        }
    }

    private fun hookReturnConstant(targetClass: Class<*>, methodName: String, value: Any) {
        val mod = XposedCompat.module ?: return
        val method = XposedCompat.findMethodOrNull(targetClass, methodName)
        if (method == null) {
            XposedCompat.logD("[StrategyAdHook] method NOT FOUND: ${targetClass.simpleName}.$methodName")
            return
        }
        try {
            mod.hook(method).intercept { chain ->
                if (ConfigManager.isAdBlockEnabled) {
                    return@intercept value
                }
                chain.proceed()
            }
            XposedCompat.log("[StrategyAdHook] hook INSTALLED: ${targetClass.simpleName}.$methodName")
        } catch (t: Throwable) { XposedCompat.logD("StrategyAdHook: ${t.message}") }
    }

    private fun hookReturnConstant(className: String, cl: ClassLoader, methodName: String, value: Any) {
        val clazz = XposedCompat.findClassOrNull(className, cl)
        if (clazz == null) {
            XposedCompat.logD("[StrategyAdHook] class NOT FOUND: $className (for $methodName)")
            return
        }
        hookReturnConstant(clazz, methodName, value)
    }

    private fun hookSwitchManager(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val smClass = XposedCompat.findClassOrNull(
            "com.baidu.adp.lib.featureSwitch.SwitchManager", cl
        ) ?: return

        val blockedKeys = setOf(
            "ad_baichuan_open",
            "bear_wxb_download",
            "pref_key_fun_ad_sdk_enable"
        )

        val method = XposedCompat.findMethodOrNull(smClass, "findType", String::class.java)
        if (method == null) {
            XposedCompat.log("[StrategyAdHook] SwitchManager.findType NOT FOUND")
            return
        }
        try {
            mod.hook(method).intercept { chain ->
                if (ConfigManager.isAdBlockEnabled) {
                    val key = chain.args.firstOrNull()
                    if (key in blockedKeys) {
                        XposedCompat.logD("[StrategyAdHook] > findType blocked key=$key")
                        return@intercept 0
                    }
                }
                chain.proceed()
            }
            XposedCompat.log("[StrategyAdHook] SwitchManager.findType hook INSTALLED")
        } catch (t: Throwable) {
            XposedCompat.log("Failed to hook SwitchManager.findType: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookZga(cl: ClassLoader, symbols: HookSymbols) {
        val mod = XposedCompat.module ?: return
        val zgaClassName = symbols.zgaClass ?: return
        val zgaClass = XposedCompat.findClassOrNull(zgaClassName, cl) ?: return

        var installed = 0
        val zgaMethods = symbols.zgaMethods ?: return
        for (methodName in zgaMethods) {
            val method = XposedCompat.findMethodOrNull(zgaClass, methodName, String::class.java) ?: continue
            try {
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    result ?: ""
                }
                installed++
            } catch (t: Throwable) { XposedCompat.logD("StrategyAdHook: ${t.message}") }
        }

        if (installed == 0) {
            XposedCompat.log("[StrategyAdHook] zga string hook skipped (no matching methods)")
        }
    }
}
