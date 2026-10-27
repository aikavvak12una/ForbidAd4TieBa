package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.cache.HookSymbolCachePolicy
import com.forbidad4tieba.hook.symbol.contract.PerformanceContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceAbStatusTest {
    @Test fun missingPbSubHookCannotReportFullAndDoesNotDisableHostFlags() {
        val symbols = buildHookSymbols {
            this[PerformanceContract.performanceAbMethods] = PerformanceAbTarget.entries
                .filter { it != PerformanceAbTarget.IMAGE_PERF_LOG }.map { it.methodName }
        }
        val statuses = HookFeatureStatusDeriver.derive(symbols)
        val pb = statuses.getValue(HookFeatureKey.ENABLE_PB_PERFORMANCE_MODE)
        assertEquals(HookFeatureState.DISABLED, pb.state)
        assertTrue(pb.missingCritical.contains("PerformanceAB.imagePerfLog"))
        assertEquals(HookFeatureState.FULL, statuses.getValue(HookFeatureKey.FORCE_HOST_PERFORMANCE_FLAGS).state)
        assertEquals(HookPointState.MISSING, PerformanceAbStatus.hookPoints(symbols).single { it.name == "PerformanceAB.imagePerfLog" }.state)
    }

    @Test fun retiredDescriptorsAreDiscardedWithoutChangingRemainingCapabilities() {
        val symbols = buildHookSymbols {
            source = "scan"
            createdAt = 1L
            this[PerformanceContract.performanceAbMethods] = PerformanceAbTarget.entries.map { it.methodName }
        }
        val retired = listOf(
            "pbPreloadProviderMethodSpec", "pbPreloadCardGetterMethodSpec",
            "pbPreloadPageStateMutableField", "pbPreloadPageStateFlowField",
            "pbPreloadCommitMethodSpec", "pbPreloadHybridMethodSpec",
            "pbPreloadResultSuccessField", "pbPreloadResultModelField",
            "pbPreloadResultStageField", "pbPreloadResultResponseField",
            "pbPreloadModelThreadField", "pbPreloadModelWebViewField",
            "pbPreloadRenderGateMethod", "pbPreloadNoCacheGateMethod", "pbPreloadAnchorFromChildrenMethod",
        )
        val json = JSONObject(symbols.toJson()).apply { retired.forEach { put(it, "oldTarget") } }
        val restored = requireNotNull(HookSymbols.fromJson(json.toString()))
        val saved = JSONObject(restored.toJson())
        retired.forEach { assertFalse(it, saved.has(it)) }
        assertEquals(PerformanceAbStatus.features(symbols), PerformanceAbStatus.features(restored))
        assertTrue(PerformanceAbStatus.features(restored).values.all { it.state == HookFeatureState.FULL })
        assertFalse(HookFeatureStatusDeriver.derive(restored).containsKey("force_pb_preload"))
        assertFalse(HookSymbolStatusFormatter.collectHookPointStatuses(restored).any { it.name.startsWith("PbForcePreloadHook") })
        assertTrue(HookSymbolCachePolicy.isUsable(restored))
        val oldRule = requireNotNull(HookSymbols.fromJson(json.put("dexKitRuleVersion", 80).toString()))
        assertFalse(HookSymbolCachePolicy.isUsable(oldRule))
    }

    @Test fun unscannedPerformanceFeaturesStayDisabled() {
        assertTrue(PerformanceAbStatus.features(buildHookSymbols {}).values.all { it.state == HookFeatureState.DISABLED })
    }
}
