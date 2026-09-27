package com.forbidad4tieba.hook.feature.diagnostic

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.ReplyVisibilityContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val ReplyVisibilityProbeFeature = FeatureDefinition.observed(
    id = "ReplyVisibilityProbeHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallReplyVisibilityProbe(settings) },
) { cl, settings ->
    ReplyVisibilityContract.resolveReplyVisibilityProbeSymbols(cl, symbols)?.let { targets ->
        ReplyVisibilityProbeHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallReplyVisibilityProbe(settings: SettingsSnapshot): Boolean {
    return isMain &&
        settings.isReplyVisibilityProbeEnabled &&
        available(HookFeatureKey.VERIFY_REPLY_AFTER_POST)
}
