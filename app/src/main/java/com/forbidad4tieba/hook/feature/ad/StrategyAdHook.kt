package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object StrategyAdHook {
    private var sHooked = false

    fun hook(cl: ClassLoader) {
        if (sHooked) return
        sHooked = true

        hookAccountData(cl)
        hookSwitchManager(cl)
        hookZga(cl)
    }

    private fun hookAccountData(cl: ClassLoader) {
        val accountClass = XposedHelpers.findClassIfExists(
            "com.baidu.tbadk.core.data.AccountData", cl
        ) ?: return

        val hooks = arrayOf(
            "getMemberCloseAdVipClose" to 1 as Any,
            "isMemberCloseAdVipClose" to true,
            "getMemberCloseAdIsOpen" to 1 as Any,
            "isMemberCloseAdIsOpen" to true,
            "getMemberType" to 2 as Any
        )

        for ((method, value) in hooks) {
            hookReturnConstant(accountClass, method, value)
        }

        // Splash ad forbid (dynamic resolution)
        val symbols = com.forbidad4tieba.hook.HookSymbolResolver.getMemorySymbols()
        
        // SplashForbidAdHelperKt
        val splashClass = symbols?.splashAdHelperClass ?: "com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt"
        val splashMethod = symbols?.splashAdHelperMethod ?: "a"
        hookReturnConstant(splashClass, cl, splashMethod, true)

        // VIP close ad status
        val closeAdClass = symbols?.closeAdDataClass ?: "com.baidu.tbadk.data.CloseAdData"
        val closeAdG1 = symbols?.closeAdDataMethodG1 ?: "G1"
        val closeAdJ1 = symbols?.closeAdDataMethodJ1 ?: "J1"
        hookReturnConstant(closeAdClass, cl, closeAdG1, 1)
        hookReturnConstant(closeAdClass, cl, closeAdJ1, 1)

        // Ad rendering switch / ad container checks
        val nd7Class = symbols?.nd7Class ?: "com.baidu.tieba.nd7"
        val nd7MethodI0 = symbols?.nd7MethodI0 ?: "i0"
        val nd7MethodL = symbols?.nd7MethodL ?: "l"
        
        if (nd7MethodI0 != "getSize" && nd7MethodI0 != "draw") {
            hookReturnConstant(nd7Class, cl, nd7MethodI0, true)
            hookReturnConstant(nd7Class, cl, nd7MethodL, 0)
        }
    }

    private fun hookReturnConstant(targetClass: Class<*>, methodName: String, value: Any) {
        try {
            XposedHelpers.findAndHookMethod(targetClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (ConfigManager.isAdBlockEnabled) param.result = value
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookReturnConstant(className: String, cl: ClassLoader, methodName: String, value: Any) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (ConfigManager.isAdBlockEnabled) param.result = value
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookSwitchManager(cl: ClassLoader) {
        val smClass = XposedHelpers.findClassIfExists(
            "com.baidu.adp.lib.featureSwitch.SwitchManager", cl
        ) ?: return

        val blockedKeys = setOf(
            "ad_baichuan_open",
            "bear_wxb_download",
            "pref_key_fun_ad_sdk_enable"
        )

        try {
            XposedHelpers.findAndHookMethod(
                smClass, "findType", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isAdBlockEnabled) return
                        if (param.args[0] in blockedKeys) {
                            param.result = 0
                        }
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook SwitchManager.findType: ${t.message}")
        }
    }

    private fun hookZga(cl: ClassLoader) {
        val symbols = com.forbidad4tieba.hook.HookSymbolResolver.getMemorySymbols()
        val zgaClassName = symbols?.zgaClass ?: "com.baidu.tieba.zga"
        val zgaClass = XposedHelpers.findClassIfExists(zgaClassName, cl) ?: return

        val safeStringHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.result == null) param.result = ""
            }
        }

        var installed = 0
        val zgaMethods = symbols?.zgaMethods ?: listOf("d", "f")
        for (method in zgaMethods) {
            try {
                XposedHelpers.findAndHookMethod(zgaClass, method, String::class.java, safeStringHook)
                installed++
            } catch (_: Throwable) {}
        }

        if (installed == 0) {
            XposedBridge.log("${Constants.TAG}: zga string hook skipped (no matching methods)")
        }
    }
}
