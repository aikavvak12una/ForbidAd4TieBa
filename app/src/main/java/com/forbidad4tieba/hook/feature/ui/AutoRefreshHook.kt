package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.forbidad4tieba.hook.symbol.model.AutoRefreshSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object AutoRefreshHook {
    /**
     * Every home-feed refresh chain (cold start, background->foreground, 30-min
     * hot reload, stale-cache reload, message refresh, pull-to-refresh, home-tab
     * switch) funnels into RecPersonalizePageModel.m/r request methods; see
     * [installNetRequestHook] for why that funnel is the single interception
     * point. After a genuine user refresh gesture (pull-to-refresh release or
     * home-tab switch) requests are allowed for this long. The host refresh
     * chain is asynchronous (AsyncTask -> cache read -> idle handler ->
     * request), so the window must outlive the whole chain, but stays short
     * enough that later automatic refreshes are blocked again.
     */

    private const val USER_REFRESH_GRACE_MS = 8000L

    /**
     * User-gesture hook points resolved by install-time lookup on the readable
     * host classes in [StableTiebaHookPoints]. Verified against host versions
     * 22.9.1.0 and 22.0.1.0; if a lookup fails the hook is skipped (the auto
     * refresh stays blocked, only that manual gesture loses its exemption).
     */
    private const val PULL_RELEASE_METHOD = "F"
    private const val TAB_SELECTION_CHANGED_METHOD = "onTabSelectionChanged"

    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()
    private val foregroundCallbacksRegistered = AtomicBoolean(false)
    /** End (uptimeMillis) of the user-refresh grace window. 0 = no grace. */
    private val userRefreshUntilMs = AtomicLong(0L)

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
                    beginAutomaticPhase("foreground")
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
        val netRequestMethods = targets.netRequestMethods
        for (netRequestMethod in netRequestMethods) {
            if (installNetRequestHook(mod, netRequestMethod)) {
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
        val hostClassLoader = triggerMethod.declaringClass.classLoader
            ?: run {
                XposedCompat.log("[AutoRefreshHook] skipped: host classloader unavailable")
                return
            }
        installRefreshAnimationHook(mod, hostClassLoader)
        installPullGestureHook(mod, hostClassLoader)
        installTabClickGestureHook(mod, hostClassLoader)

        beginAutomaticPhase("startup")

        val netDesc = netRequestMethods.joinToString(" + ") { "${it.declaringClass.name}.${it.name}()" }
        XposedCompat.log(
            "[AutoRefreshHook] hook INSTALLED: $netDesc blockMode=all-auto-except-user-gesture",
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
     * BigdaySwipeRefreshLayout.setRefreshing(true) during automatic refreshes.
     * With the auto-refresh blocked there is no completion callback to hide it,
     * so the animation would spin forever. Skipping setRefreshing(true) when the
     * refresh is NOT inside a user-gesture grace window keeps the cached feed
     * visible without the stuck spinner, while the user's own pull-to-refresh
     * (which marks the grace window) still shows the spinner normally.
     */
    private fun installRefreshAnimationHook(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ) {
        val refreshClass = XposedCompat.findClassOrNull(
            StableTiebaHookPoints.HOME_SWIPE_REFRESH_LAYOUT_CLASS,
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
                if (refreshing == true && SystemClock.uptimeMillis() > userRefreshUntilMs.get()) {
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
     * The user's pull-to-refresh gesture ends in
     * BigdaySwipeRefreshLayout.F(true, true) (from the pull-release handler),
     * while the programmatic path goes through setRefreshing(boolean) ->
     * F(z, false). F(true, true) is therefore the unambiguous marker for a
     * real pull gesture. The method name/signature is stable across the
     * supported host versions.
     */
    private fun installPullGestureHook(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ) {
        val refreshClass = XposedCompat.findClassOrNull(
            StableTiebaHookPoints.HOME_SWIPE_REFRESH_LAYOUT_CLASS,
            cl,
        ) ?: return
        val method = runCatching {
            refreshClass.getDeclaredMethod(
                PULL_RELEASE_METHOD,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
        }.getOrNull() ?: return
        runCatching {
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val refreshing = chain.args.firstOrNull() as? Boolean
                val fromUserGesture = chain.args.getOrNull(1) as? Boolean
                if (refreshing == true && fromUserGesture == true) {
                    markUserRefresh("pull")
                }
                chain.proceed()
            }
            XposedCompat.log("[AutoRefreshHook] pull-gesture hook INSTALLED")
        }.onFailure { t ->
            XposedCompat.logD("[AutoRefreshHook] pull-gesture hook skipped: ${t.message}")
        }
    }

    /**
     * Every bottom-tab button click funnels into
     * FragmentTabHost.onTabSelectionChanged(int, boolean): the tab views are
     * wrapped by FragmentTabWidget with click listeners that call this
     * interface method, and it is the first thing to run on the click — before
     * setCurrentTab/setPrimary/onPrimary and therefore before the refresh
     * chain. It is only reachable from real user clicks (programmatic tab
     * switches and the startup ViewPager init go through setCurrentTab /
     * setPrimaryItem directly), so it needs no user-interaction guard and also
     * covers re-clicking the current home tab.
     */
    private fun installTabClickGestureHook(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ) {
        val tabHostClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS, cl) ?: return
        val method = runCatching {
            tabHostClass.getDeclaredMethod(
                TAB_SELECTION_CHANGED_METHOD,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
        }.getOrNull() ?: return
        runCatching {
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                markUserRefresh("tab")
                chain.proceed()
            }
            XposedCompat.log("[AutoRefreshHook] tab-click gesture hook INSTALLED")
        }.onFailure { t ->
            XposedCompat.logD("[AutoRefreshHook] tab-click gesture hook skipped: ${t.message}")
        }
    }

    /**
     * The single interception point for automatic home-feed refreshes.
     *
     * All home-feed refresh chains funnel into RecPersonalizePageModel.m/r
     * requests: cold-start and background->foreground refresh (w1/B0 chain),
     * the 30-minute hot reload, the stale-cache reload (n2b), the
     * HomePageRefreshEvent message, the pull-to-refresh gesture and the
     * home-tab switch. Intercepting further upstream (w1/B0/jza.k1) would also
     * stop the cold-start cached-feed render, which lives in the same chain, so
     * the request funnel is the only gate that blocks the network refresh
     * without emptying the cached feed.
     *
     * A request is allowed only when it is (a) a load-more request
     * (RecPersonalizePageModel.m with loadType >= 2, which never resets the
     * feed) or (b) issued inside the short user-gesture window marked by a real
     * pull-to-refresh release or home-tab switch. Everything else — automatic
     * refreshes of any kind — is blocked.
     */
    private fun installNetRequestHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isAutoRefreshDisabled) {
                return@intercept chain.proceed()
            }
            // Load-more (loadType >= 2) must never be blocked; only the
            // reload/refresh type (loadType == 1) is an auto-refresh target.
            val loadType = chain.args.firstOrNull() as? Int
            if (loadType != null && loadType >= 2) {
                return@intercept chain.proceed()
            }
            if (SystemClock.uptimeMillis() <= userRefreshUntilMs.get()) {
                return@intercept chain.proceed()
            }
            XposedCompat.logW("[AutoRefreshHook] blocked auto refresh: ${method.name}()")
            return@intercept null
        }
        return true
    }

    private fun markUserRefresh(reason: String) {
        if (!ConfigManager.isAutoRefreshDisabled) return
        userRefreshUntilMs.set(SystemClock.uptimeMillis() + USER_REFRESH_GRACE_MS)
        XposedCompat.logD("[AutoRefreshHook] user refresh gesture: $reason")
    }

    /**
     * Enters an automatic phase (startup or background->foreground). Any
     * leftover user-gesture grace is cleared so the auto refresh fired by the
     * lifecycle chain is blocked, not accidentally attributed to an older
     * gesture.
     */
    private fun beginAutomaticPhase(reason: String) {
        if (!ConfigManager.isAutoRefreshDisabled) return
        userRefreshUntilMs.set(0L)
        XposedCompat.logD("[AutoRefreshHook] automatic phase: $reason")
    }
}
