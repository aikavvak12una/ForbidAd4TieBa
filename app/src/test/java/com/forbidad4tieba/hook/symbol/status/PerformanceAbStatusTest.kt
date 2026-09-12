package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
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
    fun preloadNeedsBothVerifiedAbMethodsAndItsRenderGate() {
        val abOnly = buildHookSymbols {
            performanceAbMethods = PerformanceAbTarget.entries.map { it.methodName }
        }
        val complete = buildHookSymbols {
            performanceAbMethods = PerformanceAbTarget.entries.map { it.methodName }
            pbPreloadRenderGateMethod = "renderPreloadGate"
        }
        assertEquals(
            HookFeatureState.DISABLED,
            PerformanceAbStatus.features(abOnly).getValue(HookFeatureKey.FORCE_PB_PRELOAD).state,
        )
        assertEquals(
            HookFeatureState.FULL,
            PerformanceAbStatus.features(complete).getValue(HookFeatureKey.FORCE_PB_PRELOAD).state,
        )
    }

    @Test
    fun unscannedPerformanceFeaturesStayDisabled() {
        assertTrue(PerformanceAbStatus.features(buildHookSymbols {}).values.all { it.state == HookFeatureState.DISABLED })
    }
}
