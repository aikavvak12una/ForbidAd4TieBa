package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*

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
    fun currentHostTrackingEntriesEnableMonitoringBlockWithoutRemovedSdk() {
        val symbols = buildHookSymbols {
            this[TrackingContract.trackingMethods] = listOf(TrackingTarget.LOKI_SERVICE.name, TrackingTarget.PAGE_TRACE.name, "onEvent", "start")
        }
        val status = HookFeatureStatusDeriver.derive(symbols)
            .getValue(HookFeatureKey.DISABLE_MONITOR_SYNC_COMPONENTS)
        assertEquals(HookFeatureState.FULL, status.state)
        assertTrue(status.missingCritical.isEmpty())
    }

    @Test
    fun cacheRoundTripPreservesAllTrackingTargetsAndFeatureAvailability() {
        val symbols = buildHookSymbols { this[TrackingContract.trackingMethods] = TrackingTarget.entries.map { it.name } }
        val restored = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        assertEquals(symbols[TrackingContract.trackingMethods], restored[TrackingContract.trackingMethods])
        assertEquals(HookFeatureState.FULL, TrackingStatus.feature(restored).state)
        assertTrue(TrackingStatus.hookPoints(restored).all { it.state == HookPointState.FOUND })
    }

    @Test
    fun legacyAutoTraceAndLokiCacheCannotStandInForPageTraceGate() {
        val symbols = buildHookSymbols {
            this[TrackingContract.trackingMethods] = listOf("CLOSE_TRACE", TrackingTarget.LOKI_SERVICE.name)
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
        assertEquals(2, status.missingCritical.size)
    }

    @Test
    fun pageTraceCannotStandInForLokiService() {
        val symbols = buildHookSymbols { this[TrackingContract.trackingMethods] = listOf(TrackingTarget.PAGE_TRACE.name) }
        val status = TrackingStatus.feature(symbols)
        assertEquals(HookFeatureState.DISABLED, status.state)
        assertEquals(listOf("Tracking.onStartCommand"), status.missingCritical)
    }
}
