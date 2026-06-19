package com.forbidad4tieba.hook.symbol.model

data class HomeSymbols(
    val tab: HomeTabSymbolsGroup = HomeTabSymbolsGroup(),
    val search: HomeSearchSymbolsGroup = HomeSearchSymbolsGroup(),
    val personalization: HomePersonalizationSymbolsGroup = HomePersonalizationSymbolsGroup(),
    val rightSlot: HomeRightSlotSymbolsGroup = HomeRightSlotSymbolsGroup(),
    val mainTab: MainTabSymbolsGroup = MainTabSymbolsGroup(),
    val nativeGlass: HomeNativeGlassSymbolsGroup = HomeNativeGlassSymbolsGroup(),
)

data class HomeTabSymbolsGroup(
    val homeTabClass: String? = null,
    val homeTabRebuildMethod: String? = null,
    val homeTabListField: String? = null,
    val item: HomeTabItemSymbolsGroup = HomeTabItemSymbolsGroup(),
    val itemMain: HomeTabItemMainSymbolsGroup = HomeTabItemMainSymbolsGroup(),
)

data class HomeTabItemMainSymbolsGroup(
    val homeTabItemMainSetterMethod: String? = null,
    val homeTabItemMainIntField: String? = null,
    val homeTabItemMainBooleanField: String? = null,
)

data class HomeTabItemSymbolsGroup(
    val homeTabItemTypeField: String? = null,
    val homeTabItemCodeField: String? = null,
    val homeTabItemNameField: String? = null,
    val homeTabItemUrlField: String? = null,
)

data class HomeSearchSymbolsGroup(
    val searchBoxViewClass: String? = null,
    val searchBoxSetHintMethod: String? = null,
    val homeSearchBoxOwnerClass: String? = null,
    val homeSearchBoxInitMethod: String? = null,
    val homeSearchBoxGetterMethod: String? = null,
)

data class HomePersonalizationSymbolsGroup(
    val homePersonalizeAnchorClasses: List<String>? = null,
)

data class HomeRightSlotSymbolsGroup(
    val homeRightSlotClass: String? = null,
    val homeRightSlotStateMethods: List<String>? = null,
)

data class MainTabSymbolsGroup(
    val mainTabDataClass: String? = null,
    val mainTabAddMethod: String? = null,
    val mainTabGetListMethod: String? = null,
    val mainTabDelegateGetStructureMethod: String? = null,
    val structure: MainTabStructureSymbolsGroup = MainTabStructureSymbolsGroup(),
)

data class MainTabStructureSymbolsGroup(
    val mainTabStructureTypeField: String? = null,
    val mainTabStructureDynamicIconField: String? = null,
    val mainTabStructureFragmentField: String? = null,
)

data class HomeNativeGlassSymbolsGroup(
    val topChrome: HomeNativeGlassTopChromeSymbolsGroup = HomeNativeGlassTopChromeSymbolsGroup(),
    val subPbNextPage: HomeNativeGlassSubPbNextPageSymbolsGroup =
        HomeNativeGlassSubPbNextPageSymbolsGroup(),
    val sortSwitch: HomeNativeGlassSortSwitchSymbolsGroup = HomeNativeGlassSortSwitchSymbolsGroup(),
    val enterForumCapsule: HomeNativeGlassEnterForumCapsuleSymbolsGroup = HomeNativeGlassEnterForumCapsuleSymbolsGroup(),
    val hostDarkModeSwitch: HomeNativeGlassHostDarkModeSwitchSymbolsGroup =
        HomeNativeGlassHostDarkModeSwitchSymbolsGroup(),
)

data class HomeNativeGlassTopChromeSymbolsGroup(
    val homeNativeGlassTopChromeTabSelectedMethodSpecs: List<String>? = null,
)

data class HomeNativeGlassSubPbNextPageSymbolsGroup(
    val homeNativeGlassSubPbSetNextPageMethod: String? = null,
    val homeNativeGlassSubPbSetNextPageParamType: String? = null,
)

data class HomeNativeGlassSortSwitchSymbolsGroup(
    val homeNativeGlassSortSwitchBackgroundPaintField: String? = null,
    val homeNativeGlassSortSwitchSlideDrawMethod: String? = null,
    val homeNativeGlassSortSwitchSlidePathField: String? = null,
)

data class HomeNativeGlassEnterForumCapsuleSymbolsGroup(
    val homeNativeGlassEnterForumCapsuleControllerClass: String? = null,
    val homeNativeGlassEnterForumCapsuleInitMethod: String? = null,
    val homeNativeGlassEnterForumCapsuleRefreshMethod: String? = null,
    val homeNativeGlassEnterForumCapsuleViewField: String? = null,
    val homeNativeGlassEnterForumCapsuleTitleField: String? = null,
)

data class HomeNativeGlassHostDarkModeSwitchSymbolsGroup(
    val homeNativeGlassHostDarkModeMoreActivityClass: String? = null,
    val homeNativeGlassHostDarkModeControllerField: String? = null,
    val homeNativeGlassHostDarkModeSwitchGetterMethod: String? = null,
    val homeNativeGlassHostDarkModeSwitchStateField: String? = null,
    val homeNativeGlassHostDarkModeSwitchSetOnMethod: String? = null,
    val homeNativeGlassHostDarkModeSwitchSetOffMethod: String? = null,
    val homeNativeGlassHostDarkModeSwitchCallbackMethod: String? = null,
)
