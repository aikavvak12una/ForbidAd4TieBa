package com.forbidad4tieba.hook.symbol.contract

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.math.roundToInt

/** Stable public APIs only. No Dex scan, persisted descriptors, or host resource IDs. */
object PostReplyBarContract {
    private const val PAGE_ROOT = "com.baidu.tieba.pb.pb.main.view.LogParamConstraintLayout"
    private const val ACTION_VIEW = "com.baidu.tieba.pb.replybar.core.CustomView"
    private const val EDITOR_TOOLS = "com.baidu.tbadk.editortools.EditorTools"
    private const val REPLY_MASK = "com.baidu.tbadk.util.MaskView"
    private const val AGREE_VIEW = "com.baidu.tbadk.core.view.AgreeView"

    class Artwork internal constructor(val view: ImageView, val animated: Boolean)

    class Targets internal constructor(
        val onCreate: Method,
        private val rootType: Class<*>,
        private val actionType: Class<*>,
        private val contentType: Class<*>,
        private val editorType: Class<*>,
        private val maskType: Class<*>,
        private val application: Any,
        private val skinType: Method,
        private val commentList: Method,
        private val agreeType: Class<*>,
        private val agreeIcon: Method,
    ) {
        fun isNight(): Boolean = when (skinType.invoke(application) as Int) {
            1, 4 -> true // Host skin protocol, shared with the native Tab bar.
            else -> false
        }

        /** Native accessor follows the active comment tab without rediscovering its view tree. */
        fun currentComments(page: Page): ViewGroup? {
            val comments = commentList.invoke(page.recycler) as? ViewGroup ?: return null
            var ancestor = comments.parent
            repeat(12) {
                if (ancestor === page.recycler) return comments
                ancestor = ancestor?.parent
            }
            return null
        }

        /** Match artwork only inside the validated action row, never by button order or resource ID. */
        fun actionArtwork(page: Page): List<Artwork>? {
            val artwork = ArrayList<Artwork>()
            for (action in page.actions) {
                if (action.visibility != View.VISIBLE) continue
                val body = children(action as ViewGroup).singleOrNull() as? ViewGroup ?: return null
                val label = children(body).filterIsInstance<TextView>().singleOrNull() ?: return null
                val icon = children(body).filter { it !== label }.singleOrNull() ?: return null
                if (icon is ImageView) {
                    artwork.add(Artwork(icon, false))
                } else {
                    val wrapper = icon as? ViewGroup ?: return null
                    val agree = children(wrapper).singleOrNull { agreeType.isInstance(it) } ?: return null
                    val image = agreeIcon.invoke(agree) as? ImageView ?: return null
                    var ancestor = image.parent
                    repeat(6) { if (ancestor !== agree) ancestor = ancestor?.parent }
                    if (ancestor !== agree) return null
                    artwork.add(Artwork(image, true))
                }
            }
            return artwork
        }

        /** Called only during page attachment. The known page root bounds all structural matching. */
        fun findPage(content: ViewGroup): Page? {
            val roots = ArrayList<ViewGroup>()
            fun visit(view: View, remaining: Int) {
                if (rootType.isInstance(view)) {
                    roots.add(view as ViewGroup)
                } else if (remaining > 0 && view is ViewGroup) {
                    for (index in 0 until view.childCount) visit(view.getChildAt(index), remaining - 1)
                }
            }
            visit(content, 4)
            val root = roots.singleOrNull() ?: return null
            val editors = children(root).filter { editorType.isInstance(it) }
            // EditorTools is added lazily, after the native reply row has already been drawn.
            if (editors.size > 1) return null
            val editor = editors.singleOrNull()
            val backdrops = children(root).filterIsInstance<ViewGroup>().mapNotNull { group ->
                val pages = children(group).filter { contentType.isInstance(it) }
                pages.singleOrNull()?.let { group to (it as ViewGroup) }
            }
            val (backdrop, recycler) = backdrops.singleOrNull() ?: return null
            val matches = ArrayList<Page>()
            for (underlay in children(root).filterIsInstance<FrameLayout>()) {
                val stack = children(underlay).singleOrNull() as? LinearLayout ?: continue
                if (stack.orientation != LinearLayout.VERTICAL) continue
                for (slot in children(stack).filterIsInstance<FrameLayout>()) {
                    if (slot.layoutParams !is LinearLayout.LayoutParams) continue
                    // The native input exists before its actions are populated from post data.
                    if (children(slot).count { maskType.isInstance(it) } != 1) continue
                    for (row in children(slot).filterIsInstance<LinearLayout>()) {
                        if (row.orientation != LinearLayout.HORIZONTAL ||
                            row.layoutParams !is FrameLayout.LayoutParams || row.childCount < 1
                        ) continue
                        val input = row.getChildAt(0) as? LinearLayout ?: continue
                        val inputLayout = input.layoutParams as? LinearLayout.LayoutParams ?: continue
                        if (inputLayout.weight <= 0f || children(input).none { it is TextView }) continue
                        val actions = children(row).drop(1)
                        if (actions.any { !actionType.isInstance(it) || it.layoutParams !is LinearLayout.LayoutParams }) continue
                        matches.add(Page(root, underlay, stack, slot, row, input, actions, dividerBefore(stack, slot),
                            backdrop, recycler, editor, actionType, editorType))
                    }
                }
            }
            return matches.singleOrNull() // Never choose by traversal order when the shape is ambiguous.
        }
    }

