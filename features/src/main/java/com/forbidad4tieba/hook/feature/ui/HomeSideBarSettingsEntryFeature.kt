package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val HomeSideBarSettingsEntryFeature = FeatureDefinition.observed(
    id = "HomeSideBarSettingsEntryHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
) { cl, settings ->
    HomeSideBarSettingsEntryHook.hook(cl)
}
