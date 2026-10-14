// samplingResidual adapts miuix LayerBackdrop's texel alignment (Apache-2.0).
// Copyright 2026, compose-miuix-ui contributors
// The other helpers use local native-layout rules; see app/src/main/assets/open-source-licenses.txt.
package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.round

/** Native host hit testing and layout offsets, independent of the material renderer. */
internal object LiquidGlassGeom {

    /** The downsampled backdrop is anchored to an even texel grid, then its residual is restored. */
    fun samplingResidual(offset: Float, factor: Int): Float = if (factor <= 1) 0f else {
        val scaled = offset / factor
        (scaled - round(scaled * 0.5f).toInt() * 2f) * factor
    }

    /** Matches the filled capsule, excluding its transparent corners. */
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

    /**
     * Layout origin in the ancestor's viewport; scale and translation are applied separately.
     * Android's rectangle conversion ignores matrices and subtracts each descendant's own scroll.
     * Start at that scroll origin, then include the ancestor's scroll to obtain viewport coordinates.
     * An unrelated view or a path over 32 links leaves the caller's scratch rectangle untouched.
     */
    fun layoutOffsetInAncestor(descendant: View, ancestor: ViewGroup, out: Rect): Boolean {
        var node = descendant
        var remaining = 32
        while (node !== ancestor) {
            if (remaining-- == 0) return false
            node = node.parent as? View ?: return false
        }
        out.set(descendant.scrollX, descendant.scrollY, descendant.scrollX, descendant.scrollY)
        ancestor.offsetDescendantRectToMyCoords(descendant, out)
        out.offset(-ancestor.scrollX, -ancestor.scrollY)
        return true
    }
}