    /** Optional full-width decorative separator immediately above the validated reply slot. */
    private fun dividerBefore(stack: LinearLayout, slot: FrameLayout): View? {
        val index = stack.indexOfChild(slot)
        if (index <= 0) return null
        val divider = stack.getChildAt(index - 1)
        val layout = divider.layoutParams ?: return null
        val maxHeight = stack.resources.displayMetrics.density.roundToInt().coerceAtLeast(1)
        return divider.takeIf {
            it.javaClass == View::class.java && !it.isClickable && !it.isFocusable &&
                it.background is ColorDrawable && layout.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                layout.height in 1..maxHeight
        }
    }

    class Page internal constructor(
        val root: ViewGroup,
        val underlay: FrameLayout,
        val stack: LinearLayout,
        val slot: FrameLayout,
        val row: LinearLayout,
        val input: LinearLayout,
        actions: List<View>,
        val divider: View?,
        val backdrop: ViewGroup,
        val recycler: ViewGroup,
        editor: View?,
        private val actionType: Class<*>,
        private val editorType: Class<*>,
    ) {
        var actions: List<View> = actions
            private set
        var editor: View? = editor
            private set

        /** Only layout changes can add the lazy editor; absence is valid, ambiguity is not. */
        fun refreshEditor(): Boolean {
            var current: View? = null
            for (index in 0 until root.childCount) {
                val child = root.getChildAt(index)
                if (!editorType.isInstance(child)) continue
                if (current != null) return false
                current = child
            }
            editor = current
            return true
        }

        /** Only the already-matched row can supply newly populated native action views. */
        fun refreshActions(): Boolean {
            if (row.getChildAt(0) !== input) return false
            val current = children(row).drop(1)
            if (current.any { !actionType.isInstance(it) || it.layoutParams !is LinearLayout.LayoutParams }) return false
            actions = current
            return true
        }

        fun isIntact(): Boolean = underlay.parent === root && stack.parent === underlay &&
            (divider == null || divider.parent === stack) &&
            slot.parent === stack && row.parent === slot && input.parent === row &&
            row.childCount == actions.size + 1 && actions.all { it.parent === row } &&
            recycler.parent === backdrop && backdrop.parent === root && (editor == null || editor?.parent === root)
    }

    fun resolve(classLoader: ClassLoader): Targets {
        fun load(name: String) = Class.forName(name, false, classLoader)
        val activity = load(StableTiebaHookPoints.PB_ACTIVITY_CLASS)
        val root = load(PAGE_ROOT)
        val action = load(ACTION_VIEW)
        val content = load(StableTiebaHookPoints.PB_PAGE_BROWSER_RECYCLER_VIEW_CLASS)
        val editor = load(EDITOR_TOOLS)
        val mask = load(REPLY_MASK)
        val agree = load(AGREE_VIEW)
        check(View::class.java.isAssignableFrom(mask) && !ViewGroup::class.java.isAssignableFrom(mask)) {
            "Invalid reply mask type"
        }
        val onCreate = validate(activity, root, action, content, editor)
        val coreApp = load(StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS)
        val getInst = coreApp.getMethod("getInst")
        check(Modifier.isStatic(getInst.modifiers) && coreApp.isAssignableFrom(getInst.returnType)) {
            "Invalid core application getter"
        }
        val skinType = coreApp.getMethod("getSkinType")
        check(!Modifier.isStatic(skinType.modifiers) && skinType.returnType == Integer.TYPE) {
            "Invalid skin type getter"
        }
        return Targets(onCreate, root, action, content, editor, mask,
            checkNotNull(getInst.invoke(null)) { "Core application unavailable" }, skinType,
            validateCommentAccessor(content), agree, validateAgreeIcon(agree))
    }

    internal fun validateAgreeIcon(agree: Class<*>): Method {
        check(LinearLayout::class.java.isAssignableFrom(agree)) { "Invalid agree view type" }
        return agree.getMethod("getImgAgree").apply {
            check(!Modifier.isStatic(modifiers) && ImageView::class.java.isAssignableFrom(returnType)) {
                "Invalid agree icon getter"
            }
        }
    }

    internal fun validateCommentAccessor(content: Class<*>): Method =
        content.getMethod("getCurrentCommentRecyclerView").apply {
            check(!Modifier.isStatic(modifiers) && ViewGroup::class.java.isAssignableFrom(returnType)) {
                "Invalid active comment list getter"
            }
        }

    internal fun validate(
        activity: Class<*>, root: Class<*>, action: Class<*>, content: Class<*>, editor: Class<*>,
    ): Method {
        check(Activity::class.java.isAssignableFrom(activity)) { "Post owner is not an Activity" }
        check(listOf(root, content).all { ViewGroup::class.java.isAssignableFrom(it) } &&
            FrameLayout::class.java.isAssignableFrom(action) &&
            LinearLayout::class.java.isAssignableFrom(editor)) { "Invalid post view types" }
        val layout = action.getMethod("setCustomLayout", Integer.TYPE)
        val press = action.getMethod("getPressState")
        check(!Modifier.isStatic(layout.modifiers) && layout.returnType == Void.TYPE &&
            !Modifier.isStatic(press.modifiers) && press.returnType == java.lang.Boolean.TYPE) {
            "Invalid reply action API"
        }
        return activity.getDeclaredMethod("onCreate", Bundle::class.java).apply {
            check(!Modifier.isStatic(modifiers) && returnType == Void.TYPE) { "Invalid post onCreate" }
            isAccessible = true
        }
    }

    private fun children(group: ViewGroup): List<View> =
        (0 until group.childCount).map { group.getChildAt(it) }
}
