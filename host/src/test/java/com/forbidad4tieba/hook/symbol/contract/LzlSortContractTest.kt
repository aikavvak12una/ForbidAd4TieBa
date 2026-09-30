package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.junit.Assert.*
import org.junit.Test

class LzlSortContractTest {
    @Test fun cachedSetterRoundTripsAndDrivesBothCapabilityAndDiagnostics() {
        val empty = buildHookSymbols {}
        val found = buildHookSymbols { this[LzlSortContract.defaultSortSetter] = "renamedDefault" }
        assertEquals(found, HookSymbols.fromJson(found.toJson()))
        assertFalse(LzlSortContract.capabilities(empty).getValue(HookFeatureKey.DEFAULT_LZL_EARLIEST).isSupported())
        assertTrue(LzlSortContract.capabilities(found).getValue(HookFeatureKey.DEFAULT_LZL_EARLIEST).isSupported())
        assertEquals(HookPointState.MISSING, LzlSortContract.points(empty).single().state)
        assertEquals(HookPointState.FOUND, LzlSortContract.points(found).single().state)
        assertEquals(listOf("lzlDefaultSortSetter"), LzlSortContract.points(empty).single().missing)
        assertEquals(MessageTabContract.capabilities(empty), MessageTabContract.capabilities(found))
        assertEquals(listOf(HookFeatureKey.DEFAULT_LZL_EARLIEST),
            SymbolContracts.featuresForPoint("DefaultLzlEarliestHook"))
    }

    @Test fun missingTargetIsNegativelyCachedAndAnInvalidSavedTargetFailsValidation() {
        val loader = javaClass.classLoader!!
        assertTrue(LzlSortContract.isCacheValid(buildHookSymbols {}, loader))
        val invalid = buildHookSymbols { this[LzlSortContract.defaultSortSetter] = "missing" }
        assertNull(LzlSortContract.resolve(loader, invalid))
        assertFalse(LzlSortContract.isCacheValid(invalid, loader))
    }

    @Test fun restoreValidatesTheSavedSetterSignature() {
        assertEquals("renamedDefault", LzlSortContract.restore(Model::class.java, "renamedDefault").name)
        for (name in listOf("wrongReturn", "staticSetter", "absent")) {
            assertTrue(name, runCatching { LzlSortContract.restore(Model::class.java, name) }.isFailure)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private class Model {
        fun renamedDefault(sort: String?) {}
        fun wrongReturn(sort: String?): Boolean = false
        companion object { @JvmStatic fun staticSetter(sort: String?) {} }
    }
}
