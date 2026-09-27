package com.forbidad4tieba.hook.feature.account

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val AccountListDedupFeature = FeatureDefinition.observed(
    id = "AccountListDedupHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
) { cl, settings ->
    AccountListDedupHook.hook(cl)
}
