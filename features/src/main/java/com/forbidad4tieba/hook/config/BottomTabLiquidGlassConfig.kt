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

    val HEIGHT_SETTING = IntPreference(HEIGHT, BottomTabLiquidGlassConfig.DEFAULT.heightDp, PreferenceUse.FORM)
    val WIDTH_SETTING = IntPreference(WIDTH, BottomTabLiquidGlassConfig.DEFAULT.widthPercent, PreferenceUse.FORM)
    val BOTTOM_GAP_SETTING = IntPreference(BOTTOM_GAP, BottomTabLiquidGlassConfig.DEFAULT.bottomGapDp, PreferenceUse.FORM)
    val BLUR_SETTING = BooleanPreference(BLUR, BottomTabLiquidGlassConfig.DEFAULT.blurEnabled, PreferenceUse.FORM, null)
    val REFRACTION_SETTING = BooleanPreference(REFRACTION, BottomTabLiquidGlassConfig.DEFAULT.refractionEnabled, PreferenceUse.FORM, null)
    val HIGHLIGHT_SETTING = BooleanPreference(HIGHLIGHT, BottomTabLiquidGlassConfig.DEFAULT.highlightEnabled, PreferenceUse.FORM, null)
    val SHADOW_SETTING = BooleanPreference(SHADOW, BottomTabLiquidGlassConfig.DEFAULT.shadowEnabled, PreferenceUse.FORM, null)
    val TILT_SETTING = BooleanPreference(TILT, BottomTabLiquidGlassConfig.DEFAULT.tiltEnabled, PreferenceUse.FORM, null)
    val PRESS_SETTING = BooleanPreference(PRESS, BottomTabLiquidGlassConfig.DEFAULT.pressEffectEnabled, PreferenceUse.FORM, null)
    val preferences: List<Preference<*>> = listOf(HEIGHT_SETTING, WIDTH_SETTING, BOTTOM_GAP_SETTING, BLUR_SETTING, REFRACTION_SETTING, HIGHLIGHT_SETTING, SHADOW_SETTING, TILT_SETTING, PRESS_SETTING)

    fun read(prefs: SharedPreferences): BottomTabLiquidGlassConfig = read(prefs.readOnlyValues())

    fun read(prefs: SettingsValues): BottomTabLiquidGlassConfig {
        return BottomTabLiquidGlassConfig(
            heightDp = HEIGHT_SETTING.read(prefs),
            widthPercent = WIDTH_SETTING.read(prefs),
            bottomGapDp = BOTTOM_GAP_SETTING.read(prefs),
            blurEnabled = BLUR_SETTING.read(prefs),
            refractionEnabled = REFRACTION_SETTING.read(prefs),
            highlightEnabled = HIGHLIGHT_SETTING.read(prefs),
            shadowEnabled = SHADOW_SETTING.read(prefs),
            tiltEnabled = TILT_SETTING.read(prefs),
            pressEffectEnabled = PRESS_SETTING.read(prefs),
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
