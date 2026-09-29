package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.symbol.contract.HomeTabsContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val HomeTabFeature = FeatureDefinition.observed(
    id = "HomeTabHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    // The settings catalog observes host updates even before customization is enabled.
    enabled = { available(HookFeatureKey.SIMPLIFY_HOME_TOP_TABS) },
) { cl, settings ->
    HomeTabsContract.resolveHomeTabSymbols(cl, symbols)?.let { targets ->
        HomeTabHook.hook(targets)
    }
}
