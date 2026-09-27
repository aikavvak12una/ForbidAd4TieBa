package com.forbidad4tieba.hook.feature.ui.liquidglass

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val BottomTabLiquidGlassFeature = FeatureDefinition.observed(
    id = "BottomTabLiquidGlassHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isBottomTabLiquidGlassEnabled },
) { cl, settings ->
    BottomTabLiquidGlassHook.hook(cl)
}
