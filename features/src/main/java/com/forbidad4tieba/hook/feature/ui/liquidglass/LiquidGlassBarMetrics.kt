package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Host tab dimensions and safe placement. */
internal object LiquidGlassBarMetrics {
    const val TAB_HEIGHT_DP = 60f
    const val DROPLET_INSET_DP = 3.5f
    const val HORIZONTAL_PADDING_DP = DROPLET_INSET_DP
    const val VERTICAL_PADDING_DP = DROPLET_INSET_DP + 4f
    private const val SCREEN_EDGE_MARGIN_DP = 24f

    fun floatingTop(
        hostHeight: Int,
        hostBottomInWindow: Int,
        windowBottom: Int,
        navigationBottom: Int,
        pillHeight: Int,
        density: Float,
        bottomGapDp: Int = -1,
    ): Int {
        val navigation = navigationBottom.coerceAtLeast(0)
        val overlap = (hostBottomInWindow - (windowBottom - navigation)).coerceIn(0, hostHeight)
        // Both surfaces share a center, even though the reply controls are shorter than the tabs.
        // Automatic placement preserves the normal 60dp home bar's existing position.
        val minimumCenterGapDp = TAB_HEIGHT_DP / 2f
        val centerGapDp = if (bottomGapDp >= 0) {
            maxOf(bottomGapDp.toFloat(), minimumCenterGapDp)
        } else {
            minimumCenterGapDp + if (navigation > 0) 8f else 28f
        }
        val safeBottom = hostHeight - overlap
        val top = (safeBottom - centerGapDp * density - pillHeight / 2f).roundToInt()
        // Very large controls or short windows still have to fit above the navigation area.
        return top.coerceIn(0, (safeBottom - pillHeight).coerceAtLeast(0))
    }

    /** Per-bar scratch; measuring a settled tab set allocates no lists, boxes or bounds arrays. */
    class ContentMeasure {
        private val pending = ArrayDeque<View>()
        private val rect = Rect()
        private var tops = IntArray(0)
        private var bottoms = IntArray(0)

        /** Whole visible columns horizontally; upper median icon/label extents vertically. */
        fun measure(row: ViewGroup, out: IntArray): Boolean {
            if (tops.size < row.childCount) {
                tops = IntArray(row.childCount)
                bottoms = IntArray(row.childCount)
            }
            var count = 0
            var left = row.width
            var right = 0
            for (index in 0 until row.childCount) {
                val column = row.getChildAt(index)
                if (column.visibility != View.VISIBLE || column.width <= 0) continue
                var top = Int.MAX_VALUE
                var bottom = Int.MIN_VALUE
                pending.add(column)
                while (pending.isNotEmpty()) {
                    val artwork = pending.removeFirst()
                    if (artwork.visibility != View.VISIBLE) continue
                    if (artwork is ViewGroup) {
                        for (i in 0 until artwork.childCount) pending.addLast(artwork.getChildAt(i))
                    } else if ((artwork is ImageView || artwork is TextView) && artwork.width > 0 && artwork.height > 0) {
                        rect.set(0, artwork.scrollY, artwork.width, artwork.scrollY + artwork.height)
                        row.offsetDescendantRectToMyCoords(artwork, rect)
                        // Artwork placement uses layout bounds, independent of scrolling or press transforms.
                        var parent = artwork.parent
                        while (parent !== row && parent is View) {
                            rect.offset(0, parent.scrollY)
                            parent = parent.parent
                        }
                        top = min(top, rect.top)
                        bottom = max(bottom, rect.bottom)
                    }
                }
                if (top >= bottom) continue
                left = min(left, column.left)
                right = max(right, column.right)
                tops[count] = top
                bottoms[count++] = bottom
            }
            if (count == 0 || left >= right) return false
            tops.sort(0, count)
            bottoms.sort(0, count)
            out[0] = left
            out[1] = tops[count / 2]
            out[2] = right
            out[3] = bottoms[count / 2]
            return out[1] < out[3]
        }
    }

    fun barHeight(contentHeight: Int, density: Float): Int = maxOf(
        (TAB_HEIGHT_DP * density).roundToInt(), contentHeight + 2 * (VERTICAL_PADDING_DP * density).roundToInt())

    /** 100% is a circle. Preserve press clearance where the window can fit all circles. */
    fun columnWidth(available: Int, count: Int, barHeight: Int, density: Float, widthPercent: Int): Int {
        if (available <= 0 || count <= 0 || barHeight <= 0 || !density.isFinite() || density <= 0f) return 0
        val inset = 2 * (HORIZONTAL_PADDING_DP * density).roundToInt()
        val minimum = (barHeight - 2 * dropletInset(barHeight)).coerceAtLeast(1)
        val margin = minOf((SCREEN_EDGE_MARGIN_DP * density).roundToInt(),
            ((available - inset - minimum * count) / 2).coerceAtLeast(0))
        val percent = widthPercent.coerceIn(BottomTabLiquidGlassConfig.MIN_DROPLET_WIDTH_PERCENT,
            BottomTabLiquidGlassConfig.MAX_DROPLET_WIDTH_PERCENT)
        val desired = (minimum * percent / 100f).roundToInt()
        return minOf(desired, (available - inset - 2 * margin) / count).coerceAtLeast(0)
    }

    /** A very narrow window can shrink the lens uniformly; it must never become a vertical oval. */
    fun dropletHeight(columnWidth: Int, barHeight: Int): Int =
        minOf(columnWidth, barHeight - 2 * dropletInset(barHeight)).coerceAtLeast(1)

    /** Preserve the reference's 4/64 inset ratio at the current bar height. */
    fun dropletInset(barHeight: Int): Int = (barHeight / 16f).roundToInt().coerceAtLeast(1)

    /** Only the native column's plain, top-aligned hairline is decorative chrome. */
    fun hideColumnHairlines(row: ViewGroup, density: Float) {
        val maxHeight = max(1, (2f * density).roundToInt())
        val maxTop = (4f * density).roundToInt()
        for (i in 0 until row.childCount) {
            val column = row.getChildAt(i) as? ViewGroup ?: continue
            if (column.visibility != View.VISIBLE || column.width <= 0) continue
            for (j in 0 until column.childCount) {
                val child = column.getChildAt(j)
                if (child.javaClass == View::class.java && child.visibility == View.VISIBLE &&
                    child.height in 1..maxHeight && child.top <= maxTop &&
                    child.width >= column.width * 0.75f) child.visibility = View.GONE
            }
        }
    }
}
