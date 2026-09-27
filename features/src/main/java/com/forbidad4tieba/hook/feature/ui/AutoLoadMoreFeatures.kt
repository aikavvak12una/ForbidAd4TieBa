package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.AutoLoadMoreContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbCommentAutoLoadFeature = FeatureDefinition.observed(
    id = "PbCommentAutoLoadHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallAutoLoadMore(settings) },
) { cl, settings ->
    AutoLoadMoreContract.resolvePbCommentAutoLoadSymbols(cl, symbols)?.let { targets ->
        PbCommentAutoLoadHook.hook(targets)
    }
}

internal val AutoLoadMoreFeature = FeatureDefinition.observed(
    id = "AutoLoadMoreHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallAutoLoadMore(settings) },
) { cl, settings ->
    AutoLoadMoreContract.resolveAutoLoadMoreSymbols(cl, symbols)?.let { targets ->
        AutoLoadMoreHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallAutoLoadMore(settings: SettingsSnapshot): Boolean {
    return settings.isAutoLoadMoreEnabled && available(HookFeatureKey.AUTO_LOAD_MORE)
}
