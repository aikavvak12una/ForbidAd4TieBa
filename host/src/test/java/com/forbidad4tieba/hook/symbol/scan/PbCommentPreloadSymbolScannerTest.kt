package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PbCommentPreloadSymbolScannerTest {
    class Config {
        fun threshold(): Int = 5
        fun wrongResult(): Boolean = true
        fun parameter(value: Int): Int = value
        companion object { @JvmStatic fun staticThreshold(): Int = 5 }
    }

    @Test
    fun restoringMissingOrInvalidThresholdDisablesOnlyThisTarget() {
        val cl = javaClass.classLoader!!
        val owner = Config::class.java.name
        val restored = PbCommentPreloadSymbolScanner.restore(cl, "$owner#threshold")
        assertNotNull(restored)
        assertEquals(5, restored!!.invoke(Config()))
        for (spec in listOf(null, "", "missing.Config#threshold", "$owner#missing", "$owner#parameter",
            "$owner#wrongResult", "$owner#staticThreshold", "$owner#threshold#extra")) {
            assertNull(spec, PbCommentPreloadSymbolScanner.restore(cl, spec))
        }
    }

    @Test
    fun equallyQualifiedThresholdsAreNeverChosenByNameOrOrder() {
        assertNull(selectUniqueScanCandidate("comment preload", emptyList<String>(), null) { it })
        assertNull(selectUniqueScanCandidate("comment preload", listOf("a", "b"), null) { it })
        assertNull(selectUniqueScanCandidate("comment preload", listOf("b", "a"), null) { it })
    }
}
