package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.symbol.model.ForumPageAdSymbolReadiness
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForumPageAdCapabilityTest {
    @Test fun retiredHeaderRainDescriptorsCannotEnableRain() {
        val json = JSONObject(buildHookSymbols {
            this[ForumPageAdContract.forumPageMapperClass] = "forum.Mapper"
            this[ForumPageAdContract.forumRainDataClass] = "forum.RainData"
        }.toJson())
            .put("forumHeaderDataMapperMethod", "mapHeader")
            .put("forumHeaderDataClass", "forum.HeaderData")
            .put("forumRainSetterMethod", "setRain")
        val restored = requireNotNull(HookSymbols.fromJson(json.toString()))

        assertFalse(ForumPageAdSymbolReadiness.evaluate(restored).rain)
        assertEquals(HookFeatureState.DISABLED, feature(restored))
        val roundtrip = JSONObject(restored.toJson())
        listOf("forumHeaderDataMapperMethod", "forumHeaderDataClass", "forumRainSetterMethod").forEach {
            assertFalse(it, roundtrip.has(it))
        }
    }

    @Test fun newRainDescriptorsRoundtripAndEnableRainIndependently() {
        val symbols = buildHookSymbols {
            this[ForumPageAdContract.forumRainMapperClass] = "forum.RainMapper"
            this[ForumPageAdContract.forumRainMapperMethod] = "convertRain"
            this[ForumPageAdContract.forumRainDataClass] = "forum.RainData"
        }
        val restored = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        val readiness = ForumPageAdSymbolReadiness.evaluate(restored)
        assertTrue(readiness.rain)
        assertFalse(readiness.bottom)
        assertFalse(readiness.gameBar)
        assertNotEquals(HookFeatureState.DISABLED, feature(restored))
        assertEquals(symbols[ForumPageAdContract.forumRainMapperClass], restored[ForumPageAdContract.forumRainMapperClass])
        assertEquals(symbols[ForumPageAdContract.forumRainMapperMethod], restored[ForumPageAdContract.forumRainMapperMethod])
        assertEquals(symbols[ForumPageAdContract.forumRainDataClass], restored[ForumPageAdContract.forumRainDataClass])
        val point = HookSymbolStatusFormatter.collectHookPointStatuses(restored)
            .single { it.name == "ForumPageAdBlockHook.Rain" }
        assertEquals(HookPointState.FOUND, point.state)
        assertEquals("forum.RainMapper.convertRain(tbclient.FrsPage.FrsRainInfo) -> forum.RainData", point.target)
    }

    @Test fun eachMissingRainDescriptorLeavesAnotherForumRouteReady() {
        for (missing in 0..2) {
            val symbols = buildHookSymbols {
                this[ForumPageAdContract.forumResponseDataClass] = "forum.ResponseData"
                this[ForumPageAdContract.forumResponseParserMethod] = "parseResponse"
                this[ForumPageAdContract.forumResponseAdFields] = listOf("adMixFloor", "adShowSelect", "adSampleMapKey", "businessPromot")
                if (missing != 0) this[ForumPageAdContract.forumRainMapperClass] = "forum.RainMapper"
                if (missing != 1) this[ForumPageAdContract.forumRainMapperMethod] = "convertRain"
                if (missing != 2) this[ForumPageAdContract.forumRainDataClass] = "forum.RainData"
            }
            val readiness = ForumPageAdSymbolReadiness.evaluate(symbols)
            assertTrue(readiness.response)
            assertFalse(readiness.rain)
            assertNotEquals(HookFeatureState.DISABLED, feature(symbols))
        }
    }

    @Test fun retiredAnimationEntryCannotEnableTheDialogOrFeature() {
        val json = JSONObject(buildHookSymbols {
            this[ForumPageAdContract.forumDialogControllerClass] = "forum.DialogController"
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
            this[ForumPageAdContract.forumDialogControllerClass] = "forum.DialogController"
            this[ForumPageAdContract.forumBusinessPromotShowMethod] = "showBusinessPromotion"
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
            this[ForumPageAdContract.forumResponseDataClass] = "forum.ResponseData"
            this[ForumPageAdContract.forumResponseParserMethod] = "parseResponse"
            this[ForumPageAdContract.forumResponseAdFields] = listOf("adMixFloor", "adShowSelect", "adSampleMapKey", "businessPromot")
            this[ForumPageAdContract.forumDialogControllerClass] = "forum.DialogController"
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
        HookSymbolStatusFormatter.collectHookPointStatuses(symbols)
            .single { it.name == "ForumPageAdBlockHook.Dialog" }
}
