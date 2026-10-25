package com.forbidad4tieba.hook.symbol.contract

import android.content.res.Resources
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Stable widgets and named artwork, resolved at installation without DexKit or a persisted cache. */
object RedDotContract {
    private val drawableNames = listOf(
        "icon_news_red_dot", "icon_news_red_dot_stroke", "icon_news_red_dot_stroke_2",
        "icon_official_bar_red_dot",
    )

    fun resolve(loader: ClassLoader, resources: Resources?, packageName: String?): RedDotTargets {
        val issues = ArrayList<String>()
        fun <T> point(name: String, resolve: () -> T): T? = try {
            resolve()
        } catch (failure: Exception) {
            issues.add(name + ": " + failure.javaClass.simpleName + ": " + failure.message)
            null
        } catch (failure: LinkageError) {
            issues.add(name + ": " + failure.javaClass.simpleName)
            null
        }
        fun type(name: String) = Class.forName(name, false, loader)
        val visibility = listOf(
            StableTiebaHookPoints.HOME_SLIDING_INDEX_TAB_VIEW_CLASS,
            StableTiebaHookPoints.HOME_TAB_BAR_RIGHT_SLOT_CLASS,
        ).mapNotNull { name -> point(name) { visibilityMethod(type(name)) } }
        val message = point("MessageRedDotView") {
            messageTargets(type(StableTiebaHookPoints.MESSAGE_RED_DOT_VIEW_CLASS))
        }
        val resourceSetters = ArrayList<RedDotResourceSetter>()
        point("SkinManager") { type(StableTiebaHookPoints.SKIN_MANAGER_CLASS) }?.let { skin ->
            for (layer in RedDotLayer.entries) {
                for (withSkin in listOf(false, true)) {
                    point(layer.name + if (withSkin) ".skin" else ".default") {
                        resourceSetter(skin, layer, withSkin)
                    }?.let(resourceSetters::add)
                }
            }
        }
        val ids = if (resources == null || packageName == null) {
            issues.add("RedDotResources: application resources unavailable")
            emptySet()
        } else {
            resolveDrawableIds { name ->
                point(name) {
                    resources.getIdentifier(name, "drawable", packageName).also { id ->
                        check(id != 0 && resources.getResourceEntryName(id) == name &&
                            resources.getResourceTypeName(id) == "drawable") { "missing or renamed drawable" }
                    }
                }
            }
        }
        val navigation = if (resources == null) null else point("MessageNavigation") {
            navigationTargets(
                type(StableTiebaHookPoints.MSG_TAB_SIDE_NAVIGATION_ADAPTER_CLASS),
                type(StableTiebaHookPoints.RECYCLER_VIEW_CLASS + "\$ViewHolder"),
                (resources.displayMetrics.density * 20f).toInt().coerceAtLeast(1),
            )
        }
        return RedDotTargets(visibility, message, resourceSetters, ids, navigation, issues)
    }

    internal fun visibilityMethod(type: Class<*>): Method {
        check(View::class.java.isAssignableFrom(type)) { "Red-dot owner is not a View" }
        return method(type, "setRedDotVisible", false, Void.TYPE, Boolean::class.javaPrimitiveType!!)
    }

    internal fun messageTargets(type: Class<*>): RedDotMessageTargets {
        check(View::class.java.isAssignableFrom(type)) { "Badge owner is not a View" }
        return RedDotMessageTargets(
            method(type, "onDraw", false, Void.TYPE, Canvas::class.java),
            method(type, "getRedNum", false, String::class.java),
        )
    }

    internal fun resourceSetter(type: Class<*>, layer: RedDotLayer, withSkin: Boolean): RedDotResourceSetter {
        val view = if (layer == RedDotLayer.IMAGE) ImageView::class.java else View::class.java
        val parameters = mutableListOf(view, Int::class.javaPrimitiveType!!)
        if (withSkin) parameters.add(Int::class.javaPrimitiveType!!)
        val name = if (layer == RedDotLayer.IMAGE) "setImageResource" else "setBackgroundResource"
        return RedDotResourceSetter(method(type, name, true, Void.TYPE, *parameters.toTypedArray()), layer)
    }

    internal fun resolveDrawableIds(find: (String) -> Int?): Set<Int> {
        val ids = drawableNames.mapNotNull(find).filter { it > 0 }
        return ids.groupingBy { it }.eachCount().filterValues { it == 1 }.keys
    }

    internal fun navigationTargets(adapter: Class<*>, holder: Class<*>, maxDotPixels: Int): RedDotNavigationTargets {
        val create = adapter.getDeclaredMethod("onCreateViewHolder", ViewGroup::class.java, Int::class.javaPrimitiveType).apply {
            check(!Modifier.isStatic(modifiers) && holder.isAssignableFrom(returnType)) { "Invalid navigation create" }
            isAccessible = true
        }
        val bind = method(adapter, "onBindViewHolder", false, Void.TYPE, holder, Int::class.javaPrimitiveType!!)
        val itemView = holder.getField("itemView").apply {
            check(!Modifier.isStatic(modifiers) && type == View::class.java) { "Invalid navigation itemView" }
        }
        return RedDotNavigationTargets(create, bind) { receiver ->
            (itemView.get(receiver) as? View)?.let { navigationDot(it, maxDotPixels) }
        }
    }

    /** Only the dot overlay beside an icon and unread TextView inside the known navigation adapter. */
    fun navigationDot(root: View, maxDotPixels: Int): View? {
        val queue = ArrayDeque<Pair<View, Int>>()
        queue.add(root to 0)
        var visited = 0
        var match: View? = null
        while (queue.isNotEmpty()) {
            if (++visited > 48) return null
            val (view, depth) = queue.removeFirst()
            if (view.javaClass == View::class.java && !view.isClickable && !view.isLongClickable &&
                !view.isFocusable && !view.hasOnClickListeners()
            ) {
                val params = view.layoutParams
                val parent = view.parent as? FrameLayout
                if (params != null && params.width in 1..maxDotPixels && params.height == params.width &&
                    parent != null && (0 until parent.childCount).any { parent.getChildAt(it) is TextView } &&
                    (0 until parent.childCount).any { parent.getChildAt(it) is ImageView }
                ) {
                    if (match != null) return null
                    match = view
                }
            }
            if (view is ViewGroup) {
                if (depth >= 5 && view.childCount > 0) return null
                for (index in 0 until view.childCount) queue.add(view.getChildAt(index) to depth + 1)
            }
        }
        return match
    }

    private fun method(type: Class<*>, name: String, static: Boolean, result: Class<*>,
                       vararg parameters: Class<*>): Method =
        type.getDeclaredMethod(name, *parameters).apply {
            check(Modifier.isStatic(modifiers) == static && returnType == result) { "Invalid method: " + name }
            isAccessible = true
        }
}

enum class RedDotLayer { BACKGROUND, IMAGE }

data class RedDotResourceSetter(val method: Method, val layer: RedDotLayer)
data class RedDotMessageTargets(val draw: Method, val text: Method)
data class RedDotNavigationTargets(
    val create: Method,
    val bind: Method,
    val findDot: (Any) -> View?,
)
data class RedDotTargets(
    val visibility: List<Method>,
    val message: RedDotMessageTargets?,
    val resources: List<RedDotResourceSetter>,
    val drawableIds: Set<Int>,
    val navigation: RedDotNavigationTargets?,
    val issues: List<String> = emptyList(),
)

