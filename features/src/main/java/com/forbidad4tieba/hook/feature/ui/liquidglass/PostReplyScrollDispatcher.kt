package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** UI-thread bindings route exact native deltas to their page without retaining an Activity. */
internal object PostReplyScrollDispatcher {
    private val pages = WeakHashMap<View, WeakReference<PostReplyGlassSession>>()

    fun bind(root: View, session: PostReplyGlassSession) { pages[root] = WeakReference(session) }

    fun unbind(root: View, session: PostReplyGlassSession) {
        if (pages[root]?.get() === session) pages.remove(root)
    }

    fun onScrolled(target: View, dy: Int) {
        if (dy == 0 || pages.isEmpty()) return
        var current: View? = target
        repeat(13) {
            val view = current ?: return
            pages[view]?.get()?.let { it.onScrolled(dy); return }
            current = view.parent as? View
        }
    }
}
