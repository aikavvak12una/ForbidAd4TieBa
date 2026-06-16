package com.forbidad4tieba.hook.ui

import android.content.Context
import android.widget.TextView
import java.lang.ref.WeakReference

internal class HomeNativeGlassImageSelectionState(
    var path: String,
    var tintColor: Int,
) {
    var paletteColors: List<Int> = emptyList()
    var defaultTintColor: Int? = null
}

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

internal data class PendingHomeNativeGlassImagePick(
    val contextRef: WeakReference<Context>,
    val displayRef: WeakReference<TextView>,
    val state: HomeNativeGlassImageSelectionState,
    val darkMode: Boolean,
    val refreshPalette: (() -> Unit)?,
    val onImportedAnalysis: ((HomeNativeGlassImageAnalysis) -> Unit)?,
)

internal data class VersionDisplayInfo(
    val tiebaVersion: String,
    val tiebaBuildType: String,
    val moduleVersion: String,
    val moduleBuildType: String,
)
