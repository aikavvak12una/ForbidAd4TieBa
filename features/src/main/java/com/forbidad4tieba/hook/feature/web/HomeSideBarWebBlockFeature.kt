package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val HomeSideBarWebBlockFeature = FeatureDefinition.observed(
    id = "HomeSideBarWebBlockHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallHomeSideBarWebBlock(settings) },
) { cl, settings ->
    HomeSideBarWebBlockHook.hook(cl, symbols)
}

internal fun HookInstallContext.canInstallHomeSideBarWebBlock(settings: SettingsSnapshot): Boolean {
    return (settings.isHomeSideBarWebAdBlockEnabled) && available(HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB)
}
