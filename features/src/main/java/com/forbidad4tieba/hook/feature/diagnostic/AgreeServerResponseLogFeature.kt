package com.forbidad4tieba.hook.feature.diagnostic

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.symbol.contract.AgreeLogContract

internal val AgreeServerResponseLogFeature = FeatureDefinition.observed(
    id = "AgreeServerResponseLogHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isDetailedLoggingEnabled },
) { cl, settings ->
    AgreeLogContract.resolveAgreeServerResponseLogSymbols(cl, symbols)?.let { targets ->
        AgreeServerResponseLogHook.hook(targets)
    }
}
