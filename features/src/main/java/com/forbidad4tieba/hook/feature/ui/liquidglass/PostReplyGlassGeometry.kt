package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.view.Gravity
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.symbol.contract.PostReplyLoadingArtwork
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Window coordinates make edge-to-edge and already-inset hosts use the same clearance. */
internal object PostReplyGlassGeometry {
    const val HEIGHT_DP = 36f
    const val EDGE_MARGIN_DP = 24f
    const val SURFACE_GAP_DP = 8f
    const val ACTION_PADDING_DP = 4f
    val PENDING_ACTION_COUNT: Int get() = PostReplyLoadingArtwork.actionCount
    private const val INPUT_FRACTION = 0.618f
    private const val VERTICAL_PADDING_DP = 3f
    private const val MIN_INPUT_WIDTH_DP = 96f
    private const val MIN_ACTION_WIDTH_DP = 24f

    data class Surfaces(val inputWidth: Int, val actionsLeft: Int, val actionsContentWidth: Int) {
        fun columnWidth(index: Int, count: Int): Int =
            actionColumnWidth(actionsContentWidth, index, count)
    }

    fun actionColumnWidth(contentWidth: Int, index: Int, count: Int): Int =
        contentWidth / count + if (index < contentWidth % count) 1 else 0

    fun minimumWidth(actionCount: Int, density: Float): Int {
        val input = (MIN_INPUT_WIDTH_DP * density).roundToInt()
        if (actionCount <= 0) return input
        val actions = actionCount * (MIN_ACTION_WIDTH_DP * density).roundToInt() +
            2 * (ACTION_PADDING_DP * density).roundToInt()
        return (SURFACE_GAP_DP * density).roundToInt() +
            ceil(maxOf(input / INPUT_FRACTION, actions / (1f - INPUT_FRACTION))).toInt()
    }

    /** The ratio owns both surfaces; minimum content sizes constrain the whole bar, not the split. */
    fun separate(width: Int, actionCount: Int, density: Float): Surfaces? {
        if (!density.isFinite() || density <= 0f || actionCount <= 0 ||
            width < minimumWidth(actionCount, density)) return null
        val gap = (SURFACE_GAP_DP * density).roundToInt()
        val usable = width - gap
        val inputWidth = (usable * INPUT_FRACTION).roundToInt()
        val actionsLeft = inputWidth + gap
        return Surfaces(inputWidth, actionsLeft,
            width - actionsLeft - 2 * (ACTION_PADDING_DP * density).roundToInt())
    }

    /** A loading input must not shrink the native slot reserved for the complete reply controls. */
    fun height(controlHeight: Int, density: Float, nativeHeight: Int = 0): Int =
        maxOf((HEIGHT_DP * density).roundToInt(), nativeHeight,
            controlHeight + 2 * (VERTICAL_PADDING_DP * density).roundToInt())

    /** Loading icons reserve exactly the same caption line as the native icon/number stack. */
    fun actionContentHeight(iconSize: Int, labelHeight: Int, density: Float): Int =
        iconSize + labelHeight + if (labelHeight > 0) density.roundToInt() else 0

    fun needsVerticalCenter(gravity: Int): Boolean =
        gravity and Gravity.VERTICAL_GRAVITY_MASK != Gravity.CENTER_VERTICAL

    data class Bounds(val left: Int, val top: Int, val width: Int, val height: Int)

    fun calculate(
        hostWidth: Int, hostHeight: Int, hostLeft: Int, hostTop: Int,
        windowLeft: Int, windowRight: Int, windowBottom: Int,
        safeLeft: Int, safeRight: Int, navigationBottom: Int,
        contentTop: Int, actionCount: Int, controlHeight: Int,
        density: Float, config: BottomTabLiquidGlassConfig, nativeHeight: Int = 0,
    ): Bounds? {
        if (hostWidth <= 0 || hostHeight <= 0 || !density.isFinite() || density <= 0f || actionCount < 0) return null
        val left = (windowLeft + safeLeft - hostLeft).coerceIn(0, hostWidth)
        val right = (hostLeft + hostWidth - (windowRight - safeRight)).coerceIn(0, hostWidth)
        val available = hostWidth - left - right
        val minimumWidth = minimumWidth(actionCount, density)
        if (available < minimumWidth) return null
        val desired = available - 2 * (EDGE_MARGIN_DP * density).roundToInt()
        val width = LiquidGlassBarMetrics.adjustedSize(desired, minimumWidth, available, config.widthAdjustment)
            ?: return null
        val baselineHeight = height(controlHeight, density, nativeHeight)
        val minimumHeight = maxOf((HEIGHT_DP * density).roundToInt(),
            controlHeight + 2 * (VERTICAL_PADDING_DP * density).roundToInt())
        val placement = LiquidGlassBarMetrics.placement(hostHeight, hostTop + hostHeight,
            windowBottom, navigationBottom, baselineHeight, minimumHeight, density, config, contentTop)
            ?: return null
        return Bounds(left + (available - width) / 2, placement.top, width, placement.height)
    }

    fun contentBottomPadding(nativePadding: Int, contentBottom: Int, glassTop: Int, density: Float): Int =
        maxOf(nativePadding, contentBottom - glassTop + (8 * density).roundToInt())

    // An unfocused window still supplies exit/resume frames and the system's task snapshot.
    fun shouldFloat(enabled: Boolean, shown: Boolean, ime: Boolean, editor: Boolean): Boolean =
        enabled && shown && !ime && !editor
}
