package com.forbidad4tieba.hook.symbol.lowend

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LowEndConfigSymbolsTest {
    @Test fun fullDomainSurvivesWholeSymbolCacheRoundTrip() {
        val domain = LowEndConfigSymbols(LowEndConfigTarget.entries.associateWith { "method_${it.name}" })
        val symbols = buildHookSymbols { this[LowEndContract.lowEndConfig] = domain }
        val restored = HookSymbols.fromJson(symbols.toJson())!!
        assertEquals(domain, restored[LowEndContract.lowEndConfig])
        assertEquals(HookFeatureState.FULL, restored[LowEndContract.lowEndConfig].featureStatus().state)
    }

    @Test fun malformedOrMissingNamesDoNotProduceCapabilities() {
        val domain = LowEndConfigSymbols.fromJson(JSONObject("""{"THRESHOLD":null,"STRING_CONFIG":4,"DEVICE_SCORE":" "}"""))
        assertTrue(domain.methods.isEmpty())
        assertEquals(HookFeatureState.DISABLED, domain.featureStatus().state)
        assertEquals(3, domain.featureStatus().missingCritical.size)
    }

    @Test fun missingTargetDoesNotDisableIndependentOverrides() {
        val domain = LowEndConfigSymbols(mapOf(LowEndConfigTarget.THRESHOLD to "readThreshold"))
        assertEquals(HookFeatureState.PARTIAL, domain.featureStatus().state)
        assertTrue(domain.featureStatus().isSupported())
        assertTrue(domain.featureStatus().missingCritical.isEmpty())
        assertEquals(listOf("STRING_CONFIG", "DEVICE_SCORE"), domain.featureStatus().missingOptional)
    }
}
