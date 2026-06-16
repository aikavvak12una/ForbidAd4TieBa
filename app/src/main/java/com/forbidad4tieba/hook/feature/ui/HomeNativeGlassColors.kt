package com.forbidad4tieba.hook.feature.ui

import android.graphics.Color
import com.forbidad4tieba.hook.config.ConfigManager

private const val PB_SORT_SWITCH_SELECTED_TINT_OVERLAY_ALPHA = 28
private const val PB_REPLY_BAR_INPUT_CAPSULE_COLOR_SHIFT_ALPHA = 28
private const val SUB_PB_INPUT_CAPSULE_COLOR_SHIFT_ALPHA = 18

internal fun homeNativePageFallbackColor(): Int {
    return Color.rgb(246, 248, 250)
}

internal fun pbCommentBaseRgb(color: Int): Int {
    return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
}

internal fun HomeNativeGlassRuntimeStyle.configuredPbCommentTintColor(): Int? {
    if (tintColor != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR) return tintColor
    return autoTintColor.takeIf { it != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR }
}

internal fun offsetSubPbInputCapsuleColor(color: Int): Int {
    return blendOpaqueColorOverlay(
        base = color,
        overlay = Color.BLACK,
        overlayAlpha = SUB_PB_INPUT_CAPSULE_COLOR_SHIFT_ALPHA,
    )
}

internal fun offsetPbReplyBarInputCapsuleColor(color: Int): Int {
    return blendOpaqueColorOverlay(
        base = color,
        overlay = Color.BLACK,
        overlayAlpha = PB_REPLY_BAR_INPUT_CAPSULE_COLOR_SHIFT_ALPHA,
    )
}

internal fun applyPbSortSwitchSelectedTintOverlay(color: Int): Int {
    return blendOpaqueColorOverlay(
        base = color,
        overlay = Color.BLACK,
        overlayAlpha = PB_SORT_SWITCH_SELECTED_TINT_OVERLAY_ALPHA,
    )
}

private fun blendOpaqueColorOverlay(base: Int, overlay: Int, overlayAlpha: Int): Int {
    return Color.rgb(
        blendOpaqueColorChannel(Color.red(base), Color.red(overlay), overlayAlpha),
        blendOpaqueColorChannel(Color.green(base), Color.green(overlay), overlayAlpha),
        blendOpaqueColorChannel(Color.blue(base), Color.blue(overlay), overlayAlpha),
    )
}

private fun blendOpaqueColorChannel(base: Int, overlay: Int, alpha: Int): Int {
    return ((base * (255 - alpha)) + (overlay * alpha)) / 255
}
