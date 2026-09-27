package com.forbidad4tieba.hook.ui.settings.glass

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageSelectionState
import com.forbidad4tieba.hook.ui.UiStyle

internal object HomeNativeGlassFormState {
    fun createHomeNativeGlassModeConfigState(
        style: ConfigManager.HomeNativeGlassStyleConfig,
    ): HomeNativeGlassModeConfigState {
        val imageState = HomeNativeGlassImageSelectionState(
            style.backgroundImagePath.trim(),
            ConfigManager.normalizeHomeNativeGlassTintColor(style.tintColor),
        ).apply {
            paletteColors = parseHomeNativeGlassTintPalette(style.tintPalette)
            defaultTintColor = homeNativeGlassCachedAutoTintColorOrNull(style.autoTintColor)
            if (path.isBlank()) {
                tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
            } else if (
                tintColor != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR &&
                paletteColors.none { it == tintColor }
            ) {
                paletteColors = paletteColors + tintColor
            }
        }
        return HomeNativeGlassModeConfigState(
            imageState = imageState,
            blurCacheImagePath = style.blurCacheImagePath.trim(),
            tintAlphaPercent = style.tintAlphaPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
            cardBlurPercent = style.cardBlurPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            cardRadiusDp = style.cardRadiusDp.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ),
            strokeEnabled = style.strokeEnabled,
            shadowStrengthPercent = style.shadowStrengthPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            ),
        )
    }

    fun copyHomeNativeGlassImageState(
        target: HomeNativeGlassImageSelectionState,
        source: HomeNativeGlassImageSelectionState,
    ) {
        target.path = source.path.trim()
        target.tintColor = ConfigManager.normalizeHomeNativeGlassTintColor(source.tintColor)
        target.paletteColors = source.paletteColors
        target.defaultTintColor = source.defaultTintColor
    }

    fun homeNativeGlassStyleFromModeState(
        state: HomeNativeGlassModeConfigState,
        blurCacheImagePath: String,
    ): ConfigManager.HomeNativeGlassStyleConfig {
        val imageState = state.imageState
        val backgroundImagePath = imageState.path.trim()
        val hasBackgroundImage = backgroundImagePath.isNotBlank()
        return ConfigManager.HomeNativeGlassStyleConfig(
            backgroundImagePath = backgroundImagePath,
            blurCacheImagePath = if (hasBackgroundImage) {
                blurCacheImagePath.trim()
            } else {
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH
            },
            tintColor = if (hasBackgroundImage) {
                ConfigManager.normalizeHomeNativeGlassTintColor(imageState.tintColor)
            } else {
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
            },
            autoTintColor = if (hasBackgroundImage) {
                ConfigManager.normalizeHomeNativeGlassTintColor(
                    imageState.defaultTintColor ?: ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR
                )
            } else {
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR
            },
            tintPalette = if (hasBackgroundImage) {
                serializeHomeNativeGlassTintPalette(imageState.paletteColors)
            } else {
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE
            },
            tintAlphaPercent = state.tintAlphaPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
            cardBlurPercent = state.cardBlurPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            cardRadiusDp = state.cardRadiusDp.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ),
            strokeEnabled = state.strokeEnabled,
            shadowStrengthPercent = state.shadowStrengthPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            ),
        )
    }

    fun homeNativeGlassPreviewStyleFromModeState(
        state: HomeNativeGlassModeConfigState,
    ): ConfigManager.HomeNativeGlassStyleConfig {
        return homeNativeGlassStyleFromModeState(
            state,
            blurCacheImagePath = state.blurCacheImagePath,
        )
    }

    fun homeNativeGlassDialogTokens(
        context: Context,
        state: HomeNativeGlassModeConfigState,
        darkMode: Boolean,
    ): UiStyle.Tokens {
        return UiStyle.homeNativeGlassPreviewTokens(
            context,
            homeNativeGlassPreviewStyleFromModeState(state),
            darkMode,
        )
    }

    fun homeNativeGlassPreviewBitmapKey(
        style: ConfigManager.HomeNativeGlassStyleConfig,
    ): HomeNativeGlassPreviewBitmapKey {
        return HomeNativeGlassPreviewBitmapKey(
            sourcePath = style.backgroundImagePath.trim(),
            blurPercent = style.cardBlurPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            tintAlphaPercent = style.tintAlphaPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
        )
    }

    fun putHomeNativeGlassStyle(
        editor: android.content.SharedPreferences.Editor,
        keys: ConfigManager.HomeNativeGlassStyleKeys,
        style: ConfigManager.HomeNativeGlassStyleConfig,
    ): android.content.SharedPreferences.Editor {
        return editor
            .putString(keys.backgroundImagePath, style.backgroundImagePath)
            .putString(keys.blurCacheImagePath, style.blurCacheImagePath)
            .putInt(keys.tintColor, style.tintColor)
            .putInt(keys.autoTintColor, style.autoTintColor)
            .putString(keys.tintPalette, style.tintPalette)
            .putInt(keys.tintAlphaPercent, style.tintAlphaPercent)
            .putInt(keys.cardBlurPercent, style.cardBlurPercent)
            .putInt(keys.cardRadiusDp, style.cardRadiusDp)
            .putBoolean(keys.strokeEnabled, style.strokeEnabled)
            .putInt(keys.shadowStrengthPercent, style.shadowStrengthPercent)
            .remove(keys.legacyShadowEnabled)
    }

    private fun parseHomeNativeGlassTintPalette(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', ';', '\n')
            .asSequence()
            .mapNotNull { it.trim().toIntOrNull() }
            .map { ConfigManager.normalizeHomeNativeGlassTintColor(it) }
            .filter { it != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR }
            .distinct()
            .toList()
    }

    private fun serializeHomeNativeGlassTintPalette(colors: List<Int>): String {
        return colors.asSequence()
            .map { ConfigManager.normalizeHomeNativeGlassTintColor(it) }
            .filter { it != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR }
            .distinct()
            .joinToString(",") { it.toString() }
    }

    private fun homeNativeGlassCachedAutoTintColorOrNull(color: Int): Int? {
        val normalized = ConfigManager.normalizeHomeNativeGlassTintColor(color)
        return normalized.takeIf { it != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR }
    }
}
