package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import com.forbidad4tieba.hook.ui.UiText

/** Independent bar widths; height, position and materials are shared. Zero restores the baseline. */
data class BottomTabLiquidGlassConfig(
    val homeWidthAdjustment: Int = 0,
    val heightAdjustment: Int = 0,
    val positionAdjustment: Int = 0,
    val blurEnabled: Boolean = true,
    val liquidGlassEnabled: Boolean = true,
    val blurPercent: Int = 20,
    val replyWidthAdjustment: Int = 0,
) {
    val needsBackdrop: Boolean get() = liquidGlassEnabled || (blurEnabled && blurPercent > 0)

    companion object {
        const val MIN_ADJUSTMENT = -100
        const val MAX_ADJUSTMENT = 100
        const val MAX_BLUR_PERCENT = 100
        val DEFAULT = BottomTabLiquidGlassConfig()
    }

    fun normalized(): BottomTabLiquidGlassConfig = copy(
        homeWidthAdjustment = homeWidthAdjustment.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT),
        replyWidthAdjustment = replyWidthAdjustment.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT),
        heightAdjustment = heightAdjustment.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT),
        positionAdjustment = positionAdjustment.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT),
        blurPercent = blurPercent.coerceIn(0, MAX_BLUR_PERCENT),
    )
}

/** Feature-specific persistence stays out of the shared ConfigManager container. */
internal object BottomTabLiquidGlassPreferences {
    const val ENABLED = "floating_bottom_bar"
    const val HOME_WIDTH_ADJUSTMENT = "floating_bottom_bar_width_adjustment"
    const val REPLY_WIDTH_ADJUSTMENT = "floating_post_reply_bar_width_adjustment"
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
    val HOME_WIDTH_ADJUSTMENT_SETTING = IntPreference(HOME_WIDTH_ADJUSTMENT, BottomTabLiquidGlassConfig.DEFAULT.homeWidthAdjustment, PreferenceUse.FORM)
    val REPLY_WIDTH_ADJUSTMENT_SETTING = IntPreference(REPLY_WIDTH_ADJUSTMENT, BottomTabLiquidGlassConfig.DEFAULT.replyWidthAdjustment, PreferenceUse.FORM)
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
        ENABLED_SETTING, HOME_WIDTH_ADJUSTMENT_SETTING, REPLY_WIDTH_ADJUSTMENT_SETTING,
        HEIGHT_ADJUSTMENT_SETTING, POSITION_ADJUSTMENT_SETTING,
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
        val initializeEnabled = !prefs.contains(ENABLED)
        val migrateWidth = !prefs.contains(REPLY_WIDTH_ADJUSTMENT) && prefs.contains(HOME_WIDTH_ADJUSTMENT)
        if (!initializeEnabled && !migrateWidth) return
        val editor = prefs.edit()
        if (initializeEnabled) editor.putBoolean(ENABLED, isEnabled(prefs.readOnlyValues()))
        if (migrateWidth) editor.putInt(REPLY_WIDTH_ADJUSTMENT,
            HOME_WIDTH_ADJUSTMENT_SETTING.read(prefs).coerceIn(
                BottomTabLiquidGlassConfig.MIN_ADJUSTMENT, BottomTabLiquidGlassConfig.MAX_ADJUSTMENT))
        editor.apply()
    }

    fun read(prefs: SharedPreferences): BottomTabLiquidGlassConfig = read(prefs.readOnlyValues())

    fun read(prefs: SettingsValues): BottomTabLiquidGlassConfig {
        val homeWidth = HOME_WIDTH_ADJUSTMENT_SETTING.read(prefs)
        return BottomTabLiquidGlassConfig(
            homeWidthAdjustment = homeWidth,
            heightAdjustment = HEIGHT_ADJUSTMENT_SETTING.read(prefs),
            positionAdjustment = POSITION_ADJUSTMENT_SETTING.read(prefs),
            blurEnabled = BLUR_SETTING.read(prefs),
            liquidGlassEnabled = LIQUID_GLASS_SETTING.read(prefs),
            blurPercent = BLUR_PERCENT_SETTING.read(prefs),
            replyWidthAdjustment = if (prefs.contains(REPLY_WIDTH_ADJUSTMENT))
                REPLY_WIDTH_ADJUSTMENT_SETTING.read(prefs) else homeWidth,
        ).normalized()
    }

    /** The extension menu owns enablement; the form commits style changes as they are made. */
    fun save(prefs: SharedPreferences, config: BottomTabLiquidGlassConfig) {
        val value = config.normalized()
        prefs.edit()
            .putInt(HOME_WIDTH_ADJUSTMENT, value.homeWidthAdjustment)
            .putInt(REPLY_WIDTH_ADJUSTMENT, value.replyWidthAdjustment)
            .putInt(HEIGHT_ADJUSTMENT, value.heightAdjustment)
            .putInt(POSITION_ADJUSTMENT, value.positionAdjustment)
            .putBoolean(BLUR, value.blurEnabled)
            .putBoolean(LIQUID_GLASS, value.liquidGlassEnabled)
            .putInt(BLUR_PERCENT, value.blurPercent)
            .apply()
    }
}
