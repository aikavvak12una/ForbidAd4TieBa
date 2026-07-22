package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

object BottomTabTopLineHook {
    private const val METHOD_SET_SHOULD_DRAW_TOP_LINE = "setShouldDrawTopLine"
    private const val METHOD_SET_SHOULD_DRAW_DIVIDER_LINE = "setShouldDrawDividerLine"
    private const val METHOD_SET_TAB_CONTAINER_SHADOW_SHOW = "setTabContainerShadowShow"
    private const val METHOD_SET_UI_TYPE = "setUIType"
    private const val METHOD_SET_BACKGROUND_COLOR = "setBackgroundColor"
    private const val METHOD_SET_BACKGROUND_RESOURCE = "setBackgroundResource"
    private const val METHOD_GET_FRAGMENT_TAB_WIDGET = "getFragmentTabWidget"
    private const val MAX_BOUNDARY_DECORATION_HEIGHT_DP = 24f
    private const val MAX_TOP_EDGE_OFFSET_DP = 4f
    private const val MIN_FULL_WIDTH_RATIO = 0.75f

    private val installed = AtomicBoolean(false)
    private val applyErrorLogged = AtomicBoolean(false)
    private val configuredHosts = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val scheduledHosts = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val methodCache = Collections.synchronizedMap(mutableMapOf<MethodKey, Method?>())
    private val applying = ThreadLocal<Boolean>()

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!isRuntimeEnabled()) {
            XposedCompat.logD("[BottomTabTopLineHook] disabled by config")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        try {
            val hostClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS, cl)
            val widgetClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS, cl)
            val skinManagerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.SKIN_MANAGER_CLASS, cl)
            if (hostClass == null || widgetClass == null) {
                installed.set(false)
                XposedCompat.log(
                    "[BottomTabTopLineHook] class NOT FOUND: " +
                        "host=${hostClass != null}, widget=${widgetClass != null}",
                )
                return
            }

            var hookCount = 0
            hookCount += hookConstructors(mod, hostClass, ::afterHostChanged)
            hookCount += hookConstructors(mod, widgetClass, ::afterWidgetChanged)
            hookCount += hookBooleanMethod(mod, hostClass, METHOD_SET_SHOULD_DRAW_TOP_LINE, ::afterHostChanged)
            hookCount += hookBooleanMethod(mod, hostClass, METHOD_SET_SHOULD_DRAW_DIVIDER_LINE, ::afterHostChanged)
            hookCount += hookBooleanMethod(mod, hostClass, METHOD_SET_TAB_CONTAINER_SHADOW_SHOW, ::afterHostChanged)
            hookCount += hookIntMethod(mod, hostClass, METHOD_SET_UI_TYPE, ::afterHostChanged)
            hookCount += hookBooleanMethod(mod, widgetClass, METHOD_SET_SHOULD_DRAW_TOP_LINE, ::afterWidgetChanged)
            hookCount += hookBooleanMethod(mod, widgetClass, METHOD_SET_SHOULD_DRAW_DIVIDER_LINE, ::afterWidgetChanged)
            hookCount += hookSkinManagerBackgroundMethods(mod, skinManagerClass)

            if (hookCount == 0) {
                installed.set(false)
                XposedCompat.log("[BottomTabTopLineHook] hook point missing")
                return
            }
            XposedCompat.log(
                "[BottomTabTopLineHook] hook INSTALLED: " +
                    "${hostClass.name}/${widgetClass.name} hooks=$hookCount",
            )
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[BottomTabTopLineHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookConstructors(
        mod: io.github.libxposed.api.XposedModule,
        clazz: Class<*>,
        after: (Any?) -> Unit,
    ): Int {
        var count = 0
        for (ctor in clazz.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                after(chain.thisObject)
                result
            }
            count++
        }
        return count
    }

    private fun hookBooleanMethod(
        mod: io.github.libxposed.api.XposedModule,
        clazz: Class<*>,
        methodName: String,
        after: (Any?) -> Unit,
    ): Int {
        val method = XposedCompat.findMethodOrNull(clazz, methodName, java.lang.Boolean.TYPE) ?: run {
            XposedCompat.logD("[BottomTabTopLineHook] method NOT FOUND: ${clazz.name}.$methodName(boolean)")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (!isApplying()) {
                after(chain.thisObject)
            }
            result
        }
        return 1
    }

    private fun hookIntMethod(
        mod: io.github.libxposed.api.XposedModule,
        clazz: Class<*>,
        methodName: String,
        after: (Any?) -> Unit,
    ): Int {
        val method = XposedCompat.findMethodOrNull(clazz, methodName, java.lang.Integer.TYPE) ?: run {
            XposedCompat.logD("[BottomTabTopLineHook] method NOT FOUND: ${clazz.name}.$methodName(int)")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (!isApplying()) {
                after(chain.thisObject)
            }
            result
        }
        return 1
    }

    private fun hookSkinManagerBackgroundMethods(
        mod: io.github.libxposed.api.XposedModule,
        skinManagerClass: Class<*>?,
    ): Int {
        if (skinManagerClass == null) {
            XposedCompat.logD("[BottomTabTopLineHook] class NOT FOUND: ${StableTiebaHookPoints.SKIN_MANAGER_CLASS}")
            return 0
        }
        var count = 0
        count += hookSkinBackgroundMethod(
            mod,
            skinManagerClass,
            METHOD_SET_BACKGROUND_COLOR,
            java.lang.Integer.TYPE,
        )
        count += hookSkinBackgroundMethod(
            mod,
            skinManagerClass,
            METHOD_SET_BACKGROUND_COLOR,
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
        )
        count += hookSkinBackgroundMethod(
            mod,
            skinManagerClass,
            METHOD_SET_BACKGROUND_RESOURCE,
            java.lang.Integer.TYPE,
        )
        count += hookSkinBackgroundMethod(
            mod,
            skinManagerClass,
            METHOD_SET_BACKGROUND_RESOURCE,
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
        )
        return count
    }

    private fun hookSkinBackgroundMethod(
        mod: io.github.libxposed.api.XposedModule,
        clazz: Class<*>,
        methodName: String,
        vararg extraParameterTypes: Class<*>,
    ): Int {
        val method = XposedCompat.findMethodOrNull(
            clazz,
            methodName,
            View::class.java,
            *extraParameterTypes,
        ) ?: run {
            val signature = extraParameterTypes.joinToString(prefix = "View", separator = ",") { it.simpleName }
            XposedCompat.logD("[BottomTabTopLineHook] method NOT FOUND: ${clazz.name}.$methodName($signature)")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val target = chain.args.firstOrNull() as? View
            if (target == null || !isRuntimeEnabled()) {
                return@intercept chain.proceed()
            }
            if (isBottomTabBoundaryDecorationView(target)) {
                suppressBoundaryDecorationView(target)
                return@intercept null
            }
            val result = chain.proceed()
            if (isRuntimeEnabled() && isBottomTabBoundaryDecorationView(target)) {
                suppressBoundaryDecorationView(target)
            }
            result
        }
        return 1
    }

    private fun afterHostChanged(owner: Any?) {
        val host = owner as? View ?: return
        if (!isRuntimeEnabled()) return
        applyHostDecorationsSafely(host)
        scheduleHostReapply(host)
    }

    private fun afterWidgetChanged(owner: Any?) {
        val widget = owner as? View ?: return
        if (!isRuntimeEnabled()) return
        applyWidgetDecorationsSafely(widget)
        findHostAncestor(widget)?.let { host ->
            scheduleHostReapply(host)
        }
    }

    private fun applyHostDecorationsSafely(host: View) {
        try {
            applyHostDecorations(host)
        } catch (t: Throwable) {
            logApplyError("apply host failed", t)
        }
    }

    private fun applyWidgetDecorationsSafely(widget: View) {
        try {
            applyWidgetDecorations(widget)
        } catch (t: Throwable) {
            logApplyError("apply widget failed", t)
        }
    }

    private fun applyHostDecorations(host: View) {
        if (!shouldHandleHost(host)) return
        configureHostRefresh(host)

        runWithoutReentry {
            invokeBooleanMethod(host, METHOD_SET_SHOULD_DRAW_TOP_LINE, false)
            invokeBooleanMethod(host, METHOD_SET_SHOULD_DRAW_DIVIDER_LINE, false)
            invokeBooleanMethod(host, METHOD_SET_TAB_CONTAINER_SHADOW_SHOW, false)
            invokeNoArgView(host, METHOD_GET_FRAGMENT_TAB_WIDGET)?.let { widget ->
                invokeBooleanMethod(widget, METHOD_SET_SHOULD_DRAW_TOP_LINE, false)
                invokeBooleanMethod(widget, METHOD_SET_SHOULD_DRAW_DIVIDER_LINE, false)
                widget.invalidate()
            }
        }
        hideBoundaryLineViews(host)
    }

    private fun applyWidgetDecorations(widget: View) {
        val host = findHostAncestor(widget)
        if (host != null) {
            if (!shouldHandleHost(host)) return
        } else if (!isRuntimeEnabled() || !isMainTabActivityContext(widget.context)) {
            return
        }
        runWithoutReentry {
            invokeBooleanMethod(widget, METHOD_SET_SHOULD_DRAW_TOP_LINE, false)
            invokeBooleanMethod(widget, METHOD_SET_SHOULD_DRAW_DIVIDER_LINE, false)
        }
        widget.invalidate()
    }

    private fun configureHostRefresh(host: View) {
        val firstConfigure = synchronized(configuredHosts) {
            if (configuredHosts.containsKey(host)) {
                false
            } else {
                configuredHosts[host] = true
                true
            }
        }
        if (!firstConfigure) return

        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                scheduleHostReapply(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        host.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            applyHostDecorationsSafely(v)
        }
    }

    private fun scheduleHostReapply(host: View) {
        if (!isRuntimeEnabled()) return
        synchronized(scheduledHosts) {
            if (scheduledHosts.containsKey(host)) return
            scheduledHosts[host] = true
        }
        host.post {
            try {
                applyHostDecorationsSafely(host)
            } finally {
                synchronized(scheduledHosts) {
                    scheduledHosts.remove(host)
                }
            }
        }
    }

    private fun hideBoundaryLineViews(host: View) {
        collectBoundaryLineViews(host).forEach { line ->
            suppressBoundaryDecorationView(line)
        }
    }

    private fun suppressBoundaryDecorationView(view: View) {
        val layoutParams = view.layoutParams
        if (layoutParams != null && layoutParams.height != 0) {
            layoutParams.height = 0
            view.layoutParams = layoutParams
        }
        if (view.minimumHeight != 0) {
            view.minimumHeight = 0
        }
        if (view.visibility != View.GONE) {
            view.visibility = View.GONE
        }
        if (view.alpha != 0f) {
            view.alpha = 0f
        }
        if (view.background != null) {
            view.background = null
        }
    }

    private fun collectBoundaryLineViews(host: View): List<View> {
        val lines = ArrayList<View>(2)
        collectBoundaryLineViews(host, lines)
        invokeNoArgView(host, StableTiebaHookPoints.METHOD_GET_TAB_WRAPPER)?.let { wrapper ->
            collectBoundaryLineViews(wrapper, lines)
        }
        invokeNoArgView(host, METHOD_GET_FRAGMENT_TAB_WIDGET)?.let { widget ->
            collectDescendantBoundaryLineViews(widget, lines)
        }
        return lines.distinct()
    }

    private fun collectBoundaryLineViews(container: View, out: MutableList<View>) {
        val group = container as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (isBoundaryLineCandidate(child)) {
                out.add(child)
            }
        }
    }

    private fun collectDescendantBoundaryLineViews(container: View, out: MutableList<View>) {
        val group = container as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (isBottomTabBoundaryDecorationView(child)) {
                out.add(child)
            }
            if (child is ViewGroup) {
                collectDescendantBoundaryLineViews(child, out)
            }
        }
    }

    private fun isBoundaryLineCandidate(view: View): Boolean {
        if (view is ViewGroup) return false
        if (view.isClickable || view.isLongClickable || view.isFocusable || view.hasOnClickListeners()) {
            return false
        }
        val maxHeightPx = maxOf(2, (view.resources.displayMetrics.density * MAX_BOUNDARY_DECORATION_HEIGHT_DP).toInt())
        val layoutHeight = view.layoutParams?.height ?: 0
        return layoutHeight in 1..maxHeightPx ||
            view.height in 1..maxHeightPx ||
            view.measuredHeight in 1..maxHeightPx
    }

    private fun isBottomTabBoundaryDecorationView(view: View): Boolean {
        if (!isRuntimeEnabled()) return false
        if (!isBoundaryLineCandidate(view)) return false
        val host = findHostAncestor(view)
        if (host == null) {
            val bottomIndicator = findBottomIndicatorAncestor(view, null) ?: return false
            return isMainTabActivityContext(view.context) &&
                isIndicatorBoundaryDecoration(view, bottomIndicator)
        }
        if (!shouldHandleHost(host)) return false
        val parent = view.parent
        if (parent === host) return true
        val wrapper = invokeNoArgView(host, StableTiebaHookPoints.METHOD_GET_TAB_WRAPPER)
        if (parent === wrapper) return true
        val widget = invokeNoArgView(host, METHOD_GET_FRAGMENT_TAB_WIDGET) as? ViewGroup ?: return false
        if (!hasChild(widget, view)) return false
        val bottomIndicator = findBottomIndicatorAncestor(view, widget) ?: return false
        return isIndicatorBoundaryDecoration(view, bottomIndicator)
    }

    private fun isIndicatorBoundaryDecoration(view: View, indicator: View): Boolean {
        if (isFullWidthDecoration(view, indicator)) return true
        return view.javaClass == View::class.java && isNearTopEdge(view, indicator)
    }

    private fun isFullWidthDecoration(view: View, ancestor: View): Boolean {
        val layoutWidth = view.layoutParams?.width ?: 0
        if (layoutWidth == ViewGroup.LayoutParams.MATCH_PARENT) return true
        val width = maxOf(view.width, view.measuredWidth)
        val ancestorWidth = maxOf(ancestor.width, ancestor.measuredWidth)
        return ancestorWidth > 0 && width >= (ancestorWidth * MIN_FULL_WIDTH_RATIO).toInt()
    }

    private fun isNearTopEdge(view: View, ancestor: View): Boolean {
        val top = topWithinAncestor(view, ancestor) ?: return false
        val maxTopPx = maxOf(2, (view.resources.displayMetrics.density * MAX_TOP_EDGE_OFFSET_DP).toInt())
        return top in -maxTopPx..maxTopPx
    }

    private fun topWithinAncestor(view: View, ancestor: View): Int? {
        var top = view.top
        var current = view.parent
        var depth = 0
        while (current is View && depth < 16) {
            if (current === ancestor) return top
            top += current.top
            current = current.parent
            depth++
        }
        return null
    }

    private fun findBottomIndicatorAncestor(view: View, stopAt: View?): View? {
        var current = view.parent
        var depth = 0
        while (current is View && current !== stopAt && depth < 16) {
            if (isBottomIndicatorClass(current.javaClass)) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun isBottomIndicatorClass(clazz: Class<*>): Boolean {
        return hasClassInHierarchy(clazz, StableTiebaHookPoints.MAIN_TAB_BOTTOM_INDICATOR_CLASS) ||
            hasClassInHierarchy(clazz, StableTiebaHookPoints.MAIN_TAB_BOTTOM_OPT_INDICATOR_CLASS)
    }

    private fun hasClassInHierarchy(clazz: Class<*>, className: String): Boolean {
        var current: Class<*>? = clazz
        while (current != null) {
            if (current.name == className) return true
            current = current.superclass
        }
        return false
    }

    private fun invokeBooleanMethod(target: View, methodName: String, value: Boolean): Boolean {
        return try {
            val method = findCachedMethodInHierarchy(target.javaClass, methodName, java.lang.Boolean.TYPE) ?: return false
            method.invoke(target, value)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun invokeNoArgView(target: View, methodName: String): View? {
        return try {
            findCachedMethodInHierarchy(target.javaClass, methodName)?.invoke(target) as? View
        } catch (_: Throwable) {
            null
        }
    }

    private fun findCachedMethodInHierarchy(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method? {
        val key = MethodKey(clazz, methodName, parameterTypes.toList())
        synchronized(methodCache) {
            val cached = methodCache[key]
            if (cached != null || methodCache.containsKey(key)) return cached
        }
        val resolved = findMethodInHierarchy(clazz, methodName, *parameterTypes)
        synchronized(methodCache) {
            methodCache[key] = resolved
        }
        return resolved
    }

    private fun findMethodInHierarchy(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private data class MethodKey(
        val clazz: Class<*>,
        val methodName: String,
        val parameterTypes: List<Class<*>>,
    )

    private fun isMainTabActivityContext(context: Context?): Boolean {
        return findActivity(context)?.javaClass?.name == StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS
    }

    private fun shouldHandleHost(host: View): Boolean {
        if (!isRuntimeEnabled()) return false
        if (isMainTabActivityContext(host.context)) return true
        if (host.javaClass.name != StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS) return false
        val wrapper = invokeNoArgView(host, StableTiebaHookPoints.METHOD_GET_TAB_WRAPPER) as? ViewGroup ?: return false
        val widget = invokeNoArgView(host, METHOD_GET_FRAGMENT_TAB_WIDGET) ?: return false
        if (widget.javaClass.name != StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS) return false
        return hasChild(wrapper, widget)
    }

    private fun isRuntimeEnabled(): Boolean {
        return ConfigManager.snapshot().isHomeNativeGlassRuntimeActive()
    }

    private fun hasChild(group: ViewGroup, target: View): Boolean {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (child === target) return true
            if (child is ViewGroup && hasChild(child, target)) return true
        }
        return false
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

    private fun findHostAncestor(view: View): View? {
        var depth = 0
        var current = view.parent
        while (current is View && depth < 16) {
            if (current.javaClass.name == StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun isApplying(): Boolean = applying.get() == true

    private inline fun runWithoutReentry(block: () -> Unit) {
        if (isApplying()) return
        applying.set(true)
        try {
            block()
        } finally {
            applying.remove()
        }
    }

    private fun logApplyError(message: String, t: Throwable) {
        if (applyErrorLogged.compareAndSet(false, true)) {
            XposedCompat.log("[BottomTabTopLineHook] $message: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
