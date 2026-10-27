package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStepUtilsTest {
    @Test
    fun recoverableCandidateReflectionFailureDoesNotEnterScanErrors() = withScanErrors { errors ->
        val result = scanSubStep("ScanRules.SettingsLevel1Rule.com.baidu.tieba.fy.ClassShape", null, "fallback") {
            throw NoClassDefFoundError("Failed resolution of: Lcom/baidu/searchbox/boxdownload/IBoxDownloadDbOperator;")
        }

        assertEquals("fallback", result)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun nonCandidateNoClassDefStillEntersScanErrors() = withScanErrors { errors ->
        val result = scanSubStep("StrategyAdHook.SplashAdHelper", null, "fallback") {
            throw NoClassDefFoundError("Failed resolution of: Lcom/example/Missing;")
        }

        assertEquals("fallback", result)
        assertEquals(1, errors.size)
        assertTrue(errors.single().startsWith("StrategyAdHook.SplashAdHelper :: java.lang.NoClassDefFoundError"))
    }

    @Test
    fun ordinaryCandidateExceptionStillEntersScanErrors() = withScanErrors { errors ->
        val result = scanSubStep("ScanRules.SettingsLevel1Rule.com.baidu.tieba.wsd.ClassShape", null, "fallback") {
            throw IllegalStateException("bad scan rule")
        }

        assertEquals("fallback", result)
        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("bad scan rule"))
    }

    @Test
    fun candidateReflectionClassifierOnlyMatchesReflectionProbeTags() {
        assertTrue(
            isRecoverableCandidateReflectionFailure(
                "ImageViewerNativeShareHook.Icon.com.baidu.tieba.b12.OwnerShape",
                NoClassDefFoundError("missing"),
            ),
        )
        assertFalse(
            isRecoverableCandidateReflectionFailure(
                "ImageViewerNativeShareHook.Icon",
                NoClassDefFoundError("missing"),
            ),
        )
        assertFalse(
            isRecoverableCandidateReflectionFailure(
                "ImageViewerNativeShareHook.Icon.com.baidu.tieba.b12.OwnerShape",
                IllegalStateException("bad scan rule"),
            ),
        )
    }

    private fun withScanErrors(block: (MutableList<String>) -> Unit) {
        val errors = mutableListOf<String>()
        val context = HookSymbolScanContext(requireNotNull(javaClass.classLoader))
        context.scanErrors = errors
        HookSymbolScanSession.set(context)
        try {
            block(errors)
        } finally {
            HookSymbolScanSession.set(null)
        }
    }
}
