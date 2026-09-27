package com.forbidad4tieba.hook.feature.diagnostic

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val TiebaHostLogFeature = FeatureDefinition.single(
    id = "TiebaHostLogHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isDetailedLoggingEnabled },
) { cl, settings ->
    TiebaHostLogHook.hook(cl)
}
