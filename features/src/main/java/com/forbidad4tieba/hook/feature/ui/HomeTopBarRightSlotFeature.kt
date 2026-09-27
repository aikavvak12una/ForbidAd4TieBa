package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.HomeRightSlotContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val HomeTopBarRightSlotFeature = FeatureDefinition.observed(
    id = "HomeTopBarRightSlotHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallHomeTopBarAdBlock(settings) },
) { cl, settings ->
    HomeRightSlotContract.resolveHomeTopBarRightSlotSymbols(cl, symbols)?.let { targets ->
        HomeTopBarRightSlotHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallHomeTopBarAdBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isHomeTopBarAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_HOME_TOP_BAR)
}
