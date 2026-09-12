package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.TrackingTarget
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingStatusTest {
    @Test
    fun lokiAndRetainedManualApisCannotStandInForAutoTraceGate() {
        val symbols = buildHookSymbols {
            trackingMethods = listOf(TrackingTarget.LOKI_SERVICE.name, TrackingTarget.PAGE_TRACE.name, "onEvent", "start")
        }
        val status = HookFeatureStatusDeriver.derive(symbols)
            .getValue(HookFeatureKey.DISABLE_MONITOR_SYNC_COMPONENTS)
        assertEquals(HookFeatureState.DISABLED, status.state)
        assertEquals(listOf("Tracking.isCloseTrace"), status.missingCritical)
    }

    @Test
    fun cacheRoundTripPreservesAllTrackingTargetsAndFeatureAvailability() {
        val symbols = buildHookSymbols { trackingMethods = TrackingTarget.entries.map { it.name } }
        val restored = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        assertEquals(symbols.trackingMethods, restored.trackingMethods)
        assertEquals(HookFeatureState.FULL, TrackingStatus.feature(restored).state)
        assertTrue(TrackingStatus.hookPoints(restored).all { it.state == HookPointState.FOUND })
    }

    @Test
    fun legacyAutoTraceAndLokiCacheCannotStandInForPageTraceGate() {
        val symbols = buildHookSymbols {
            trackingMethods = listOf(TrackingTarget.CLOSE_TRACE.name, TrackingTarget.LOKI_SERVICE.name)
        }
        val restored = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        val status = HookFeatureStatusDeriver.derive(restored)
            .getValue(HookFeatureKey.DISABLE_MONITOR_SYNC_COMPONENTS)
        assertEquals(HookFeatureState.DISABLED, status.state)
        assertEquals(listOf("Tracking.startTrack"), status.missingCritical)
    }

    @Test
    fun absentTrackingScanFailsClosed() {
        val status = TrackingStatus.feature(buildHookSymbols {})
        assertEquals(HookFeatureState.DISABLED, status.state)
        assertEquals(3, status.missingCritical.size)
    }
}
