package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import android.content.SharedPreferences
import android.graphics.Color

object HomeGlassPreferences {
    const val DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH = ""

    const val DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH = ""

    const val DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR = 0

    const val DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR = 0

    const val DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE = ""

    const val DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 0

    const val DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 72

    const val DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 24

    const val DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED = true

    const val DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED = true

    const val DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED = true

    const val DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT = 100

    const val APPLE_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 0

    const val APPLE_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 72

    const val APPLE_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 24

    const val MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = -100

    const val MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 100

    const val MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 10

    const val MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 100

    const val MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 0

    const val MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 32

    const val MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT = 0

    const val MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT = 100

    const val KEY_ENABLE_HOME_NATIVE_GLASS = "enable_home_native_glass"

    const val KEY_ENABLE_HOME_TAB_DYNAMIC_TINT = "enable_home_tab_dynamic_tint"

    const val KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH = "home_native_glass_background_image_path"

    const val KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH = "home_native_glass_blur_cache_image_path"

    const val KEY_HOME_NATIVE_GLASS_TINT_COLOR = "home_native_glass_tint_color"

    const val KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR = "home_native_glass_auto_tint_color"

    const val KEY_HOME_NATIVE_GLASS_TINT_PALETTE = "home_native_glass_tint_palette"

    const val KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = "home_native_glass_tint_alpha_percent"

    const val KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED =
        "home_native_glass_tint_alpha_offset_migrated"

    const val KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = "home_native_glass_card_blur_percent"

    const val KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP = "home_native_glass_card_radius_dp"

    const val KEY_HOME_NATIVE_GLASS_STROKE_ENABLED = "home_native_glass_stroke_enabled"

    const val KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED = "home_native_glass_shadow_enabled"

    const val KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT =
        "home_native_glass_shadow_strength_percent"

    const val KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT =
        "home_native_glass_background_image_path_light"

    const val KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_LIGHT =
        "home_native_glass_blur_cache_image_path_light"

    const val KEY_HOME_NATIVE_GLASS_TINT_COLOR_LIGHT = "home_native_glass_tint_color_light"

    const val KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR_LIGHT = "home_native_glass_auto_tint_color_light"

    const val KEY_HOME_NATIVE_GLASS_TINT_PALETTE_LIGHT = "home_native_glass_tint_palette_light"

    const val KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_LIGHT =
        "home_native_glass_tint_alpha_percent_light"

    const val KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_LIGHT =
        "home_native_glass_card_blur_percent_light"

    const val KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_LIGHT =
        "home_native_glass_card_radius_dp_light"

    const val KEY_HOME_NATIVE_GLASS_STROKE_ENABLED_LIGHT = "home_native_glass_stroke_enabled_light"

    const val KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED_LIGHT = "home_native_glass_shadow_enabled_light"

    const val KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_LIGHT =
        "home_native_glass_shadow_strength_percent_light"

    const val KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_DARK =
        "home_native_glass_background_image_path_dark"

    const val KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_DARK =
        "home_native_glass_blur_cache_image_path_dark"

    const val KEY_HOME_NATIVE_GLASS_TINT_COLOR_DARK = "home_native_glass_tint_color_dark"

    const val KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR_DARK = "home_native_glass_auto_tint_color_dark"

    const val KEY_HOME_NATIVE_GLASS_TINT_PALETTE_DARK = "home_native_glass_tint_palette_dark"

    const val KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_DARK =
        "home_native_glass_tint_alpha_percent_dark"

    const val KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_DARK =
        "home_native_glass_card_blur_percent_dark"

    const val KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_DARK =
        "home_native_glass_card_radius_dp_dark"

    const val KEY_HOME_NATIVE_GLASS_STROKE_ENABLED_DARK = "home_native_glass_stroke_enabled_dark"

    const val KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED_DARK = "home_native_glass_shadow_enabled_dark"

    const val KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_DARK =
        "home_native_glass_shadow_strength_percent_dark"

    @Volatile private var homeNativeGlassDarkModeActive: Boolean = false

    data class HomeNativeGlassStyleConfig(
        val backgroundImagePath: String = DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
        val blurCacheImagePath: String = DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
        val tintColor: Int = DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR,
        val autoTintColor: Int = DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
        val tintPalette: String = DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE,
        val tintAlphaPercent: Int = DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        val cardBlurPercent: Int = DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
        val cardRadiusDp: Int = DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
        val strokeEnabled: Boolean = DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED,
        val shadowStrengthPercent: Int = DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
    ) {
        fun hasBackgroundImage(): Boolean = backgroundImagePath.isNotBlank()
    }

    val hasAnyHomeNativeGlassBackgroundImage: Boolean
        get() = ConfigManager.snapshot().hasAnyHomeNativeGlassBackgroundImage()

