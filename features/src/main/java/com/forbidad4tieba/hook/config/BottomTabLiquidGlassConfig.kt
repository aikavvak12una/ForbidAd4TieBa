package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import com.forbidad4tieba.hook.ui.UiText

/** Both bars apply the same adjustments to their own default geometry. Zero restores the baseline. */
data class BottomTabLiquidGlassConfig(
    val widthAdjustment: Int = 0,
    val heightAdjustment: Int = 0,
    val positionAdjustment: Int = 0,
    val blurEnabled: Boolean = true,
    val liquidGlassEnabled: Boolean = true,
    val blurPercent: Int = 20,
) {
    val needsBackdrop: Boolean get() = liquidGlassEnabled || (blurEnabled && blurPercent > 0)

    companion object {
        const val MIN_ADJUSTMENT = -100
        const val MAX_ADJUSTMENT = 100
        const val MAX_BLUR_PERCENT = 100
        val DEFAULT = BottomTabLiquidGlassConfig()
    }

    fun normalized(): BottomTabLiquidGlassConfig = copy(
        widthAdjustment = widthAdjustment.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT),
        heightAdjustment = heightAdjustment.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT),
        positionAdjustment = positionAdjustment.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT),
        blurPercent = blurPercent.coerceIn(0, MAX_BLUR_PERCENT),
    )
}

/** Feature-specific persistence stays out of the shared ConfigManager container. */
internal object BottomTabLiquidGlassPreferences {
    const val ENABLED = "floating_bottom_bar"
    const val WIDTH_ADJUSTMENT = "floating_bottom_bar_width_adjustment"
    const val HEIGHT_ADJUSTMENT = "floating_bottom_bar_height_adjustment"
    const val POSITION_ADJUSTMENT = "floating_bottom_bar_position_adjustment"
    const val BLUR = "bottom_tab_liquid_glass_blur"
    const val BLUR_PERCENT = "bottom_tab_liquid_glass_blur_percent"
    const val FLOATING = "bottom_tab_liquid_glass_floating"
    const val LIQUID_GLASS = "bottom_tab_liquid_glass_effect"
    const val POST_REPLY_BAR = "bottom_tab_liquid_glass_post_reply_bar"

    val ENABLED_SETTING = BooleanPreference(
        ENABLED, false, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_FLOATING, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_FLOATING_DESC),
    )
    val WIDTH_ADJUSTMENT_SETTING = IntPreference(WIDTH_ADJUSTMENT, BottomTabLiquidGlassConfig.DEFAULT.widthAdjustment, PreferenceUse.FORM)
    val HEIGHT_ADJUSTMENT_SETTING = IntPreference(HEIGHT_ADJUSTMENT, BottomTabLiquidGlassConfig.DEFAULT.heightAdjustment, PreferenceUse.FORM)
    val POSITION_ADJUSTMENT_SETTING = IntPreference(POSITION_ADJUSTMENT, BottomTabLiquidGlassConfig.DEFAULT.positionAdjustment, PreferenceUse.FORM)
    val BLUR_PERCENT_SETTING = IntPreference(BLUR_PERCENT, BottomTabLiquidGlassConfig.DEFAULT.blurPercent, PreferenceUse.FORM)
    val BLUR_SETTING = BooleanPreference(
        BLUR, BottomTabLiquidGlassConfig.DEFAULT.blurEnabled, PreferenceUse.FORM, null,
        SwitchPresentation(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BLUR, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BLUR_DESC),
    )
    val FLOATING_SETTING = BooleanPreference(
        FLOATING, true, PreferenceUse.LEGACY, null,
    )
    val LIQUID_GLASS_SETTING = BooleanPreference(
        LIQUID_GLASS, BottomTabLiquidGlassConfig.DEFAULT.liquidGlassEnabled, PreferenceUse.FORM, null,
        SwitchPresentation(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_EFFECT, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_EFFECT_DESC),
    )
    val POST_REPLY_BAR_SETTING = BooleanPreference(
        POST_REPLY_BAR, false, PreferenceUse.LEGACY, null,
    )
    val preferences: List<Preference<*>> = listOf(
        ENABLED_SETTING, WIDTH_ADJUSTMENT_SETTING, HEIGHT_ADJUSTMENT_SETTING, POSITION_ADJUSTMENT_SETTING,
        BLUR_SETTING, BLUR_PERCENT_SETTING,
        FLOATING_SETTING, LIQUID_GLASS_SETTING, POST_REPLY_BAR_SETTING,
    )

    fun isEnabled(prefs: SettingsValues): Boolean {
        if (prefs.contains(ENABLED)) return ENABLED_SETTING.read(prefs)
        val tab = TabPreferences.BOTTOM_TAB_LIQUID_GLASS.read(prefs)
        val reply = POST_REPLY_BAR_SETTING.read(prefs)
        val parentAllowed = !prefs.contains(TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION) ||
            TabPreferences.ENABLE_TAB_CUSTOMIZATION.read(prefs)
        return (tab || reply) && parentAllowed && FLOATING_SETTING.read(prefs)
    }

    /** Migrate before the Tab master is initialized; only previously active bars opt in. */
    fun ensureInitialized(prefs: SharedPreferences) {
        if (prefs.contains(ENABLED)) return
        prefs.edit().putBoolean(ENABLED, isEnabled(prefs.readOnlyValues())).apply()
    }

    fun read(prefs: SharedPreferences): BottomTabLiquidGlassConfig = read(prefs.readOnlyValues())

    fun read(prefs: SettingsValues): BottomTabLiquidGlassConfig {
        return BottomTabLiquidGlassConfig(
            widthAdjustment = WIDTH_ADJUSTMENT_SETTING.read(prefs),
            heightAdjustment = HEIGHT_ADJUSTMENT_SETTING.read(prefs),
            positionAdjustment = POSITION_ADJUSTMENT_SETTING.read(prefs),
            blurEnabled = BLUR_SETTING.read(prefs),
            liquidGlassEnabled = LIQUID_GLASS_SETTING.read(prefs),
            blurPercent = BLUR_PERCENT_SETTING.read(prefs),
        ).normalized()
    }

    /** The extension menu owns enablement; the form commits style changes as they are made. */
    fun save(prefs: SharedPreferences, config: BottomTabLiquidGlassConfig) {
        val value = config.normalized()
        prefs.edit()
            .putInt(WIDTH_ADJUSTMENT, value.widthAdjustment)
            .putInt(HEIGHT_ADJUSTMENT, value.heightAdjustment)
            .putInt(POSITION_ADJUSTMENT, value.positionAdjustment)
            .putBoolean(BLUR, value.blurEnabled)
            .putBoolean(LIQUID_GLASS, value.liquidGlassEnabled)
            .putInt(BLUR_PERCENT, value.blurPercent)
            .apply()
    }
}
