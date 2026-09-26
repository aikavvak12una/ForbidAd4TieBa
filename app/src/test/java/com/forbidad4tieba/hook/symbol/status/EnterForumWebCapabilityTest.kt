package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertEquals
import org.junit.Test

class EnterForumWebCapabilityTest {
    private val required = listOf(
        "enterForumWebControllerClass", "enterForumWebLoadMethod", "enterForumWebViewFieldOwnerClass",
        "enterForumWebViewField", "enterForumWebSetForceCommonMethod",
    )

    @Test fun urlSourceAloneCannotEnablePurificationWithoutItsLoadingPolicy() {
        val symbols = buildHookSymbols {
            enterForumInitInfoDataClass = "verified.InitInfoData"
            enterForumInitInfoGetUrlMethod = "getForumEnterUrl"
        }
        assertEquals(
            HookFeatureState.DISABLED,
            HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.FILTER_ENTER_FORUM_WEB).state,
        )
        assertEquals(
            HookPointState.MISSING,
            HookSymbolStatusFormatter.collectHookPointStatuses(symbols, "", "", "")
                .single { it.name == "EnterForumWebHook" }.state,
        )
    }

    @Test fun everyMissingLoadDependencyDisablesTheFeatureEvenWithAUrlSource() {
        for (missing in required) {
            val symbols = complete(missing)
            val feature = HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.FILTER_ENTER_FORUM_WEB)
            val point = HookSymbolStatusFormatter.collectHookPointStatuses(symbols, "", "", "")
                .single { it.name == "EnterForumWebHook" }
            assertEquals(HookFeatureState.DISABLED, feature.state)
            assertEquals(listOf(missing), feature.missingCritical)
            assertEquals(HookPointState.MISSING, point.state)
            assertEquals(listOf(missing), point.missing)
        }
    }

    @Test fun controllerAndPolicyAreSufficientWhenTheOptionalSourceIsAbsent() {
        val symbols = complete(source = false)
        assertEquals(HookFeatureState.FULL,
            HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.FILTER_ENTER_FORUM_WEB).state)
        val points = HookSymbolStatusFormatter.collectHookPointStatuses(symbols, "", "", "")
        for (name in listOf("EnterForumWebHook", "EnterForumWebHook.WebLoad", "EnterForumWebHook.LoadPolicy")) {
            assertEquals(HookPointState.FOUND, points.single { it.name == name }.state)
        }
    }

    private fun complete(missing: String? = null, source: Boolean = true): HookSymbols = buildHookSymbols {
        if (missing != "enterForumWebControllerClass") enterForumWebControllerClass = "verified.Controller"
        if (missing != "enterForumWebLoadMethod") enterForumWebLoadMethod = "loadPage"
        if (missing != "enterForumWebViewFieldOwnerClass") enterForumWebViewFieldOwnerClass = "verified.Base"
        if (missing != "enterForumWebViewField") enterForumWebViewField = "webView"
        if (missing != "enterForumWebSetForceCommonMethod") enterForumWebSetForceCommonMethod = "setForceCommon"
        if (source) {
            enterForumInitInfoDataClass = "verified.InitInfoData"
            enterForumInitInfoGetUrlMethod = "getForumEnterUrl"
        }
    }
}
