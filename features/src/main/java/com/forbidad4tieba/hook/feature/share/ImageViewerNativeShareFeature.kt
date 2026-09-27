package com.forbidad4tieba.hook.feature.share

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.symbol.contract.ImageSharingContract

internal val ImageViewerNativeShareFeature = FeatureDefinition.observed(
    id = "ImageViewerNativeShareHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.IMAGE_VIEWER,
    enabled = { settings -> canInstallImageViewerNativeShare() },
) { cl, settings ->
    ImageSharingContract.resolveImageViewerNativeShareSymbols(cl, symbols)?.let { targets ->
        ImageViewerNativeShareHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallImageViewerNativeShare(): Boolean {
    return isImageViewerProcess &&
        ImageSharingContract.isNativeShareReady(symbols)
}
