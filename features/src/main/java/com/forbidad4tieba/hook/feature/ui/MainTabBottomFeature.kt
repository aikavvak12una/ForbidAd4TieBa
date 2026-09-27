package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.MainTabsContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val MainTabBottomFeature = FeatureDefinition.observed(
    id = "MainTabBottomHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallBottomTabs(settings) },
) { cl, settings ->
    MainTabsContract.resolveMainTabBottomSymbols(cl, symbols)?.let { targets ->
        MainTabBottomHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallBottomTabs(settings: SettingsSnapshot): Boolean {
    return settings.isBottomTabsCustomEnabled &&
        available(HookFeatureKey.SIMPLIFY_BOTTOM_TABS)
}
