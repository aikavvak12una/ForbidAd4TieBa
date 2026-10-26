package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.feature.ui.nativeglass.HomeNativeGlassIntegration
import com.forbidad4tieba.hook.symbol.contract.SettingsContract

internal val SettingsMenuFeature = FeatureDefinition.observed(
    id = "SettingsMenuHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
) { cl, settings ->
    HomeNativeGlassIntegration.installSettingsBridge(cl, symbols)
    SettingsContract.resolveSettingsMenuSymbols(cl, symbols)?.let { targets ->
        SettingsMenuHook.hook(cl, targets, HomeNativeGlassIntegration::observeSettingsController)
    }
}
