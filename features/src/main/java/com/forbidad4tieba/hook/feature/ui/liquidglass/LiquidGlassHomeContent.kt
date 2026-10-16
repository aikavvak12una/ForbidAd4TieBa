package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.annotation.TargetApi
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import com.forbidad4tieba.hook.symbol.contract.HomeTabContentContract
import java.util.WeakHashMap

/** Owns only the home web/live padding released while this host has a floating bottom bar. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal class LiquidGlassHomeContent(private val targets: HomeTabContentContract.Targets?) {
    private var active = false
    private val released = WeakHashMap<ViewGroup, Int>()
    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) {
            restore(view as ViewGroup)
        }
    }

    /** The caller supplies the published floating switch and actual panel ownership together. */
    fun beginPass(enabled: Boolean): Boolean {
        if (!enabled) restore()
        active = enabled
        return enabled
    }

    fun expandIntoTabReserve(content: ViewGroup, pager: ViewGroup, tabRowHeight: Int): Boolean {
        if (!active || tabRowHeight <= 0) return false
        val owner = targets?.paddingOwner(content, pager) ?: return false
        if (owner.height <= tabRowHeight || owner.paddingBottom != tabRowHeight ||
            owner.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        ) return false
        // A later host rewrite may use a new bar height; restore that latest value on release.
        if (released.put(owner, owner.paddingBottom) == null) owner.addOnAttachStateChangeListener(detachListener)
        owner.setPadding(owner.paddingLeft, owner.paddingTop, owner.paddingRight, 0)
        return true
    }

    fun restore() {
        active = false
        for (view in released.keys.toList()) restore(view)
    }

    private fun restore(view: ViewGroup) {
        val padding = released.remove(view) ?: return
        view.removeOnAttachStateChangeListener(detachListener)
        // Respect a newer host-owned layout rather than overwriting it with our saved value.
        if (view.paddingBottom == 0) view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, padding)
    }
}
