package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.symbol.contract.PbBottomBannerContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbBottomEnterBarHotTopicGuideFeature = FeatureDefinition.observed(
    id = "PbBottomEnterBarHook.HotTopicGuide",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPbBottomEnterBarHotTopicGuide() },
) { cl, settings ->
    PbBottomBannerContract.resolvePbBottomEnterBarHotTopicGuideSymbols(cl, symbols)?.let { targets ->
        PbBottomEnterBarHook.hookHotTopicGuide(targets)
    }
}

internal val PbBottomEnterBarStableFeature = FeatureDefinition.observed(
    id = "PbBottomEnterBarHook.Stable",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPbBottomEnterBarStable() },
) { cl, settings ->
    PbBottomBannerContract.resolvePbBottomEnterBarStableSymbols(cl, symbols)?.let { targets ->
        PbBottomEnterBarHook.hookStable(targets)
    }
}

internal fun HookInstallContext.canInstallPbBottomEnterBarStable(): Boolean {
    return isMain &&
        available(HookFeatureKey.HIDE_PB_BOTTOM_BANNER) &&
        PbBottomBannerContract.isStableReady(symbols)
}

internal fun HookInstallContext.canInstallPbBottomEnterBarHotTopicGuide(): Boolean {
    return isMain &&
        available(HookFeatureKey.HIDE_PB_BOTTOM_BANNER) &&
        PbBottomBannerContract.isHotTopicGuideReady(symbols)
}
