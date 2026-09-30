package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LzlSortSymbolScannerTest {
    @Test fun selectsOnlyTheSetterWithInitializationAndRequestEvidence() {
        val expected = Model::class.java.getDeclaredMethod("renamedDefault", String::class.java)
        assertEquals(expected, LzlSortSymbolScanner.selectSetter(Model::class.java, null) { it == expected })
    }

    @Test fun missingOrAmbiguousEvidenceNeverChoosesAnArbitrarySetter() {
        assertNull(LzlSortSymbolScanner.selectSetter(Model::class.java, null) { false })
        assertNull(LzlSortSymbolScanner.selectSetter(Model::class.java, null) { true })
    }

    @Test fun semanticEvidenceCannotOverrideAnInvalidSignature() {
        for (name in listOf("wrongReturn", "wrongParameter", "staticSetter")) {
            assertNull(name, LzlSortSymbolScanner.selectSetter(Model::class.java, null) { it.name == name })
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private class Model {
        fun renamedDefault(sort: String?) {}
        fun unrelated(value: String?) {}
        fun wrongReturn(sort: String?): Boolean = false
        fun wrongParameter(sort: Int) {}
        companion object { @JvmStatic fun staticSetter(sort: String?) {} }
    }
}
