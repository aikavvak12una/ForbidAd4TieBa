package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.symbol.contract.CollectionContract

internal val CollectionSearchFeature = FeatureDefinition.observed(
    id = "CollectionSearchHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallCollectionSearch() },
) { cl, settings ->
    CollectionContract.resolveCollectionSearchSymbols(cl, symbols)?.let { targets ->
        CollectionSearchHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallCollectionSearch(): Boolean {
    return isMain && CollectionContract.isSearchComplete(symbols)
}
