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
    private const val APP_BAR_LAYOUT_PARAMS_CLASS =
        "com.google.android.material.appbar.AppBarLayout\$LayoutParams"
    private const val APP_BAR_SCROLLING_VIEW_BEHAVIOR_CLASS =
        "com.google.android.material.appbar.AppBarLayout\$ScrollingViewBehavior"
    private const val COORDINATOR_LAYOUT_BEHAVIOR_CLASS =
        "androidx.coordinatorlayout.widget.CoordinatorLayout\$Behavior"
    private const val METHOD_DISPATCH_NESTED_PRE_SCROLL = "dispatchNestedPreScroll"
    private const val METHOD_DISPATCH_ON_PAGE_SELECTED = "dispatchOnPageSelected"
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
    private val appBarOffsetControllers = Collections.synchronizedMap(WeakHashMap<ViewGroup, AppBarOffsetController>())
    private val homeViewPagers = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val methodCache = ConcurrentHashMap<Class<*>, ScrollFlagMethods>()
    private val methodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val behaviorMethodCache = ConcurrentHashMap<Class<*>, BehaviorMethods>()
    private val behaviorMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val offsetMethodCache = ConcurrentHashMap<Class<*>, OffsetMethods>()
    private val offsetMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val expandedMethodCache = ConcurrentHashMap<Class<*>, Method>()
    private val expandedMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val totalScrollRangeMethodCache = ConcurrentHashMap<Class<*>, Method>()
    private val totalScrollRangeMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val offsetChangedMethodCache = ConcurrentHashMap<Class<*>, Method>()
    private val offsetChangedMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val dependentDispatchMethodCache = ConcurrentHashMap<Class<*>, Method>()
    private val dependentDispatchMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val locationScratchLocal = ThreadLocal<IntArray>()
    private val applyErrorLogged = AtomicBoolean(false)
    private val behaviorErrorLogged = AtomicBoolean(false)
    private val autoHideActiveLogged = AtomicBoolean(false)
    private val autoHideErrorLogged = AtomicBoolean(false)
    private val preScrollErrorLogged = AtomicBoolean(false)

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
                val scrollObservers = installRecyclerViewPreScrollObserver(mod, cl)
                val pageObservers = installHomeViewPagerMarker(mod, cl) +
                    installViewPagerPageSelectedObserver(mod, cl)
                val lockObservers = HomeTabAutoHideLockController.hook(cl)
                XposedCompat.log(
                    "[HomeTopTabAutoHideHook] hook INSTALLED: constructors=$installed, " +
                        "scrollObservers=$scrollObservers, pageObservers=$pageObservers, " +
                        "lockObservers=$lockObservers"
                )
            }
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[HomeTopTabAutoHideHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installRecyclerViewPreScrollObserver(
        mod: com.forbidad4tieba.hook.core.Api102ModuleFacade,
        cl: ClassLoader,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, cl)
        if (clazz == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] class NOT FOUND: ${StableTiebaHookPoints.RECYCLER_VIEW_CLASS}")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            METHOD_DISPATCH_NESTED_PRE_SCROLL,
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
            IntArray::class.java,
            IntArray::class.java,
            java.lang.Integer.TYPE,
        )
        if (method == null) {
            XposedCompat.log("[HomeTopTabAutoHideHook] method NOT FOUND: RecyclerView.$METHOD_DISPATCH_NESTED_PRE_SCROLL")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (!ConfigManager.isHomeTabAutoHideEnabled) return@intercept result
            val target = chain.thisObject as? View ?: return@intercept result
            val dy = (chain.args.getOrNull(1) as? Int) ?: return@intercept result
            if (dy == 0) return@intercept result
            val consumed = chain.args.getOrNull(2) as? IntArray ?: return@intercept result
            if (consumed.size <= 1) return@intercept result
            val remainingDy = dy - consumed.getOrElse(1) { 0 }
            if (dy > 0 && remainingDy <= 0) return@intercept result
            if (dy < 0 && remainingDy >= 0) return@intercept result
            val offsetInWindow = chain.args.getOrNull(3) as? IntArray
            val topConsumed = consumeTopAppBarPreScroll(target, remainingDy, consumed, offsetInWindow)
            if (topConsumed != 0) true else result
        }
        return 1
    }

    private fun installHomeViewPagerMarker(mod: com.forbidad4tieba.hook.core.Api102ModuleFacade, cl: ClassLoader): Int {
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

    private fun installViewPagerPageSelectedObserver(mod: com.forbidad4tieba.hook.core.Api102ModuleFacade, cl: ClassLoader): Int {
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
        expandAppBarImmediately(appBar)
    }

    internal fun applyLockedHidden(anchor: View): Boolean {
        val appBar = if (isHomeAppBar(anchor)) {
            anchor as? ViewGroup
        } else {
            resolveHomeAppBarForScrollTarget(anchor)
        } ?: return false
        if (appBar.height <= 0 || appBar.windowToken == null) return false
        val changed = hideAppBarImmediately(appBar)
        return changed || isAppBarFullyHidden(appBar)
    }

    internal fun releaseLockedHidden(anchor: View): Boolean {
        var handled = false
        val resolved = resolveHomeAppBarForScrollTarget(anchor)
        if (resolved != null) {
            expandAppBarImmediately(resolved)
            handled = true
        }

        val knownAppBars = synchronized(appBarExpandedStates) {
            appBarExpandedStates.keys.toList()
        }
        for (appBar in knownAppBars) {
            if (appBar === resolved) continue
            expandAppBarImmediately(appBar)
            handled = true
        }
        return handled
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
        val appBar = findHomeAppBarForTarget(root, target)
        if (appBar != null) {
            configureAppBar(appBar)
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

    private fun consumeTopAppBarPreScroll(
        target: View,
        dy: Int,
        consumed: IntArray,
        offsetInWindow: IntArray?,
    ): Int {
        val appBar = resolveHomeAppBarForScrollTarget(target) ?: return 0
        if (appBar.height <= 0 || appBar.windowToken == null) return 0
        if (HomeTabAutoHideLockController.isLockedHidden()) {
            applyLockedHidden(appBar)
            return 0
        }

        val range = hiddenRange(appBar)
        if (range <= 0) return 0
        val controller = appBarOffsetController(appBar) ?: return 0
        val currentOffset = currentAppBarOffset(controller)?.coerceIn(-range, 0) ?: return 0
        val targetOffset = (currentOffset - dy).coerceIn(-range, 0)
        val consumedY = currentOffset - targetOffset
        if (consumedY == 0) return 0

        return try {
            if (consumed.size > 1) {
                consumed[1] += consumedY
            }
            if (setAppBarOffset(appBar, controller, targetOffset, target, offsetInWindow)) {
                consumedY
            } else {
                consumed[1] -= consumedY
                0
            }
        } catch (t: Throwable) {
            if (preScrollErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeTopTabAutoHideHook] pre-scroll appbar offset FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            0
        }
    }

    private fun locationScratch(): IntArray {
        val cached = locationScratchLocal.get()
        if (cached != null) return cached
        return IntArray(2).also { locationScratchLocal.set(it) }
    }

    private fun hideAppBarImmediately(appBar: ViewGroup): Boolean {
        val range = hiddenRange(appBar)
        if (range <= 0) return setAppBarExpandedIfNeeded(appBar, false)
        val controller = appBarOffsetController(appBar) ?: return setAppBarExpandedIfNeeded(appBar, false)
        val currentOffset = currentAppBarOffset(controller)?.coerceIn(-range, 0)
            ?: return setAppBarExpandedIfNeeded(appBar, false)
        if (currentOffset <= -range) {
            synchronized(appBarExpandedStates) {
                appBarExpandedStates[appBar] = false
            }
            return false
        }
        return setAppBarOffset(appBar, controller, -range, null, null)
    }

    private fun expandAppBarImmediately(appBar: ViewGroup): Boolean {
        val controller = appBarOffsetController(appBar)
        val currentOffset = controller?.let { currentAppBarOffset(it) }
        if (controller != null && currentOffset != null && currentOffset != 0) {
            return setAppBarOffset(appBar, controller, 0, null, null)
        }
        return setAppBarExpandedIfNeeded(appBar, true)
    }

    private fun setAppBarExpandedIfNeeded(appBar: ViewGroup, expanded: Boolean): Boolean {
        val actualExpanded = isAppBarExpanded(appBar)
        val current = synchronized(appBarExpandedStates) {
            appBarExpandedStates[appBar] ?: actualExpanded
        }
        if (current == expanded && actualExpanded == expanded) return false
        val method = expandedMethod(appBar.javaClass) ?: return false
        return try {
            method.invoke(appBar, expanded, false)
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

    private fun appBarOffsetController(appBar: ViewGroup): AppBarOffsetController? {
        synchronized(appBarOffsetControllers) {
            appBarOffsetControllers[appBar]?.let { return it }
        }
        val layoutParams = appBar.layoutParams ?: return null
        val layoutParamMethods = behaviorMethods(layoutParams.javaClass) ?: return null
        val behavior = try {
            layoutParamMethods.getBehavior.invoke(layoutParams)
        } catch (t: Throwable) {
            if (behaviorErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeTopTabAutoHideHook] get appbar behavior FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            null
        } ?: return null
        val offsetMethods = offsetMethods(behavior.javaClass) ?: return null
        val controller = AppBarOffsetController(behavior, offsetMethods)
        synchronized(appBarOffsetControllers) {
            appBarOffsetControllers[appBar] = controller
        }
        return controller
    }

    private fun currentAppBarOffset(controller: AppBarOffsetController): Int? {
        return try {
            controller.methods.getTopAndBottomOffset.invoke(controller.behavior) as? Int
        } catch (t: Throwable) {
            if (preScrollErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeTopTabAutoHideHook] get appbar offset FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            null
        }
    }

    private fun setAppBarOffset(
        appBar: ViewGroup,
        controller: AppBarOffsetController,
        targetOffset: Int,
        windowTarget: View?,
        offsetInWindow: IntArray?,
    ): Boolean {
        val scratch = if (windowTarget != null && offsetInWindow != null && offsetInWindow.size > 1) {
            locationScratch().also { windowTarget.getLocationInWindow(it) }
        } else {
            null
        }
        val beforeWindowX = scratch?.get(0) ?: 0
        val beforeWindowY = scratch?.get(1) ?: 0
        return try {
            val changed = controller.methods.setTopAndBottomOffset.invoke(
                controller.behavior,
                targetOffset,
            ) as? Boolean
            if (changed != true) return false
            notifyAppBarOffsetChanged(appBar, targetOffset)
            dispatchDependentViewsChanged(appBar)
            if (scratch != null && windowTarget != null && offsetInWindow != null) {
                windowTarget.getLocationInWindow(scratch)
                offsetInWindow[0] += scratch[0] - beforeWindowX
                offsetInWindow[1] += scratch[1] - beforeWindowY
            }
            synchronized(appBarExpandedStates) {
                appBarExpandedStates[appBar] = targetOffset == 0
            }
            true
        } catch (t: Throwable) {
            if (preScrollErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeTopTabAutoHideHook] set appbar offset FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            false
        }
    }

    private fun isAppBarFullyHidden(appBar: ViewGroup): Boolean {
        val range = hiddenRange(appBar)
        if (range <= 0) return !isAppBarExpanded(appBar)
        val controller = appBarOffsetController(appBar) ?: return false
        val currentOffset = currentAppBarOffset(controller) ?: return false
        return currentOffset <= -range
    }

    private fun hiddenRange(appBar: ViewGroup): Int {
        val range = totalScrollRange(appBar)
        return if (range > 0) range else appBar.height
    }

    private fun totalScrollRange(appBar: ViewGroup): Int {
        val method = totalScrollRangeMethod(appBar.javaClass) ?: return 0
        return try {
            (method.invoke(appBar) as? Int) ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    private fun notifyAppBarOffsetChanged(appBar: ViewGroup, offset: Int) {
        val method = offsetChangedMethod(appBar.javaClass) ?: return
        try {
            method.invoke(appBar, offset)
        } catch (_: Throwable) {
            // Offset listeners are best-effort; the layout movement is already applied.
        }
    }

    private fun dispatchDependentViewsChanged(appBar: ViewGroup) {
        val parent = appBar.parent as? ViewGroup ?: return
        val method = dependentDispatchMethod(parent.javaClass) ?: return
        try {
            method.invoke(parent, appBar)
        } catch (_: Throwable) {
            // Coordinator dependency dispatch is best-effort; native pre-draw can still reconcile.
        }
    }

    private fun isAppBarExpanded(appBar: ViewGroup): Boolean {
        return appBar.top >= 0
    }

    private fun isScrollTargetInHomeContainer(target: View, appBar: ViewGroup): Boolean {
        val container = appBar.parent as? ViewGroup ?: return false
        var current: View? = target
        var depth = 0
        while (current != null && depth < 16) {
            if (current === container) return true
            current = current.parent as? View
            depth++
        }
        return false
    }

    private fun findHomeAppBarForTarget(root: View, target: View): ViewGroup? {
        val group = root as? ViewGroup ?: return null
        if (isHomeAppBar(group) && isScrollTargetInHomeContainer(target, group)) return group
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            val found = findHomeAppBarForTarget(child, target)
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
            val currentBehavior = methods.getBehavior.invoke(layoutParams)
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

    private fun offsetMethods(clazz: Class<*>): OffsetMethods? {
        offsetMethodCache[clazz]?.let { return it }
        if (offsetMethodMissCache.contains(clazz)) return null
        return try {
            val methods = OffsetMethods(
                getTopAndBottomOffset = clazz.getMethod("getTopAndBottomOffset").apply { isAccessible = true },
                setTopAndBottomOffset = clazz.getMethod(
                    "setTopAndBottomOffset",
                    java.lang.Integer.TYPE,
                ).apply { isAccessible = true },
            )
            offsetMethodCache[clazz] = methods
            methods
        } catch (_: Throwable) {
            offsetMethodMissCache.add(clazz)
            null
        }
    }

    private fun totalScrollRangeMethod(clazz: Class<*>): Method? {
        totalScrollRangeMethodCache[clazz]?.let { return it }
        if (totalScrollRangeMethodMissCache.contains(clazz)) return null
        return try {
            val method = clazz.getMethod("getTotalScrollRange").apply { isAccessible = true }
            totalScrollRangeMethodCache[clazz] = method
            method
        } catch (_: Throwable) {
            totalScrollRangeMethodMissCache.add(clazz)
            null
        }
    }

    private fun offsetChangedMethod(clazz: Class<*>): Method? {
        offsetChangedMethodCache[clazz]?.let { return it }
        if (offsetChangedMethodMissCache.contains(clazz)) return null
        return try {
            val method = clazz.getMethod("onOffsetChanged", java.lang.Integer.TYPE).apply {
                isAccessible = true
            }
            offsetChangedMethodCache[clazz] = method
            method
        } catch (_: Throwable) {
            offsetChangedMethodMissCache.add(clazz)
            null
        }
    }

    private fun dependentDispatchMethod(clazz: Class<*>): Method? {
        dependentDispatchMethodCache[clazz]?.let { return it }
        if (dependentDispatchMethodMissCache.contains(clazz)) return null
        return try {
            val method = clazz.getMethod("dispatchDependentViewsChanged", View::class.java).apply {
                isAccessible = true
            }
            dependentDispatchMethodCache[clazz] = method
            method
        } catch (_: Throwable) {
            dependentDispatchMethodMissCache.add(clazz)
            null
        }
    }

    private fun behaviorMethods(clazz: Class<*>): BehaviorMethods? {
        behaviorMethodCache[clazz]?.let { return it }
        if (behaviorMethodMissCache.contains(clazz)) return null
        return try {
            val loader = clazz.classLoader ?: throw ClassNotFoundException(COORDINATOR_LAYOUT_BEHAVIOR_CLASS)
            val behaviorClass = XposedCompat.findClassOrNull(COORDINATOR_LAYOUT_BEHAVIOR_CLASS, loader)
                ?: throw ClassNotFoundException(COORDINATOR_LAYOUT_BEHAVIOR_CLASS)
            val setBehavior = clazz.getMethod("setBehavior", behaviorClass)
            val getBehavior = clazz.getMethod("getBehavior")
            setBehavior.isAccessible = true
            getBehavior.isAccessible = true
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
        val getBehavior: Method,
    )

    private data class OffsetMethods(
        val getTopAndBottomOffset: Method,
        val setTopAndBottomOffset: Method,
    )

    private data class AppBarOffsetController(
        val behavior: Any,
        val methods: OffsetMethods,
    )

    private data class RuntimeTargets(
        val customViewPagerClass: Class<*>?,
        val scrollingViewBehaviorCtor: Constructor<*>?,
    )
}
