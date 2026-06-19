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
        topChromeTabSelectedMethods = symbols.homeNativeGlassTopChromeTabSelectedMethodSpecs
            .orEmpty()
            .mapNotNull(::parseHomeTopChromeTabSelectedTarget),
        subPbSetNextPageTarget = parseHomeSubPbSetNextPageTarget(
            methodName = symbols.homeNativeGlassSubPbSetNextPageMethod,
            parameterTypeName = symbols.homeNativeGlassSubPbSetNextPageParamType,
        ),
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
