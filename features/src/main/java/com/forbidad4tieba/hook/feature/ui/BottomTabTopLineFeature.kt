package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val BottomTabTopLineFeature = FeatureDefinition.observed(
    id = "BottomTabTopLineHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.ANY,
    enabled = { settings -> canInstallHomeNativeGlass(settings) && !settings.isBottomTabLiquidGlassEnabled },
) { cl, settings ->
    BottomTabTopLineHook.hook(cl)
}
