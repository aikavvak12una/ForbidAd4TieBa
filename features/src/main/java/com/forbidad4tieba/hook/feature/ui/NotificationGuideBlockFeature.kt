package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.symbol.contract.DefaultPopupsContract

internal val NotificationGuideBlockFeature = FeatureDefinition.observed(
    id = "NotificationGuideBlockHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
) { cl, settings ->
    DefaultPopupsContract.resolveNotificationGuideSymbols(cl, symbols)?.let(NotificationGuideBlockHook::hook)
}
