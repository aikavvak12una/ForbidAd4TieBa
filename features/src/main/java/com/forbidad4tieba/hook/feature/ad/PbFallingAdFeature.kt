package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.PbFallingContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbFallingAdFeature = FeatureDefinition.observed(
    id = "PbFallingAdHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPbFallingAdBlock(settings) },
) { cl, settings ->
    PbFallingContract.resolvePbFallingAdSymbols(cl, symbols)?.let { targets ->
        PbFallingAdHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallPbFallingAdBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isPostPageAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_POST_PAGE) && PbFallingContract.pathStatus(symbols).isSupported()
}
