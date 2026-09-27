package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager

object HomeNativeGlassDynamicTintCache {
    fun resolveAccentColor(): Int? {
        configuredTintColor()?.let { return it }
        return cachedAutoTintColor()
    }

    fun configuredTintColor(): Int? {
        return ConfigManager.homeNativeGlassTintColor
            .takeIf { it != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR }
    }

    private fun cachedAutoTintColor(): Int? {
        return ConfigManager.homeNativeGlassAutoTintColor
            .takeIf { it != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR }
    }
}
