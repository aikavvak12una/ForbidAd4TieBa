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

/**
 * 给目标应用 NavigationBar 注入搜索按钮的共用工具。
 * 类名是 com.baidu.tbadk.core.view.NavigationBar。
 *
 * 调用方仍然自己决定：
 * - 怎么拿到 NavigationBar 实例，比如 Activity 字段或控制器链
 * - 按哪个目标重排位置，比如清除按钮或右侧同级 View
 */
internal object NavBarSearchButton {

    private const val NAV_ADD_CUSTOM_VIEW_METHOD = "addCustomView"
    private const val NAV_ALIGN_RIGHT_FIELD = "HORIZONTAL_RIGHT"

    /**
     * 缓存从 HomeTabBarRightSlot 拿到的搜索图标 ConstantState。
     * 由 [cacheSearchIconDrawable] 写入一次，再由 [resolveSearchIconDrawable] 使用。
     */
    @Volatile
    private var sCachedSearchIconState: Drawable.ConstantState? = null

    /**
     * 缓存 HomeTabBarRightSlot.getSearchIconView() 返回的搜索图标 drawable。
     * 由 HomeTopBarRightSlotHook 在确认槽位搜索图标可用后调用。
     */
    fun cacheSearchIconDrawable(drawable: Drawable?) {
        if (drawable == null) return
        if (sCachedSearchIconState != null) return
        sCachedSearchIconState = drawable.constantState
    }

    /**
     * 从目标应用读取静态常量 `ControlAlign.HORIZONTAL_RIGHT`。
     */
    fun resolveNavigationRightAlign(cl: ClassLoader?): Any? {
        val targetCl = cl ?: return null
        return try {
            val cls = Class.forName(StableTiebaHookPoints.NAV_CONTROL_ALIGN_CLASS, false, targetCl)
            cls.getDeclaredField(NAV_ALIGN_RIGHT_FIELD).apply { isAccessible = true }.get(null)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 按签名形状解析 `NavigationBar.addCustomView(Align, View, ...)`。
     */
    fun resolveAddCustomViewMethod(navClass: Class<*>): Method? {
        return navClass.declaredMethods.firstOrNull { method ->
            method.name == NAV_ADD_CUSTOM_VIEW_METHOD &&
                method.parameterTypes.size == 3 &&
                View::class.java.isAssignableFrom(method.parameterTypes[1])
        }?.apply { isAccessible = true }
    }

    /**
     * 构建标准的 40dp 容器，里面放居中的 32dp 图标。
     * [iconDrawable] 为空时使用 Android 系统搜索图标。
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
     * 解析搜索图标 Drawable，查找顺序如下：
     * 1. HomeTopBarRightSlotHook 缓存的 HomeTabBarRightSlot 图标。
     * 2. 当前 Activity decor view 里的 HomeTabBarRightSlot.getSearchIconView()。
     * 3. Android 内置菜单搜索图标。
     */
    fun resolveSearchIconDrawable(activity: Activity, @Suppress("UNUSED_PARAMETER") navigationBar: Any): Drawable? {
        sCachedSearchIconState?.newDrawable()?.mutate()?.let { return it }
        findHomeRightSlotSearchDrawable(activity.window?.decorView)?.let { return it }
        return getDrawableCompat(activity, android.R.drawable.ic_menu_search)
    }

    /**
     * 从 NavigationBar 实例取根 View。
     * 兼容实例本身就是 View，或实例内部持有 View 类型字段这两种情况。
     */
    fun extractNavigationRootView(navigationBar: Any): View? {
        if (navigationBar is View) return navigationBar
        findFieldValue(navigationBar) { it is View }?.let { return it as? View }
        return null
    }

    /**
     * 在 [root] 内沿 [target] 的父链向上找，返回它的直接父级 ViewGroup。
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
     * 在按钮上按 0、80、220ms 投递 [reposition]。
     * 等布局稳定后执行，不阻塞调用方。
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

    // 内部工具

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
