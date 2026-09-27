package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val HelpCenterFooterBlockFeature = FeatureDefinition.observed(
    id = "HelpCenterFooterBlockHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
) { cl, settings ->
    HelpCenterFooterBlockHook.hook(cl)
}
