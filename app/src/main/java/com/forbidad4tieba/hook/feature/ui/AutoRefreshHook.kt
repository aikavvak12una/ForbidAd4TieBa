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
    private const val STARTUP_BLOCK_WINDOW_MS = 10000L

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
        if (installDirectHook(mod, triggerMethod, blockNetworkSend = false)) {
            XposedCompat.log(
                "[AutoRefreshHook] trigger hook INSTALLED: " +
                    "${triggerMethod.declaringClass.name}.${triggerMethod.name}()",
            )
        } else {
            XposedCompat.log("[AutoRefreshHook] already installed: ${ReflectionUtils.methodSignature(triggerMethod)}")
        }
        val netRequestMethods = targets.netRequestMethods
        for (netRequestMethod in netRequestMethods) {
            if (installDirectHook(mod, netRequestMethod, blockNetworkSend = true)) {
                XposedCompat.log(
                    "[AutoRefreshHook] net-request hook INSTALLED: " +
                        "${netRequestMethod.declaringClass.name}.${netRequestMethod.name}()",
                )
            } else {
                XposedCompat.log("[AutoRefreshHook] already installed: ${ReflectionUtils.methodSignature(netRequestMethod)}")
            }
        }
        val cacheRestoreMethod = targets.cacheRestoreMethod
        if (cacheRestoreMethod != null && installCacheRestoreHook(mod, cacheRestoreMethod)) {
            XposedCompat.log(
                "[AutoRefreshHook] cache-restore hook INSTALLED: " +
                    "${cacheRestoreMethod.declaringClass.name}.${cacheRestoreMethod.name}()",
            )
        }
        installRefreshAnimationHook(mod, triggerMethod.declaringClass.classLoader)
        installUserInteractionHook(mod, triggerMethod.declaringClass.classLoader)
        armNextAutoRefresh("startup", STARTUP_BLOCK_WINDOW_MS)
        val netDesc = netRequestMethods.joinToString(" + ") { "${it.declaringClass.name}.${it.name}()" }
        XposedCompat.log(
            "[AutoRefreshHook] hook INSTALLED: " +
                "${triggerMethod.declaringClass.name}.${triggerMethod.name}() + $netDesc " +
                "blockMode=startup-and-foreground",
        )
    }

    /**
     * LowScoreScheduler.C(taskId) decides whether a host task is blocked by the
     * low-score scheduler. When the host disables home caching, the cold-start
     * feed has no cached data after the auto-refresh is blocked. Returning false
     * for "disable_home_cache" restores the host's own home-cache read/write path
     * so the last-seen feed renders without a network refresh. All other taskIds
     * keep the host's original behavior.
     */
    private fun installCacheRestoreHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false
        val key = "disable_home_cache"
        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isAutoRefreshDisabled) {
                return@intercept chain.proceed()
            }
            val taskId = chain.args.firstOrNull() as? String
            if (taskId == key) {
                XposedCompat.logD("[AutoRefreshHook] cache restore: $key -> false")
                return@intercept false
            }
            chain.proceed()
        }
        return true
    }

    /**
     * The pull-to-refresh loading animation is shown by
     * BigdaySwipeRefreshLayout.setRefreshing(true) during cold start. With the
     * auto-refresh blocked there is no completion callback to hide it, so the
     * animation would spin forever. Skipping setRefreshing(true) while the
     * startup window is armed keeps the cached feed visible without the stuck
     * spinner, without touching the refresh mechanism itself (the progress view
     * setup in W0 and the pull gesture still work). Once the user interacts and
     * the window is disarmed, setRefreshing behaves normally again.
     */
    private fun installRefreshAnimationHook(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ) {
        val refreshClass = XposedCompat.findClassOrNull(
            "com.baidu.tieba.homepage.personalize.bigday.BigdaySwipeRefreshLayout",
            cl,
        ) ?: return
        val method = runCatching {
            refreshClass.getDeclaredMethod("setRefreshing", Boolean::class.javaPrimitiveType)
        }.getOrNull() ?: return
        runCatching {
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (!ConfigManager.isAutoRefreshDisabled) {
                    return@intercept chain.proceed()
                }
                val refreshing = chain.args.firstOrNull() as? Boolean
                if (refreshing == true &&
                    blockNextAutoRefresh.get() &&
                    SystemClock.uptimeMillis() <= blockExpiresAtMs.get()
                ) {
                    XposedCompat.logD("[AutoRefreshHook] skip refreshing animation (auto-refresh blocked)")
                    return@intercept null
                }
                chain.proceed()
            }
            XposedCompat.log("[AutoRefreshHook] refresh-animation hook INSTALLED")
        }.onFailure { t ->
            XposedCompat.logD("[AutoRefreshHook] refresh-animation hook skipped: ${t.message}")
        }
    }

    /**
     * Activity.onUserInteraction() fires on real user interaction with the
     * activity. It is the manual boundary for the auto-refresh block: once the
     * user interacts with the app (including the pull-to-refresh gesture), the
     * startup window is disarmed so their own refresh requests pass through.
     * Cold start itself produces no user interaction, so the automatic refresh
     * chain stays blocked.
     */
    private fun installUserInteractionHook(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ) {
        val activityClass = XposedCompat.findClassOrNull("android.app.Activity", cl) ?: return
        val method = runCatching {
            activityClass.getDeclaredMethod("onUserInteraction")
        }.getOrNull() ?: return
        runCatching {
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (blockNextAutoRefresh.compareAndSet(true, false)) {
                    blockReason.set(null)
                    blockExpiresAtMs.set(Long.MAX_VALUE)
                    XposedCompat.logD("[AutoRefreshHook] user interaction, auto-refresh window disarmed")
                }
                chain.proceed()
            }
            XposedCompat.log("[AutoRefreshHook] user-interaction boundary hook INSTALLED")
        }.onFailure { t ->
            XposedCompat.logD("[AutoRefreshHook] user-interaction hook skipped: ${t.message}")
        }
    }

    private fun installDirectHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
        blockNetworkSend: Boolean,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        // Cold start fires the refresh chain multiple times (w1/B0 -> k1 ->
        // RecPersonalizePageModel.m/r). Keep blocking every request send while
        // the startup window is armed so no auto-refresh escapes. The window is
        // lifted by Activity.onUserInteraction() (see installUserInteractionHook)
        // so the user's own pull-to-refresh after launch works normally.

        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isAutoRefreshDisabled) {
                disarmNextAutoRefresh()
                return@intercept chain.proceed()
            }
            if (!blockNetworkSend) {
                return@intercept chain.proceed()
            }
            if (!blockNextAutoRefresh.get()) {
                return@intercept chain.proceed()
            }
            val expiresAtMs = blockExpiresAtMs.get()
            if (SystemClock.uptimeMillis() > expiresAtMs) {
                disarmNextAutoRefresh()
                return@intercept chain.proceed()
            }
            val reason = blockReason.get() ?: "armed"
            XposedCompat.logW("[AutoRefreshHook] blocked $reason refresh: ${method.name}()")
            return@intercept null
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
