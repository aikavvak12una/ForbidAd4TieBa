package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.HomeGlassPreferences

object HomeNativeGlassDynamicTintCache {
    fun resolveAccentColor(): Int? {
        configuredTintColor()?.let { return it }
        return cachedAutoTintColor()
    }

    fun configuredTintColor(): Int? {
        return HomeGlassPreferences.activeHomeNativeGlassStyle().tintColor
            .takeIf { it != HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR }
    }

    private fun cachedAutoTintColor(): Int? {
        return HomeGlassPreferences.activeHomeNativeGlassStyle().autoTintColor
            .takeIf { it != HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR }
    }
}
