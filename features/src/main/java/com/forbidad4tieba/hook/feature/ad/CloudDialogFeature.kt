package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.symbol.contract.CloudDialogContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val CloudDialogFeature = FeatureDefinition.single(
    id = "CloudDialogHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings ->
        settings.isCloudDialogAdBlockEnabled && available(HookFeatureKey.BLOCK_AD_CLOUD_DIALOGS)
    },
) { cl, _ ->
    CloudDialogContract.resolveCloudDialogSymbols(cl, symbols)?.let(CloudDialogHook::hook)
        ?: InstallOutcome.skipped("cloud admission targets unavailable")
}
