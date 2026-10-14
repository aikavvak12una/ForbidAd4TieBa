package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import com.forbidad4tieba.hook.ui.UiText

/** Shared material and center placement; the droplet width applies only to the home Tab bar. */
data class BottomTabLiquidGlassConfig(
    /** Center distance above the navigation area, subject to safe clearance; -1 selects the default. */
    val bottomGapDp: Int = -1,
    val blurEnabled: Boolean = true,
    val liquidGlassEnabled: Boolean = true,
    val dropletWidthPercent: Int = 135,
    val blurPercent: Int = 20,
) {
    val needsBackdrop: Boolean get() = liquidGlassEnabled || (blurEnabled && blurPercent > 0)

    companion object {
        const val MAX_BOTTOM_GAP_DP = 120
        const val MIN_DROPLET_WIDTH_PERCENT = 100
        const val MAX_DROPLET_WIDTH_PERCENT = 200
        const val MAX_BLUR_PERCENT = 100
        val DEFAULT = BottomTabLiquidGlassConfig()
    }

    fun normalized(): BottomTabLiquidGlassConfig = copy(
        bottomGapDp = bottomGapDp.coerceIn(-1, MAX_BOTTOM_GAP_DP),
        dropletWidthPercent = dropletWidthPercent.coerceIn(MIN_DROPLET_WIDTH_PERCENT, MAX_DROPLET_WIDTH_PERCENT),
        blurPercent = blurPercent.coerceIn(0, MAX_BLUR_PERCENT),
    )
}

/** Feature-specific persistence stays out of the shared ConfigManager container. */
internal object BottomTabLiquidGlassPreferences {
    const val ENABLED = "floating_bottom_bar"
    const val BOTTOM_GAP = "bottom_tab_liquid_glass_bottom_gap_dp"
    const val BLUR = "bottom_tab_liquid_glass_blur"
    const val DROPLET_WIDTH = "bottom_tab_liquid_glass_droplet_width_percent"
    const val BLUR_PERCENT = "bottom_tab_liquid_glass_blur_percent"
    const val FLOATING = "bottom_tab_liquid_glass_floating"
    const val LIQUID_GLASS = "bottom_tab_liquid_glass_effect"
    const val POST_REPLY_BAR = "bottom_tab_liquid_glass_post_reply_bar"

    val ENABLED_SETTING = BooleanPreference(
        ENABLED, false, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_FLOATING, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_FLOATING_DESC),
    )
    val BOTTOM_GAP_SETTING = IntPreference(BOTTOM_GAP, BottomTabLiquidGlassConfig.DEFAULT.bottomGapDp, PreferenceUse.FORM)
    val DROPLET_WIDTH_SETTING = IntPreference(DROPLET_WIDTH, BottomTabLiquidGlassConfig.DEFAULT.dropletWidthPercent, PreferenceUse.FORM)
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
        ENABLED_SETTING, BOTTOM_GAP_SETTING, BLUR_SETTING, DROPLET_WIDTH_SETTING, BLUR_PERCENT_SETTING,
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
            bottomGapDp = BOTTOM_GAP_SETTING.read(prefs),
            blurEnabled = BLUR_SETTING.read(prefs),
            liquidGlassEnabled = LIQUID_GLASS_SETTING.read(prefs),
            dropletWidthPercent = DROPLET_WIDTH_SETTING.read(prefs),
            blurPercent = BLUR_PERCENT_SETTING.read(prefs),
        ).normalized()
    }

    /** The extension menu owns enablement; the form commits style changes as they are made. */
    fun save(prefs: SharedPreferences, config: BottomTabLiquidGlassConfig) {
        val value = config.normalized()
        prefs.edit()
            .putInt(BOTTOM_GAP, value.bottomGapDp)
            .putBoolean(BLUR, value.blurEnabled)
            .putBoolean(LIQUID_GLASS, value.liquidGlassEnabled)
            .putInt(DROPLET_WIDTH, value.dropletWidthPercent)
            .putInt(BLUR_PERCENT, value.blurPercent)
            .apply()
    }
}
