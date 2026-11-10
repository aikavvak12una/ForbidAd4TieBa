package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.symbol.contract.ForumTopShiftContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val ForumNativeTopShiftBlockFeature = FeatureDefinition.single(
    id = "ForumNativeTopShiftBlockHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallForumNativeTopShift() },
) { cl, settings ->
    val targets = ForumTopShiftContract.resolveForumNativeTopShiftSymbols(cl, symbols)
        ?: return@single InstallOutcome(
            InstallState.FAILED,
            reason = "ForumTopShift cached targets could not be restored; rescan required",
        )
    ForumNativeTopShiftBlockHook.hook(targets)
    null
}

internal fun HookInstallContext.canInstallForumNativeTopShift(): Boolean = available(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT)
