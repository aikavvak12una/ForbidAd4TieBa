package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.symbol.contract.BannerAdContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val BannerAdFeature = FeatureDefinition.single(
    id = "BannerAdHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings ->
        settings.isFeedAdBlockEnabled && available(HookFeatureKey.BLOCK_AD_FEED) &&
            !symbols[BannerAdContract.bannerAdConversionMethodSpec].isNullOrBlank()
    },
) { cl, _ ->
    BannerAdContract.resolveBannerAdSymbols(cl, symbols)?.let(BannerAdHook::hook)
        ?: InstallOutcome.skipped("feed banner source targets unavailable")
}
