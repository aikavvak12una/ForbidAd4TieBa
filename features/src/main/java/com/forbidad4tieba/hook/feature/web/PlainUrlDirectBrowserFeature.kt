package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.feature.shared.canInstallCommentAvatarDirectProfile
import com.forbidad4tieba.hook.symbol.contract.MountCardContract
import com.forbidad4tieba.hook.symbol.contract.PlainUrlContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal fun plainUrlDirectBrowserFeature(
    openUserProfile: (android.content.Context?, String?) -> Boolean,
) = FeatureDefinition.observed(
    id = "PlainUrlDirectBrowserHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallSystemBrowser(settings) || canInstallCommentAvatarDirectProfile(settings) },
) { cl, settings ->
    val systemBrowser = canInstallSystemBrowser(settings)
    val spanTargets = PlainUrlContract.resolvePlainUrlClickableSpanSymbols(cl, symbols)
    val messageTarget = PlainUrlContract.resolvePlainUrlMessageDispatchSymbols(cl, symbols)
    val browserHelperTargets = if (systemBrowser) {
        PlainUrlContract.resolvePlainUrlBrowserHelperSymbols(cl, symbols)
    } else {
        null
    }
    val mountCardTargets = if (systemBrowser) {
        MountCardContract.resolveMountCardLinkLayoutSymbols(cl, symbols)
    } else {
        null
    }
    val clickSpanMarkerField = PlainUrlContract.resolvePlainUrlClickSpanMarkerField(cl)
    val targets = PlainUrlDirectBrowserHook.RuntimeTargets(
        spanTargets = spanTargets,
        messageTarget = messageTarget,
        browserHelperTargets = browserHelperTargets,
        mountCardTargets = mountCardTargets,
        clickSpanMarkerField = clickSpanMarkerField,
        isClickMessageCmd = PlainUrlContract::isPlainUrlClickMessageCmd,
        resolveMessageDataSymbols = PlainUrlContract::resolvePlainUrlMessageDataSymbols,
        openUserProfile = openUserProfile,
    )
    PlainUrlDirectBrowserHook.hook(targets)
}

internal fun HookInstallContext.canInstallSystemBrowser(settings: SettingsSnapshot): Boolean {
    return settings.isOpenWebLinkInSystemBrowserEnabled &&
        available(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER)
}
