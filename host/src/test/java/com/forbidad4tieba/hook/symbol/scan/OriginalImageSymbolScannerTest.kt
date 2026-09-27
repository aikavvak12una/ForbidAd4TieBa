package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.DexOriginalImageMethodsMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginalImageSymbolScannerTest {
    @Test
    fun acceptsSemanticMethodNamesWithoutDependingOnObfuscatedNames() {
        val result = OriginalImageSymbolScanner.validateDexMethodMatch(
            urlDragClass = ShiftedMethodFixture::class.java,
            match = DexOriginalImageMethodsMatch(
                primaryReadyMethod = "prepareOriginal",
                triggerMethod = "requestOriginal",
                directStartMethod = "startOriginal",
                evidence = "test",
            ),
            logger = null,
        )

        assertEquals("prepareOriginal", result?.primaryReadyMethod)
        assertEquals("requestOriginal", result?.triggerMethod)
        assertEquals("startOriginal", result?.directStartMethod)
    }

    @Test
    fun failsClosedWhenCriticalTriggerSignatureDoesNotMatch() {
        val result = OriginalImageSymbolScanner.validateDexMethodMatch(
            urlDragClass = InvalidTriggerFixture::class.java,
            match = DexOriginalImageMethodsMatch(
                primaryReadyMethod = "prepareOriginal",
                triggerMethod = "requestOriginal",
                directStartMethod = "startOriginal",
                evidence = "test",
            ),
            logger = null,
        )

        assertNull(result)
    }

    private class ShiftedMethodFixture {
        fun prepareOriginal() = Unit

        fun requestOriginal() = Unit

        fun startOriginal(downloadName: String) {
            downloadName.length
        }
    }

    private class InvalidTriggerFixture {
        fun prepareOriginal() = Unit

        fun requestOriginal(force: Boolean) {
            force.hashCode()
        }

        fun startOriginal(downloadName: String) {
            downloadName.length
        }
    }
}
