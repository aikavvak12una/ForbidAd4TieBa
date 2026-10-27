package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.PerformanceContract
import com.forbidad4tieba.hook.symbol.contract.SymbolContracts
import com.forbidad4tieba.hook.symbol.contract.VideoDisableContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.*
import org.junit.Test

class PerformanceRuntimeStatusTest {
    @Test fun allTargetSubsetsPreserveIndependentAdChannels() {
        val targets = PerformanceRuntimeTarget.entries
        repeat(1 shl targets.size) { mask ->
            val found = targets.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val symbols = buildHookSymbols { this[PerformanceContract.runtimeMethods] = found.map { it.name } }
            for ((key, group) in targets.groupBy { it.featureKey }) {
                val absent = group.filter { it !in found }
                val status = PerformanceRuntimeStatus.features(symbols).getValue(key)
                val expected = when (absent.size) { 0 -> HookFeatureState.FULL; group.size -> HookFeatureState.DISABLED; else -> HookFeatureState.PARTIAL }
                assertEquals("mask=$mask key=$key", expected, status.state)
                assertEquals(absent.map { "PerformanceRuntime.${it.name}" }, status.missingCritical + status.missingOptional)
            }
            val points = PerformanceRuntimeStatus.hookPoints(symbols)
            assertEquals(found.map { it.name }.toSet(), points.filter { it.state == HookPointState.FOUND }.map { it.name.removePrefix("PerformanceRuntime.") }.toSet())
            val roundtrip = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
            assertEquals(PerformanceRuntimeStatus.features(symbols), PerformanceRuntimeStatus.features(roundtrip))
            assertEquals(points, PerformanceRuntimeStatus.hookPoints(roundtrip))
        }
    }

    @Test fun adChannelsVideoPreloadAndCookieHaveIndependentCapabilities() {
        PerformanceRuntimeTarget.entries.forEach {
            assertEquals(listOf(it.featureKey), SymbolContracts.featuresForPoint("PerformanceRuntime.${it.name}"))
        }
        val symbols = buildHookSymbols { this[PerformanceContract.runtimeMethods] = listOf("AD_CSJ", "retiredSdk") }
        val states = PerformanceContract.capabilities(symbols)
        assertEquals(HookFeatureState.PARTIAL, states.getValue(HookFeatureKey.DISABLE_AD_SDK_COMPONENTS).state)
        assertFalse(states.containsKey(HookFeatureKey.DISABLE_VIDEO_COMPONENTS))
        assertEquals(HookFeatureState.DISABLED, states.getValue(HookFeatureKey.COOKIE_SYNC_DEDUP).state)
        assertEquals(3, PerformanceRuntimeStatus.hookPoints(symbols).size)
    }

    @Test fun retiredPartialVideoCacheCannotEnableTheNewCompleteVideoGroup() {
        val symbols = buildHookSymbols { this[PerformanceContract.runtimeMethods] = listOf("VIDEO_PREFETCH", "VIDEO_OPEN") }
        assertEquals(HookFeatureState.DISABLED,
            VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS).state)
        assertEquals(4, VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS).missingCritical.size)
        assertFalse(PerformanceRuntimeStatus.hookPoints(symbols).any { it.name.contains("VIDEO_") })
    }
}
