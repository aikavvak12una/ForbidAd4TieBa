package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val HomeTabRedDotBlockFeature = FeatureDefinition.observed(
    id = "HomeTabRedDotBlockHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isHomeTabRedDotHidden },
) { cl, settings ->
    HomeTabRedDotBlockHook.hook(cl)
}
