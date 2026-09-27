package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.FeedContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val FeedAdFeature = FeatureDefinition.single(
    id = "FeedAdHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallFeedListAdBlock(settings) || canInstallCustomPostFilter(settings) },
) { cl, settings ->
    FeedContract.resolveFeedAdSymbols(
        cl = cl,
        symbols = symbols,
        includeCustomPostFilter = canInstallCustomPostFilter(settings),
    )?.let { targets ->
        FeedAdHook.hook(targets)
    } ?: InstallOutcome.skipped("feed list targets unavailable")
}

internal fun HookInstallContext.canInstallFeedListAdBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isFeedAdBlockEnabled || settings.isStrategyAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_FEED)
}

internal fun HookInstallContext.canInstallCustomPostFilter(settings: SettingsSnapshot): Boolean {
    return settings.isCustomPostFilterEnabled && available(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER)
}
