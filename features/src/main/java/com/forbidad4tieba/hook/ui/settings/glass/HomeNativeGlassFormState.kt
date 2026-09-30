package com.forbidad4tieba.hook.ui.settings.glass

import com.forbidad4tieba.hook.config.HomeGlassPreferences
import android.content.Context
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageSelectionState
import com.forbidad4tieba.hook.ui.UiStyle

internal object HomeNativeGlassFormState {
    fun createHomeNativeGlassModeConfigState(
        style: HomeGlassPreferences.HomeNativeGlassStyleConfig,
    ): HomeNativeGlassModeConfigState {
        val imageState = HomeNativeGlassImageSelectionState(
            style.backgroundImagePath.trim(),
            HomeGlassPreferences.normalizeHomeNativeGlassTintColor(style.tintColor),
        ).apply {
            paletteColors = parseHomeNativeGlassTintPalette(style.tintPalette)
            defaultTintColor = homeNativeGlassCachedAutoTintColorOrNull(style.autoTintColor)
            if (path.isBlank()) {
                tintColor = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
            } else if (
                tintColor != HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR &&
                paletteColors.none { it == tintColor }
            ) {
                paletteColors = paletteColors + tintColor
            }
        }
        return HomeNativeGlassModeConfigState(
            imageState = imageState,
            blurCacheImagePath = style.blurCacheImagePath.trim(),
            tintAlphaPercent = style.tintAlphaPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
            cardBlurPercent = style.cardBlurPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            cardRadiusDp = style.cardRadiusDp.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ),
            strokeEnabled = style.strokeEnabled,
            shadowStrengthPercent = style.shadowStrengthPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            ),
        )
    }

    fun copyHomeNativeGlassImageState(
        target: HomeNativeGlassImageSelectionState,
        source: HomeNativeGlassImageSelectionState,
    ) {
        target.path = source.path.trim()
        target.tintColor = HomeGlassPreferences.normalizeHomeNativeGlassTintColor(source.tintColor)
        target.paletteColors = source.paletteColors
        target.defaultTintColor = source.defaultTintColor
    }

    fun homeNativeGlassStyleFromModeState(
        state: HomeNativeGlassModeConfigState,
        blurCacheImagePath: String,
    ): HomeGlassPreferences.HomeNativeGlassStyleConfig {
        val imageState = state.imageState
        val backgroundImagePath = imageState.path.trim()
        val hasBackgroundImage = backgroundImagePath.isNotBlank()
        return HomeGlassPreferences.HomeNativeGlassStyleConfig(
            backgroundImagePath = backgroundImagePath,
            blurCacheImagePath = if (hasBackgroundImage) {
                blurCacheImagePath.trim()
            } else {
                HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH
            },
            tintColor = if (hasBackgroundImage) {
                HomeGlassPreferences.normalizeHomeNativeGlassTintColor(imageState.tintColor)
            } else {
                HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
            },
            autoTintColor = if (hasBackgroundImage) {
                HomeGlassPreferences.normalizeHomeNativeGlassTintColor(
                    imageState.defaultTintColor ?: HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR
                )
            } else {
                HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR
            },
            tintPalette = if (hasBackgroundImage) {
                serializeHomeNativeGlassTintPalette(imageState.paletteColors)
            } else {
                HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE
            },
            tintAlphaPercent = state.tintAlphaPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
            cardBlurPercent = state.cardBlurPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            cardRadiusDp = state.cardRadiusDp.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ),
            strokeEnabled = state.strokeEnabled,
            shadowStrengthPercent = state.shadowStrengthPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            ),
        )
    }

    fun homeNativeGlassPreviewStyleFromModeState(
        state: HomeNativeGlassModeConfigState,
    ): HomeGlassPreferences.HomeNativeGlassStyleConfig {
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
        style: HomeGlassPreferences.HomeNativeGlassStyleConfig,
    ): HomeNativeGlassPreviewBitmapKey {
        return HomeNativeGlassPreviewBitmapKey(
            sourcePath = style.backgroundImagePath.trim(),
            blurPercent = style.cardBlurPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            tintAlphaPercent = style.tintAlphaPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
        )
    }

    fun putHomeNativeGlassStyle(
        editor: android.content.SharedPreferences.Editor,
        keys: HomeGlassPreferences.HomeNativeGlassStyleKeys,
        style: HomeGlassPreferences.HomeNativeGlassStyleConfig,
    ): android.content.SharedPreferences.Editor {
        return editor
            .putString(keys.backgroundImagePath.key, style.backgroundImagePath)
            .putString(keys.blurCacheImagePath.key, style.blurCacheImagePath)
            .putInt(keys.tintColor.key, style.tintColor)
            .putInt(keys.autoTintColor.key, style.autoTintColor)
            .putString(keys.tintPalette.key, style.tintPalette)
            .putInt(keys.tintAlphaPercent.key, style.tintAlphaPercent)
            .putInt(keys.cardBlurPercent.key, style.cardBlurPercent)
            .putInt(keys.cardRadiusDp.key, style.cardRadiusDp)
            .putBoolean(keys.strokeEnabled.key, style.strokeEnabled)
            .putInt(keys.shadowStrengthPercent.key, style.shadowStrengthPercent)
            .remove(keys.legacyShadowEnabled.key)
    }

    private fun parseHomeNativeGlassTintPalette(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', ';', '\n')
            .asSequence()
            .mapNotNull { it.trim().toIntOrNull() }
            .map { HomeGlassPreferences.normalizeHomeNativeGlassTintColor(it) }
            .filter { it != HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR }
            .distinct()
            .toList()
    }

    private fun serializeHomeNativeGlassTintPalette(colors: List<Int>): String {
        return colors.asSequence()
            .map { HomeGlassPreferences.normalizeHomeNativeGlassTintColor(it) }
            .filter { it != HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR }
            .distinct()
            .joinToString(",") { it.toString() }
    }

    private fun homeNativeGlassCachedAutoTintColorOrNull(color: Int): Int? {
        val normalized = HomeGlassPreferences.normalizeHomeNativeGlassTintColor(color)
        return normalized.takeIf { it != HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR }
    }
}
