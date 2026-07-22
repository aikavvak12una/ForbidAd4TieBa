package com.forbidad4tieba.hook.feature.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object HomeBottomTabAutoHideHook {
    private const val METHOD_DISPATCH_ON_SCROLLED = "dispatchOnScrolled"
    private const val METHOD_ON_SCROLL_CHANGED = "onScrollChanged"
    private const val METHOD_ON_PAGE_SELECTED = "onPageSelected"
    private const val METHOD_SET_CURRENT_TAB = "setCurrentTab"
    private const val METHOD_SET_CURRENT_TAB_BY_INDEX = "setCurrentTabByIndex"
    private const val METHOD_SET_CURRENT_TAB_BY_TYPE = "setCurrentTabByType"
    private const val HOME_TAB_TYPE = 2
    private const val TARGET_MISS_RETRY_INTERVAL_MS = 1000L

    private val bottomTabCache = Collections.synchronizedMap(WeakHashMap<View, ResolvedBottomTab>())
    private val bottomTabMissRetryAt = Collections.synchronizedMap(WeakHashMap<View, Long>())
    private val tabHostStates = Collections.synchronizedMap(WeakHashMap<View, BottomTabState>())
    private val configuredWrappers = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val hostMethodCache = ConcurrentHashMap<Class<*>, HostMethods>()
    private val hostMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val activeLogged = AtomicBoolean(false)
    private val methodErrorLogged = AtomicBoolean(false)
    private val actionErrorLogged = AtomicBoolean(false)
    private val hiddenTabPresent = AtomicBoolean(false)

    @Volatile private var hooked = false
    @Volatile private var runtimeTargets: RuntimeTargets? = null

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!ConfigManager.isHomeTabAutoHideEnabled) {
            XposedCompat.logD("[HomeBottomTabAutoHideHook] disabled by config")
            return
        }
        if (!tryMarkHooked()) return

        try {
            val targets = resolveRuntimeTargets(cl)
            runtimeTargets = targets
            val tabHostClass = targets.fragmentTabHostClass
            if (tabHostClass == null) {
                resetHooked()
                XposedCompat.log(
                    "[HomeBottomTabAutoHideHook] class NOT FOUND: " +
                        StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS
                )
                return
            }

            val scrollObservers = installRecyclerViewScrollObserver(mod, cl) +
                installWebViewScrollObserver(mod, cl)
            val tabObservers = installTabSelectionObservers(mod, tabHostClass)
            if (scrollObservers == 0 || tabObservers == 0) {
                resetHooked()
                XposedCompat.log(
                    "[HomeBottomTabAutoHideHook] hook point missing: " +
                        "scrollObservers=$scrollObservers, tabObservers=$tabObservers"
                )
                return
            }
            registerSystemBarCompatIfNeeded()
            XposedCompat.log(
                "[HomeBottomTabAutoHideHook] hook INSTALLED: " +
                    "scrollObservers=$scrollObservers, tabObservers=$tabObservers"
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[HomeBottomTabAutoHideHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installRecyclerViewScrollObserver(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, cl)
        if (clazz == null) {
            XposedCompat.log("[HomeBottomTabAutoHideHook] class NOT FOUND: ${StableTiebaHookPoints.RECYCLER_VIEW_CLASS}")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            METHOD_DISPATCH_ON_SCROLLED,
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
        ) ?: XposedCompat.findMethodOrNull(
            clazz,
            "onScrolled",
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
        )
        if (method == null) {
            XposedCompat.log("[HomeBottomTabAutoHideHook] method NOT FOUND: RecyclerView scroll dispatch")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            val target = chain.thisObject as? View
            val dy = (chain.args.getOrNull(1) as? Int) ?: 0
            if (target != null && dy != 0 && shouldHandleScroll()) {
                handleScroll(target, dy)
            }
            result
        }
        return 1
    }

    private fun installWebViewScrollObserver(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ): Int {
        var installed = 0
        val classNames = arrayOf(
            StableTiebaHookPoints.TB_WEB_VIEW_CLASS,
            StableTiebaHookPoints.NESTED_SCROLLING_WEB_VIEW_CLASS,
        )
        for (className in classNames) {
            val clazz = XposedCompat.findClassOrNull(className, cl)
            if (clazz == null) {
                XposedCompat.log("[HomeBottomTabAutoHideHook] class NOT FOUND: $className")
                continue
            }
            val method = XposedCompat.findMethodOrNull(
                clazz,
                METHOD_ON_SCROLL_CHANGED,
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE,
            )
            if (method == null) {
                XposedCompat.log("[HomeBottomTabAutoHideHook] method NOT FOUND: $className.onScrollChanged")
                continue
            }
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val target = chain.thisObject as? View
                val scrollY = (chain.args.getOrNull(1) as? Int) ?: 0
                val oldScrollY = (chain.args.getOrNull(3) as? Int) ?: 0
                val dy = scrollY - oldScrollY
                if (target != null && dy != 0 && shouldHandleScroll()) {
                    handleScroll(target, dy)
                }
                result
            }
            installed++
        }
        return installed
    }

    private fun installTabSelectionObservers(
        mod: io.github.libxposed.api.XposedModule,
        tabHostClass: Class<*>,
    ): Int {
        var installed = 0
        val methodNames = arrayOf(
            METHOD_ON_PAGE_SELECTED,
            METHOD_SET_CURRENT_TAB,
            METHOD_SET_CURRENT_TAB_BY_INDEX,
            METHOD_SET_CURRENT_TAB_BY_TYPE,
        )
        for (methodName in methodNames) {
            val method = XposedCompat.findMethodOrNull(tabHostClass, methodName, java.lang.Integer.TYPE)
                ?: continue
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { onTabHostSelectionChanged(it) }
                result
            }
            installed++
        }
        return installed
    }

    private fun handleScroll(target: View, dy: Int) {
        val resolved = resolveBottomTabForScrollTarget(target) ?: return
        val state = bottomTabStateFor(resolved.tabHost, resolved.wrapper)
        if (!ConfigManager.isHomeTabAutoHideEnabled) {
            if (state.hidden) showBottomTab(state)
            HomeTabAutoHideStrategy.reset(state)
            return
        }

        val currentTabType = state.currentTabType.takeIf { it >= 0 }
            ?: resolveCurrentTabType(resolved.tabHost).also { state.currentTabType = it }
        if (HomeTabAutoHideLockController.isLockedHidden()) {
            HomeTabAutoHideStrategy.reset(state)
            if (currentTabType == HOME_TAB_TYPE) {
                if (!state.hidden) {
                    hideBottomTab(state, target, resolved.tabHost)
                }
                return
            }
        }
        if (currentTabType != HOME_TAB_TYPE) {
            if (state.hidden) showBottomTab(state)
            HomeTabAutoHideStrategy.reset(state)
            return
        }

        if (resolved.wrapper.height <= 0 || resolved.wrapper.windowToken == null) return
        val direction = HomeTabAutoHideStrategy.directionOf(dy)
        val desiredHidden = HomeTabAutoHideStrategy.shouldHide(direction)
        if (state.hidden == desiredHidden) {
            HomeTabAutoHideStrategy.reset(state)
            return
        }
        if (!HomeTabAutoHideStrategy.reachedScrollThreshold(resolved.wrapper, state, direction, dy)) return

        if (performBottomTabAction(state, target, resolved.tabHost, desiredHidden)) {
            HomeTabAutoHideStrategy.reset(state)
        }
    }

    private fun onTabHostSelectionChanged(tabHost: View) {
        val state = synchronized(tabHostStates) {
            tabHostStates[tabHost]
        } ?: resolveBottomTabForTabHost(tabHost)?.let { resolved ->
            bottomTabStateFor(resolved.tabHost, resolved.wrapper)
        } ?: return

        state.currentTabType = resolveCurrentTabType(tabHost)
        HomeTabAutoHideStrategy.reset(state)
        if (HomeTabAutoHideLockController.isLockedHidden() && state.currentTabType != HOME_TAB_TYPE) {
            HomeTabAutoHideLockController.unlockForTabSwitch(tabHost)
            return
        }
        if (state.hidden && !HomeTabAutoHideLockController.isLockedHidden()) {
            showBottomTab(state)
        }
    }

    private fun resolveBottomTabForScrollTarget(target: View): ResolvedBottomTab? {
        if (!isMainTabActivityContext(target.context)) return null
        val targets = runtimeTargets ?: return null
        val root = target.rootView ?: target
        synchronized(bottomTabCache) {
            val cached = bottomTabCache[target]
            if (cached != null && cached.root === root && cached.tabHost.rootView === root) {
                return cached
            }
        }

        val now = SystemClock.uptimeMillis()
        synchronized(bottomTabMissRetryAt) {
            val retryAt = bottomTabMissRetryAt[target]
            if (retryAt != null && now < retryAt) return null
        }

        val tabHost = findFragmentTabHost(root, targets.fragmentTabHostClass)
        if (tabHost == null) {
            synchronized(bottomTabMissRetryAt) {
                bottomTabMissRetryAt[target] = now + TARGET_MISS_RETRY_INTERVAL_MS
            }
            return null
        }

        val resolved = resolveBottomTabForTabHost(tabHost, root)
        if (resolved != null) {
            synchronized(bottomTabCache) {
                bottomTabCache[target] = resolved
            }
            synchronized(bottomTabMissRetryAt) {
                bottomTabMissRetryAt.remove(target)
            }
            if (activeLogged.compareAndSet(false, true)) {
                XposedCompat.logD(
                    "[HomeBottomTabAutoHideHook] auto-hide observer active: " +
                        "target=${target.javaClass.name}, tabHost=${tabHost.javaClass.name}"
                )
            }
            return resolved
        }

        synchronized(bottomTabMissRetryAt) {
            bottomTabMissRetryAt[target] = now + TARGET_MISS_RETRY_INTERVAL_MS
        }
        return null
    }

    private fun resolveBottomTabForTabHost(tabHost: View, root: View = tabHost.rootView ?: tabHost): ResolvedBottomTab? {
        val methods = hostMethods(tabHost.javaClass) ?: return null
        val wrapper = try {
            methods.getTabWrapper.invoke(tabHost) as? View
        } catch (t: Throwable) {
            if (actionErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeBottomTabAutoHideHook] get tab wrapper FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            null
        } ?: return null

        configureWrapper(tabHost, wrapper)
        val state = bottomTabStateFor(tabHost, wrapper)
        if (state.currentTabType < 0) {
            state.currentTabType = resolveCurrentTabType(tabHost)
        }
        SystemBarCompatHook.applyIfNeeded(findActivity(tabHost.context))
        return ResolvedBottomTab(root, tabHost, wrapper)
    }

    private fun bottomTabStateFor(tabHost: View, wrapper: View): BottomTabState {
        synchronized(tabHostStates) {
            val cached = tabHostStates[tabHost]
            if (cached != null && cached.wrapper === wrapper) return cached
            val state = BottomTabState(wrapper)
            tabHostStates[tabHost] = state
            return state
        }
    }

    private fun configureWrapper(tabHost: View, wrapper: View) {
        val firstConfigure = synchronized(configuredWrappers) {
            if (configuredWrappers.containsKey(wrapper)) {
                false
            } else {
                configuredWrappers[wrapper] = true
                true
            }
        }
        if (!firstConfigure) return

        wrapper.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val state = synchronized(tabHostStates) {
                    tabHostStates[tabHost]
                } ?: return
                if (!ConfigManager.isHomeTabAutoHideEnabled) {
                    showBottomTab(state, animate = false)
                    return
                }
                if (state.hidden) {
                    v.translationY = hiddenOffset(v)
                    v.alpha = 0f
                    v.visibility = View.INVISIBLE
                }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        wrapper.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val state = synchronized(tabHostStates) {
                tabHostStates[tabHost]
            } ?: return@addOnLayoutChangeListener
            if (state.hidden) {
                v.translationY = hiddenOffset(v)
            }
        }
    }

    private fun resolveCurrentTabType(tabHost: View): Int {
        val methods = hostMethods(tabHost.javaClass) ?: return -1
        return try {
            (methods.getCurrentTabType.invoke(tabHost) as? Int) ?: -1
        } catch (t: Throwable) {
            if (actionErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeBottomTabAutoHideHook] get current tab type FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            -1
        }
    }

    private fun hideBottomTab(state: BottomTabState, scrollTarget: View, tabHost: View): Boolean {
        val wrapper = state.wrapper
        if (wrapper.height <= 0 || wrapper.windowToken == null) return false
        if (state.hidden && wrapper.visibility == View.INVISIBLE) return false
        if (isWebViewScrollTarget(scrollTarget)) {
            restoreContentInsetCompensation(state)
        } else {
            applyContentInsetCompensation(state, scrollTarget, tabHost)
        }
        state.hidden = true
        hiddenTabPresent.set(true)
        wrapper.animate().setListener(null)
        wrapper.animate().cancel()
        wrapper.visibility = View.VISIBLE
        return try {
            if (HomeTabAutoHideStrategy.ANIMATE_SCROLL_ACTIONS) {
                wrapper.animate()
                    .translationY(hiddenOffset(wrapper))
                    .alpha(0f)
                    .setDuration(HomeTabAutoHideStrategy.TRANSITION_DURATION_MS)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            wrapper.animate().setListener(null)
                            if (state.hidden) {
                                wrapper.visibility = View.INVISIBLE
                            }
                        }
                    })
                    .start()
            } else {
                wrapper.translationY = hiddenOffset(wrapper)
                wrapper.alpha = 0f
                wrapper.visibility = View.INVISIBLE
            }
            true
        } catch (t: Throwable) {
            state.hidden = false
            hiddenTabPresent.set(false)
            restoreContentInsetCompensation(state)
            wrapper.visibility = View.VISIBLE
            wrapper.translationY = 0f
            wrapper.alpha = 1f
            if (actionErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeBottomTabAutoHideHook] hide bottom tab FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            false
        }
    }

    private fun showBottomTab(state: BottomTabState, animate: Boolean = true): Boolean {
        val wrapper = state.wrapper
        if (!state.hidden && wrapper.visibility == View.VISIBLE && wrapper.translationY == 0f && wrapper.alpha == 1f) {
            return false
        }
        state.hidden = false
        hiddenTabPresent.set(false)
        restoreContentInsetCompensation(state)
        wrapper.animate().setListener(null)
        wrapper.animate().cancel()
        wrapper.visibility = View.VISIBLE
        return try {
            if (animate && HomeTabAutoHideStrategy.ANIMATE_SCROLL_ACTIONS) {
                wrapper.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(HomeTabAutoHideStrategy.TRANSITION_DURATION_MS)
                    .setListener(null)
                    .start()
            } else {
                wrapper.translationY = 0f
                wrapper.alpha = 1f
            }
            true
        } catch (t: Throwable) {
            state.hidden = true
            hiddenTabPresent.set(true)
            if (actionErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeBottomTabAutoHideHook] show bottom tab FAILED: ${t.message}")
                XposedCompat.log(t)
            }
            false
        }
    }

    private fun shouldHandleScroll(): Boolean {
        return ConfigManager.isHomeTabAutoHideEnabled || hiddenTabPresent.get()
    }

    private fun registerSystemBarCompatIfNeeded() {
        val app = ConfigManager.getAppContext() as? Application ?: return
        SystemBarCompatHook.register(app)
    }

    private fun performBottomTabAction(
        state: BottomTabState,
        scrollTarget: View,
        tabHost: View,
        hidden: Boolean,
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - state.lastActionAt < HomeTabAutoHideStrategy.ACTION_MIN_INTERVAL_MS) return false
        if (state.hidden == hidden) return false
        val changed = if (hidden) {
            hideBottomTab(state, scrollTarget, tabHost)
        } else {
            showBottomTab(state)
        }
        if (changed) {
            state.lastActionAt = now
        }
        return changed
    }

    internal fun applyLockedHidden(anchor: View): Boolean {
        val resolved = resolveBottomTabForScrollTarget(anchor) ?: return false
        val state = bottomTabStateFor(resolved.tabHost, resolved.wrapper)
        val currentTabType = state.currentTabType.takeIf { it >= 0 }
            ?: resolveCurrentTabType(resolved.tabHost).also { state.currentTabType = it }
        if (currentTabType != HOME_TAB_TYPE) return false
        HomeTabAutoHideStrategy.reset(state)
        if (state.hidden) return true
        return hideBottomTab(state, anchor, resolved.tabHost)
    }

    internal fun releaseLockedHidden(anchor: View): Boolean {
        var handled = false
        resolveBottomTabForScrollTarget(anchor)?.let { resolved ->
            val state = bottomTabStateFor(resolved.tabHost, resolved.wrapper)
            HomeTabAutoHideStrategy.reset(state)
            showBottomTab(state)
            handled = true
        }

        val states = synchronized(tabHostStates) {
            tabHostStates.values.toList()
        }
        for (state in states) {
            if (state.hidden) {
                showBottomTab(state)
                handled = true
            }
        }
        return handled
    }

    private fun applyContentInsetCompensation(state: BottomTabState, scrollTarget: View, tabHost: View) {
        restoreContentInsetCompensation(state)
        val barHeight = state.wrapper.height
        if (barHeight <= 0) return

        var current: View? = scrollTarget
        var depth = 0
        while (current != null && current !== tabHost && depth < 12) {
            if (current !== state.wrapper) {
                adjustBottomInset(current, barHeight)?.let { adjustment ->
                    state.contentInsetAdjustments.add(adjustment)
                    return
                }
            }
            current = current.parent as? View
            depth++
        }
    }

    private fun restoreContentInsetCompensation(state: BottomTabState) {
        if (state.contentInsetAdjustments.isEmpty()) return
        for (adjustment in state.contentInsetAdjustments.asReversed()) {
            val view = adjustment.view
            if (adjustment.restorePadding) {
                view.setPadding(
                    adjustment.paddingLeft,
                    adjustment.paddingTop,
                    adjustment.paddingRight,
                    adjustment.paddingBottom,
                )
            }
            if (adjustment.restoreBottomMargin) {
                val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
                if (lp.bottomMargin != adjustment.bottomMargin) {
                    lp.bottomMargin = adjustment.bottomMargin
                    view.layoutParams = lp
                    view.requestLayout()
                }
            }
        }
        state.contentInsetAdjustments.clear()
    }

    private fun adjustBottomInset(view: View, barHeight: Int): ContentInsetAdjustment? {
        val paddingBottom = view.paddingBottom
        if (shouldCompensateInset(paddingBottom, barHeight)) {
            val adjustment = ContentInsetAdjustment(
                view = view,
                restorePadding = true,
                paddingLeft = view.paddingLeft,
                paddingTop = view.paddingTop,
                paddingRight = view.paddingRight,
                paddingBottom = paddingBottom,
            )
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                (paddingBottom - barHeight).coerceAtLeast(0),
            )
            return adjustment
        }

        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return null
        val bottomMargin = lp.bottomMargin
        if (!shouldCompensateInset(bottomMargin, barHeight)) return null
        val adjustment = ContentInsetAdjustment(
            view = view,
            restoreBottomMargin = true,
            bottomMargin = bottomMargin,
        )
        lp.bottomMargin = (bottomMargin - barHeight).coerceAtLeast(0)
        view.layoutParams = lp
        view.requestLayout()
        return adjustment
    }

    private fun shouldCompensateInset(value: Int, barHeight: Int): Boolean {
        if (value <= 0 || barHeight <= 0) return false
        val minInset = (barHeight * 0.45f).toInt().coerceAtLeast(1)
        val maxInset = (barHeight * 1.8f).toInt().coerceAtLeast(minInset)
        return value in minInset..maxInset
    }

    private fun isWebViewScrollTarget(view: View): Boolean {
        var clazz: Class<*>? = view.javaClass
        while (clazz != null) {
            val name = clazz.name
            if (name == StableTiebaHookPoints.TB_WEB_VIEW_CLASS ||
                name == StableTiebaHookPoints.NESTED_SCROLLING_WEB_VIEW_CLASS
            ) {
                return true
            }
            clazz = clazz.superclass
        }
        return false
    }

    private fun hiddenOffset(view: View): Float {
        val margin = (view.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        return (view.height + margin).coerceAtLeast(1).toFloat()
    }

    private fun findFragmentTabHost(root: View, tabHostClass: Class<*>?): View? {
        tabHostClass ?: return null
        if (tabHostClass.isInstance(root)) return root
        val group = root as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            val found = findFragmentTabHost(child, tabHostClass)
            if (found != null) return found
        }
        return null
    }

    private fun hostMethods(clazz: Class<*>): HostMethods? {
        hostMethodCache[clazz]?.let { return it }
        if (hostMethodMissCache.contains(clazz)) return null
        return try {
            val methods = HostMethods(
                getTabWrapper = clazz.getMethod(StableTiebaHookPoints.METHOD_GET_TAB_WRAPPER).apply {
                    isAccessible = true
                },
                getCurrentTabType = clazz.getMethod(StableTiebaHookPoints.METHOD_GET_CURRENT_TAB_TYPE).apply {
                    isAccessible = true
                },
            )
            hostMethodCache[clazz] = methods
            methods
        } catch (t: Throwable) {
            hostMethodMissCache.add(clazz)
            if (methodErrorLogged.compareAndSet(false, true)) {
                XposedCompat.log("[HomeBottomTabAutoHideHook] host methods NOT FOUND: ${t.message}")
                XposedCompat.log(t)
            }
            null
        }
    }

    private fun isMainTabActivityContext(context: Context?): Boolean {
        return findActivity(context)?.javaClass?.name == StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS
    }

    private fun findActivity(context: Context?): Activity? {
        var current = context
        var depth = 0
        while (current != null && depth < 8) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
            depth++
        }
        return null
    }

    private fun resolveRuntimeTargets(cl: ClassLoader): RuntimeTargets {
        return RuntimeTargets(
            fragmentTabHostClass = XposedCompat.findClassOrNull(
                StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS,
                cl,
            ),
        )
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

    private data class RuntimeTargets(
        val fragmentTabHostClass: Class<*>?,
    )

    private data class HostMethods(
        val getTabWrapper: Method,
        val getCurrentTabType: Method,
    )

    private data class ResolvedBottomTab(
        val root: View,
        val tabHost: View,
        val wrapper: View,
    )

    private data class BottomTabState(
        val wrapper: View,
        var currentTabType: Int = -1,
        var hidden: Boolean = false,
        override var lastDirection: Int = 0,
        override var accumulatedDy: Int = 0,
        override var hideThresholdPx: Int = 0,
        override var showThresholdPx: Int = 0,
        var lastActionAt: Long = 0L,
        val contentInsetAdjustments: MutableList<ContentInsetAdjustment> = ArrayList(),
    ) : HomeTabAutoHideStrategy.ScrollAccumulator

    private data class ContentInsetAdjustment(
        val view: View,
        val restorePadding: Boolean = false,
        val paddingLeft: Int = 0,
        val paddingTop: Int = 0,
        val paddingRight: Int = 0,
        val paddingBottom: Int = 0,
        val restoreBottomMargin: Boolean = false,
        val bottomMargin: Int = 0,
    )
}
