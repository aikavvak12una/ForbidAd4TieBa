package com.forbidad4tieba.hook.feature.ui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object HomeTopTabAutoHideHook {
    private const val APP_BAR_LAYOUT_CLASS =
        "com.google.android.material.appbar.AppBarLayout"
    private const val APP_BAR_BEHAVIOR_CLASS =
        "com.google.android.material.appbar.AppBarLayout\$Behavior"
    private const val APP_BAR_LAYOUT_PARAMS_CLASS =
        "com.google.android.material.appbar.AppBarLayout\$LayoutParams"
    private const val APP_BAR_SCROLLING_VIEW_BEHAVIOR_CLASS =
        "com.google.android.material.appbar.AppBarLayout\$ScrollingViewBehavior"
    private const val COORDINATOR_LAYOUT_CLASS =
        "androidx.coordinatorlayout.widget.CoordinatorLayout"
    private const val METHOD_DISPATCH_ON_SCROLLED = "dispatchOnScrolled"
    private const val METHOD_DISPATCH_ON_PAGE_SELECTED = "dispatchOnPageSelected"
    private const val METHOD_ON_START_NESTED_SCROLL = "onStartNestedScroll"
    private const val METHOD_SET_FORM = "setForm"
    private const val METHOD_SET_EXPANDED = "setExpanded"
    private const val SCROLL_FLAG_SCROLL_FALLBACK = 1
    private const val SCROLL_FLAG_ENTER_ALWAYS_FALLBACK = 4
    private const val HOME_VIEW_PAGER_FORM = 1
    private const val APP_BAR_MISS_RETRY_INTERVAL_MS = 1000L

    private val targetClassNames = arrayOf(
        StableTiebaHookPoints.HOME_SCROLL_TAB_BAR_LAYOUT_CLASS,
        StableTiebaHookPoints.HOME_FIXED_APP_BAR_LAYOUT_CLASS,
    )
    private val appBarCache = Collections.synchronizedMap(WeakHashMap<View, ViewGroup>())
    private val appBarMissRetryAt = Collections.synchronizedMap(WeakHashMap<View, Long>())
    private val appBarExpandedStates = Collections.synchronizedMap(WeakHashMap<ViewGroup, Boolean>())
    private val appBarActionStates = Collections.synchronizedMap(WeakHashMap<ViewGroup, AutoHideAppBarState>())
    private val scrollStateCache = Collections.synchronizedMap(WeakHashMap<View, AutoHideScrollState>())
    private val homeViewPagers = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val methodCache = ConcurrentHashMap<Class<*>, ScrollFlagMethods>()
    private val methodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val behaviorMethodCache = ConcurrentHashMap<Class<*>, BehaviorMethods>()
    private val behaviorMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val expandedMethodCache = ConcurrentHashMap<Class<*>, Method>()
    private val expandedMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val applyErrorLogged = AtomicBoolean(false)
    private val behaviorErrorLogged = AtomicBoolean(false)
    private val autoHideActiveLogged = AtomicBoolean(false)
    private val autoHideErrorLogged = AtomicBoolean(false)

    @Volatile private var hooked = false
    @Volatile private var targetScrollFlags = SCROLL_FLAG_SCROLL_FALLBACK or SCROLL_FLAG_ENTER_ALWAYS_FALLBACK
    @Volatile private var runtimeTargets: RuntimeTargets? = null

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!ConfigManager.isHomeTabAutoHideEnabled) {
            XposedCompat.logD("[HomeTopTabAutoHideHook] disabled by config")
            return
        }
        if (!tryMarkHooked()) return

        targetScrollFlags = resolveScrollFlags(cl)
        runtimeTargets = resolveRuntimeTargets(cl)
        var installed = 0
        try {
            for (className in targetClassNames) {
                val clazz = XposedCompat.findClassOrNull(className, cl)
                if (clazz == null) {
                    XposedCompat.log("[HomeTopTabAutoHideHook] class NOT FOUND: $className")
                    continue
                }
                for (ctor in clazz.declaredConstructors) {
                    ctor.isAccessible = true
                    mod.hook(ctor).intercept { chain ->
                        val result = chain.proceed()
                        val appBar = chain.thisObject as? ViewGroup
                        if (appBar != null) {
                            scheduleConfigure(appBar)
                        }
                        result
                    }
                    installed++
                }
            }

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[HomeTopTabAutoHideHook] constructor NOT FOUND")
            } else {
                val scrollObservers = installRecyclerViewScrollObserver(mod, cl)
                val pageObservers = installHomeViewPagerMarker(mod, cl) +
                    installViewPagerPageSelectedObserver(mod, cl)
                val nativeGuards = installNativeAppBarNestedScrollGuard(mod, cl)
                val lockObservers = HomeTabAutoHideLockController.hook(cl)
                XposedCompat.log(
                    "[HomeTopTabAutoHideHook] hook INSTALLED: constructors=$installed, " +
                        "scrollObservers=$scrollObservers, pageObservers=$pageObservers, " +
                        "nativeGuards=$nativeGuards, lockObservers=$lockObservers"
                )
            }
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[HomeTopTabAutoHideHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installRecyclerViewScrollObserver(mod: io.github.libxposed.api.XposedModule, cl: ClassLoader): Int {
        val clazz = XposedCompat.findClassOrNull(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, cl)
        if (clazz == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] class NOT FOUND: ${StableTiebaHookPoints.RECYCLER_VIEW_CLASS}")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, METHOD_DISPATCH_ON_SCROLLED, java.lang.Integer.TYPE, java.lang.Integer.TYPE)
            ?: XposedCompat.findMethodOrNull(clazz, "onScrolled", java.lang.Integer.TYPE, java.lang.Integer.TYPE)
        if (method == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] method NOT FOUND: RecyclerView scroll dispatch")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (ConfigManager.isHomeTabAutoHideEnabled) {
                val target = chain.thisObject as? View
                val dy = (chain.args.getOrNull(1) as? Int) ?: 0
                if (target != null && dy != 0) {
                    handleAutoHideScroll(target, dy)
                }
            }
            result
        }
        return 1
    }

    private fun installHomeViewPagerMarker(mod: io.github.libxposed.api.XposedModule, cl: ClassLoader): Int {
        val clazz = XposedCompat.findClassOrNull(StableTiebaHookPoints.CUSTOM_VIEW_PAGER_CLASS, cl)
        if (clazz == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] class NOT FOUND: ${StableTiebaHookPoints.CUSTOM_VIEW_PAGER_CLASS}")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, METHOD_SET_FORM, java.lang.Integer.TYPE)
        if (method == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] method NOT FOUND: CustomViewPager.setForm")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            val viewPager = chain.thisObject as? View
            val form = (chain.args.getOrNull(0) as? Int) ?: 0
            if (viewPager != null && form == HOME_VIEW_PAGER_FORM) {
                synchronized(homeViewPagers) {
                    homeViewPagers[viewPager] = true
                }
            }
            result
        }
        return 1
    }

    private fun installViewPagerPageSelectedObserver(mod: io.github.libxposed.api.XposedModule, cl: ClassLoader): Int {
        val clazz = XposedCompat.findClassOrNull(StableTiebaHookPoints.VIEW_PAGER_CLASS, cl)
        if (clazz == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] class NOT FOUND: ${StableTiebaHookPoints.VIEW_PAGER_CLASS}")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, METHOD_DISPATCH_ON_PAGE_SELECTED, java.lang.Integer.TYPE)
        if (method == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] method NOT FOUND: ViewPager.dispatchOnPageSelected")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (ConfigManager.isHomeTabAutoHideEnabled) {
                val viewPager = chain.thisObject as? View
                if (viewPager != null && isHomeViewPager(viewPager)) {
                    if (!HomeTabAutoHideLockController.unlockForTabSwitch(viewPager)) {
                        expandHomeTabForPageSwitch(viewPager)
                    }
                }
            }
            result
        }
        return 1
    }

    private fun installNativeAppBarNestedScrollGuard(mod: io.github.libxposed.api.XposedModule, cl: ClassLoader): Int {
        val behaviorClass = XposedCompat.findClassOrNull(APP_BAR_BEHAVIOR_CLASS, cl)
        val coordinatorClass = XposedCompat.findClassOrNull(COORDINATOR_LAYOUT_CLASS, cl)
        val appBarClass = XposedCompat.findClassOrNull(APP_BAR_LAYOUT_CLASS, cl)
        if (behaviorClass == null || coordinatorClass == null || appBarClass == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] class NOT FOUND: native AppBar nested scroll guard")
            return 0
        }
        val method = findDeclaredMethodInHierarchy(
            behaviorClass,
            METHOD_ON_START_NESTED_SCROLL,
            coordinatorClass,
            appBarClass,
            View::class.java,
            View::class.java,
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
        )
        if (method == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] method NOT FOUND: AppBarLayout.Behavior.onStartNestedScroll")
            return 0
        }
        mod.hook(method).intercept { chain ->
            if (ConfigManager.isHomeTabAutoHideEnabled) {
                val appBar = chain.args.getOrNull(1) as? View
                if (appBar != null && isHomeAppBar(appBar)) {
                    return@intercept false
                }
            }
            chain.proceed()
        }
        return 1
    }

    private fun scheduleConfigure(appBar: ViewGroup) {
        appBar.post { configureAppBar(appBar) }
    }

    private fun configureAppBar(appBar: ViewGroup) {
        if (!ConfigManager.isHomeTabAutoHideEnabled) return
        applyScrollFlagsToChildren(appBar)
        applyScrollingBehaviorToContentSibling(appBar)
        synchronized(appBarExpandedStates) {
            if (!appBarExpandedStates.containsKey(appBar)) {
                appBarExpandedStates[appBar] = isAppBarExpanded(appBar)
            }
        }
        if (HomeTabAutoHideLockController.isLockedHidden()) {
            applyLockedHidden(appBar)
        }
    }

    private fun isHomeViewPager(viewPager: View): Boolean {
        synchronized(homeViewPagers) {
            return homeViewPagers[viewPager] == true
        }
    }

    private fun expandHomeTabForPageSwitch(viewPager: View) {
        val appBar = resolveHomeAppBarForScrollTarget(viewPager) ?: return
        if (setAppBarExpandedIfNeeded(appBar, true)) {
            quietAutoHideForAppBar(
                appBar,
                HomeTabAutoHideStrategy.pageSwitchQuietUntil(SystemClock.uptimeMillis()),
            )
        }
    }

    private fun handleAutoHideScroll(target: View, dy: Int) {
        val appBar = resolveHomeAppBarForScrollTarget(target) ?: return
        if (appBar.height <= 0 || appBar.windowToken == null) return
        val now = SystemClock.uptimeMillis()
        val state = scrollStateFor(target, appBar)
        if (HomeTabAutoHideLockController.isLockedHidden()) {
            HomeTabAutoHideStrategy.reset(state)
            applyLockedHidden(appBar)
            return
        }
        val direction = HomeTabAutoHideStrategy.directionOf(dy)
        val appBarState = appBarActionStateFor(appBar)
        if (now < appBarState.quietUntilAt) return
        val desiredExpanded = direction < 0
        val currentExpanded = synchronized(appBarExpandedStates) {
            appBarExpandedStates[appBar] ?: isAppBarExpanded(appBar)
        }
        if (currentExpanded == desiredExpanded) {
            HomeTabAutoHideStrategy.reset(state)
            return
        }
        if (!HomeTabAutoHideStrategy.reachedScrollThreshold(target, state, direction, dy)) return
        HomeTabAutoHideStrategy.reset(state)

        if (now - appBarState.lastActionAt < HomeTabAutoHideStrategy.ACTION_MIN_INTERVAL_MS) return
        if (setAppBarExpandedIfNeeded(appBar, desiredExpanded)) {
            appBarState.lastActionAt = now
            appBarState.quietUntilAt = HomeTabAutoHideStrategy.postActionQuietUntil(now)
        }
    }

    internal fun applyLockedHidden(anchor: View): Boolean {
        val appBar = if (isHomeAppBar(anchor)) {
            anchor as? ViewGroup
        } else {
            resolveHomeAppBarForScrollTarget(anchor)
        } ?: return false
        if (appBar.height <= 0 || appBar.windowToken == null) return false
        val changed = setAppBarExpandedIfNeeded(appBar, false)
        quietAutoHideForAppBar(
            appBar,
            HomeTabAutoHideStrategy.postActionQuietUntil(SystemClock.uptimeMillis()),
        )
        return changed || !isAppBarExpanded(appBar)
    }

    internal fun releaseLockedHidden(anchor: View): Boolean {
        val quietUntilAt = HomeTabAutoHideStrategy.pageSwitchQuietUntil(SystemClock.uptimeMillis())
        var handled = false
        val resolved = resolveHomeAppBarForScrollTarget(anchor)
        if (resolved != null) {
            setAppBarExpandedIfNeeded(resolved, true)
            quietAutoHideForAppBar(resolved, quietUntilAt)
            handled = true
        }

        val knownAppBars = synchronized(appBarExpandedStates) {
            appBarExpandedStates.keys.toList()
        }
        for (appBar in knownAppBars) {
            if (appBar === resolved) continue
            setAppBarExpandedIfNeeded(appBar, true)
            quietAutoHideForAppBar(appBar, quietUntilAt)
            handled = true
        }
        return handled
    }

    private fun scrollStateFor(target: View, appBar: ViewGroup): AutoHideScrollState {
        synchronized(scrollStateCache) {
            val cached = scrollStateCache[target]
            if (cached != null && cached.appBar === appBar) return cached
            val state = AutoHideScrollState(appBar)
            scrollStateCache[target] = state
            return state
        }
    }

    private fun appBarActionStateFor(appBar: ViewGroup): AutoHideAppBarState {
        synchronized(appBarActionStates) {
            val cached = appBarActionStates[appBar]
            if (cached != null) return cached
            val state = AutoHideAppBarState()
            appBarActionStates[appBar] = state
            return state
        }
    }

    private fun resolveHomeAppBarForScrollTarget(target: View): ViewGroup? {
        val root = target.rootView ?: target
        synchronized(appBarCache) {
            val cached = appBarCache[target]
            if (cached != null && cached.rootView === root) return cached
        }
        val now = SystemClock.uptimeMillis()
        synchronized(appBarMissRetryAt) {
            val retryAt = appBarMissRetryAt[target]
            if (retryAt != null && now < retryAt) return null
        }
        val appBar = findHomeAppBar(root)
        if (appBar != null) {
            synchronized(appBarCache) {
                appBarCache[target] = appBar
            }
            synchronized(appBarMissRetryAt) {
                appBarMissRetryAt.remove(target)
            }
            if (autoHideActiveLogged.compareAndSet(false, true)) {
                XposedCompat.logD(
                    "[HomeTopTabAutoHideHook] auto-hide observer active: " +
                        "target=${target.javaClass.name}, appBar=${appBar.javaClass.name}"
                )
            }
            return appBar
        }
        synchronized(appBarMissRetryAt) {
            appBarMissRetryAt[target] = now + APP_BAR_MISS_RETRY_INTERVAL_MS
        }
        return null
    }

    private fun setAppBarExpandedIfNeeded(appBar: ViewGroup, expanded: Boolean): Boolean {
        val actualExpanded = isAppBarExpanded(appBar)
        val current = synchronized(appBarExpandedStates) {
            appBarExpandedStates[appBar] ?: actualExpanded
        }
        if (current == expanded && actualExpanded == expanded) return false
        val method = expandedMethod(appBar.javaClass) ?: return false
        return try {
            method.invoke(appBar, expanded, HomeTabAutoHideStrategy.ANIMATE_SCROLL_ACTIONS)
            synchronized(appBarExpandedStates) {
                appBarExpandedStates[appBar] = expanded
            }
            true
        } catch (t: Throwable) {
            if (autoHideErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeTopTabAutoHideHook] set appbar expanded FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            false
        }
    }

    private fun isAppBarExpanded(appBar: ViewGroup): Boolean {
        return appBar.top >= 0
    }

    private fun quietAutoHideForAppBar(appBar: ViewGroup, quietUntilAt: Long) {
        synchronized(appBarActionStates) {
            val state = appBarActionStates[appBar] ?: AutoHideAppBarState().also {
                appBarActionStates[appBar] = it
            }
            state.quietUntilAt = maxOf(state.quietUntilAt, quietUntilAt)
        }
        synchronized(scrollStateCache) {
            for (state in scrollStateCache.values) {
                if (state.appBar === appBar) {
                    HomeTabAutoHideStrategy.reset(state)
                }
            }
        }
    }

    private fun findHomeAppBar(root: View): ViewGroup? {
        val group = root as? ViewGroup ?: return null
        if (isHomeAppBar(group)) return group
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            val found = findHomeAppBar(child)
            if (found != null) return found
        }
        return null
    }

    private fun isHomeAppBar(view: View): Boolean {
        return targetClassNames.any { view.javaClass.name == it }
    }

    private fun applyScrollFlagsToChildren(appBar: ViewGroup) {
        for (index in 0 until appBar.childCount) {
            val child = appBar.getChildAt(index) ?: continue
            val lp = child.layoutParams ?: continue
            if (setScrollFlagsIfNeeded(lp, targetScrollFlags)) {
                child.layoutParams = lp
                child.requestLayout()
            }
        }
    }

    private fun applyScrollingBehaviorToContentSibling(appBar: ViewGroup) {
        val targets = runtimeTargets ?: return
        val parent = appBar.parent as? ViewGroup ?: return
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index) ?: continue
            if (child === appBar) continue
            if (!containsViewPager(child, targets.customViewPagerClass, 0)) continue
            val lp = child.layoutParams ?: continue
            if (setScrollingBehaviorIfNeeded(lp, targets.scrollingViewBehaviorCtor)) {
                child.layoutParams = lp
                child.requestLayout()
            }
            return
        }
    }

    private fun containsViewPager(view: View, viewPagerClass: Class<*>?, depth: Int): Boolean {
        if (viewPagerClass != null && viewPagerClass.isInstance(view)) return true
        if (depth >= 8) return false
        val group = view as? ViewGroup ?: return false
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (containsViewPager(child, viewPagerClass, depth + 1)) return true
        }
        return false
    }

    private fun setScrollingBehaviorIfNeeded(
        layoutParams: ViewGroup.LayoutParams,
        behaviorCtor: Constructor<*>?,
    ): Boolean {
        behaviorCtor ?: return false
        val methods = behaviorMethods(layoutParams.javaClass) ?: return false
        return try {
            val currentBehavior = methods.getBehavior?.invoke(layoutParams)
            if (currentBehavior != null && behaviorCtor.declaringClass.isInstance(currentBehavior)) {
                false
            } else {
                methods.setBehavior.invoke(layoutParams, behaviorCtor.newInstance())
                true
            }
        } catch (t: Throwable) {
            if (behaviorErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeTopTabAutoHideHook] apply scrolling behavior FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            false
        }
    }

    private fun setScrollFlagsIfNeeded(layoutParams: ViewGroup.LayoutParams, flags: Int): Boolean {
        val methods = scrollFlagMethods(layoutParams.javaClass) ?: return false
        return try {
            val current = methods.getScrollFlags.invoke(layoutParams) as? Int
            if (current == flags) {
                false
            } else {
                methods.setScrollFlags.invoke(layoutParams, flags)
                true
            }
        } catch (t: Throwable) {
            if (applyErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeTopTabAutoHideHook] apply scroll flags FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            false
        }
    }

    private fun expandedMethod(clazz: Class<*>): Method? {
        expandedMethodCache[clazz]?.let { return it }
        if (expandedMethodMissCache.contains(clazz)) return null
        return try {
            val method = clazz.getMethod(METHOD_SET_EXPANDED, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
                .apply { isAccessible = true }
            expandedMethodCache[clazz] = method
            method
        } catch (_: Throwable) {
            expandedMethodMissCache.add(clazz)
            null
        }
    }

    private fun findDeclaredMethodInHierarchy(
        clazz: Class<*>,
        methodName: String,
        vararg paramTypes: Class<*>,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, *paramTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun behaviorMethods(clazz: Class<*>): BehaviorMethods? {
        behaviorMethodCache[clazz]?.let { return it }
        if (behaviorMethodMissCache.contains(clazz)) return null
        return try {
            val setBehavior = clazz.methods.firstOrNull { method ->
                method.name == "setBehavior" && method.parameterTypes.size == 1
            } ?: clazz.declaredMethods.firstOrNull { method ->
                method.name == "setBehavior" && method.parameterTypes.size == 1
            } ?: throw NoSuchMethodException("setBehavior")
            val getBehavior = clazz.methods.firstOrNull { method ->
                method.name == "getBehavior" && method.parameterTypes.isEmpty()
            } ?: clazz.declaredMethods.firstOrNull { method ->
                method.name == "getBehavior" && method.parameterTypes.isEmpty()
            }
            setBehavior.isAccessible = true
            getBehavior?.isAccessible = true
            val methods = BehaviorMethods(setBehavior, getBehavior)
            behaviorMethodCache[clazz] = methods
            methods
        } catch (_: Throwable) {
            behaviorMethodMissCache.add(clazz)
            null
        }
    }

    private fun scrollFlagMethods(clazz: Class<*>): ScrollFlagMethods? {
        methodCache[clazz]?.let { return it }
        if (methodMissCache.contains(clazz)) return null
        return try {
            val methods = ScrollFlagMethods(
                getScrollFlags = clazz.getMethod("getScrollFlags").apply { isAccessible = true },
                setScrollFlags = clazz.getMethod("setScrollFlags", java.lang.Integer.TYPE).apply {
                    isAccessible = true
                },
            )
            methodCache[clazz] = methods
            methods
        } catch (_: Throwable) {
            methodMissCache.add(clazz)
            null
        }
    }

    private fun resolveRuntimeTargets(cl: ClassLoader): RuntimeTargets {
        val customViewPagerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.CUSTOM_VIEW_PAGER_CLASS, cl)
        val behaviorCtor = try {
            XposedCompat.findClassOrNull(APP_BAR_SCROLLING_VIEW_BEHAVIOR_CLASS, cl)
                ?.getDeclaredConstructor()
                ?.apply { isAccessible = true }
        } catch (t: Throwable) {
            XposedCompat.log("[HomeTopTabAutoHideHook] ScrollingViewBehavior ctor NOT FOUND: ${t.message}")
            null
        }
        if (customViewPagerClass == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] class NOT FOUND: ${StableTiebaHookPoints.CUSTOM_VIEW_PAGER_CLASS}")
        }
        if (behaviorCtor == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] class NOT FOUND: $APP_BAR_SCROLLING_VIEW_BEHAVIOR_CLASS")
        }
        return RuntimeTargets(
            customViewPagerClass = customViewPagerClass,
            scrollingViewBehaviorCtor = behaviorCtor,
        )
    }

    private fun resolveScrollFlags(cl: ClassLoader): Int {
        val paramsClass = XposedCompat.findClassOrNull(APP_BAR_LAYOUT_PARAMS_CLASS, cl)
        val scroll = staticIntField(paramsClass, "SCROLL_FLAG_SCROLL", SCROLL_FLAG_SCROLL_FALLBACK)
        val enterAlways = staticIntField(
            paramsClass,
            "SCROLL_FLAG_ENTER_ALWAYS",
            SCROLL_FLAG_ENTER_ALWAYS_FALLBACK,
        )
        return scroll or enterAlways
    }

    private fun staticIntField(clazz: Class<*>?, name: String, fallback: Int): Int {
        if (clazz == null) return fallback
        return try {
            clazz.getField(name).getInt(null)
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private data class ScrollFlagMethods(
        val getScrollFlags: Method,
        val setScrollFlags: Method,
    )

    private data class BehaviorMethods(
        val setBehavior: Method,
        val getBehavior: Method?,
    )

    private data class AutoHideScrollState(
        val appBar: ViewGroup,
        override var lastDirection: Int = 0,
        override var accumulatedDy: Int = 0,
        override var hideThresholdPx: Int = 0,
        override var showThresholdPx: Int = 0,
    ) : HomeTabAutoHideStrategy.ScrollAccumulator

    private data class AutoHideAppBarState(
        var lastActionAt: Long = 0L,
        var quietUntilAt: Long = 0L,
    )

    private data class RuntimeTargets(
        val customViewPagerClass: Class<*>?,
        val scrollingViewBehaviorCtor: Constructor<*>?,
    )
}
