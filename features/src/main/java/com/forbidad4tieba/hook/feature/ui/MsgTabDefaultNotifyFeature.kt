package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.MessageTabContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val MsgTabDefaultNotifyFeature = FeatureDefinition.observed(
    id = "MsgTabDefaultNotifyHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallDefaultNotifyTab(settings) },
) { cl, settings ->
    MessageTabContract.resolveMsgTabDefaultNotifySymbols(cl, symbols)?.let { targets ->
        MsgTabDefaultNotifyHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallDefaultNotifyTab(settings: SettingsSnapshot): Boolean {
    return settings.isDefaultNotifyTabEnabled && available(HookFeatureKey.DEFAULT_NOTIFY_TAB)
}
