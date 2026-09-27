package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val HomeNativeGlassFeature = FeatureDefinition.single(
    id = "HomeNativeGlassHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallHomeNativeGlass(settings) },
) { cl, settings ->
    HomeNativeGlassHook.hook(cl, symbols)
}

internal fun HookInstallContext.canInstallHomeNativeGlass(settings: SettingsSnapshot): Boolean {
    return settings.isHomeNativeGlassEnabled &&
        settings.hasAnyHomeNativeGlassBackgroundImage() &&
        available(HookFeatureKey.HOME_NATIVE_GLASS)
}
