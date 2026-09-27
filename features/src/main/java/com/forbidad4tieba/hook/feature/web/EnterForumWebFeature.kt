package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.EnterForumContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val EnterForumWebFeature = FeatureDefinition.observed(
    id = "EnterForumWebHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallEnterForumWeb(settings) },
) { cl, settings ->
    EnterForumContract.resolveEnterForumWebSymbols(cl, symbols)?.let { targets ->
        EnterForumWebHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallEnterForumWeb(settings: SettingsSnapshot): Boolean {
    return settings.isEnterForumWebFilterEnabled && available(HookFeatureKey.FILTER_ENTER_FORUM_WEB)
}
