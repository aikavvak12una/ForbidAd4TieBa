package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.textSymbols
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class CapabilityIsolationTest {
    private val rain = listOf("forumRainMapperClass", "forumRainMapperMethod", "forumRainDataClass")
    private val response = listOf("forumResponseDataClass", "forumResponseParserMethod")
    private val dialog = listOf("forumDialogControllerClass", "forumBusinessPromotShowMethod")
    private val message = listOf("plainUrlMessageManagerClass", "plainUrlMessageDispatchMethod", "plainUrlResponsedMessageClass",
        "plainUrlResponsedMessageGetCmdMethod", "plainUrlCustomResponsedMessageClass", "plainUrlCustomResponsedMessageGetDataMethod",
        "plainUrlApplicationClass", "plainUrlApplicationGetInstMethod", "plainUrlMessageDataClass", "plainUrlMessageDataTypeField",
        "plainUrlMessageDataUrlField", "plainUrlMessageDataTextField")
    private val browser = listOf("plainUrlBrowserHelperClass", "plainUrlBrowserHelperStartWebActivityMethod")
    private val mount = listOf("mountCardLinkLayoutClass", "mountCardLinkLayoutOnClickMethod", "mountCardLinkLayoutDataField",
        "mountCardLinkInfoDataClass", "mountCardLinkInfoGetUrlMethod")
    private val span = listOf("plainUrlClickableSpanClass", "plainUrlClickableSpanOnClickMethod", "plainUrlClickableSpanTypeField",
        "plainUrlClickableSpanUrlField", "plainUrlClickableSpanTextField")

    @Test fun missingRainAndOptionalDialogDoNotRemoveTheResponseRoute() {
        for (missing in rain + "forumBusinessPromotShowMethod") {
            val symbols = textSymbols(rain + response + dialog, setOf(missing)) {
                put("forumResponseAdFields", JSONArray(listOf("adMixFloor", "adShowSelect", "adSampleMapKey", "businessPromot")))
            }
            val readiness = ForumPageAdSymbolReadiness.evaluate(symbols)
            assertTrue(readiness.response)
            assertEquals(missing !in rain, readiness.rain)
            assertEquals(missing != "forumBusinessPromotShowMethod", readiness.dialog)
            assertTrue(HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.BLOCK_AD_FORUM_PAGE).isSupported())
        }
        val rainOnly = textSymbols(rain)
        assertTrue(ForumPageAdSymbolReadiness.evaluate(rainOnly).rain)
        assertFalse(ForumPageAdSymbolReadiness.evaluate(rainOnly).response)
        assertEquals(HookPointState.FOUND, ForumPageAdContract.points(rainOnly).single { it.name.endsWith(".Rain") }.state)
    }

    @Test fun dialogControllerWithoutItsDisplayMethodDoesNotClaimAnyRoute() {
        val incomplete = textSymbols(listOf("forumDialogControllerClass"))
        assertFalse(ForumPageAdSymbolReadiness.evaluate(incomplete).any)
        assertEquals(HookPointState.OPTIONAL, ForumPageAdContract.points(incomplete).single { it.name.endsWith(".Dialog") }.state)
        val complete = textSymbols(dialog)
        assertTrue(ForumPageAdSymbolReadiness.evaluate(complete).dialog)
        assertEquals(HookPointState.FOUND, ForumPageAdContract.points(complete).single { it.name.endsWith(".Dialog") }.state)
    }

    @Test fun independentNavigationPathsEnableTheBrowserWithoutRetiredContainerFields() {
        val symbols = textSymbols(message + browser + mount)
        assertEquals(HookFeatureState.FULL,
            HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER).state)
    }

    @Test fun eachMissingPayloadRoleDisablesMessageWhileDirectAndHelperRemainAvailable() {
        for (missing in message.takeLast(4)) {
            val symbols = textSymbols(message + browser + span, setOf(missing)) {
                put("plainUrlClickableSpanOnClickOwnerClasses", JSONArray(listOf("fixture.Span")))
            }
            val states = HookFeatureStatusDeriver.derive(symbols)
            assertEquals(HookFeatureState.PARTIAL, states.getValue(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER).state)
            assertTrue(states.getValue(HookFeatureKey.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE).missingOptional.contains(missing))
            val points = PlainUrlContract.points(symbols).associateBy { it.name }
            assertEquals(listOf(missing), points.getValue("PlainUrlDirectBrowserHook.Message").missing)
            assertEquals(HookPointState.FOUND, points.getValue("PlainUrlDirectBrowserHook.Direct").state)
            assertEquals(HookPointState.FOUND, points.getValue("PlainUrlDirectBrowserHook.BrowserHelper").state)
        }
    }
}
