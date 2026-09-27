package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val HomeBottomTabAutoHideFeature = FeatureDefinition.observed(
    id = "HomeBottomTabAutoHideHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isHomeTabAutoHideEnabled },
) { cl, settings ->
    HomeBottomTabAutoHideHook.hook(cl)
}

internal val HomeTopTabAutoHideFeature = FeatureDefinition.observed(
    id = "HomeTopTabAutoHideHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isHomeTabAutoHideEnabled },
) { cl, settings ->
    HomeTopTabAutoHideHook.hook(cl)
}
