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
 * 缁欑洰鏍囧簲鐢?NavigationBar 娉ㄥ叆鎼滅储鎸夐挳鐨勫叡鐢ㄥ伐鍏枫€? * 绫诲悕鏄?com.baidu.tbadk.core.view.NavigationBar銆? *
 * 璋冪敤鏂逛粛鐒惰嚜宸卞喅瀹氾細
 * - 鎬庝箞鎷垮埌 NavigationBar 瀹炰緥锛屾瘮濡?Activity 瀛楁鎴栨帶鍒跺櫒閾? * - 鎸夊摢涓洰鏍囬噸鎺掍綅缃紝姣斿娓呴櫎鎸夐挳鎴栧彸渚у悓绾?View
 */
internal object NavBarSearchButton {

    private const val NAV_ADD_CUSTOM_VIEW_METHOD = "addCustomView"
    private const val NAV_ALIGN_RIGHT_FIELD = "HORIZONTAL_RIGHT"
    private val NO_VALUE = Any()
    private val sAlignRightCache = Collections.synchronizedMap(WeakHashMap<ClassLoader, Any>())
    private val sAddCustomViewMethodCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Any>())

    /**
     * 缂撳瓨浠?HomeTabBarRightSlot 鎷垮埌鐨勬悳绱㈠浘鏍?ConstantState銆?     * 鐢?[cacheSearchIconDrawable] 鍐欏叆涓€娆★紝鍐嶇敱 [resolveSearchIconDrawable] 浣跨敤銆?     */
    @Volatile
    private var sCachedSearchIconState: Drawable.ConstantState? = null

    /**
     * 缂撳瓨 HomeTabBarRightSlot.getSearchIconView() 杩斿洖鐨勬悳绱㈠浘鏍?drawable銆?     * 鐢?HomeTopBarRightSlotHook 鍦ㄧ‘璁ゆЫ浣嶆悳绱㈠浘鏍囧彲鐢ㄥ悗璋冪敤銆?     */
    fun cacheSearchIconDrawable(drawable: Drawable?) {
        if (drawable == null) return
        if (sCachedSearchIconState != null) return
        sCachedSearchIconState = drawable.constantState
    }

    /**
     * 浠庣洰鏍囧簲鐢ㄨ鍙栭潤鎬佸父閲?`ControlAlign.HORIZONTAL_RIGHT`銆?     */
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
     * 鎸夌鍚嶅舰鐘惰В鏋?`NavigationBar.addCustomView(Align, View, ...)`銆?     */
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
     * 鏋勫缓鏍囧噯鐨?40dp 瀹瑰櫒锛岄噷闈㈡斁灞呬腑鐨?32dp 鍥炬爣銆?     * [iconDrawable] 涓虹┖鏃朵娇鐢?Android 绯荤粺鎼滅储鍥炬爣銆?     */
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
     * 瑙ｆ瀽鎼滅储鍥炬爣 Drawable锛屾煡鎵鹃『搴忓涓嬶細
     * 1. HomeTopBarRightSlotHook 缂撳瓨鐨?HomeTabBarRightSlot 鍥炬爣銆?     * 2. 褰撳墠 Activity decor view 閲岀殑 HomeTabBarRightSlot.getSearchIconView()銆?     * 3. Android 鍐呯疆鑿滃崟鎼滅储鍥炬爣銆?     */
    fun resolveSearchIconDrawable(activity: Activity, @Suppress("UNUSED_PARAMETER") navigationBar: Any): Drawable? {
        sCachedSearchIconState?.newDrawable()?.mutate()?.let { return it }
        findHomeRightSlotSearchDrawable(activity.window?.decorView)?.let { return it }
        return getDrawableCompat(activity, android.R.drawable.ic_menu_search)
    }

    /**
     * 浠?NavigationBar 瀹炰緥鍙栨牴 View銆?     * 鍏煎瀹炰緥鏈韩灏辨槸 View锛屾垨瀹炰緥鍐呴儴鎸佹湁 View 绫诲瀷瀛楁杩欎袱绉嶆儏鍐点€?     */
    fun extractNavigationRootView(navigationBar: Any): View? {
        if (navigationBar is View) return navigationBar
        findFieldValue(navigationBar) { it is View }?.let { return it as? View }
        return null
    }

    /**
     * 鍦?[root] 鍐呮部 [target] 鐨勭埗閾惧悜涓婃壘锛岃繑鍥炲畠鐨勭洿鎺ョ埗绾?ViewGroup銆?     */
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
     * 鍦ㄦ寜閽笂鎸?0銆?0銆?20ms 鎶曢€?[reposition]銆?     * 绛夊竷灞€绋冲畾鍚庢墽琛岋紝涓嶉樆濉炶皟鐢ㄦ柟銆?     */
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
