package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal fun formatHomeNativeGlassHookPointStatusLines(symbols: HookSymbols): List<String> {
    val out = ArrayList<String>(9)

    fun has(value: String?): Boolean = !value.isNullOrBlank()
    fun has(value: Int?): Boolean = value != null && value != 0
    fun hasIntList(values: List<Int>?): Boolean = values.orEmpty().any { it != 0 }
    fun hasTopChromeTabSelectedSpecs(): Boolean {
        return symbols.homeNativeGlassTopChromeTabSelectedMethodSpecs
            .orEmpty()
            .any { spec ->
                val sep = spec.indexOf('#')
                sep > 0 && sep < spec.lastIndex
            }
    }
    fun topChromeTarget(): String {
        val specs = symbols.homeNativeGlassTopChromeTabSelectedMethodSpecs.orEmpty()
        return if (specs.isEmpty()) "-" else specs.joinToString(",")
    }
    fun hasHomeNativeGlassPageClass(): Boolean {
        return symbols.homePersonalizeAnchorClasses
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
        val missing = checks.asSequence()
            .filter { !it.second }
            .map { it.first }
            .toList()
        val state = if (missing.isEmpty()) "FOUND" else "MISSING"
        val missingText = if (missing.isEmpty()) "-" else missing.joinToString(",")
        out.add("HookPoint[$name] state=$state missing=$missingText target=$target")
    }

    add(
        "HomeNativeGlassHook",
        "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.<init> / " +
            "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.${symbols.feedCardBindMethodSpec}",
        listOf(
            "homeNativeGlassPageClass" to hasHomeNativeGlassPageClass(),
            "feedCardBindMethodSpec" to has(symbols.feedCardBindMethodSpec),
        ),
    )
    add(
        "HomeNativeGlassHook.Resources",
        "subPbNext=${formatResourceId(symbols.homeNativeGlassSubPbNextPageMoreViewId)}, " +
            "titleDivider=${formatResourceId(symbols.homeNativeGlassPbReplyTitleDividerViewId)}",
        listOf(
            "homeNativeGlassSubPbNextPageMoreViewId" to has(symbols.homeNativeGlassSubPbNextPageMoreViewId),
            "homeNativeGlassPbReplyTitleDividerViewId" to has(symbols.homeNativeGlassPbReplyTitleDividerViewId),
        ),
    )
    add(
        "HomeNativeGlassHook.DynamicBackgroundColors",
        resourceListTarget(symbols.homeNativeGlassDynamicBackgroundColorIds),
        listOf(
            "homeNativeGlassDynamicBackgroundColorIds" to
                hasIntList(symbols.homeNativeGlassDynamicBackgroundColorIds),
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
            "${symbols.homeNativeGlassSubPbSetNextPageMethod}" +
            "(${symbols.homeNativeGlassSubPbSetNextPageParamType})",
        listOf(
            "homeNativeGlassSubPbSetNextPageMethod" to
                has(symbols.homeNativeGlassSubPbSetNextPageMethod),
            "homeNativeGlassSubPbSetNextPageParamType" to
                has(symbols.homeNativeGlassSubPbSetNextPageParamType),
        ),
    )
    add(
        "HomeNativeGlassHook.SortSwitchBackground",
        "${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}.${symbols.homeNativeGlassSortSwitchBackgroundPaintField}",
        listOf(
            "homeNativeGlassSortSwitchBackgroundPaintField" to
                has(symbols.homeNativeGlassSortSwitchBackgroundPaintField),
        ),
    )
    add(
        "HomeNativeGlassHook.SortSwitchSelectedSlide",
        "${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}." +
            "${symbols.homeNativeGlassSortSwitchSlideDrawMethod}(Canvas)" +
            "[${symbols.homeNativeGlassSortSwitchSlidePathField}]",
        listOf(
            "homeNativeGlassSortSwitchSlideDrawMethod" to
                has(symbols.homeNativeGlassSortSwitchSlideDrawMethod),
            "homeNativeGlassSortSwitchSlidePathField" to
                has(symbols.homeNativeGlassSortSwitchSlidePathField),
        ),
    )
    add(
        "HomeNativeGlassHook.EnterForumCapsule",
        "${symbols.homeNativeGlassEnterForumCapsuleControllerClass}." +
            "${symbols.homeNativeGlassEnterForumCapsuleInitMethod}/" +
            "${symbols.homeNativeGlassEnterForumCapsuleRefreshMethod}" +
            "[${symbols.homeNativeGlassEnterForumCapsuleViewField}]",
        listOf(
            "homeNativeGlassEnterForumCapsuleControllerClass" to
                has(symbols.homeNativeGlassEnterForumCapsuleControllerClass),
            "homeNativeGlassEnterForumCapsuleInitMethod" to
                has(symbols.homeNativeGlassEnterForumCapsuleInitMethod),
            "homeNativeGlassEnterForumCapsuleRefreshMethod" to
                has(symbols.homeNativeGlassEnterForumCapsuleRefreshMethod),
            "homeNativeGlassEnterForumCapsuleViewField" to
                has(symbols.homeNativeGlassEnterForumCapsuleViewField),
            "homeNativeGlassEnterForumCapsuleTitleField" to
                has(symbols.homeNativeGlassEnterForumCapsuleTitleField),
        ),
    )
    add(
        "HomeNativeGlassHook.HostDarkModeSwitch",
        "${symbols.homeNativeGlassHostDarkModeMoreActivityClass}" +
            "[${symbols.homeNativeGlassHostDarkModeControllerField}]." +
            "${symbols.homeNativeGlassHostDarkModeSwitchGetterMethod}/" +
            "${symbols.homeNativeGlassHostDarkModeSwitchStateField}/" +
            "${symbols.homeNativeGlassHostDarkModeSwitchSetOnMethod}/" +
            "${symbols.homeNativeGlassHostDarkModeSwitchSetOffMethod}/" +
            "${symbols.homeNativeGlassHostDarkModeSwitchCallbackMethod}",
        listOf(
            "homeNativeGlassHostDarkModeMoreActivityClass" to
                has(symbols.homeNativeGlassHostDarkModeMoreActivityClass),
            "homeNativeGlassHostDarkModeControllerField" to
                has(symbols.homeNativeGlassHostDarkModeControllerField),
            "homeNativeGlassHostDarkModeSwitchGetterMethod" to
                has(symbols.homeNativeGlassHostDarkModeSwitchGetterMethod),
            "homeNativeGlassHostDarkModeSwitchStateField" to
                has(symbols.homeNativeGlassHostDarkModeSwitchStateField),
            "homeNativeGlassHostDarkModeSwitchSetOnMethod" to
                has(symbols.homeNativeGlassHostDarkModeSwitchSetOnMethod),
            "homeNativeGlassHostDarkModeSwitchSetOffMethod" to
                has(symbols.homeNativeGlassHostDarkModeSwitchSetOffMethod),
            "homeNativeGlassHostDarkModeSwitchCallbackMethod" to
                has(symbols.homeNativeGlassHostDarkModeSwitchCallbackMethod),
        ),
    )
    add(
        "HomeNativeGlassHook.CommonLayoutPreloader",
        "${StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS}.${symbols.pbCommonLayoutPreloaderGetOrDefaultMethod}",
        listOf(
            "pbCommonLayoutPreloaderGetOrDefaultMethod" to
                has(symbols.pbCommonLayoutPreloaderGetOrDefaultMethod),
        ),
    )

    return out
}
