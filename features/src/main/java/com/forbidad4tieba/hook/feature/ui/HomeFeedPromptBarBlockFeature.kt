package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val HomeFeedPromptBarBlockFeature = FeatureDefinition.observed(
    id = "HomeFeedPromptBarBlockHook",
    phase = FeaturePhase.STATIC,
    process = FeatureProcess.MAIN,
) { cl, settings ->
    HomeFeedPromptBarBlockHook.hook(cl)
}
