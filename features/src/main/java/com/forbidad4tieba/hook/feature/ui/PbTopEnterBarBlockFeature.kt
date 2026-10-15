package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.symbol.contract.PbTopEnterBarContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbTopEnterBarBlockFeature = FeatureDefinition.observed(
    id = "PbTopEnterBarBlockHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isPbTopEnterBarHidden && available(HookFeatureKey.HIDE_PB_TOP_ENTER_BAR) },
) { cl, _ ->
    PbTopEnterBarContract.resolve(cl, symbols)?.let(PbTopEnterBarBlockHook::hook)
}
