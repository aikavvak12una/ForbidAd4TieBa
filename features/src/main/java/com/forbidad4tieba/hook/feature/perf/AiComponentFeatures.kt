package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.AiContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val AiComponentDisableFeature = FeatureDefinition.observed(
    id = "AiComponentDisableHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallMainAiComponents(settings) },
) { cl, settings ->
    AiContract.resolveAiComponentSymbols(cl, symbols)?.let { targets ->
        AiComponentDisableHook.hook(targets)
    }
}

internal val AiImageViewerJumpButtonFeature = FeatureDefinition.observed(
    id = "AiComponentDisableHook.imageViewerJumpButton",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.IMAGE_VIEWER_REMOTE,
    enabled = { settings -> canInstallImageViewerAiJumpButton(settings) },
) { cl, settings ->
    AiContract.resolveAiImageViewerJumpButtonSymbols(cl, symbols)?.let { targets ->
        AiComponentDisableHook.hookImageViewerJumpButton(targets)
    }
}

internal fun HookInstallContext.canInstallImageViewerAiJumpButton(settings: SettingsSnapshot): Boolean {
    return isImageViewerRemote &&
        settings.isAiComponentsDisabled &&
        AiContract.isReady(symbols)
}

internal fun HookInstallContext.canInstallMainAiComponents(settings: SettingsSnapshot): Boolean {
    return isMain &&
        settings.isAiComponentsDisabled &&
        available(HookFeatureKey.DISABLE_AI_COMPONENTS)
}