    val isHomeNativeGlassStrokeEnabled: Boolean get() = activeHomeNativeGlassStyle().strokeEnabled

    val isHomeNativeGlassDarkModeActive: Boolean
        get() = homeNativeGlassDarkModeActive

    fun setHomeNativeGlassDarkModeActive(enabled: Boolean?): Boolean {
        val next = enabled ?: return false
        if (homeNativeGlassDarkModeActive == next) return false
        homeNativeGlassDarkModeActive = next
        return true
    }

    fun activeHomeNativeGlassStyle(): HomeNativeGlassStyleConfig {
        return ConfigManager.snapshot().homeNativeGlassStyleForDarkMode(homeNativeGlassDarkModeActive)
    }

    internal fun readHomeNativeGlassStyle(
        p: SharedPreferences,
        keys: HomeNativeGlassStyleKeys,
    ): HomeNativeGlassStyleConfig {
        val hasModeStyle = keys.all().any { p.contains(it) }
        val allowLegacyFallback = !hasModeStyle &&
            keys.backgroundImagePath.key == KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT

        fun <T> readMode(setting: Preference<T>, legacy: Preference<T>): T = when {
            p.contains(setting.key) -> setting.read(p)
            allowLegacyFallback -> legacy.read(p)
            else -> setting.defaultValue
        }

        fun readString(setting: StringPreference, legacy: StringPreference): String =
            readMode(setting, legacy)?.trim().orEmpty()

        fun readShadowStrengthPercent(): Int {
            return when {
                p.contains(keys.shadowStrengthPercent.key) -> keys.shadowStrengthPercent.read(p)
                p.contains(keys.legacyShadowEnabled.key) -> {
                    if (keys.legacyShadowEnabled.read(p)) {
                        DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    } else {
                        MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    }
                }
                allowLegacyFallback && p.contains(KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT) -> HomeGlassPreferences.HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT.read(p)
                allowLegacyFallback && p.contains(KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED) -> {
                    if (HomeGlassPreferences.HOME_NATIVE_GLASS_SHADOW_ENABLED.read(p)) {
                        DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    } else {
                        MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    }
                }
                else -> DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
            }.coerceIn(
                MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            )
        }

        val tintAlphaPercent = when {
            p.contains(keys.tintAlphaPercent.key) -> keys.tintAlphaPercent.read(p).coerceIn(
                MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            )
            allowLegacyFallback -> readHomeNativeGlassTintAlphaPercent(p)
            else -> DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
        }

        return HomeNativeGlassStyleConfig(
            backgroundImagePath = readString(keys.backgroundImagePath, HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH),
            blurCacheImagePath = readString(keys.blurCacheImagePath, HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH),
            tintColor = normalizeHomeNativeGlassTintColor(
                readMode(keys.tintColor, HOME_NATIVE_GLASS_TINT_COLOR)
            ),
            autoTintColor = normalizeHomeNativeGlassTintColor(
                readMode(keys.autoTintColor, HOME_NATIVE_GLASS_AUTO_TINT_COLOR)
            ),
            tintPalette = readString(keys.tintPalette, HOME_NATIVE_GLASS_TINT_PALETTE),
            tintAlphaPercent = tintAlphaPercent,
            cardBlurPercent = readMode(keys.cardBlurPercent, HOME_NATIVE_GLASS_CARD_BLUR_PERCENT).coerceIn(
                MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            cardRadiusDp = readMode(keys.cardRadiusDp, HOME_NATIVE_GLASS_CARD_RADIUS_DP).coerceIn(
                MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ),
            strokeEnabled = readMode(keys.strokeEnabled, HOME_NATIVE_GLASS_STROKE_ENABLED),
            shadowStrengthPercent = readShadowStrengthPercent(),
        )
    }

    private fun readHomeNativeGlassTintAlphaPercent(p: SharedPreferences): Int {
        val rawValue = HomeGlassPreferences.HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT.read(p)
        val migrated = HomeGlassPreferences.HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED.read(p)
        val normalized = if (!migrated && p.contains(KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT)) {
            (rawValue - 50).coerceIn(
                MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            )
        } else {
            rawValue.coerceIn(
                MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            )
        }
        if (!migrated && p.contains(KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT)) {
            runCatching {
                p.edit()
                    .putInt(KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT, normalized)
                    .putBoolean(KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED, true)
                    .apply()
            }
        }
        return normalized
    }

    fun normalizeHomeNativeGlassTintColor(color: Int): Int {
        if (color == DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR || Color.alpha(color) == 0) {
            return DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
        }
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }

    internal fun resetRuntime() { homeNativeGlassDarkModeActive = false }

    internal val ENABLE_HOME_NATIVE_GLASS = BooleanPreference(
        KEY_ENABLE_HOME_NATIVE_GLASS, false, PreferenceUse.SWITCH, HookFeatureKey.HOME_NATIVE_GLASS,
        SwitchPresentation(UiText.Settings.HOME_NATIVE_GLASS_LABEL, UiText.Settings.HOME_NATIVE_GLASS_DESC),
    )
    internal val ENABLE_HOME_TAB_DYNAMIC_TINT = BooleanPreference(
        KEY_ENABLE_HOME_TAB_DYNAMIC_TINT, DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED, PreferenceUse.LEGACY, null,
    )
    internal val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH = StringPreference(
        KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH, DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH, PreferenceUse.LEGACY,
    )
    internal val HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH = StringPreference(
        KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH, DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH, PreferenceUse.LEGACY,
    )
    internal val HOME_NATIVE_GLASS_TINT_COLOR = IntPreference(
        KEY_HOME_NATIVE_GLASS_TINT_COLOR, DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR, PreferenceUse.LEGACY,
    )
    internal val HOME_NATIVE_GLASS_AUTO_TINT_COLOR = IntPreference(
        KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR, DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR, PreferenceUse.LEGACY,
    )
    internal val HOME_NATIVE_GLASS_TINT_PALETTE = StringPreference(
        KEY_HOME_NATIVE_GLASS_TINT_PALETTE, DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE, PreferenceUse.LEGACY,
    )
    internal val HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = IntPreference(
        KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT, DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT, PreferenceUse.LEGACY,
    )
    internal val HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED = BooleanPreference(
        KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED, false, PreferenceUse.LEGACY, null,
    )
    internal val HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = IntPreference(
        KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT, DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT, PreferenceUse.LEGACY,
    )
    internal val HOME_NATIVE_GLASS_CARD_RADIUS_DP = IntPreference(
        KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP, DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP, PreferenceUse.LEGACY,
    )
    internal val HOME_NATIVE_GLASS_STROKE_ENABLED = BooleanPreference(
        KEY_HOME_NATIVE_GLASS_STROKE_ENABLED, DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED, PreferenceUse.LEGACY, null,
    )
    internal val HOME_NATIVE_GLASS_SHADOW_ENABLED = BooleanPreference(
        KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED, DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED, PreferenceUse.LEGACY, null,
    )
    internal val HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT = IntPreference(
        KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT, DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT, PreferenceUse.LEGACY,
    )
    internal val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT = StringPreference(
        KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT, DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_LIGHT = StringPreference(
        KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_LIGHT, DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_TINT_COLOR_LIGHT = IntPreference(
        KEY_HOME_NATIVE_GLASS_TINT_COLOR_LIGHT, DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_AUTO_TINT_COLOR_LIGHT = IntPreference(
        KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR_LIGHT, DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_TINT_PALETTE_LIGHT = StringPreference(
        KEY_HOME_NATIVE_GLASS_TINT_PALETTE_LIGHT, DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_LIGHT = IntPreference(
        KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_LIGHT, DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_LIGHT = IntPreference(
        KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_LIGHT, DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_CARD_RADIUS_DP_LIGHT = IntPreference(
        KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_LIGHT, DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_STROKE_ENABLED_LIGHT = BooleanPreference(
        KEY_HOME_NATIVE_GLASS_STROKE_ENABLED_LIGHT, DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED, PreferenceUse.FORM, null,
    )
    internal val HOME_NATIVE_GLASS_SHADOW_ENABLED_LIGHT = BooleanPreference(
        KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED_LIGHT, DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED, PreferenceUse.LEGACY, null,
    )
    internal val HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_LIGHT = IntPreference(
        KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_LIGHT, DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_DARK = StringPreference(
        KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_DARK, DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_DARK = StringPreference(
        KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_DARK, DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_TINT_COLOR_DARK = IntPreference(
        KEY_HOME_NATIVE_GLASS_TINT_COLOR_DARK, DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_AUTO_TINT_COLOR_DARK = IntPreference(
        KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR_DARK, DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_TINT_PALETTE_DARK = StringPreference(
        KEY_HOME_NATIVE_GLASS_TINT_PALETTE_DARK, DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_DARK = IntPreference(
        KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_DARK, DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_DARK = IntPreference(
        KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_DARK, DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_CARD_RADIUS_DP_DARK = IntPreference(
        KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_DARK, DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP, PreferenceUse.FORM,
    )
    internal val HOME_NATIVE_GLASS_STROKE_ENABLED_DARK = BooleanPreference(
        KEY_HOME_NATIVE_GLASS_STROKE_ENABLED_DARK, DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED, PreferenceUse.FORM, null,
    )
    internal val HOME_NATIVE_GLASS_SHADOW_ENABLED_DARK = BooleanPreference(
        KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED_DARK, DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED, PreferenceUse.LEGACY, null,
    )
    internal val HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_DARK = IntPreference(
        KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_DARK, DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT, PreferenceUse.FORM,
    )

    internal val preferences: List<Preference<*>> = listOf(
        ENABLE_HOME_NATIVE_GLASS,
        ENABLE_HOME_TAB_DYNAMIC_TINT,
        HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
        HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
        HOME_NATIVE_GLASS_TINT_COLOR,
        HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
        HOME_NATIVE_GLASS_TINT_PALETTE,
        HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED,
        HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
        HOME_NATIVE_GLASS_CARD_RADIUS_DP,
        HOME_NATIVE_GLASS_STROKE_ENABLED,
        HOME_NATIVE_GLASS_SHADOW_ENABLED,
        HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
        HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT,
        HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_LIGHT,
        HOME_NATIVE_GLASS_TINT_COLOR_LIGHT,
        HOME_NATIVE_GLASS_AUTO_TINT_COLOR_LIGHT,
        HOME_NATIVE_GLASS_TINT_PALETTE_LIGHT,
        HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_LIGHT,
        HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_LIGHT,
        HOME_NATIVE_GLASS_CARD_RADIUS_DP_LIGHT,
        HOME_NATIVE_GLASS_STROKE_ENABLED_LIGHT,
        HOME_NATIVE_GLASS_SHADOW_ENABLED_LIGHT,
        HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_LIGHT,
        HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_DARK,
        HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_DARK,
        HOME_NATIVE_GLASS_TINT_COLOR_DARK,
        HOME_NATIVE_GLASS_AUTO_TINT_COLOR_DARK,
        HOME_NATIVE_GLASS_TINT_PALETTE_DARK,
        HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_DARK,
        HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_DARK,
        HOME_NATIVE_GLASS_CARD_RADIUS_DP_DARK,
        HOME_NATIVE_GLASS_STROKE_ENABLED_DARK,
        HOME_NATIVE_GLASS_SHADOW_ENABLED_DARK,
        HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_DARK,
    )

    internal data class HomeNativeGlassStyleKeys(
        val backgroundImagePath: StringPreference,
        val blurCacheImagePath: StringPreference,
        val tintColor: IntPreference,
        val autoTintColor: IntPreference,
        val tintPalette: StringPreference,
        val tintAlphaPercent: IntPreference,
        val cardBlurPercent: IntPreference,
        val cardRadiusDp: IntPreference,
        val strokeEnabled: BooleanPreference,
        val shadowStrengthPercent: IntPreference,
        val legacyShadowEnabled: BooleanPreference,
    ) {
        fun all(): Array<String> = arrayOf(
            backgroundImagePath.key,
            blurCacheImagePath.key,
            tintColor.key,
            autoTintColor.key,
            tintPalette.key,
            tintAlphaPercent.key,
            cardBlurPercent.key,
            cardRadiusDp.key,
            strokeEnabled.key,
            shadowStrengthPercent.key,
            legacyShadowEnabled.key,
        )
    }

    internal val HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS = HomeNativeGlassStyleKeys(
        backgroundImagePath = HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT,
        blurCacheImagePath = HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_LIGHT,
        tintColor = HOME_NATIVE_GLASS_TINT_COLOR_LIGHT,
        autoTintColor = HOME_NATIVE_GLASS_AUTO_TINT_COLOR_LIGHT,
        tintPalette = HOME_NATIVE_GLASS_TINT_PALETTE_LIGHT,
        tintAlphaPercent = HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_LIGHT,
        cardBlurPercent = HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_LIGHT,
        cardRadiusDp = HOME_NATIVE_GLASS_CARD_RADIUS_DP_LIGHT,
        strokeEnabled = HOME_NATIVE_GLASS_STROKE_ENABLED_LIGHT,
        shadowStrengthPercent = HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_LIGHT,
        legacyShadowEnabled = HOME_NATIVE_GLASS_SHADOW_ENABLED_LIGHT,
    )

    internal val HOME_NATIVE_GLASS_DARK_STYLE_KEYS = HomeNativeGlassStyleKeys(
        backgroundImagePath = HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_DARK,
        blurCacheImagePath = HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_DARK,
        tintColor = HOME_NATIVE_GLASS_TINT_COLOR_DARK,
        autoTintColor = HOME_NATIVE_GLASS_AUTO_TINT_COLOR_DARK,
        tintPalette = HOME_NATIVE_GLASS_TINT_PALETTE_DARK,
        tintAlphaPercent = HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_DARK,
        cardBlurPercent = HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_DARK,
        cardRadiusDp = HOME_NATIVE_GLASS_CARD_RADIUS_DP_DARK,
        strokeEnabled = HOME_NATIVE_GLASS_STROKE_ENABLED_DARK,
        shadowStrengthPercent = HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_DARK,
        legacyShadowEnabled = HOME_NATIVE_GLASS_SHADOW_ENABLED_DARK,
    )


}
