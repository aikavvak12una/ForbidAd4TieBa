package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.InputMemeBarContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val InputMemeBarBlockFeature = FeatureDefinition.observed(
    id = "InputMemeBarBlockHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallInputMemeBarBlock(settings) },
) { cl, settings ->
    InputMemeBarContract.resolveInputMemeBarSymbols(cl, symbols)?.let { targets ->
        InputMemeBarBlockHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallInputMemeBarBlock(settings: SettingsSnapshot): Boolean {
    return isMain &&
        settings.isInputMemeBarHidden &&
        available(HookFeatureKey.HIDE_INPUT_MEME_BAR)
}
