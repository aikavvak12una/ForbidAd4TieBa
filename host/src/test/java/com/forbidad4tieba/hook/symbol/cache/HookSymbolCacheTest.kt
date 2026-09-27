package com.forbidad4tieba.hook.symbol.cache

import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.*
import org.junit.Test

class HookSymbolCacheTest {
    @Test fun changedFingerprintDoesNotReadOrDecodePayload() {
        assertNull(HookSymbolCachePolicy.decodeIfFingerprint("old-host", "new-host") {
            error("Stale JSON must not be read")
        })
        assertNull(HookSymbolCachePolicy.decodeIfFingerprint(null, "host") {
            error("Missing fingerprint must not read JSON")
        })
    }

    @Test fun currentFingerprintReadsOnePayloadAndRejectsMalformedJson() {
        val symbols = HookSymbols.unsupported(createdAt = 1)
        var reads = 0
        assertEquals(symbols, HookSymbolCachePolicy.decodeIfFingerprint("host", "host") {
            reads++
            symbols.toJson()
        })
        assertEquals(1, reads)
        assertNull(HookSymbolCachePolicy.decodeIfFingerprint("host", "host") { "broken" })
    }

    @Test fun bothPositiveAndUnsupportedCachesRequireCurrentSchemaAndRule() {
        for (source in listOf("scan", "partial", "unsupported")) {
            val symbols = buildHookSymbols { this.source = source; createdAt = 1 }
            assertTrue(HookSymbolCachePolicy.isUsable(symbols))
            assertFalse(HookSymbolCachePolicy.isUsable(symbols.copy(meta = symbols.meta.copy(
                cacheSchemaVersion = HookSymbols.CACHE_SCHEMA_VERSION - 1,
            ))))
            assertFalse(HookSymbolCachePolicy.isUsable(symbols.copy(meta = symbols.meta.copy(
                dexKitRuleVersion = HookSymbols.DEXKIT_RULE_VERSION - 1,
            ))))
        }
        assertFalse(HookSymbolCachePolicy.isUsable(buildHookSymbols { source = "other"; createdAt = 1 }))
        assertFalse(HookSymbolCachePolicy.isUsable(buildHookSymbols { source = "scan"; createdAt = 0 }))
    }

    @Test fun memoryCachePublishesAndInvalidatesFingerprintWithItsSymbols() {
        val cache = HookSymbolMemoryCache()
        val first = HookSymbols.unsupported(createdAt = 1)
        val second = HookSymbols.unsupported(createdAt = 2)
        cache.put("first", first)
        assertSame(first, cache.getIfFingerprint("first"))
        cache.put("second", second)
        assertNull(cache.getIfFingerprint("first"))
        assertSame(second, cache.getIfFingerprint("second"))
        cache.clear()
        assertNull(cache.currentSymbols())
        assertNull(cache.getIfFingerprint("second"))
    }
}
