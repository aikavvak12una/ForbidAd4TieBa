package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.ForumPageAdSymbolReadiness
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForumPageAdCapabilityTest {
    @Test fun retiredAnimationEntryCannotEnableTheDialogOrFeature() {
        val json = JSONObject(buildHookSymbols {
            forumDialogControllerClass = "forum.DialogController"
        }.toJson()).put("forumAnimationShowMethod", "notifyFollowStatus")
        val restored = requireNotNull(HookSymbols.fromJson(json.toString()))

        assertFalse(ForumPageAdSymbolReadiness.evaluate(restored).any)
        assertEquals(HookFeatureState.DISABLED, feature(restored))
        assertEquals(HookPointState.OPTIONAL, dialogPoint(restored).state)
        assertTrue(dialogPoint(restored).missing.contains("forumDialogDisplayMethod"))
        assertFalse(JSONObject(restored.toJson()).has("forumAnimationShowMethod"))
    }

    @Test fun businessDialogKeepsItsTargetWhenOldCacheContainsAnUnrelatedMethod() {
        val json = JSONObject(buildHookSymbols {
            forumDialogControllerClass = "forum.DialogController"
            forumBusinessPromotShowMethod = "showBusinessPromotion"
        }.toJson()).put("forumAnimationShowMethod", "notifyFollowStatus")
        val restored = requireNotNull(HookSymbols.fromJson(json.toString()))

        assertTrue(ForumPageAdSymbolReadiness.evaluate(restored).dialog)
        assertNotEquals(HookFeatureState.DISABLED, feature(restored))
        assertEquals(HookPointState.FOUND, dialogPoint(restored).state)
        assertEquals("forum.DialogController.{showBusinessPromotion}", dialogPoint(restored).target)
        val roundtrip = requireNotNull(HookSymbols.fromJson(restored.toJson()))
        assertEquals(dialogPoint(restored), dialogPoint(roundtrip))
    }

    @Test fun missingBusinessDialogDoesNotDisableAnotherReadyForumRoute() {
        val symbols = buildHookSymbols {
            forumResponseDataClass = "forum.ResponseData"
            forumResponseParserMethod = "parseResponse"
            forumResponseAdFields = listOf("adMixFloor", "adShowSelect", "adSampleMapKey", "businessPromot")
            forumDialogControllerClass = "forum.DialogController"
        }

        val readiness = ForumPageAdSymbolReadiness.evaluate(symbols)
        assertTrue(readiness.response)
        assertFalse(readiness.dialog)
        assertTrue(readiness.any)
        assertNotEquals(HookFeatureState.DISABLED, feature(symbols))
    }

    private fun feature(symbols: HookSymbols) =
        HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.BLOCK_AD_FORUM_PAGE).state

    private fun dialogPoint(symbols: HookSymbols) =
        HookSymbolStatusFormatter.collectHookPointStatuses(symbols, "", "", "")
            .single { it.name == "ForumPageAdBlockHook.Dialog" }
}
