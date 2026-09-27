package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportContractTest {
    private val loader = javaClass.classLoader!!
    private fun symbols(name: String?) = buildHookSymbols { this[CrashReportContract.crashReportExceptionMethod] = name }

    @Test fun restoresAndRoundTripsTheSelectedName() {
        val saved = symbols("renamed")
        val restored = HookSymbols.fromJson(saved.toJson())!!
        assertEquals(saved, restored)
        assertEquals("renamed", CrashReportContract.resolveExceptionReportMethod(loader, restored)?.name)
        assertTrue(CrashReportContract.isCacheValid(restored, loader))
        assertEquals(HookPointState.FOUND, CrashReportContract.points(restored).single().state)
    }

    @Test fun invalidCachedNamesAndSignaturesHaveNoFallback() {
        listOf("b", "missing", "wrongSignature").forEach { name ->
            assertNull(CrashReportContract.resolveExceptionReportMethod(loader, symbols(name)))
            assertFalse(CrashReportContract.isCacheValid(symbols(name), loader))
        }
    }

    @Test fun absenceOnlyDisablesTheOptionalReportPoint() {
        val empty = symbols(null)
        assertTrue(CrashReportContract.isCacheValid(empty, loader))
        assertNull(CrashReportContract.resolveExceptionReportMethod(loader, empty))
        assertEquals(HookPointState.OPTIONAL, CrashReportContract.points(empty).single().state)
        assertFalse(CrashReportContract.points(empty).single().isUnavailable())
        assertTrue(CrashReportContract.capabilities(empty).isEmpty())
    }
}
