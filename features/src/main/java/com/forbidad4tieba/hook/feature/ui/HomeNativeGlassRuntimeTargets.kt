package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal fun resolveHomeNativeGlassRuntimeTargets(symbols: HookSymbols): RuntimeTargets {
    return RuntimeTargets(
        homeTabItemTypeField = symbols[HomeTabsContract.homeTabItemTypeField]?.takeIf { it.isNotBlank() },
        homeTabItemCodeField = symbols[HomeTabsContract.homeTabItemCodeField]?.takeIf { it.isNotBlank() },
        searchBoxClass = symbols[SearchBoxContract.searchBoxViewClass]?.takeIf { it.isNotBlank() },
        homeSearchBoxOwnerClass = symbols[SearchBoxContract.homeSearchBoxOwnerClass]?.takeIf { it.isNotBlank() },
        homeSearchBoxInitMethod = symbols[SearchBoxContract.homeSearchBoxInitMethod]?.takeIf { it.isNotBlank() },
        homeSearchBoxGetterMethod = symbols[SearchBoxContract.homeSearchBoxGetterMethod]?.takeIf { it.isNotBlank() },
        subPbNextPageMoreViewId = symbols[NativeGlassContract.homeNativeGlassSubPbNextPageMoreViewId]?.takeIf { it != 0 },
        pbReplyTitleDividerViewId = symbols[NativeGlassContract.homeNativeGlassPbReplyTitleDividerViewId]?.takeIf { it != 0 },
        dynamicBackgroundColorIds = symbols[NativeGlassContract.homeNativeGlassDynamicBackgroundColorIds]
            .filter { it != 0 }
            .toSet(),
        topChromeTabSelectedMethods = symbols[NativeGlassContract.homeNativeGlassTopChromeTabSelectedMethodSpecs]
            .orEmpty()
            .mapNotNull(::parseHomeTopChromeTabSelectedTarget),
        subPbSetNextPageTarget = parseHomeSubPbSetNextPageTarget(
            methodName = symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageMethod],
            parameterTypeName = symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageParamType],
        ),
        sortSwitchBackgroundPaintField =
            symbols[NativeGlassContract.homeNativeGlassSortSwitchBackgroundPaintField]?.takeIf { it.isNotBlank() },
        sortSwitchSlideDrawMethod =
            symbols[NativeGlassContract.homeNativeGlassSortSwitchSlideDrawMethod]?.takeIf { it.isNotBlank() },
        sortSwitchSlidePathField =
            symbols[NativeGlassContract.homeNativeGlassSortSwitchSlidePathField]?.takeIf { it.isNotBlank() },
        enterForumCapsuleControllerClass =
            symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleControllerClass]?.takeIf { it.isNotBlank() },
        enterForumCapsuleInitMethod =
            symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleInitMethod]?.takeIf { it.isNotBlank() },
        enterForumCapsuleRefreshMethod =
            symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleRefreshMethod]?.takeIf { it.isNotBlank() },
        enterForumCapsuleViewField =
            symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleViewField]?.takeIf { it.isNotBlank() },
        enterForumCapsuleTitleField =
            symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleTitleField]?.takeIf { it.isNotBlank() },
        pbCommonLayoutPreloaderGetOrDefaultMethod =
            symbols[NativeGlassContract.pbCommonLayoutPreloaderGetOrDefaultMethod]?.takeIf { it.isNotBlank() },
    )
}

private fun parseHomeTopChromeTabSelectedTarget(spec: String): HomeTopChromeTabSelectedTarget? {
    val sep = spec.indexOf('#')
    if (sep <= 0 || sep == spec.lastIndex) return null
    val className = spec.substring(0, sep).trim()
    val methodName = spec.substring(sep + 1).trim()
    if (className.isEmpty() || methodName.isEmpty()) return null
    return HomeTopChromeTabSelectedTarget(className = className, methodName = methodName)
}

private fun parseHomeSubPbSetNextPageTarget(
    methodName: String?,
    parameterTypeName: String?,
): HomeSubPbSetNextPageTarget? {
    val method = methodName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parameterType = parameterTypeName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return HomeSubPbSetNextPageTarget(methodName = method, parameterTypeName = parameterType)
}
