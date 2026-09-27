package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.symbol.contract.FeedContract

internal val FeedInfoLogFeature = FeatureDefinition.observed(
    id = "FeedInfoLogHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isDetailedLoggingEnabled },
) { cl, settings ->
    FeedContract.resolveFeedInfoLogSymbols(cl, symbols)?.let { targets ->
        FeedInfoLogHook.hook(targets)
    }
}
