package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.PostAdDataContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PostAdFeature = FeatureDefinition.observed(
    id = "PostAdHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPostAdBlock(settings) },
) { cl, settings ->
    PostAdDataContract.resolvePostAdDataFilterSymbols(cl, symbols)?.let { targets ->
        PostAdHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallPostAdBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isPostPageAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_POST_PAGE) && PostAdDataContract.pathStatus(symbols).isSupported()
}
