package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.cache.HookSymbolCachePolicy
import com.forbidad4tieba.hook.symbol.contract.PerformanceContract
import com.forbidad4tieba.hook.symbol.contract.SymbolContracts
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PerformanceAbStatusTest {
    @Test fun cookieCapabilityDoesNotOwnOrRequirePostAdPoints() {
        val cookie = buildHookSymbols {
            this[PerformanceContract.performanceAbMethods] = listOf("cookieRepeatedOpt")
        }
        assertEquals(setOf(HookFeatureKey.COOKIE_SYNC_DEDUP), PerformanceAbStatus.features(cookie).keys)
        assertEquals(HookFeatureState.FULL, PerformanceAbStatus.features(cookie).getValue(HookFeatureKey.COOKIE_SYNC_DEDUP).state)
        assertEquals(listOf("PerformanceAB.isPbCommentFunAdABTest", "PerformanceAB.isPbPageBannerFunAdSdkTest"),
            PerformanceAbStatus.hookPoints(cookie).filter { it.isUnavailable() }.map { it.name })
        val postAd = buildHookSymbols {
            this[PerformanceContract.performanceAbMethods] = listOf("isPbCommentFunAdABTest", "isPbPageBannerFunAdSdkTest")
        }
        assertEquals(HookFeatureState.DISABLED, PerformanceAbStatus.features(postAd).getValue(HookFeatureKey.COOKIE_SYNC_DEDUP).state)
        assertEquals(listOf("PerformanceAB.cookieRepeatedOpt"), PerformanceAbStatus.hookPoints(postAd).filter { it.isUnavailable() }.map { it.name })
        for (name in listOf("isPbCommentFunAdABTest", "isPbPageBannerFunAdSdkTest")) {
            assertEquals(listOf(HookFeatureKey.BLOCK_AD_POST_PAGE), SymbolContracts.featuresForPoint("PerformanceAB.$name"))
        }
    }

    @Test fun retiredPerformanceDescriptorsDisappearWithoutRemovingRetainedConsumers() {
        val symbols = buildHookSymbols {
            source = "scan"
            createdAt = 1L
            this[PerformanceContract.performanceAbMethods] = PerformanceAbTarget.entries.map { it.methodName }
            this[PerformanceContract.runtimeMethods] = PerformanceRuntimeTarget.entries.map { it.name }
        }
        val retired = listOf(
            "lowEndConfig", "trackingMethods", "aiSpriteMemePanControllerClass", "aiSpriteMemeEnableMethod",
            "aiPbNewInputContainerClass", "aiPbNewInputContainerInitSpriteMemeMethod", "aiPbNewInputContainerInitAiWriteMethod",
            "aiPbAiEmojiCreationViewBindMethod", "aiPbPageBrowserAiEmojiCreationViewClass", "aiPbPageBrowserAiEmojiCreationBindMethod",
            "aiImageViewerJumpButtonOwnerClass", "aiImageViewerJumpButtonInitMethod",
            "pbCommentScrollListenerClass", "pbCommentScrollMethod", "pbCommentScrollFragmentField",
            "pbCommentScrollBottomListenerField", "pbCommentScrollBottomMethod",
            "pbPreloadProviderMethodSpec", "pbPreloadCardGetterMethodSpec", "pbPreloadPageStateMutableField",
            "pbPreloadPageStateFlowField", "pbPreloadCommitMethodSpec", "pbPreloadHybridMethodSpec",
            "pbPreloadResultSuccessField", "pbPreloadResultModelField", "pbPreloadResultStageField", "pbPreloadResultResponseField",
            "pbPreloadModelThreadField", "pbPreloadModelWebViewField", "pbPreloadRenderGateMethod", "pbPreloadNoCacheGateMethod", "pbPreloadAnchorFromChildrenMethod",
        )
        val json = JSONObject(symbols.toJson()).apply { retired.forEach { put(it, "retired") } }
        val restored = requireNotNull(HookSymbols.fromJson(json.toString()))
        val saved = JSONObject(restored.toJson())
        retired.forEach { assertFalse(it, saved.has(it)) }
        assertEquals(symbols, restored)
        assertTrue(PerformanceContract.capabilities(restored).values.all { it.state == HookFeatureState.FULL })
        val retiredFeatures = listOf("force_pb_preload", "force_low_end_device_config", "enable_pb_scroll_coalesce",
            "enable_pb_performance_mode", "force_host_feed_cold_opt", "disable_apsaras_schedule", "disable_monitor_sync_components", "disable_ai_components")
        val capabilities = HookFeatureStatusDeriver.derive(restored)
        retiredFeatures.forEach { assertFalse(it, capabilities.containsKey(it)) }
        assertFalse(HookSymbolStatusFormatter.collectHookPointStatuses(restored).any {
            it.name.startsWith("PbForcePreloadHook") || it.name.startsWith("PbScrollCoalesceHook") || it.name.startsWith("AiComponent") || it.name.startsWith("Tracking.")
        })
        assertTrue(HookSymbolCachePolicy.isUsable(restored))
        for (rule in listOf(81, 82)) {
            val oldRule = requireNotNull(HookSymbols.fromJson(json.put("dexKitRuleVersion", rule).toString()))
            assertFalse("retired rule=$rule", HookSymbolCachePolicy.isUsable(oldRule))
        }
    }

    @Test fun unscannedCookieRemainsDisabled() {
        assertEquals(HookFeatureState.DISABLED, PerformanceAbStatus.features(buildHookSymbols {}).getValue(HookFeatureKey.COOKIE_SYNC_DEDUP).state)
    }
}
