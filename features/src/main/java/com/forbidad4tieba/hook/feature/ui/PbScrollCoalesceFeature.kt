package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.AutoLoadMoreContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbScrollCoalesceFeature = FeatureDefinition.observed(
    id = "PbScrollCoalesceHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPbScrollCoalesce(settings) },
) { cl, settings ->
    AutoLoadMoreContract.resolvePbScrollCoalesceSymbols(cl, symbols)?.let { targets ->
        PbScrollCoalesceHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallPbScrollCoalesce(settings: SettingsSnapshot): Boolean {
    return settings.isPbScrollCoalesceEnabled && available(HookFeatureKey.ENABLE_PB_SCROLL_COALESCE)
}
