package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.*
import org.junit.Assert.*
import org.junit.Test

class InlineReplyContractTest {
    @Test fun absentAndInvalidTargetsDisableOnlyThisCapabilityAndRoundTrip() {
        val c = InlineReplyContract
        val empty = buildHookSymbols { this[LzlSortContract.defaultSortSetter] = "fixture" }
        assertFalse(c.capabilities(empty).getValue(HookFeatureKey.INLINE_REPLY_REPAIR).isSupported())
        assertTrue(LzlSortContract.capabilities(empty).getValue(HookFeatureKey.DEFAULT_LZL_EARLIEST).isSupported())
        assertTrue(c.isCacheValid(empty, javaClass.classLoader!!))
        val broken = buildHookSymbols { this[c.targets] = "{\"bind\":\"missing\"}" }
        assertEquals(broken, HookSymbols.fromJson(broken.toJson()))
        assertNull(c.resolve(javaClass.classLoader!!, broken))
        assertFalse(c.isCacheValid(broken, javaClass.classLoader!!))
        assertEquals(listOf(HookFeatureKey.INLINE_REPLY_REPAIR), SymbolContracts.featuresForPoint("InlineReplyRepair"))
    }
}
