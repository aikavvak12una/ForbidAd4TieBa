package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.HomeBottomEasterEggContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val HomeBottomEasterEggAdFeature = FeatureDefinition.observed(
    id = "HomeBottomEasterEggAdHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallHomeBottomEasterEggAdBlock(settings) },
) { cl, settings ->
    HomeBottomEasterEggContract.resolveHomeBottomEasterEggAdSymbols(cl, symbols)?.let { targets ->
        HomeBottomEasterEggAdHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallHomeBottomEasterEggAdBlock(settings: SettingsSnapshot): Boolean {
    return isMain && (settings.isHomeBottomEasterEggAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_HOME_BOTTOM_EASTER_EGG)
}
