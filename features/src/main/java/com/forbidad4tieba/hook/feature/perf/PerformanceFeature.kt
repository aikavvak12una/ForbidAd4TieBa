package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallEntry
import com.forbidad4tieba.hook.symbol.contract.PerformanceContract

internal val PerformanceFeature = FeatureDefinition(
    id = "Performance",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.ANY,
) { context, settings ->
    val entries = ArrayList<HookInstallEntry>()
    if (context.isMain && settings.isCookieSyncDedupEnabled) {
        entries += HookInstallEntry.observed("CookieSyncDedupHook") { cl ->
            CookieSyncDedupHook.hook(PerformanceContract.resolvePerformanceAbSymbols(cl, context.symbols))
        }
    }
    if (context.isMain && settings.isAdSdkComponentsDisabled) {
        entries += HookInstallEntry("AdSdkInitBlockHook") { cl ->
            AdSdkInitBlockHook.hook(PerformanceContract.resolveRuntimeSymbols(cl, context.symbols))
        }
    }
    entries
}
