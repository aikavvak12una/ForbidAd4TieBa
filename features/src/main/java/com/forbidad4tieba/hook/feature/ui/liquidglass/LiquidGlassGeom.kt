package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.view.View
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Geometry helpers for a view that samples the screen while an ancestor is scaled.
 *
 * Ported from liuran001/WeChat-LiquidGlass (MIT), `ViewGeom`.
 *
 * The lens shader works in unscaled local coordinates, so anything that samples what is behind the
 * panel has to ask for positions and scale factors that ignore ancestor transforms. Otherwise the
 * captured backdrop comes out magnified instead of revealing more of what sits behind.
 */
internal object LiquidGlassGeom {

    /** Matches the filled capsule, excluding its transparent corners and outer shadow. */
    fun containsCapsule(x: Float, y: Float, width: Float, height: Float): Boolean {
        if (!x.isFinite() || !y.isFinite() || width <= 0f || height <= 0f ||
            x < 0f || x >= width || y < 0f || y >= height
        ) return false
        val radius = minOf(width, height) * 0.5f
        val dx = x - x.coerceIn(radius, width - radius)
        val dy = y - y.coerceIn(radius, height - radius)
        return dx * dx + dy * dy <= radius * radius
    }

    /** Actual native column centres also assign the capsule's horizontal padding to an end tab. */
    fun tabIndexAt(row: ViewGroup, x: Float): Int {
        var index = -1
        var distance = Float.POSITIVE_INFINITY
        for (i in 0 until row.childCount) {
            val tab = row.getChildAt(i) ?: continue
            if (tab.visibility != View.VISIBLE || !tab.isEnabled || tab.width <= 0) continue
            val next = abs(x - tab.left - tab.translationX - tab.width * 0.5f)
            if (next < distance) {
                index = i
                distance = next
            }
        }
        return index
    }

    /** Scratch for the anchor lookup; every caller runs on the UI thread. */
    private val anchor = IntArray(2)

    /**
     * Screen position with every view scale factored out: accumulates plain layout offsets all the
     * way to the root and anchors there.
     */
    fun unscaledScreenPos(view: View, out: IntArray) {
        var x = 0f
        var y = 0f
        var current: View = view
        // All the way to the root. Stopping at the first unscaled ancestor is not enough: that
        // ancestor's own getLocationOnScreen() still carries any scale applied further up.
        while (true) {
            val parent = current.parent as? View ?: break
            x += current.left + current.translationX - parent.scrollX
            y += current.top + current.translationY - parent.scrollY
            current = parent
        }
        current.getLocationOnScreen(anchor)
        out[0] = (anchor[0] + x).roundToInt()
        out[1] = (anchor[1] + y).roundToInt()
    }

    /** Both axes of the drawn scale, including ancestors; droplet stretch is not uniform. */
    fun cumulativeScale(view: View, out: FloatArray) {
        var scaleX = 1f
        var scaleY = 1f
        var current: View? = view
        while (current != null) {
            scaleX *= abs(current.scaleX)
            scaleY *= abs(current.scaleY)
            current = current.parent as? View
        }
        out[0] = if (scaleX < 0.01f) 1f else scaleX
        out[1] = if (scaleY < 0.01f) 1f else scaleY
    }

    /**
     * Offset of [descendant] inside [ancestor], counting layout position only.
     *
     * `ViewGroup.offsetDescendantRectToMyCoords` folds in each view's matrix, so a translationY
     * applied for the floating lift would be baked into the result and then applied a second time
     * when the panel mirrors it. Walking left/top keeps the layout position and the transform as
     * two separate, independently controlled things.
     *
     * Returns null when [descendant] is not actually under [ancestor].
     */
    fun layoutOffsetInAncestor(descendant: View, ancestor: View, out: IntArray): Boolean {
        var x = 0
        var y = 0
        var current: View = descendant
        var depth = 0
        while (current !== ancestor && depth < 32) {
            val parent = current.parent as? View ?: return false
            x += current.left - parent.scrollX
            y += current.top - parent.scrollY
            current = parent
            depth++
        }
        if (current !== ancestor) return false
        out[0] = x
        out[1] = y
        return true
    }
}
