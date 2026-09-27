package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.SearchBoxContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val SearchBoxTextAdFeature = FeatureDefinition.observed(
    id = "SearchBoxTextAdHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallSearchBoxTextAdBlock(settings) },
) { cl, settings ->
    SearchBoxContract.resolveSearchBoxTextAdSymbols(cl, symbols)?.let { targets ->
        SearchBoxTextAdHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallSearchBoxTextAdBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isSearchBoxTextAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT)
}
