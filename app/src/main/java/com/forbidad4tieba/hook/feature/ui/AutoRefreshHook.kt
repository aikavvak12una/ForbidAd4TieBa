package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.symbol.model.AutoRefreshSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object AutoRefreshHook {

    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()
    private val blockNextAutoRefresh = AtomicBoolean(false)

    internal fun hook(targets: AutoRefreshSymbols) {
        if (!ConfigManager.isAutoRefreshDisabled) {
            XposedCompat.log("[AutoRefreshHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        val triggerMethod = targets.triggerMethod
        if (!installDirectHook(mod, triggerMethod)) {
            XposedCompat.log("[AutoRefreshHook] already installed: ${ReflectionUtils.methodSignature(triggerMethod)}")
            return
        }
        blockNextAutoRefresh.set(true)
        XposedCompat.log(
            "[AutoRefreshHook] hook INSTALLED: " +
                "${triggerMethod.declaringClass.name}.${triggerMethod.name}() blockMode=first-call",
        )
    }

    private fun installDirectHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        mod.hook(method).intercept { chain ->
            if (blockNextAutoRefresh.compareAndSet(true, false)) {
                XposedCompat.logD {
                    "[AutoRefreshHook] blocked first startup refresh: ${method.name}()"
                }
                return@intercept null
            }
            chain.proceed()
        }
        return true
    }

}
