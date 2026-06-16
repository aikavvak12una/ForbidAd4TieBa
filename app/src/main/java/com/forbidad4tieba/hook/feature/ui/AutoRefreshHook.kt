package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.forbidad4tieba.hook.symbol.model.AutoRefreshSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object AutoRefreshHook {
    private const val FOREGROUND_BLOCK_WINDOW_MS = 3000L

    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()
    private val foregroundCallbacksRegistered = AtomicBoolean(false)
    private val blockNextAutoRefresh = AtomicBoolean(false)
    private val blockReason = AtomicReference<String?>(null)
    private val blockExpiresAtMs = AtomicLong(Long.MAX_VALUE)
    private val startedActivityCount = AtomicInteger(0)
    private val hasSeenForeground = AtomicBoolean(false)

    internal fun registerForegroundCallbacks(app: Application) {
        if (!ConfigManager.isAutoRefreshDisabled) return
        if (!foregroundCallbacksRegistered.compareAndSet(false, true)) return

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                val previousCount = startedActivityCount.getAndIncrement()
                if (previousCount == 0 && hasSeenForeground.getAndSet(true)) {
                    armNextAutoRefresh("foreground", FOREGROUND_BLOCK_WINDOW_MS)
                }
            }

            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount.updateAndGet { count ->
                    if (count > 0) count - 1 else 0
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        XposedCompat.log("[AutoRefreshHook] foreground callbacks registered")
    }

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
        armNextAutoRefresh("startup")
        XposedCompat.log(
            "[AutoRefreshHook] hook INSTALLED: " +
                "${triggerMethod.declaringClass.name}.${triggerMethod.name}() blockMode=startup-and-foreground",
        )
    }

    private fun installDirectHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isAutoRefreshDisabled) {
                disarmNextAutoRefresh()
                return@intercept chain.proceed()
            }
            if (blockNextAutoRefresh.compareAndSet(true, false)) {
                val reason = blockReason.getAndSet(null) ?: "armed"
                val expiresAtMs = blockExpiresAtMs.getAndSet(Long.MAX_VALUE)
                if (SystemClock.uptimeMillis() > expiresAtMs) {
                    return@intercept chain.proceed()
                }
                XposedCompat.logW("[AutoRefreshHook] blocked $reason refresh: ${method.name}()")
                return@intercept null
            }
            chain.proceed()
        }
        return true
    }

    private fun armNextAutoRefresh(reason: String, validWindowMs: Long = Long.MAX_VALUE) {
        if (!ConfigManager.isAutoRefreshDisabled) return
        blockReason.set(reason)
        blockExpiresAtMs.set(
            if (validWindowMs == Long.MAX_VALUE) {
                Long.MAX_VALUE
            } else {
                SystemClock.uptimeMillis() + validWindowMs
            },
        )
        blockNextAutoRefresh.set(true)
    }

    private fun disarmNextAutoRefresh() {
        blockReason.set(null)
        blockExpiresAtMs.set(Long.MAX_VALUE)
        blockNextAutoRefresh.set(false)
    }

}
