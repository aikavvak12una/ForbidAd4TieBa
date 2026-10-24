package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.graphics.Rect
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.symbol.contract.BottomTabArtworkContract
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** KernelSU floating-bar dimensions, measured under the current host constraints. */
internal object LiquidGlassBarMetrics {
    const val TAB_HEIGHT_DP = 64f
    const val TAB_WIDTH_DP = 76f
    const val DROPLET_HEIGHT_DP = 56f
    const val DROPLET_INSET_DP = 4f
    const val HORIZONTAL_PADDING_DP = DROPLET_INSET_DP
    const val VERTICAL_PADDING_DP = DROPLET_INSET_DP
    private const val SCREEN_EDGE_MARGIN_DP = 28f

    /** Tieba rounds DisplayMetrics to a resource bucket; Configuration retains the window DPI. */
    fun density(resources: Resources): Float =
        resources.configuration.densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()

    /** Each half of a centred slider uses its own available range. */
    fun adjustedSize(baseline: Int, minimum: Int, maximum: Int, adjustment: Int): Int? {
        if (minimum <= 0 || maximum < minimum) return null
        val base = baseline.coerceIn(minimum, maximum)
        val progress = adjustment.coerceIn(BottomTabLiquidGlassConfig.MIN_ADJUSTMENT,
            BottomTabLiquidGlassConfig.MAX_ADJUSTMENT) / 100f
        val range = if (progress < 0f) base - minimum else maximum - base
        return (base + progress * range).roundToInt().coerceIn(minimum, maximum)
    }

    data class Placement(val top: Int, val height: Int)

    /** The shorter reply keeps its default offset from the home bar after the shared translation. */
    fun positionOffset(navigationBottom: Int, density: Float, adjustment: Int): Int {
        val progress = adjustment.coerceIn(BottomTabLiquidGlassConfig.MIN_ADJUSTMENT,
            BottomTabLiquidGlassConfig.MAX_ADJUSTMENT) / 100f
        val navigation = navigationBottom.coerceAtLeast(0)
        val down = navigation + ((if (navigation > 0) 8f else 28f) * density).roundToInt()
        val range = if (progress < 0f) down.toFloat() else 120f * density
        return (-progress * range).roundToInt()
    }

    fun placement(
        hostHeight: Int,
        hostBottomInWindow: Int,
        windowBottom: Int,
        navigationBottom: Int,
        baselineHeight: Int,
        minimumHeight: Int,
        density: Float,
        config: BottomTabLiquidGlassConfig,
        contentTop: Int = 0,
    ): Placement? {
        if (hostHeight <= 0 || baselineHeight <= 0 || minimumHeight <= 0 ||
            !density.isFinite() || density <= 0f) return null
        val windowBottomInHost = hostHeight + windowBottom - hostBottomInWindow
        val bottomLimit = minOf(hostHeight, windowBottomInHost)
        val topLimit = contentTop.coerceAtLeast(0)
        if (bottomLimit - topLimit < minimumHeight) return null
        val navigation = navigationBottom.coerceAtLeast(0)
        val defaultBottom = (windowBottomInHost - navigation).coerceIn(0, hostHeight)
        val centreGap = (TAB_HEIGHT_DP * density).roundToInt() / 2f +
            ((if (navigation > 0) 8f else 28f) * density).roundToInt()
        val defaultTop = (defaultBottom - centreGap - baselineHeight / 2f).roundToInt()
            .coerceIn(0, (defaultBottom - baselineHeight).coerceAtLeast(0))
        val bottom = (defaultTop + baselineHeight + positionOffset(navigation, density, config.positionAdjustment))
            .coerceIn(topLimit + minimumHeight, bottomLimit)
        val height = adjustedSize(baselineHeight, minimumHeight, bottom - topLimit, config.heightAdjustment)
            ?: return null
        return Placement(bottom - height, height)
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

    /** The default keeps 28dp side padding; the right half can use the remaining window width. */
    fun columnWidth(available: Int, count: Int, density: Float, adjustment: Int, minimumContentWidth: Int = 0): Int {
        if (available <= 0 || count <= 0 || !density.isFinite() || density <= 0f) return 0
        val inset = 2 * (HORIZONTAL_PADDING_DP * density).roundToInt()
        val margin = (SCREEN_EDGE_MARGIN_DP * density).roundToInt()
        val baseline = minOf((TAB_WIDTH_DP * density).roundToInt(), (available - inset - 2 * margin) / count)
        val minimum = maxOf((24f * density).roundToInt(), minimumContentWidth)
        return adjustedSize(baseline, minimum, (available - inset) / count, adjustment) ?: 0
    }

    /** Intrinsic labels and fixed-size icons do not shrink with a previously adjusted column. */
    fun minimumColumnWidth(artwork: List<BottomTabArtworkContract.Artwork>, density: Float): Int {
        fun naturalWidth(view: View): Int {
            if (view.visibility == View.GONE) return 0
            val body = when (view) {
                is TextView -> kotlin.math.ceil(view.paint.measureText(view.text?.toString().orEmpty())).toInt() +
                    view.compoundPaddingLeft + view.compoundPaddingRight
                is ImageView -> (view.layoutParams?.width ?: 0).takeIf { it > 0 }
                    ?: minOf(view.width, view.height).coerceAtLeast(0)
                is ViewGroup -> (0 until view.childCount).maxOfOrNull { naturalWidth(view.getChildAt(it)) } ?: 0
                else -> view.minimumWidth
            }
            return if (view is TextView) body else body + view.paddingLeft + view.paddingRight
        }
        return (artwork.maxOfOrNull { naturalWidth(it.content) } ?: 0) + (4f * density).roundToInt()
    }

    /** KernelSU keeps the 56dp inner height while parent constraints determine the column width. */
    fun dropletHeight(columnWidth: Int, barHeight: Int, density: Float, baselineHeight: Int = barHeight): Int =
        if (columnWidth <= 0 || barHeight <= 0 || !density.isFinite() || density <= 0f) 0
        else ((DROPLET_HEIGHT_DP * density).roundToInt() + barHeight - baselineHeight).coerceIn(1, barHeight)

    /** Independently measured 64dp/56dp layers can leave an odd number of pixels between them. */
    fun dropletInset(barHeight: Int, density: Float, baselineHeight: Int = barHeight): Int =
        ((barHeight - dropletHeight(1, barHeight, density, baselineHeight)) / 2f).roundToInt().coerceAtLeast(0)

    /** Only the native column's plain, top-aligned hairline is decorative chrome. */
    fun hideColumnHairlines(row: ViewGroup, density: Float, beforeHide: (View) -> Unit) {
        val maxHeight = max(1, (2f * density).roundToInt())
        val maxTop = (4f * density).roundToInt()
        for (i in 0 until row.childCount) {
            val column = row.getChildAt(i) as? ViewGroup ?: continue
            if (column.visibility != View.VISIBLE || column.width <= 0) continue
            for (j in 0 until column.childCount) {
                val child = column.getChildAt(j)
                if (child.javaClass == View::class.java && child.visibility == View.VISIBLE &&
                    child.height in 1..maxHeight && child.top <= maxTop &&
                    child.width >= column.width * 0.75f) {
                    beforeHide(child)
                    child.visibility = View.GONE
                }
            }
        }
    }
}
