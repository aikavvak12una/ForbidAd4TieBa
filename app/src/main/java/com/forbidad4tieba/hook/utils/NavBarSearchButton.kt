package com.forbidad4tieba.hook.utils

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/**
 * Shared helper for injecting a search button into the host NavigationBar.
 *
 * Callers still decide how to obtain the NavigationBar instance and which sibling or clear button
 * should be used as the positioning anchor.
 */
internal object NavBarSearchButton {

    private const val NAV_ADD_CUSTOM_VIEW_METHOD = "addCustomView"
    private const val NAV_ALIGN_RIGHT_FIELD = "HORIZONTAL_RIGHT"
    private val NO_VALUE = Any()
    private val sAlignRightCache = Collections.synchronizedMap(WeakHashMap<ClassLoader, Any>())
    private val sAddCustomViewMethodCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Any>())

    /**
     * Cached search icon state copied from HomeTabBarRightSlot.
     */
    @Volatile
    private var sCachedSearchIconState: Drawable.ConstantState? = null

    /**
     * Stores the drawable returned by HomeTabBarRightSlot.getSearchIconView() once it is known.
     */
    fun cacheSearchIconDrawable(drawable: Drawable?) {
        if (drawable == null) return
        if (sCachedSearchIconState != null) return
        sCachedSearchIconState = drawable.constantState
    }

    /**
     * Reads the host static value `ControlAlign.HORIZONTAL_RIGHT`.
     */
    fun resolveNavigationRightAlign(cl: ClassLoader?): Any? {
        val targetCl = cl ?: return null
        synchronized(sAlignRightCache) {
            if (sAlignRightCache.containsKey(targetCl)) {
                val cached = sAlignRightCache[targetCl]
                return if (cached === NO_VALUE) null else cached
            }
        }
        return try {
            val cls = Class.forName(StableTiebaHookPoints.NAV_CONTROL_ALIGN_CLASS, false, targetCl)
            val align = cls.getDeclaredField(NAV_ALIGN_RIGHT_FIELD).apply { isAccessible = true }.get(null)
            synchronized(sAlignRightCache) {
                sAlignRightCache[targetCl] = align ?: NO_VALUE
            }
            align
        } catch (_: Throwable) {
            synchronized(sAlignRightCache) {
                sAlignRightCache[targetCl] = NO_VALUE
            }
            null
        }
    }

    /**
     * Resolves `NavigationBar.addCustomView(Align, View, ...)` by signature shape.
     */
    fun resolveAddCustomViewMethod(navClass: Class<*>): Method? {
        synchronized(sAddCustomViewMethodCache) {
            if (sAddCustomViewMethodCache.containsKey(navClass)) {
                val cached = sAddCustomViewMethodCache[navClass]
                return if (cached === NO_VALUE) null else cached as? Method
            }
        }
        val resolved = navClass.declaredMethods.firstOrNull { method ->
            method.name == NAV_ADD_CUSTOM_VIEW_METHOD &&
                method.parameterTypes.size == 3 &&
                View::class.java.isAssignableFrom(method.parameterTypes[1])
        }?.apply { isAccessible = true }
        synchronized(sAddCustomViewMethodCache) {
            sAddCustomViewMethodCache[navClass] = resolved ?: NO_VALUE
        }
        return resolved
    }

    /**
     * Builds the standard 40dp button container with a centered 32dp icon.
     */
    fun buildSearchButton(
        activity: Activity,
        iconDrawable: Drawable?,
        contentDesc: String,
        onClick: (Activity) -> Unit,
    ): View {
        val icon = ImageView(activity).apply {
            if (iconDrawable != null) {
                setImageDrawable(iconDrawable)
            } else {
                setImageResource(android.R.drawable.ic_menu_search)
            }
            contentDescription = contentDesc
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        return FrameLayout(activity).apply {
            val box = dp(activity, 40)
            layoutParams = ViewGroup.LayoutParams(box, box)
            foreground = resolveSelectableForeground(activity)
            addView(
                icon,
                FrameLayout.LayoutParams(dp(activity, 32), dp(activity, 32), Gravity.CENTER),
            )
            setOnClickListener { onClick(activity) }
        }
    }

    /**
     * Resolves the search icon from the cached right-slot icon, the current decor tree, or Android's
     * built-in search icon.
     */
    fun resolveSearchIconDrawable(activity: Activity, @Suppress("UNUSED_PARAMETER") navigationBar: Any): Drawable? {
        sCachedSearchIconState?.newDrawable()?.mutate()?.let { return it }
        findHomeRightSlotSearchDrawable(activity.window?.decorView)?.let { return it }
        return getDrawableCompat(activity, android.R.drawable.ic_menu_search)
    }

    /**
     * Extracts the root [View] from a NavigationBar instance.
     */
    fun extractNavigationRootView(navigationBar: Any): View? {
        if (navigationBar is View) return navigationBar
        findFieldValue(navigationBar) { it is View }?.let { return it as? View }
        return null
    }

    /**
     * Finds the direct [ViewGroup] parent of [target] within [root].
     */
    fun findParentOfView(root: View, target: View): ViewGroup? {
        if (root !is ViewGroup) return null
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child === target) return root
            findParentOfView(child, target)?.let { return it }
        }
        return null
    }

    /**
     * Posts a few delayed reposition attempts after layout settles.
     */
    fun scheduleReposition(button: View, reposition: () -> Unit) {
        button.post(reposition)
        button.postDelayed(reposition, 80L)
        button.postDelayed(reposition, 220L)
    }

    fun dp(context: Context, value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    fun cloneDrawable(drawable: Drawable): Drawable? {
        return runCatching {
            drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        }.getOrNull()
    }

    fun getDrawableCompat(context: Context, resId: Int): Drawable? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                context.resources.getDrawable(resId, context.theme)
            } else {
                @Suppress("DEPRECATION")
                context.resources.getDrawable(resId)
            }
        }.getOrNull()?.let(::cloneDrawable)
    }

    fun resolveSelectableForeground(context: Context): Drawable? {
        return runCatching {
            val tv = TypedValue()
            if (!context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless,
                    tv,
                    true,
                )
            ) {
                return@runCatching null
            }
            if (tv.resourceId == 0) return@runCatching null
            getDrawableCompat(context, tv.resourceId)
        }.getOrNull()
    }

    // 鍐呴儴宸ュ叿

    private fun findHomeRightSlotSearchDrawable(root: View?): Drawable? {
        val decor = root ?: return null
        val queue = ArrayDeque<View>()
        queue.add(decor)
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            val clsName = view.javaClass.name
            if (
                clsName == StableTiebaHookPoints.HOME_TAB_BAR_RIGHT_SLOT_CLASS ||
                clsName.endsWith(".HomeTabBarRightSlot")
            ) {
                val searchView = callNoArgMethod(view, "getSearchIconView") as? ImageView
                val drawable = searchView?.drawable
                if (drawable != null) {
                    cacheSearchIconDrawable(drawable)
                    return cloneDrawable(drawable)
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    queue.addLast(view.getChildAt(i))
                }
            }
        }
        return null
    }

    private fun callNoArgMethod(target: Any, methodName: String): Any? {
        val method = ReflectionUtils.findMethodInHierarchy(target.javaClass, methodName) {
            it.parameterTypes.isEmpty()
        } ?: return null
        return runCatching { method.invoke(target) }.getOrNull()
    }

    private fun findFieldValue(instance: Any, predicate: (Any) -> Boolean): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            for (field in current.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                runCatching {
                    field.isAccessible = true
                    val value = field.get(instance) ?: return@runCatching
                    if (predicate(value)) return value
                }
            }
            current = current.superclass
        }
        return null
    }
}
