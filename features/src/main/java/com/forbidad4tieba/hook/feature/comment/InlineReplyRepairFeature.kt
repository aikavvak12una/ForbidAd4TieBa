package com.forbidad4tieba.hook.feature.comment

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.symbol.contract.InlineReplyContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val InlineReplyRepairFeature = FeatureDefinition.observed(
    id = "InlineReplyRepair", phase = FeaturePhase.SYMBOL, process = FeatureProcess.MAIN,
    enabled = { available(HookFeatureKey.INLINE_REPLY_REPAIR) },
) { cl, _ ->
    InlineReplyContract.resolve(cl, symbols)?.let { InlineReplyRepairHook(it).install() }
}
