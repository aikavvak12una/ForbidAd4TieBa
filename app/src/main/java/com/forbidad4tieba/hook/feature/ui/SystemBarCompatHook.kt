package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.app.Application
import android.graphics.Color
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

    private data class BottomTabInsetState(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val height: Int,
    )

    fun register(app: Application) {
        if (!registered.compareAndSet(false, true)) return
        installWindowHooks()
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
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
        })
        XposedCompat.log("$TAG registered")
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
        return ConfigManager.isHomeNativeGlassEnabled &&
            ConfigManager.homeNativeGlassBackgroundImagePath.isNotBlank() &&
            isGestureNavigation(activity) &&
            isSupportedActivity(activity)
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
    }

    private fun applyBottomInset(view: View, insetBottom: Int) {
        val state = synchronized(bottomTabInsetStates) {
            bottomTabInsetStates[view] ?: BottomTabInsetState(
                left = view.paddingLeft,
                top = view.paddingTop,
                right = view.paddingRight,
                bottom = view.paddingBottom,
                height = view.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { bottomTabInsetStates[view] = it }
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
        val lp = view.layoutParams ?: return
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
