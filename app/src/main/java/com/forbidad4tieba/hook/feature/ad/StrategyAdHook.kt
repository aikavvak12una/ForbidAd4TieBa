package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.StrategyAdSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method

object StrategyAdHook {
    @Volatile private var staticHooked = false
    @Volatile private var symbolHooked = false

    fun hookStatic(
        cl: ClassLoader,
        enableAccountData: Boolean,
        enableSwitchManager: Boolean,
    ) {
        if (!enableAccountData && !enableSwitchManager) return
        if (!tryMarkStaticHooked()) return

        try {
            if (enableAccountData) hookAccountData(cl)
            if (enableSwitchManager) hookSwitchManager(cl)
            XposedCompat.log("[StrategyAdHook] static hooks dispatched")
        } catch (t: Throwable) {
            resetStaticHooked()
            XposedCompat.log("[StrategyAdHook] static hook install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    internal fun hookWithSymbols(targets: StrategyAdSymbols) {
        if (!tryMarkSymbolHooked()) return

        try {
            for (target in targets.constantReturnMethods.distinctBy { it.method }) {
                hookReturnConstant(target.method, target.value)
            }
            hookZga(targets.zgaMethods)
            XposedCompat.log("[StrategyAdHook] symbol-dependent hooks dispatched")
        } catch (t: Throwable) {
            resetSymbolHooked()
            XposedCompat.log("[StrategyAdHook] symbol hook install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookAccountData(cl: ClassLoader) {
        val accountClass = XposedCompat.findClassOrNull(
            StableTiebaHookPoints.ACCOUNT_DATA_CLASS, cl
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

    private fun hookReturnConstant(method: Method, value: Any) {
        val mod = XposedCompat.module ?: return
        try {
            mod.hook(method).intercept { chain ->
                if (ConfigManager.isAdBlockEnabled) {
                    return@intercept value
                }
                chain.proceed()
            }
            XposedCompat.log("[StrategyAdHook] hook INSTALLED: ${method.declaringClass.simpleName}.${method.name}")
        } catch (t: Throwable) { XposedCompat.logD { "StrategyAdHook: ${t.message}" } }
    }

    private fun hookReturnConstant(targetClass: Class<*>, methodName: String, value: Any) {
        val mod = XposedCompat.module ?: return
        val method = XposedCompat.findMethodOrNull(targetClass, methodName)
        if (method == null) {
            XposedCompat.logD { "[StrategyAdHook] method NOT FOUND: ${targetClass.simpleName}.$methodName" }
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
        } catch (t: Throwable) { XposedCompat.logD { "StrategyAdHook: ${t.message}" } }
    }

    private fun hookSwitchManager(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val smClass = XposedCompat.findClassOrNull(
            StableTiebaHookPoints.SWITCH_MANAGER_CLASS, cl
        ) ?: return

        val adContentBlockedKeys = setOf(
            "ad_baichuan_open",
            "bear_wxb_download",
            "pref_key_fun_ad_sdk_enable"
        )
        val adRuntimeBlockedKeys = adContentBlockedKeys + setOf(
            "platform_csj_init",
            "platform_gdt_init",
            "platform_ks_init",
            "platform_ubix_init",
            "12_30_fun_sdk_init_switch"
        )
        val apsarasBlockedKeys = setOf("apsaras_switch")

        val method = XposedCompat.findMethodOrNull(
            smClass,
            StableTiebaHookPoints.METHOD_FIND_TYPE,
            String::class.java,
        )
        if (method == null) {
            XposedCompat.log("[StrategyAdHook] SwitchManager.${StableTiebaHookPoints.METHOD_FIND_TYPE} NOT FOUND")
            return
        }
        try {
            mod.hook(method).intercept { chain ->
                val key = chain.args.firstOrNull() as? String
                val shouldBlock = when {
                    ConfigManager.isAdBlockEnabled && key in adContentBlockedKeys -> true
                    ConfigManager.isAdSdkComponentsDisabled && key in adRuntimeBlockedKeys -> true
                    ConfigManager.isApsarasScheduleDisabled && key in apsarasBlockedKeys -> true
                    else -> false
                }
                if (shouldBlock) {
                    XposedCompat.logD { "[StrategyAdHook] > findType blocked key=$key" }
                    return@intercept 0
                }
                chain.proceed()
            }
            XposedCompat.log("[StrategyAdHook] SwitchManager.${StableTiebaHookPoints.METHOD_FIND_TYPE} hook INSTALLED")
        } catch (t: Throwable) {
            XposedCompat.log("Failed to hook SwitchManager.${StableTiebaHookPoints.METHOD_FIND_TYPE}: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookZga(methods: List<Method>) {
        val mod = XposedCompat.module ?: return
        var installed = 0
        for (method in methods.distinct()) {
            try {
                mod.hook(method).intercept { chain ->
                    if (!ConfigManager.isAdBlockEnabled) return@intercept chain.proceed()
                    ""
                }
                installed++
            } catch (t: Throwable) { XposedCompat.logD { "StrategyAdHook: ${t.message}" } }
        }

        if (installed == 0) {
            XposedCompat.log("[StrategyAdHook] zga string hook skipped (no matching methods)")
        } else {
            XposedCompat.log("[StrategyAdHook] zga string hook INSTALLED: count=$installed")
        }
    }

    private fun tryMarkStaticHooked(): Boolean {
        synchronized(this) {
            if (staticHooked) return false
            staticHooked = true
            return true
        }
    }

    private fun resetStaticHooked() {
        synchronized(this) { staticHooked = false }
    }

    private fun tryMarkSymbolHooked(): Boolean {
        synchronized(this) {
            if (symbolHooked) return false
            symbolHooked = true
            return true
        }
    }

    private fun resetSymbolHooked() {
        synchronized(this) { symbolHooked = false }
    }
}
