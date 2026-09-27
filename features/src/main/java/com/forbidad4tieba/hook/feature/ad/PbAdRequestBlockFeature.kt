package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.PbAdRequestContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbAdRequestBlockFeature = FeatureDefinition.single(
    id = "PbAdRequestBlockHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPbAdRequestBlock(settings) },
) { cl, settings ->
    PbAdRequestContract.resolvePbAdRequestBlockSymbols(cl, symbols)?.let { targets ->
        PbAdRequestBlockHook.hook(targets)
    } ?: InstallOutcome.skipped("symbols unavailable")
}

internal fun HookInstallContext.canInstallPbAdRequestBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isPostPageAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_POST_PAGE)
}
