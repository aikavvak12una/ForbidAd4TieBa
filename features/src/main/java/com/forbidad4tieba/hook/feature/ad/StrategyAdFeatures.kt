package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.StrategyAdContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val StrategyAdSymbolsFeature = FeatureDefinition.observed(
    id = "StrategyAdHook.symbols",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallStrategyAdBlock(settings) },
) { cl, settings ->
    StrategyAdContract.resolveStrategyAdSymbols(cl, symbols)?.let { targets ->
        StrategyAdHook.hookWithSymbols(targets)
    }
}

internal val StrategyAdStaticFeature = FeatureDefinition.observed(
    id = "StrategyAdHook.static",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isStrategyAdBlockEnabled || settings.isAdSdkComponentsDisabled || settings.isApsarasScheduleDisabled },
) { cl, settings ->
    StrategyAdHook.hookStatic(
        cl = cl,
        enableAccountData = settings.isStrategyAdBlockEnabled,
        enableSwitchManager = true,
    )
}

internal fun HookInstallContext.canInstallStrategyAdBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isStrategyAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_STRATEGY)
}
