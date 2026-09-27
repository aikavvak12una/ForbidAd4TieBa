package com.forbidad4tieba.hook.feature.share

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.ImageSharingContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val ShareTrackingParamCleanerFeature = FeatureDefinition.observed(
    id = "ShareTrackingParamCleanerHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallShareTrackingCleaner(settings) },
) { cl, settings ->
    ImageSharingContract.resolveShareTrackingParamCleanerSymbols(cl, symbols)?.let { targets ->
        ShareTrackingParamCleanerHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallShareTrackingCleaner(settings: SettingsSnapshot): Boolean {
    return settings.isCleanShareTrackingParamsEnabled &&
        available(HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS)
}
