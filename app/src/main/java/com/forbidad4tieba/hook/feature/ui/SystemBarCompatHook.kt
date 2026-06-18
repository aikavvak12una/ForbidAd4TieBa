package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object SystemBarCompatHook {
    private const val TAG = "[SystemBarCompatHook]"
    private const val NAV_MODE_GESTURAL = 2
    private val registered = AtomicBoolean(false)
    private val firstErrorLogged = AtomicBoolean(false)
    private val windowActivities = Collections.synchronizedMap(WeakHashMap<Window, Activity>())
    private val navigationBarColorHookedClasses = ConcurrentHashMap.newKeySet<String>()
    private val bottomTabInsetStates = Collections.synchronizedMap(WeakHashMap<View, BottomTabInsetState>())
    private val requestedNavigationBarColors = Collections.synchronizedMap(WeakHashMap<Window, Int>())
    @Volatile private var registeredApp: Application? = null
    @Volatile private var registeredCallback: Application.ActivityLifecycleCallbacks? = null

    private data class BottomTabInsetState(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        var height: Int,
        val background: Drawable?,
        var gestureBridgeColor: Int? = null,
        var insetBottom: Int = 0,
        var layoutGuardInstalled: Boolean = false,
    )

    fun register(app: Application) {
        if (!registered.compareAndSet(false, true)) return
        installWindowHooks()
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                rememberActivityWindow(activity)
                applyIfNeeded(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                rememberActivityWindow(activity)
                applyIfNeeded(activity)
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                forgetActivityWindow(activity)
            }
        }
        registeredApp = app
        registeredCallback = callback
        app.registerActivityLifecycleCallbacks(callback)
        XposedCompat.log("$TAG registered")
    }

    fun prepareForHotReload() {
        val app = registeredApp
        val callback = registeredCallback
        if (app != null && callback != null) {
            runCatching {
                app.unregisterActivityLifecycleCallbacks(callback)
            }.onFailure { t ->
                XposedCompat.logW("$TAG unregister failed: ${t.message}")
            }
        }
        registeredApp = null
        registeredCallback = null
        registered.set(false)
        firstErrorLogged.set(false)
        windowActivities.clear()
        navigationBarColorHookedClasses.clear()
        bottomTabInsetStates.clear()
        requestedNavigationBarColors.clear()
    }

    fun applyIfNeeded(activity: Activity?) {
        val host = activity ?: return
        if (!shouldApply(host)) return
        try {
            applyGestureNavigationCompat(host)
            host.window?.decorView?.post {
                if (shouldApply(host)) {
                    applyHomeBottomTabSafeInset(host)
                }
            }
        } catch (t: Throwable) {
            logFirstError("apply failed: ${t.message}")
        }
    }

    fun gestureNavigationInsetBottom(activity: Activity?): Int {
        val host = activity ?: return 0
        if (!isGestureNavigation(host)) return 0
        val decor = host.window?.decorView ?: return 0
        return resolveNavigationBarInsetBottom(host, decor)
    }

    private fun installWindowHooks() {
        val mod = XposedCompat.module ?: return
        runCatching {
            val getWindowMethod = Activity::class.java.getDeclaredMethod("getWindow")
            getWindowMethod.isAccessible = true
            mod.hook(getWindowMethod).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                val window = result as? Window
                if (activity != null && window != null) {
                    rememberActivityWindow(activity, window)
                }
                result
            }
        }.onFailure { t ->
            logFirstError("getWindow hook failed: ${t.message}")
        }
        runCatching {
            Class.forName("com.android.internal.policy.PhoneWindow", false, ClassLoader.getSystemClassLoader())
        }.getOrNull()?.let { installNavigationBarColorHook(it) }
    }

    private fun installNavigationBarColorHook(windowClass: Class<*>) {
        val mod = XposedCompat.module ?: return
        val method = findMethodInHierarchy(windowClass, "setNavigationBarColor", java.lang.Integer.TYPE)
            ?: return
        val key = "${method.declaringClass.name}#setNavigationBarColor"
        if (!navigationBarColorHookedClasses.add(key)) return
        runCatching {
            if (Modifier.isAbstract(method.modifiers)) {
                navigationBarColorHookedClasses.remove(key)
                return
            }
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val window = chain.thisObject as? Window ?: return@intercept chain.proceed()
                val activity = synchronized(windowActivities) { windowActivities[window] }
                    ?: return@intercept chain.proceed()
                if (!shouldApply(activity)) return@intercept chain.proceed()
                val color = (chain.args.getOrNull(0) as? Number)?.toInt()
                    ?: return@intercept chain.proceed()
                rememberRequestedNavigationBarColor(window, color)
                val result = if (color == Color.TRANSPARENT) {
                    chain.proceed()
                } else {
                    chain.proceed(arrayOf<Any?>(Color.TRANSPARENT))
                }
                window.decorView?.post {
                    if (shouldApply(activity)) {
                        applyGestureNavigationCompat(activity)
                    }
                }
                result
            }
            XposedCompat.logD { "$TAG navigation color hook installed: $key" }
        }.onFailure { t ->
            navigationBarColorHookedClasses.remove(key)
            logFirstError("navigation color hook failed: $key, ${t.message}")
        }
    }

    private fun rememberRequestedNavigationBarColor(window: Window, color: Int) {
        if (Color.alpha(color) <= 0) return
        synchronized(requestedNavigationBarColors) {
            requestedNavigationBarColors[window] = color
        }
    }

    private fun rememberActivityWindow(activity: Activity) {
        val window = activity.window ?: return
        rememberActivityWindow(activity, window)
    }

    private fun rememberActivityWindow(activity: Activity, window: Window) {
        synchronized(windowActivities) {
            windowActivities[window] = activity
        }
        installNavigationBarColorHook(window.javaClass)
    }

    private fun forgetActivityWindow(activity: Activity) {
        synchronized(windowActivities) {
            val iterator = windowActivities.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value === activity) iterator.remove()
            }
        }
    }

    private fun findMethodInHierarchy(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): java.lang.reflect.Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredMethod(methodName, *parameterTypes)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun logFirstError(message: String) {
        if (firstErrorLogged.compareAndSet(false, true)) {
            XposedCompat.logD { "$TAG $message" }
        }
    }

    private fun shouldApply(activity: Activity): Boolean {
        if (!isGestureNavigation(activity)) return false
        if (isHomeNativeGlassRuntimeActive()) return isSupportedActivity(activity)
        return ConfigManager.isHomeTabAutoHideEnabled && isMainTabActivity(activity)
    }

    private fun isHomeNativeGlassRuntimeActive(): Boolean {
        return ConfigManager.isHomeNativeGlassEnabled &&
            ConfigManager.hasAnyHomeNativeGlassBackgroundImage
    }

    @Suppress("DEPRECATION")
    private fun applyGestureNavigationCompat(activity: Activity) {
        val window = activity.window ?: return
        val decor = window.decorView ?: return
        val targetFlags = decor.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (decor.systemUiVisibility != targetFlags) {
            decor.systemUiVisibility = targetFlags
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            window.navigationBarColor != Color.TRANSPARENT
        ) {
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = window.attributes ?: return
            if (attrs.layoutInDisplayCutoutMode !=
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            ) {
                attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = attrs
            }
        }
        applyHomeBottomTabSafeInset(activity)
    }

    private fun applyHomeBottomTabSafeInset(activity: Activity) {
        if (!isMainTabActivity(activity)) return
        val decor = activity.window?.decorView ?: return
        val insetBottom = resolveNavigationBarInsetBottom(activity, decor)
        if (insetBottom <= 0) return
        val tabHost = findViewByClassName(decor, StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS) ?: return
        val wrapper = invokeNoArgView(tabHost, StableTiebaHookPoints.METHOD_GET_TAB_WRAPPER) ?: tabHost
        applyBottomInset(wrapper, insetBottom)
        applyHomeBottomTabGestureBridge(activity, tabHost, wrapper)
    }

    private fun applyBottomInset(view: View, insetBottom: Int) {
        val state = synchronized(bottomTabInsetStates) {
            bottomTabInsetStates[view] ?: BottomTabInsetState(
                left = view.paddingLeft,
                top = view.paddingTop,
                right = view.paddingRight,
                bottom = view.paddingBottom,
                height = view.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                background = view.background,
            ).also { bottomTabInsetStates[view] = it }
        }
        state.insetBottom = insetBottom
        installBottomInsetLayoutGuard(view, state)
        val lp = view.layoutParams
        val currentHeight = lp?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        if (state.height <= 0 && currentHeight > 0) {
            state.height = currentHeight
        }
        val targetBottom = state.bottom + insetBottom
        if (
            view.paddingLeft != state.left ||
            view.paddingTop != state.top ||
            view.paddingRight != state.right ||
            view.paddingBottom != targetBottom
        ) {
            view.setPadding(state.left, state.top, state.right, targetBottom)
        }
        if (lp == null) return
        if (state.height > 0) {
            val targetHeight = state.height + insetBottom
            if (lp.height != targetHeight) {
                lp.height = targetHeight
                view.layoutParams = lp
            }
        } else {
            view.requestLayout()
        }
    }

    private fun installBottomInsetLayoutGuard(view: View, state: BottomTabInsetState) {
        if (state.layoutGuardInstalled) return
        state.layoutGuardInstalled = true
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val currentState = synchronized(bottomTabInsetStates) {
                bottomTabInsetStates[v]
            } ?: return@addOnLayoutChangeListener
            val insetBottom = currentState.insetBottom
            if (insetBottom <= 0) return@addOnLayoutChangeListener

            val targetBottom = currentState.bottom + insetBottom
            val targetHeight = currentState.height
                .takeIf { it > 0 }
                ?.let { it + insetBottom }
            val currentHeight = v.layoutParams?.height
            val needsHeightInference = currentState.height <= 0 &&
                currentHeight != null &&
                currentHeight > 0
            if (
                !needsHeightInference &&
                v.paddingLeft == currentState.left &&
                v.paddingTop == currentState.top &&
                v.paddingRight == currentState.right &&
                v.paddingBottom == targetBottom &&
                (targetHeight == null || currentHeight == targetHeight)
            ) {
                return@addOnLayoutChangeListener
            }

            v.post {
                val latestState = synchronized(bottomTabInsetStates) {
                    bottomTabInsetStates[v]
                } ?: return@post
                val latestInsetBottom = latestState.insetBottom
                if (latestInsetBottom > 0) {
                    applyBottomInset(v, latestInsetBottom)
                }
            }
        }
    }

    private fun applyHomeBottomTabGestureBridge(activity: Activity, tabHost: View, wrapper: View) {
        val state = synchronized(bottomTabInsetStates) { bottomTabInsetStates[wrapper] } ?: return
        if (!ConfigManager.isHomeTabAutoHideEnabled || isHomeNativeGlassRuntimeActive()) {
            restoreGestureBridgeIfNeeded(wrapper, state)
            return
        }

        val currentBackground = wrapper.background
        if (
            state.gestureBridgeColor == null &&
            currentBackground != null &&
            !isTransparentColorDrawable(currentBackground)
        ) {
            return
        }

        val color = resolveHomeBottomTabGestureBridgeColor(activity, tabHost, wrapper)
        if (color == null) {
            restoreGestureBridgeIfNeeded(wrapper, state)
            return
        }
        if (state.gestureBridgeColor == color && opaqueBackgroundColor(wrapper) == color) return
        wrapper.background = ColorDrawable(color)
        state.gestureBridgeColor = color
    }

    private fun restoreGestureBridgeIfNeeded(wrapper: View, state: BottomTabInsetState) {
        if (state.gestureBridgeColor == null) return
        wrapper.background = state.background
        state.gestureBridgeColor = null
    }

    private fun resolveHomeBottomTabGestureBridgeColor(
        activity: Activity,
        tabHost: View,
        wrapper: View,
    ): Int? {
        invokeNoArgView(tabHost, "getFragmentTabWidget")?.let { widget ->
            opaqueBackgroundColor(widget)?.let { return it }
            opaqueLargeChildBackgroundColor(widget)?.let { return it }
        }
        opaqueBackgroundColor(tabHost)?.let { return it }
        opaqueLargeChildBackgroundColor(wrapper)?.let { return it }
        val window = activity.window
        return synchronized(requestedNavigationBarColors) {
            requestedNavigationBarColors[window]
        }?.takeIf { Color.alpha(it) > 0 }
    }

    private fun opaqueBackgroundColor(view: View): Int? {
        val color = (view.background as? ColorDrawable)?.color ?: return null
        return color.takeIf { Color.alpha(it) > 0 }
    }

    private fun opaqueLargeChildBackgroundColor(view: View): Int? {
        val group = view as? ViewGroup ?: return null
        val minHeight = (view.resources.displayMetrics.density * 8f + 0.5f).toInt().coerceAtLeast(1)
        val minWidth = if (view.width > 0) (view.width * 0.4f).toInt() else 0
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            val color = opaqueBackgroundColor(child) ?: continue
            val lp = child.layoutParams
            val childHeight = maxOf(child.height, child.measuredHeight, lp?.height ?: 0)
            val childWidth = maxOf(child.width, child.measuredWidth, lp?.width ?: 0)
            val matchesWidth = minWidth <= 0 ||
                childWidth >= minWidth ||
                lp?.width == ViewGroup.LayoutParams.MATCH_PARENT
            if (childHeight >= minHeight && matchesWidth) return color
        }
        return null
    }

    private fun isTransparentColorDrawable(drawable: Drawable): Boolean {
        return drawable is ColorDrawable && Color.alpha(drawable.color) == 0
    }

    @Suppress("DEPRECATION")
    private fun resolveNavigationBarInsetBottom(activity: Activity, decor: View): Int {
        val insets = decor.rootWindowInsets
        val insetBottom = when {
            insets == null -> 0
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            else -> insets.systemWindowInsetBottom
        }
        if (insetBottom > 0) return insetBottom
        val res = activity.resources ?: return 0
        val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id <= 0) return 0
        return runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0)
    }

    private fun findViewByClassName(view: View, className: String): View? {
        if (view.javaClass.name == className) return view
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            val found = findViewByClassName(group.getChildAt(index) ?: continue, className)
            if (found != null) return found
        }
        return null
    }

    private fun invokeNoArgView(target: View, methodName: String): View? {
        return try {
            var current: Class<*>? = target.javaClass
            while (current != null && current != Any::class.java) {
                val method = current.declaredMethods.firstOrNull {
                    it.name == methodName && it.parameterTypes.isEmpty()
                }
                if (method != null) {
                    method.isAccessible = true
                    return method.invoke(target) as? View
                }
                current = current.superclass
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun isGestureNavigation(activity: Activity): Boolean {
        val res = activity.resources ?: return false
        val id = res.getIdentifier("config_navBarInteractionMode", "integer", "android")
        if (id <= 0) return false
        return runCatching { res.getInteger(id) == NAV_MODE_GESTURAL }.getOrDefault(false)
    }

    private fun isSupportedActivity(activity: Activity): Boolean {
        var current: Class<*>? = activity.javaClass
        while (current != null && current != Any::class.java) {
            when (current.name) {
                StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS,
                StableTiebaHookPoints.PB_ACTIVITY_CLASS,
                StableTiebaHookPoints.PB_ABS_ACTIVITY_CLASS,
                StableTiebaHookPoints.NEW_SUB_PB_ACTIVITY_CLASS,
                StableTiebaHookPoints.FOLD_COMMENT_ACTIVITY_CLASS -> return true
            }
            current = current.superclass
        }
        return false
    }

    private fun isMainTabActivity(activity: Activity): Boolean {
        var current: Class<*>? = activity.javaClass
        while (current != null && current != Any::class.java) {
            if (current.name == StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS) return true
            current = current.superclass
        }
        return false
    }
}
