package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.status.HookPointState
import com.forbidad4tieba.hook.symbol.status.PerformanceAbStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PbPreloadContractTest {
    @Test fun pageStateAndSwitchAloneCannotClaimInstantPreloadSupport() {
        val native = buildHookSymbols {
            this[PerformanceContract.performanceAbMethods] = listOf(PerformanceAbTarget.HYBRID_PB.methodName)
            this[PbPreloadContract.pbPreloadPageStateMutableField] = "mutablePageState"
            this[PbPreloadContract.pbPreloadPageStateFlowField] = "pageState"
        }
        val restored = requireNotNull(HookSymbols.fromJson(native.toJson()))
        assertEquals(HookFeatureState.DISABLED, PerformanceAbStatus.features(restored).getValue(HookFeatureKey.FORCE_PB_PRELOAD).state)
    }

    @Test fun retiredGatesAndAnchorDescriptorsAreNotRepublished() {
        val obsolete = listOf("pbPreloadRenderGateMethod", "pbPreloadNoCacheGateMethod", "pbPreloadAnchorFromChildrenMethod")
        val json = JSONObject(buildHookSymbols {}.toJson())
        for (key in obsolete) json.put(key, "obsoleteTarget")
        val restored = requireNotNull(HookSymbols.fromJson(json.toString()))
        val saved = JSONObject(restored.toJson())
        for (key in obsolete) assertFalse(key, saved.has(key))
        assertEquals(HookFeatureState.DISABLED, PerformanceAbStatus.features(restored).getValue(HookFeatureKey.FORCE_PB_PRELOAD).state)
    }

    @Test fun eachRequiredTargetGatesForcedPreloadWithoutDisablingOtherPerformanceFeatures() {
        val complete = buildHookSymbols {
            this[PerformanceContract.performanceAbMethods] = PerformanceAbTarget.entries.map { it.methodName }
            for (field in PbPreloadContract.required.fields) {
                @Suppress("UNCHECKED_CAST")
                this[field as SymbolField<String?>] = "fixture"
            }
        }
        assertEquals(complete, HookSymbols.fromJson(complete.toJson()))
        assertEquals(HookFeatureState.FULL, PerformanceAbStatus.features(complete).getValue(HookFeatureKey.FORCE_PB_PRELOAD).state)
        for (field in PbPreloadContract.required.fields) {
            val json = JSONObject(complete.toJson()).apply { remove(field.cacheKey) }
            val incomplete = requireNotNull(HookSymbols.fromJson(json.toString()))
            val statuses = PerformanceAbStatus.features(incomplete)
            assertEquals(listOf(field.cacheKey), statuses.getValue(HookFeatureKey.FORCE_PB_PRELOAD).missingCritical)
            assertTrue(statuses.filterKeys { it != HookFeatureKey.FORCE_PB_PRELOAD }.values.all { it.isSupported() })
            assertNull(PbPreloadContract.resolvePbPreloadTargets(javaClass.classLoader!!, incomplete))
            assertEquals(HookPointState.MISSING, PbPreloadContract.points(incomplete).single { field.cacheKey in it.missing }.state)
        }
    }

    @Test fun missingPageStateIsNegativeCachedAndMalformedRestorationFailsClosed() {
        val cl = javaClass.classLoader!!
        assertTrue(PbPreloadContract.isCacheValid(buildHookSymbols {}, cl))
        val malformed = buildHookSymbols {
            this[PbPreloadContract.pbPreloadPageStateMutableField] = "missingPageState"
        }
        assertNull(PbPreloadContract.resolvePbPreloadTargets(cl, malformed))
        assertFalse(PbPreloadContract.isCacheValid(malformed, cl))
    }
}
