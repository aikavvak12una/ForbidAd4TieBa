package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.ForumPageAdContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val ForumPageAdBlockFeature = FeatureDefinition.single(
    id = "ForumPageAdBlockHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallForumPageAdBlock(settings) },
) { cl, settings ->
    ForumPageAdContract.resolveForumPageAdBlockSymbols(cl, symbols)?.let(ForumPageAdBlockHook::hook)
        ?: InstallOutcome.skipped("resolved targets unavailable")
}

internal fun HookInstallContext.canInstallForumPageAdBlock(settings: SettingsSnapshot): Boolean {
    return isMain &&
        (settings.isForumPageAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_FORUM_PAGE)
}
