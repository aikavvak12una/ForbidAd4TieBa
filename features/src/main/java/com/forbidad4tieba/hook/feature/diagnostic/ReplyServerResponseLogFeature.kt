package com.forbidad4tieba.hook.feature.diagnostic

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.symbol.contract.ReplyLogContract

internal val ReplyServerResponseLogFeature = FeatureDefinition.observed(
    id = "ReplyServerResponseLogHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isDetailedLoggingEnabled },
) { cl, settings ->
    ReplyLogContract.resolveReplyServerResponseLogSymbols(cl, symbols)?.let { targets ->
        ReplyServerResponseLogHook.hook(targets)
    }
}
