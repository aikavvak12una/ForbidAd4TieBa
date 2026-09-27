package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.symbol.contract.ForumTopShiftContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val ForumNativeTopShiftBlockFeature = FeatureDefinition.observed(
    id = "ForumNativeTopShiftBlockHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallForumNativeTopShift() },
) { cl, settings ->
    ForumTopShiftContract.resolveForumNativeTopShiftSymbols(cl, symbols)?.let { targets ->
        ForumNativeTopShiftBlockHook.hook(targets)
    }
}

internal fun HookInstallContext.canInstallForumNativeTopShift(): Boolean = available(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT)
