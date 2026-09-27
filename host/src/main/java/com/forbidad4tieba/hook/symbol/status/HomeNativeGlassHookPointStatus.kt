package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal fun collectHomeNativeGlassHookPointStatuses(symbols: HookSymbols): List<HookPointStatus> {
    val out = ArrayList<HookPointStatus>(10)

    fun has(value: String?): Boolean = !value.isNullOrBlank()
    fun has(value: Int?): Boolean = value != null && value != 0
    fun hasIntList(values: List<Int>?): Boolean = values.orEmpty().any { it != 0 }
    fun hasTopChromeTabSelectedSpecs(): Boolean {
        return symbols[NativeGlassContract.homeNativeGlassTopChromeTabSelectedMethodSpecs]
            .orEmpty()
            .any { spec ->
                val sep = spec.indexOf('#')
                sep > 0 && sep < spec.lastIndex
            }
    }
    fun topChromeTarget(): String {
        val specs = symbols[NativeGlassContract.homeNativeGlassTopChromeTabSelectedMethodSpecs].orEmpty()
        return if (specs.isEmpty()) "-" else specs.joinToString(",")
    }
    fun hasHomeNativeGlassPageClass(): Boolean {
        return symbols[HomeAnchorsContract.homePersonalizeAnchorClasses]
            .orEmpty()
            .contains(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS)
    }
    fun resourceListTarget(values: List<Int>?): String {
        val ids = values.orEmpty().filter { it != 0 }
        return if (ids.isEmpty()) "-" else "count=${ids.size}"
    }
    fun formatResourceId(value: Int?): String {
        return if (value != null && value != 0) {
            "0x${Integer.toHexString(value)}"
        } else {
            "-"
        }
    }
    fun add(name: String, target: String, checks: List<Pair<String, Boolean>>) {
        out.add(buildHookPointStatus(name, target, checks))
    }

    add(
        "HomeNativeGlassHook",
        "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.<init> / " +
            "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.${symbols[FeedContract.feedCardBindMethodSpec]}",
        listOf(
            "homeNativeGlassPageClass" to hasHomeNativeGlassPageClass(),
            "feedCardBindMethodSpec" to has(symbols[FeedContract.feedCardBindMethodSpec]),
        ),
    )
    add(
        "HomeNativeGlassHook.Resources",
        "subPbNext=${formatResourceId(symbols[NativeGlassContract.homeNativeGlassSubPbNextPageMoreViewId])}, " +
            "titleDivider=${formatResourceId(symbols[NativeGlassContract.homeNativeGlassPbReplyTitleDividerViewId])}",
        listOf(
            "homeNativeGlassSubPbNextPageMoreViewId" to has(symbols[NativeGlassContract.homeNativeGlassSubPbNextPageMoreViewId]),
            "homeNativeGlassPbReplyTitleDividerViewId" to has(symbols[NativeGlassContract.homeNativeGlassPbReplyTitleDividerViewId]),
        ),
    )
    add(
        "HomeNativeGlassHook.DynamicBackgroundColors",
        resourceListTarget(symbols[NativeGlassContract.homeNativeGlassDynamicBackgroundColorIds]),
        listOf(
            "homeNativeGlassDynamicBackgroundColorIds" to
                hasIntList(symbols[NativeGlassContract.homeNativeGlassDynamicBackgroundColorIds]),
        ),
    )
    add(
        "HomeNativeGlassHook.TopChrome",
        topChromeTarget(),
        listOf(
            "homeNativeGlassTopChromeTabSelectedMethodSpecs" to hasTopChromeTabSelectedSpecs(),
        ),
    )
    add(
        "HomeNativeGlassHook.SubPbNextPage",
        "${StableTiebaHookPoints.BD_LIST_VIEW_CLASS}." +
            "${symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageMethod]}" +
            "(${symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageParamType]})",
        listOf(
            "homeNativeGlassSubPbSetNextPageMethod" to
                has(symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageMethod]),
            "homeNativeGlassSubPbSetNextPageParamType" to
                has(symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageParamType]),
        ),
    )
    add(
        "HomeNativeGlassHook.SortSwitchBackground",
        "${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}.${symbols[NativeGlassContract.homeNativeGlassSortSwitchBackgroundPaintField]}",
        listOf(
            "homeNativeGlassSortSwitchBackgroundPaintField" to
                has(symbols[NativeGlassContract.homeNativeGlassSortSwitchBackgroundPaintField]),
        ),
    )
    add(
        "HomeNativeGlassHook.SortSwitchSelectedSlide",
        "${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}." +
            "${symbols[NativeGlassContract.homeNativeGlassSortSwitchSlideDrawMethod]}(Canvas)" +
            "[${symbols[NativeGlassContract.homeNativeGlassSortSwitchSlidePathField]}]",
        listOf(
            "homeNativeGlassSortSwitchSlideDrawMethod" to
                has(symbols[NativeGlassContract.homeNativeGlassSortSwitchSlideDrawMethod]),
            "homeNativeGlassSortSwitchSlidePathField" to
                has(symbols[NativeGlassContract.homeNativeGlassSortSwitchSlidePathField]),
        ),
    )
    add(
        "HomeNativeGlassHook.EnterForumCapsule",
        "${symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleControllerClass]}." +
            "${symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleInitMethod]}/" +
            "${symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleRefreshMethod]}" +
            "[${symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleViewField]}]",
        listOf(
            "homeNativeGlassEnterForumCapsuleControllerClass" to
                has(symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleControllerClass]),
            "homeNativeGlassEnterForumCapsuleInitMethod" to
                has(symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleInitMethod]),
            "homeNativeGlassEnterForumCapsuleRefreshMethod" to
                has(symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleRefreshMethod]),
            "homeNativeGlassEnterForumCapsuleViewField" to
                has(symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleViewField]),
            "homeNativeGlassEnterForumCapsuleTitleField" to
                has(symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleTitleField]),
        ),
    )
    add(
        "HomeNativeGlassHook.HostDarkModeSwitch",
        "${symbols[NativeGlassContract.homeNativeGlassHostDarkModeMoreActivityClass]}" +
            "[${symbols[NativeGlassContract.homeNativeGlassHostDarkModeControllerField]}]." +
            "${symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchGetterMethod]}/" +
            "${symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchStateField]}/" +
            "${symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOnMethod]}/" +
            "${symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOffMethod]}/" +
            "${symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchCallbackMethod]}",
        listOf(
            "homeNativeGlassHostDarkModeMoreActivityClass" to
                has(symbols[NativeGlassContract.homeNativeGlassHostDarkModeMoreActivityClass]),
            "homeNativeGlassHostDarkModeControllerField" to
                has(symbols[NativeGlassContract.homeNativeGlassHostDarkModeControllerField]),
            "homeNativeGlassHostDarkModeSwitchGetterMethod" to
                has(symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchGetterMethod]),
            "homeNativeGlassHostDarkModeSwitchStateField" to
                has(symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchStateField]),
            "homeNativeGlassHostDarkModeSwitchSetOnMethod" to
                has(symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOnMethod]),
            "homeNativeGlassHostDarkModeSwitchSetOffMethod" to
                has(symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOffMethod]),
            "homeNativeGlassHostDarkModeSwitchCallbackMethod" to
                has(symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchCallbackMethod]),
        ),
    )
    add(
        "HomeNativeGlassHook.CommonLayoutPreloader",
        "${StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS}.${symbols[NativeGlassContract.pbCommonLayoutPreloaderGetOrDefaultMethod]}",
        listOf(
            "pbCommonLayoutPreloaderGetOrDefaultMethod" to
                has(symbols[NativeGlassContract.pbCommonLayoutPreloaderGetOrDefaultMethod]),
        ),
    )

    return out
}
