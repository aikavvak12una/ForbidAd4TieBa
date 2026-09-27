package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.PbGestureScaleContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val PbDisableGestureFontScaleFeature = FeatureDefinition.observed(
    id = "PbDisableGestureFontScaleHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallPbGestureFontScale(settings) },
) { cl, settings ->
    PbGestureScaleContract.resolvePbGestureScaleSymbols(cl, symbols)?.let { targets ->
        PbDisableGestureFontScaleHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallPbGestureFontScale(settings: SettingsSnapshot): Boolean {
    return settings.isPbGestureFontScaleDisabled &&
        available(HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE)
}
