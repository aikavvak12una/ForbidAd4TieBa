package com.forbidad4tieba.hook.symbol.scan

import com.baidu.tbadk.abtest.UbsABTestHelper
import org.junit.Assert.*
import org.junit.Test

class PerformanceAbSymbolScannerTest {
    @Test fun retainedAbCacheRejectsRetiredUnknownAndDuplicateEntries() {
        val loader = javaClass.classLoader!!
        val names = listOf("cookieRepeatedOpt", "isPbCommentFunAdABTest", "isPbPageBannerFunAdSdkTest")
        assertEquals(names.toSet(), PerformanceAbSymbolScanner.restore(loader, names).keys)
        assertTrue(PerformanceAbSymbolScanner.isCacheValid(loader, names))
        for (extra in listOf("coldStartTTIOpt", "idleTaskOpt", "hybridPbOpt", "unknown", "cookieRepeatedOpt")) {
            assertFalse(extra, PerformanceAbSymbolScanner.isCacheValid(loader, names + extra))
        }
    }

    @Test fun onlyPublicStaticBooleanNoArgumentMethodsAreAccepted() {
        val owner = UbsABTestHelper::class.java
        assertTrue(PerformanceAbSymbolScanner.isValidMethod(owner.getDeclaredMethod("cookieRepeatedOpt")))
        for (name in listOf("instanceFlag", "privateFlag", "integerFlag")) {
            assertFalse(name, PerformanceAbSymbolScanner.isValidMethod(owner.getDeclaredMethod(name)))
        }
        assertFalse(PerformanceAbSymbolScanner.isValidMethod(owner.getDeclaredMethod("parameterFlag", String::class.java)))
    }
}
