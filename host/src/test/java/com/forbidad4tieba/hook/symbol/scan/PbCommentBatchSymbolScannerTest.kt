package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertNull
import org.junit.Test

class PbCommentBatchSymbolScannerTest {
    @Test fun missingOrMalformedBatchDescriptorFailsClosed() {
        for (spec in listOf(null, "", "not-json", "{}", "{\"scrollClass\":\"missing.Class\"}")) {
            assertNull(PbCommentBatchSymbolScanner.restore(javaClass.classLoader!!, spec))
        }
    }
}
