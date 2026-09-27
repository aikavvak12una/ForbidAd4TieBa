package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.PbFirstFloorRecommendContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbFirstFloorRecommendBlockFeature = FeatureDefinition.observed(
    id = "PbFirstFloorRecommendBlockHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPbFirstFloorRecommendBlock(settings) },
) { cl, settings ->
    PbFirstFloorRecommendContract.resolvePbFirstFloorRecommendInsertSymbols(cl, symbols)
        ?.let { targets ->
            PbFirstFloorRecommendBlockHook.hook(targets)
        }
}

internal fun HookInstallContext.canInstallPbFirstFloorRecommendBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isPostPageAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_POST_PAGE) && PbFirstFloorRecommendContract.pathStatus(symbols).isSupported()
}
