package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal fun resolveHomeNativeGlassRuntimeTargets(symbols: HookSymbols): RuntimeTargets {
    return RuntimeTargets(
        homeTabItemTypeField = symbols.homeTabItemTypeField?.takeIf { it.isNotBlank() },
        homeTabItemCodeField = symbols.homeTabItemCodeField?.takeIf { it.isNotBlank() },
        searchBoxClass = symbols.searchBoxViewClass?.takeIf { it.isNotBlank() },
        homeSearchBoxOwnerClass = symbols.homeSearchBoxOwnerClass?.takeIf { it.isNotBlank() },
        homeSearchBoxInitMethod = symbols.homeSearchBoxInitMethod?.takeIf { it.isNotBlank() },
        homeSearchBoxGetterMethod = symbols.homeSearchBoxGetterMethod?.takeIf { it.isNotBlank() },
        subPbNextPageMoreViewId = symbols.homeNativeGlassSubPbNextPageMoreViewId?.takeIf { it != 0 },
        pbReplyTitleDividerViewId = symbols.homeNativeGlassPbReplyTitleDividerViewId?.takeIf { it != 0 },
        dynamicBackgroundColorIds = symbols.homeNativeGlassDynamicBackgroundColorIds
            .filter { it != 0 }
            .toSet(),
        sortSwitchBackgroundPaintField =
            symbols.homeNativeGlassSortSwitchBackgroundPaintField?.takeIf { it.isNotBlank() },
        sortSwitchSlideDrawMethod =
            symbols.homeNativeGlassSortSwitchSlideDrawMethod?.takeIf { it.isNotBlank() },
        sortSwitchSlidePathField =
            symbols.homeNativeGlassSortSwitchSlidePathField?.takeIf { it.isNotBlank() },
        enterForumCapsuleControllerClass =
            symbols.homeNativeGlassEnterForumCapsuleControllerClass?.takeIf { it.isNotBlank() },
        enterForumCapsuleInitMethod =
            symbols.homeNativeGlassEnterForumCapsuleInitMethod?.takeIf { it.isNotBlank() },
        enterForumCapsuleRefreshMethod =
            symbols.homeNativeGlassEnterForumCapsuleRefreshMethod?.takeIf { it.isNotBlank() },
        enterForumCapsuleViewField =
            symbols.homeNativeGlassEnterForumCapsuleViewField?.takeIf { it.isNotBlank() },
        enterForumCapsuleTitleField =
            symbols.homeNativeGlassEnterForumCapsuleTitleField?.takeIf { it.isNotBlank() },
        pbCommonLayoutPreloaderGetOrDefaultMethod =
            symbols.pbCommonLayoutPreloaderGetOrDefaultMethod?.takeIf { it.isNotBlank() },
    )
}
