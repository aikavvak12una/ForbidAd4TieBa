package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.feature.shared.canInstallHomeTopTabs

internal val FollowedTabWebFeature = FeatureDefinition.observed(
    id = "FollowedTabWebHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallFollowedTabWeb(settings) },
) { cl, settings ->
    FollowedTabWebHook.hook(cl)
}

internal fun HookInstallContext.canInstallFollowedTabWeb(settings: SettingsSnapshot): Boolean {
    return canInstallHomeTopTabs(settings) && settings.isHomeTopTabFollowedEnabled
}
