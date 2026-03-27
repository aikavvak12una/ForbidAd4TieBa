package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object StrategyAdHook {
    fun hook(cl: ClassLoader) {
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberCloseAdVipClose", 1)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "isMemberCloseAdVipClose", true)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberCloseAdIsOpen", 1)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "isMemberCloseAdIsOpen", true)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberType", 2)
        hookReturnConstant(cl, "com.baidu.tbadk.data.CloseAdData", "G1", 1)
        hookReturnConstant(cl, "com.baidu.tbadk.data.CloseAdData", "J1", 1)
        hookReturnConstant(cl, "com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt", "a", true)
        hookReturnConstant(cl, "com.baidu.tieba.nd7", "i0", true)
        hookReturnConstant(cl, "com.baidu.tieba.nd7", "l", 0)

        hookSwitchManager(cl)
        hookZga(cl)
    }

    private fun hookReturnConstant(cl: ClassLoader, className: String, methodName: String, value: Any) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (ConfigManager.isAdBlockEnabled) param.result = value
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed hook constant $className.$methodName: ${t.message}")
        }
    }

    private fun hookSwitchManager(cl: ClassLoader) {
        try {
            val blockedKeys = hashSetOf(
                "ad_baichuan_open",
                "bear_wxb_download",
                "pref_key_fun_ad_sdk_enable"
            )

            XposedHelpers.findAndHookMethod(
                "com.baidu.adp.lib.featureSwitch.SwitchManager", cl, "findType", String::class.java,
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
        try {
            val safeStringHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result == null) param.result = ""
                }
            }
            val clazz = XposedHelpers.findClassIfExists("com.baidu.tieba.zga", cl) ?: return
            var installed = 0
            for (m in clazz.declaredMethods) {
                val p = m.parameterTypes
                if (p.size == 1 &&
                    p[0] == String::class.java &&
                    m.returnType == String::class.java &&
                    (m.name == "d" || m.name == "f")
                ) {
                    XposedBridge.hookMethod(m, safeStringHook)
                    installed++
                }
            }
            if (installed == 0) {
                XposedBridge.log("${Constants.TAG}: zga string hook skipped (no matching methods)")
            }
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook zga: ${t.message}")
        }
    }
}
