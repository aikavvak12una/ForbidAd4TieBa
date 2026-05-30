package com.forbidad4tieba.hook.feature.ui

import android.graphics.Color
import com.forbidad4tieba.hook.config.ConfigManager

object HomeNativeGlassDynamicTintCache {
    fun resolveAccentColor(): Int? {
        configuredTintColor()?.let { return it }
        return cachedAutoTintColor()
    }

    fun configuredTintColor(): Int? {
        return normalizedCachedTintColor(ConfigManager.homeNativeGlassTintColor)
    }

    private fun cachedAutoTintColor(): Int? {
        return normalizedCachedTintColor(ConfigManager.homeNativeGlassAutoTintColor)
    }

    private fun normalizedCachedTintColor(color: Int): Int? {
        if (color == ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR || Color.alpha(color) == 0) {
            return null
        }
        return Color.rgb(
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }
}
