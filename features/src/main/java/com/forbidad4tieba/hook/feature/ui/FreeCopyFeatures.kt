package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.FreeCopyContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val FreeCopyNativeFeature = FeatureDefinition.observed(
    id = "FreeCopyHook.Native",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallFreeCopyNative(settings) },
) { cl, settings ->
    FreeCopyContract.resolveFreeCopyNativeSymbols(cl, symbols)?.let { targets ->
        FreeCopyHook.hookNative(targets)
    }
}

internal val FreeCopyCommentInjectionFeature = FeatureDefinition.observed(
    id = "FreeCopyHook.CommentInjection",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallFreeCopyCommentInjection(settings) },
) { cl, settings ->
    FreeCopyContract.resolveFreeCopyPopupSymbols(cl, symbols)?.let { targets ->
        FreeCopyHook.hookCommentInjection(targets)
    }
}

internal fun HookInstallContext.canInstallFreeCopyCommentInjection(settings: SettingsSnapshot): Boolean {
    return isMain &&
        settings.isFreeCopyEnabled &&
        settings.isFreeCopyCommentInjectionEnabled &&
        available(HookFeatureKey.FREE_COPY_COMMENT_INJECTION)
}

internal fun HookInstallContext.canInstallFreeCopyNative(settings: SettingsSnapshot): Boolean {
    if (!isMain || !settings.isFreeCopyEnabled) return false
    return (
        settings.isFreeCopyPostBodyEnabled &&
            available(HookFeatureKey.FREE_COPY_POST_BODY)
        ) ||
        (
            settings.isFreeCopyPostLongPressEnabled &&
                available(HookFeatureKey.FREE_COPY_POST_LONG_PRESS)
            ) ||
        (
            settings.isFreeCopyCommentDialogEnabled &&
                available(HookFeatureKey.FREE_COPY_COMMENT_DIALOG)
            )
}
