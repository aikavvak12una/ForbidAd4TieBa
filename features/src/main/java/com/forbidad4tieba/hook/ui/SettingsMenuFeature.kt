package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val SettingsMenuFeature = FeatureDefinition.observed(
    id = "SettingsMenuHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
) { cl, settings ->
    SettingsMenuHook.hook(cl, symbols)
}
