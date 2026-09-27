package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.PbLikeAutoReplyContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbLikeAutoReplyFeature = FeatureDefinition.observed(
    id = "PbLikeAutoReplyHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPbLikeAutoReply(settings) },
) { cl, settings ->
    PbLikeAutoReplyContract.resolvePbLikeAutoReplySymbols(cl, symbols)?.let { targets ->
        PbLikeAutoReplyHook.hook(targets, settings.pbLikeAutoReplyText)
    }
}

internal fun HookInstallContext.canInstallPbLikeAutoReply(settings: SettingsSnapshot): Boolean {
    return settings.isPbLikeAutoReplyEnabled &&
        settings.pbLikeAutoReplyText.isNotBlank() &&
        available(HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY)
}
