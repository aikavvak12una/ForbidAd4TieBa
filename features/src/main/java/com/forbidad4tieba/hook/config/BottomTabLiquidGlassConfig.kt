package com.forbidad4tieba.hook.config

import android.content.SharedPreferences

/** Immutable, validated once when the settings snapshot is built. Dimensions are in dp. */
data class BottomTabLiquidGlassConfig(
    val heightDp: Int = 64,
    /** Zero keeps the existing content-hugging width; other values are window percentages. */
    val widthPercent: Int = 0,
    /** -1 selects the navigation-aware default, otherwise measured above the navigation area. */
    val bottomGapDp: Int = -1,
    val blurEnabled: Boolean = true,
    val refractionEnabled: Boolean = true,
    val highlightEnabled: Boolean = true,
    val shadowEnabled: Boolean = true,
    val tiltEnabled: Boolean = true,
    val pressEffectEnabled: Boolean = true,
) {
    companion object {
        const val MIN_HEIGHT_DP = 48
        const val MAX_HEIGHT_DP = 120
        const val MIN_WIDTH_PERCENT = 60
        const val MAX_WIDTH_PERCENT = 100
        const val MAX_BOTTOM_GAP_DP = 120
        val DEFAULT = BottomTabLiquidGlassConfig()
    }

    fun normalized(): BottomTabLiquidGlassConfig = copy(
        heightDp = heightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
        widthPercent = if (widthPercent == 0) 0 else
            widthPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT),
        bottomGapDp = bottomGapDp.coerceIn(-1, MAX_BOTTOM_GAP_DP),
    )
}

/** Feature-specific persistence stays out of the shared ConfigManager container. */
internal object BottomTabLiquidGlassPreferences {
    const val HEIGHT = "bottom_tab_liquid_glass_height_dp"
    const val WIDTH = "bottom_tab_liquid_glass_width_percent"
    const val BOTTOM_GAP = "bottom_tab_liquid_glass_bottom_gap_dp"
    const val BLUR = "bottom_tab_liquid_glass_blur"
    const val REFRACTION = "bottom_tab_liquid_glass_refraction"
    const val HIGHLIGHT = "bottom_tab_liquid_glass_highlight"
    const val SHADOW = "bottom_tab_liquid_glass_shadow"
    const val TILT = "bottom_tab_liquid_glass_tilt"
    const val PRESS = "bottom_tab_liquid_glass_press_effect"

    fun read(prefs: SharedPreferences): BottomTabLiquidGlassConfig = read(prefs.readOnlyValues())

    fun read(prefs: SettingsValues): BottomTabLiquidGlassConfig {
        val defaults = BottomTabLiquidGlassConfig.DEFAULT
        return BottomTabLiquidGlassConfig(
            heightDp = prefs.getInt(HEIGHT, defaults.heightDp),
            widthPercent = prefs.getInt(WIDTH, defaults.widthPercent),
            bottomGapDp = prefs.getInt(BOTTOM_GAP, defaults.bottomGapDp),
            blurEnabled = prefs.getBoolean(BLUR, defaults.blurEnabled),
            refractionEnabled = prefs.getBoolean(REFRACTION, defaults.refractionEnabled),
            highlightEnabled = prefs.getBoolean(HIGHLIGHT, defaults.highlightEnabled),
            shadowEnabled = prefs.getBoolean(SHADOW, defaults.shadowEnabled),
            tiltEnabled = prefs.getBoolean(TILT, defaults.tiltEnabled),
            pressEffectEnabled = prefs.getBoolean(PRESS, defaults.pressEffectEnabled),
        ).normalized()
    }

    fun save(prefs: SharedPreferences, config: BottomTabLiquidGlassConfig) {
        val value = config.normalized()
        prefs.edit()
            .putInt(HEIGHT, value.heightDp)
            .putInt(WIDTH, value.widthPercent)
            .putInt(BOTTOM_GAP, value.bottomGapDp)
            .putBoolean(BLUR, value.blurEnabled)
            .putBoolean(REFRACTION, value.refractionEnabled)
            .putBoolean(HIGHLIGHT, value.highlightEnabled)
            .putBoolean(SHADOW, value.shadowEnabled)
            .putBoolean(TILT, value.tiltEnabled)
            .putBoolean(PRESS, value.pressEffectEnabled)
            .apply()
    }
}
