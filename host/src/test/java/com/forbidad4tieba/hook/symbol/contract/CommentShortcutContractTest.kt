package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.*
import org.junit.Assert.*
import org.junit.Test

class CommentShortcutContractTest {
    @Test fun invalidShortcutCacheDoesNotInventTargetsAndAbsentResultIsNegativeCached() {
        val c = CommentShortcutContract
        val empty = buildHookSymbols { this[LzlSortContract.defaultSortSetter] = "independent" }
        assertFalse(c.capabilities(empty).getValue(HookFeatureKey.COMMENT_SHORTCUT).isSupported())
        assertTrue(LzlSortContract.capabilities(empty).getValue(HookFeatureKey.DEFAULT_LZL_EARLIEST).isSupported())
        assertTrue(c.isCacheValid(empty, javaClass.classLoader!!))
        val invalid = buildHookSymbols { this[c.targets] = "{}" }
        assertEquals(invalid, HookSymbols.fromJson(invalid.toJson()))
        assertNull(c.resolve(javaClass.classLoader!!, invalid))
        assertFalse(c.isCacheValid(invalid, javaClass.classLoader!!))
        assertEquals(listOf(HookFeatureKey.COMMENT_SHORTCUT), SymbolContracts.featuresForPoint("CommentShortcut"))
    }
}
