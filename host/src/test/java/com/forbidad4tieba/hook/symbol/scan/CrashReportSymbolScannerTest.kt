package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrashReportSymbolScannerTest {
    @Test fun theOldNameHasNoPreferenceOverACalledReportMethod() {
        val target = Handler::class.java.getDeclaredMethod("renamed", Throwable::class.java)
        assertEquals(target, CrashReportSymbolScanner.selectReportMethod(Handler::class.java, null) { it == target })
    }

    @Test fun rejectsMissingAndAmbiguousReportEvidence() {
        assertNull(CrashReportSymbolScanner.selectReportMethod(Handler::class.java, null) { false })
        assertNull(CrashReportSymbolScanner.selectReportMethod(Handler::class.java, null) { true })
    }

    @Test fun reportEvidenceCannotOverrideSignatureOrHandlerValidation() {
        listOf("wrongReturn", "wrongParameter", "staticReport").forEach { name ->
            val target = Handler::class.java.declaredMethods.single { it.name == name }
            assertNull(name, CrashReportSymbolScanner.selectReportMethod(Handler::class.java, null) { it == target })
        }
        assertNull(CrashReportSymbolScanner.selectReportMethod(Unrelated::class.java, null) { true })
    }

    @Suppress("UNUSED_PARAMETER")
    private class Handler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {}
        fun b(error: Throwable) {}
        fun renamed(error: Throwable) {}
        fun wrongReturn(error: Throwable): Boolean = false
        fun wrongParameter(error: Error) {}
        companion object { @JvmStatic fun staticReport(error: Throwable) {} }
    }

    @Suppress("UNUSED_PARAMETER")
    private class Unrelated { fun renamed(error: Throwable) {} }
}
