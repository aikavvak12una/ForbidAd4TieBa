package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.PbEarlyAdContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbEarlyAdBlockFeature = FeatureDefinition.observed(
    id = "PbEarlyAdBlockHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPbEarlyAdBlock(settings) },
) { cl, settings ->
    PbEarlyAdContract.resolvePbEarlyAdBlockSymbols(cl, symbols)?.let { targets ->
        PbEarlyAdBlockHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallPbEarlyAdBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isPostPageAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_POST_PAGE) && PbEarlyAdContract.pathStatus(symbols).isSupported()
}
