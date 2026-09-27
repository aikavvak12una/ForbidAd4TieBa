package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.AutoRefreshContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val AutoRefreshFeature = FeatureDefinition.observed(
    id = "AutoRefreshHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallAutoRefresh(settings) },
) { cl, settings ->
    AutoRefreshContract.resolveAutoRefreshSymbols(cl, symbols)?.let { targets ->
        AutoRefreshHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallAutoRefresh(settings: SettingsSnapshot): Boolean {
    return settings.isAutoRefreshDisabled && available(HookFeatureKey.DISABLE_AUTO_REFRESH)
}
