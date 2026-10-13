package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.view.Gravity
import kotlin.math.roundToInt

/** Window coordinates make edge-to-edge and already-inset hosts use the same clearance. */
internal object PostReplyGlassGeometry {
    const val HEIGHT_DP = 36f
    const val EDGE_MARGIN_DP = 24f
    const val SURFACE_GAP_DP = 8f
    const val ACTION_PADDING_DP = 4f
    private const val VERTICAL_PADDING_DP = 3f
    private const val MIN_INPUT_WIDTH_DP = 96f
    private const val MIN_ACTION_WIDTH_DP = 40f

    data class Surfaces(val inputWidth: Int, val actionsLeft: Int, val actionsContentWidth: Int)

    fun minimumWidth(actionCount: Int, density: Float): Int = (MIN_INPUT_WIDTH_DP * density).roundToInt() +
        if (actionCount > 0) (SURFACE_GAP_DP * density).roundToInt() + minimumActionsWidth(actionCount, density) else 0

    private fun minimumActionsWidth(count: Int, density: Float): Int =
        count * (MIN_ACTION_WIDTH_DP * density).roundToInt() + 2 * (ACTION_PADDING_DP * density).roundToInt()

    /** Share the visible glass width 61.8:38.2; narrow windows preserve usable control widths. */
    fun separate(width: Int, actionCount: Int, density: Float): Surfaces? {
        if (!density.isFinite() || density <= 0f || actionCount <= 0 ||
            width < minimumWidth(actionCount, density)) return null
        val gap = (SURFACE_GAP_DP * density).roundToInt()
        val usable = width - gap
        val actionsWidth = maxOf((usable * 0.382f).roundToInt(), minimumActionsWidth(actionCount, density))
        val actionsLeft = width - actionsWidth
        return Surfaces(actionsLeft - gap, actionsLeft,
            actionsWidth - 2 * (ACTION_PADDING_DP * density).roundToInt())
    }

    /** A loading input must not shrink the native slot reserved for the complete reply controls. */
    fun height(controlHeight: Int, density: Float, nativeHeight: Int = 0): Int =
        maxOf((HEIGHT_DP * density).roundToInt(), nativeHeight,
            controlHeight + 2 * (VERTICAL_PADDING_DP * density).roundToInt())

    fun needsVerticalCenter(gravity: Int): Boolean =
        gravity and Gravity.VERTICAL_GRAVITY_MASK != Gravity.CENTER_VERTICAL

    data class Bounds(val left: Int, val top: Int, val width: Int, val height: Int)

    fun calculate(
        hostWidth: Int, hostHeight: Int, hostLeft: Int, hostTop: Int,
        windowLeft: Int, windowRight: Int, windowBottom: Int,
        safeLeft: Int, safeRight: Int, navigationBottom: Int,
        contentTop: Int, actionCount: Int, controlHeight: Int,
        density: Float, bottomGapDp: Int, nativeHeight: Int = 0,
    ): Bounds? {
        if (hostWidth <= 0 || hostHeight <= 0 || !density.isFinite() || density <= 0f || actionCount < 0) return null
        val left = (windowLeft + safeLeft - hostLeft).coerceIn(0, hostWidth)
        val right = (hostLeft + hostWidth - (windowRight - safeRight)).coerceIn(0, hostWidth)
        val available = hostWidth - left - right
        val minimumWidth = minimumWidth(actionCount, density)
        if (available < minimumWidth) return null
        val desired = available - 2 * (EDGE_MARGIN_DP * density).roundToInt()
        val width = desired.coerceIn(minimumWidth, available)
        val height = height(controlHeight, density, nativeHeight)
        val top = LiquidGlassBarMetrics.floatingTop(hostHeight, hostTop + hostHeight,
            windowBottom, navigationBottom, height, density, bottomGapDp)
            .coerceAtLeast(contentTop)
        val bottom = minOf(hostHeight, windowBottom - navigationBottom - hostTop)
        if (top + height > bottom) return null
        return Bounds(left + (available - width) / 2, top, width, height)
    }

    fun contentBottomPadding(nativePadding: Int, contentBottom: Int, glassTop: Int, density: Float): Int =
        maxOf(nativePadding, contentBottom - glassTop + (8 * density).roundToInt())

    // An unfocused window still supplies exit/resume frames and the system's task snapshot.
    fun shouldFloat(enabled: Boolean, shown: Boolean, ime: Boolean, editor: Boolean): Boolean =
        enabled && shown && !ime && !editor
}
