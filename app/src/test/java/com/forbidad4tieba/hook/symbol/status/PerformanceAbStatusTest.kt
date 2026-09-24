package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceAbStatusTest {
    @Test
    fun missingPbSubHookCannotReportFullAndDoesNotDisableHostFlags() {
        val symbols = buildHookSymbols {
            performanceAbMethods = PerformanceAbTarget.entries
                .filter { it != PerformanceAbTarget.IMAGE_PERF_LOG }.map { it.methodName }
        }
        val statuses = HookFeatureStatusDeriver.derive(symbols)
        val pb = statuses.getValue(HookFeatureKey.ENABLE_PB_PERFORMANCE_MODE)
        assertEquals(HookFeatureState.DISABLED, pb.state)
        assertTrue(pb.missingCritical.contains("PerformanceAB.imagePerfLog"))
        assertEquals(HookFeatureState.FULL, statuses.getValue(HookFeatureKey.FORCE_HOST_PERFORMANCE_FLAGS).state)
        assertEquals(
            HookPointState.MISSING,
            PerformanceAbStatus.hookPoints(symbols).single { it.name == "PerformanceAB.imagePerfLog" }.state,
        )
    }

    @Test
    fun preloadNeedsCardProviderEvenWhenRetiredGatesExistInCache() {
        val abOnly = buildHookSymbols {
            performanceAbMethods = PerformanceAbTarget.entries.map { it.methodName }
        }
        val legacy = requireNotNull(HookSymbols.fromJson(JSONObject(abOnly.toJson())
            .put("pbPreloadRenderGateMethod", "renderPreloadGate")
            .put("pbPreloadNoCacheGateMethod", "noCacheGate").toString()))
        assertEquals(
            HookFeatureState.DISABLED,
            PerformanceAbStatus.features(abOnly).getValue(HookFeatureKey.FORCE_PB_PRELOAD).state,
        )
        assertEquals(
            HookFeatureState.DISABLED,
            PerformanceAbStatus.features(legacy).getValue(HookFeatureKey.FORCE_PB_PRELOAD).state,
        )
    }

    @Test
    fun currentPreloadSurvivesCacheRoundTripAndRequiresPageStateReplay() {
        val symbols = buildHookSymbols {
            performanceAbMethods = listOf(PerformanceAbTarget.HYBRID_PB.methodName)
            pbPreloadProviderMethodSpec = "test.Provider#load"
            pbPreloadCardGetterMethodSpec = "test.CardCache#current"
            pbPreloadPageStateMutableField = "mutablePageState"
            pbPreloadPageStateFlowField = "pageState"
        }
        val restored = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        assertEquals("test.Provider#load", restored.pbPreloadProviderMethodSpec)
        assertEquals("test.CardCache#current", restored.pbPreloadCardGetterMethodSpec)
        assertEquals("mutablePageState", restored.pbPreloadPageStateMutableField)
        assertEquals("pageState", restored.pbPreloadPageStateFlowField)
        assertTrue(!JSONObject(restored.toJson()).has("pbPreloadRenderGateMethod"))
        assertTrue(!JSONObject(restored.toJson()).has("pbPreloadNoCacheGateMethod"))
        assertEquals(
            HookFeatureState.FULL,
            PerformanceAbStatus.features(restored).getValue(HookFeatureKey.FORCE_PB_PRELOAD).state,
        )
        for (missing in listOf("pbPreloadProviderMethodSpec", "pbPreloadCardGetterMethodSpec", "pbPreloadPageStateMutableField", "pbPreloadPageStateFlowField", "performanceAbMethods")) {
            val incomplete = JSONObject(restored.toJson()).apply { remove(missing) }
            assertEquals(
                missing,
                HookFeatureState.DISABLED,
                PerformanceAbStatus.features(requireNotNull(HookSymbols.fromJson(incomplete.toString())))
                    .getValue(HookFeatureKey.FORCE_PB_PRELOAD).state,
            )
        }
    }

    @Test
    fun unscannedPerformanceFeaturesStayDisabled() {
        assertTrue(PerformanceAbStatus.features(buildHookSymbols {}).values.all { it.state == HookFeatureState.DISABLED })
    }
}
