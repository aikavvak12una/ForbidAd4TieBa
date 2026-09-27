package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.OriginalImageContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val DefaultOriginalImageFeature = FeatureDefinition.observed(
    id = "DefaultOriginalImageHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.IMAGE_VIEWER,
    enabled = { settings -> canInstallDefaultOriginalImage(settings) },
) { cl, settings ->
    OriginalImageContract.resolveDefaultOriginalImageSymbols(cl, symbols)?.let { targets ->
        DefaultOriginalImageHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallDefaultOriginalImage(settings: SettingsSnapshot): Boolean {
    return isImageViewerProcess &&
        settings.isDefaultOriginalImageEnabled &&
        available(HookFeatureKey.DEFAULT_ORIGINAL_IMAGE)
}
