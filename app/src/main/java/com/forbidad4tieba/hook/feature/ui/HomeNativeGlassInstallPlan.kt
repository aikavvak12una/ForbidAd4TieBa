package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal data class HomeNativeGlassFeedInstallPlan(
    val bindMethodName: String?,
    val hasHomePersonalizePageClass: Boolean,
) {
    val hasHomeFeedTargets: Boolean
        get() = bindMethodName != null && hasHomePersonalizePageClass

    val shouldLogMissingBindMethod: Boolean
        get() = hasHomeFeedTargets && bindMethodName == null

    fun formatSkipLog(tag: String): String {
        return "$tag home feed glass SKIP: pageClass=$hasHomePersonalizePageClass, " +
            "feedCardBind=${bindMethodName != null}"
    }
}

internal fun resolveHomeNativeGlassFeedInstallPlan(symbols: HookSymbols): HomeNativeGlassFeedInstallPlan {
    val bindMethodName = symbols.feedCardBindMethod?.takeIf { it.isNotBlank() }
    val hasHomePersonalizePageClass = symbols.homePersonalizeAnchorClasses
        .orEmpty()
        .contains(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS)
    return HomeNativeGlassFeedInstallPlan(
        bindMethodName = bindMethodName,
        hasHomePersonalizePageClass = hasHomePersonalizePageClass,
    )
}

internal data class HomeNativeGlassInstallReport(
    val pageConstructors: Int,
    val bindHooks: Int,
    val touchHooks: Int,
    val componentHooks: Int,
    val pbCommentHooks: Int,
    val dynamicColorHooks: Int,
    val sortSwitchHooks: Int,
    val enterForumCapsuleHooks: Int,
    val hostSkinTypeHooks: Int,
    val topTabObservers: Int,
    val bottomTabDynamicTintHooks: Int,
    val chromeDirectBackgroundBlock: Int,
    val searchBoxHooks: Int,
    val bindMethodName: String?,
) {
    val hasRequiredHooks: Boolean
        get() = !((pageConstructors == 0 || bindHooks == 0) && pbCommentHooks == 0)

    fun formatNoHooksLog(tag: String): String {
        return "$tag no hooks installed: pages=$pageConstructors " +
            "cards=$bindHooks pbComments=$pbCommentHooks"
    }

    fun formatInstalledLog(tag: String): String {
        return "$tag hook INSTALLED: pages=$pageConstructors, " +
            "cards=$bindHooks, touches=$touchHooks, " +
            "components=$componentHooks, pbComments=$pbCommentHooks, " +
            "dynamicColors=$dynamicColorHooks, sortSwitch=$sortSwitchHooks, " +
            "enterForumCapsule=$enterForumCapsuleHooks, " +
            "hostSkinType=$hostSkinTypeHooks, " +
            "topTabObservers=$topTabObservers, " +
            "bottomTabDynamicTint=$bottomTabDynamicTintHooks, " +
            "chromeDirectBackgroundBlock=$chromeDirectBackgroundBlock, searchBox=$searchBoxHooks, " +
            "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.$bindMethodName"
    }
}
