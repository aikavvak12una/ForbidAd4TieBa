package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.TrackingTarget
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/** Stops shared page tracing and Loki service work. */
object TrackingBlockHook {
    private const val TAG = "[TrackingBlockHook]"
    private val installed = AtomicBoolean(false)

    fun hook(methods: Map<TrackingTarget, Method>) {
        if (!ConfigManager.snapshot().isMonitorSyncComponentsDisabled) return
        if (!installed.compareAndSet(false, true)) return
        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }
        var totalInstalled = 0
        for (target in TrackingTarget.entries) {
            val method = methods[target]
            if (method == null) {
                XposedCompat.log("$TAG ${target.methodName} skipped: verified symbol unavailable")
                continue
            }
            val blockedResult: Any? = when (target) {
                TrackingTarget.LOKI_SERVICE -> 0
                TrackingTarget.PAGE_TRACE -> null
            }
            try {
                RuntimeHooks.builder(mod, method, "TrackingBlockHook", "hook:method").intercept { chain ->
                    if (ConfigManager.snapshot().isMonitorSyncComponentsDisabled) blockedResult else chain.proceed()
                }
                totalInstalled++
            } catch (t: Throwable) {
                XposedCompat.log("$TAG ${target.methodName} install failed: ${t.message}")
            }
        }
        XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled/${TrackingTarget.entries.size}")
    }
}
