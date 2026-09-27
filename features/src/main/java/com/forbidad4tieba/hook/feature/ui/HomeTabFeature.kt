package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.feature.shared.canInstallHomeTopTabs
import com.forbidad4tieba.hook.symbol.contract.HomeTabsContract

internal val HomeTabFeature = FeatureDefinition.observed(
    id = "HomeTabHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallHomeTopTabs(settings) },
) { cl, settings ->
    HomeTabsContract.resolveHomeTabSymbols(cl, symbols)?.let { targets ->
        HomeTabHook.hook(targets)
    }
}
