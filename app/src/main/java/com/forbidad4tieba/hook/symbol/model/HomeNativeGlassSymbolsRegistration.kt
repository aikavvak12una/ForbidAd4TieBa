package com.forbidad4tieba.hook.symbol.model

internal data class HomeNativeGlassScanSymbols(
    val subPbNextPageMoreViewId: Int?,
    val pbReplyTitleDividerViewId: Int?,
    val dynamicBackgroundColorIds: List<Int>,
    val sortSwitchBackgroundPaintField: String?,
    val sortSwitchSlideDrawMethod: String?,
    val sortSwitchSlidePathField: String?,
    val enterForumCapsuleControllerClass: String?,
    val enterForumCapsuleInitMethod: String?,
    val enterForumCapsuleRefreshMethod: String?,
    val enterForumCapsuleViewField: String?,
    val enterForumCapsuleTitleField: String?,
    val hostDarkModeMoreActivityClass: String?,
    val hostDarkModeControllerField: String?,
    val hostDarkModeSwitchGetterMethod: String?,
    val hostDarkModeSwitchStateField: String?,
    val hostDarkModeSwitchSetOnMethod: String?,
    val hostDarkModeSwitchSetOffMethod: String?,
    val hostDarkModeSwitchCallbackMethod: String?,
    val pbCommonLayoutPreloaderGetOrDefaultMethod: String?,
)

internal fun HookSymbolsBuilder.applyHomeNativeGlassScan(scan: HomeNativeGlassScanSymbols) {
    homeNativeGlassSubPbNextPageMoreViewId = scan.subPbNextPageMoreViewId
    homeNativeGlassPbReplyTitleDividerViewId = scan.pbReplyTitleDividerViewId
    homeNativeGlassDynamicBackgroundColorIds = scan.dynamicBackgroundColorIds
    homeNativeGlassSortSwitchBackgroundPaintField = scan.sortSwitchBackgroundPaintField
    homeNativeGlassSortSwitchSlideDrawMethod = scan.sortSwitchSlideDrawMethod
    homeNativeGlassSortSwitchSlidePathField = scan.sortSwitchSlidePathField
    homeNativeGlassEnterForumCapsuleControllerClass = scan.enterForumCapsuleControllerClass
    homeNativeGlassEnterForumCapsuleInitMethod = scan.enterForumCapsuleInitMethod
    homeNativeGlassEnterForumCapsuleRefreshMethod = scan.enterForumCapsuleRefreshMethod
    homeNativeGlassEnterForumCapsuleViewField = scan.enterForumCapsuleViewField
    homeNativeGlassEnterForumCapsuleTitleField = scan.enterForumCapsuleTitleField
    homeNativeGlassHostDarkModeMoreActivityClass = scan.hostDarkModeMoreActivityClass
    homeNativeGlassHostDarkModeControllerField = scan.hostDarkModeControllerField
    homeNativeGlassHostDarkModeSwitchGetterMethod = scan.hostDarkModeSwitchGetterMethod
    homeNativeGlassHostDarkModeSwitchStateField = scan.hostDarkModeSwitchStateField
    homeNativeGlassHostDarkModeSwitchSetOnMethod = scan.hostDarkModeSwitchSetOnMethod
    homeNativeGlassHostDarkModeSwitchSetOffMethod = scan.hostDarkModeSwitchSetOffMethod
    homeNativeGlassHostDarkModeSwitchCallbackMethod = scan.hostDarkModeSwitchCallbackMethod
    pbCommonLayoutPreloaderGetOrDefaultMethod = scan.pbCommonLayoutPreloaderGetOrDefaultMethod
}
