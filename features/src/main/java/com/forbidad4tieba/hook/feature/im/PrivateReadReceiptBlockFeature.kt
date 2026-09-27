package com.forbidad4tieba.hook.feature.im

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.PrivateReadReceiptContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PrivateReadReceiptBlockFeature = FeatureDefinition.single(
    id = "PrivateReadReceiptBlockHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPrivateReadReceipt(settings) },
) { cl, settings ->
    PrivateReadReceiptContract.resolvePrivateReadReceiptSymbols(cl, symbols)?.let { targets ->
        PrivateReadReceiptBlockHook.hook(targets)
    } ?: InstallOutcome.skipped("private read receipt targets unavailable")
}

internal fun HookInstallContext.canInstallPrivateReadReceipt(settings: SettingsSnapshot): Boolean {
    return settings.isPrivateReadReceiptInvisibleEnabled &&
        available(HookFeatureKey.PRIVATE_READ_RECEIPT_INVISIBLE)
}
