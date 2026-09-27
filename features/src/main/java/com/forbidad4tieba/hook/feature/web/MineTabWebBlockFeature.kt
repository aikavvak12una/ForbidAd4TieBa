package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val MineTabWebBlockFeature = FeatureDefinition.observed(
    id = "MineTabWebBlockHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallMineTabWebBlock(settings) },
) { cl, settings ->
    MineTabWebBlockHook.hook(cl, symbols)
}

internal fun HookInstallContext.canInstallMineTabWebBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isMineTabWebAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_MINE_TAB_WEB)
}
