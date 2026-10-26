package com.forbidad4tieba.hook.feature.ui.nativeglass

import android.graphics.Bitmap
import com.forbidad4tieba.hook.symbol.model.HookSymbols

/** The settings UI's only access to this feature's private runtime state and caches. */
internal object HomeNativeGlassIntegration {
    fun installSettingsBridge(cl: ClassLoader, symbols: HookSymbols) {
        HomeNativeGlassSettingsBridge.install(cl, symbols)
    }

    fun observeSettingsController(controller: Any?) {
        HomeNativeGlassHostDarkModeBridge.cacheFromController(controller)
    }

    fun isHostDarkModeEnabled(): Boolean? = HomeNativeGlassHostDarkModeBridge.isDarkModeEnabled()

    fun settingsAccentColor(): Int? = HomeNativeGlassDynamicTintCache.resolveAccentColor()

    fun decodeSettingsBackground(path: String, width: Int, height: Int): Bitmap? =
        HomeNativeGlassImageCache.decodeSampledBitmap(path, width, height)
}
