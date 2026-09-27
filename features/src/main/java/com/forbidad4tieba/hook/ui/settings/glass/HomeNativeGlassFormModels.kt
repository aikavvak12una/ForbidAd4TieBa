package com.forbidad4tieba.hook.ui.settings.glass

import com.forbidad4tieba.hook.ui.HomeNativeGlassImageSelectionState

internal class HomeNativeGlassModeConfigState(
    val imageState: HomeNativeGlassImageSelectionState,
    var blurCacheImagePath: String,
    var tintAlphaPercent: Int,
    var cardBlurPercent: Int,
    var cardRadiusDp: Int,
    var strokeEnabled: Boolean,
    var shadowStrengthPercent: Int,
)

internal enum class HomeNativeGlassStyleRole {
    ROW_TITLE,
    ROW_DESCRIPTION,
    MUTED_TEXT,
    ACCENT_TEXT,
    BUTTON_ACCENT,
    BUTTON_SECONDARY,
    INPUT_TEXT,
    SEEK_BAR,
    SWITCH,
}

internal data class HomeNativeGlassPreviewBitmapKey(
    val sourcePath: String,
    val blurPercent: Int,
    val tintAlphaPercent: Int,
)
