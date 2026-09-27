package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.symbol.contract.HistoryContract

internal val HistorySearchFeature = FeatureDefinition.observed(
    id = "HistorySearchHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallHistorySearch() },
) { cl, settings ->
    HistoryContract.resolveHistorySearchSymbols(cl, symbols)?.let { targets ->
        HistorySearchHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallHistorySearch(): Boolean {
    return isMain && HistoryContract.isSearchComplete(symbols)
}
