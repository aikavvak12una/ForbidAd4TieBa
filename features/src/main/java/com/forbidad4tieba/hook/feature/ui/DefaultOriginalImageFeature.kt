package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.config.SimpleToggle
import com.forbidad4tieba.hook.symbol.contract.OriginalImageContract

internal val DefaultOriginalImageFeature = FeatureDefinition.observed(
    id = "DefaultOriginalImageHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.IMAGE_VIEWER,
    toggle = SimpleToggle.DEFAULT_ORIGINAL_IMAGE,
) { cl, _ ->
    OriginalImageContract.resolveDefaultOriginalImageSymbols(cl, symbols)?.let { targets ->
        DefaultOriginalImageHook.hook(targets)
    }
}
