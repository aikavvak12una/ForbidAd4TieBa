package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.HomeGlassPreferences
import android.graphics.Color
import com.forbidad4tieba.hook.config.ConfigManager

internal class HomeNativeGlassRuntimeStyleStore(
    private val onStyleChanged: () -> Unit,
) {
    @Volatile private var runtimeStyle: HomeNativeGlassRuntimeStyle = HomeNativeGlassRuntimeStyle.EMPTY
    @Volatile private var runtimeStyleSnapshotVersion: Long = -1L
    @Volatile private var pbCommentDynamicTintState: PbCommentDynamicTintState? = null

    fun peek(): HomeNativeGlassRuntimeStyle = runtimeStyle

    fun current(): HomeNativeGlassRuntimeStyle {
        refresh()
        return runtimeStyle
    }

    fun pbCommentTintColor(): Int {
        pbCommentTintColorOrNull()?.let { return it }
        return Color.rgb(255, 255, 255)
    }

    fun pbCommentTintColorOrNull(): Int? {
        val style = current()
        val tintColor = style.tintColor
        val autoTintColor = style.autoTintColor
        val cached = pbCommentDynamicTintState
        if (
            cached != null &&
            cached.tintColor == tintColor &&
            cached.autoTintColor == autoTintColor
        ) {
            return cached.lightColor
        }
        val baseColor = style.configuredPbCommentTintColor() ?: return null
        val state = PbCommentDynamicTintState(
            tintColor = tintColor,
            autoTintColor = autoTintColor,
            lightColor = pbCommentBaseRgb(baseColor),
        )
        pbCommentDynamicTintState = state
        return state.lightColor
    }

    fun clearDerivedTint() {
        pbCommentDynamicTintState = null
    }

    fun refresh(
        forceHostRead: Boolean = false,
        scheduleReapply: Boolean = true,
    ): Boolean {
        val snapshotVersion = ConfigManager.snapshotVersion()
        if (!ConfigManager.snapshot().isHomeNativeGlassEnabled) {
            val changed = update(HomeNativeGlassRuntimeStyle.EMPTY, scheduleReapply)
            runtimeStyleSnapshotVersion = snapshotVersion
            return changed
        }
        val darkMode = if (forceHostRead) {
            HomeNativeGlassHostDarkModeBridge.isDarkModeEnabled()
        } else {
            HomeNativeGlassHostDarkModeBridge.currentKnownDarkMode()
        }
        if (darkMode == null) {
            val changed = update(HomeNativeGlassRuntimeStyle.EMPTY, scheduleReapply)
            runtimeStyleSnapshotVersion = -1L
            return changed
        }
        HomeGlassPreferences.setHomeNativeGlassDarkModeActive(darkMode)
        if (
            !forceHostRead &&
            runtimeStyleSnapshotVersion == snapshotVersion &&
            runtimeStyle.darkMode == darkMode
        ) {
            return false
        }
        val activeStyle = HomeGlassPreferences.activeHomeNativeGlassStyle()
        val next = HomeNativeGlassRuntimeStyle(
            darkMode = darkMode,
            backgroundImagePath = activeStyle.backgroundImagePath.trim(),
            blurCacheImagePath = activeStyle.blurCacheImagePath.trim(),
            tintColor = activeStyle.tintColor,
            autoTintColor = activeStyle.autoTintColor,
            tintAlphaPercent = activeStyle.tintAlphaPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
            cardBlurPercent = activeStyle.cardBlurPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            cardRadiusDp = activeStyle.cardRadiusDp.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ),
            strokeEnabled = activeStyle.strokeEnabled,
            shadowStrengthPercent = activeStyle.shadowStrengthPercent.coerceIn(
                HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            ),
        )
        val changed = update(next, scheduleReapply)
        runtimeStyleSnapshotVersion = snapshotVersion
        return changed
    }

    private fun update(
        next: HomeNativeGlassRuntimeStyle,
        scheduleReapply: Boolean,
    ): Boolean {
        if (runtimeStyle == next) return false
        runtimeStyle = next
        if (scheduleReapply) {
            onStyleChanged()
        }
        return true
    }

    private data class PbCommentDynamicTintState(
        val tintColor: Int,
        val autoTintColor: Int,
        val lightColor: Int,
    )
}
