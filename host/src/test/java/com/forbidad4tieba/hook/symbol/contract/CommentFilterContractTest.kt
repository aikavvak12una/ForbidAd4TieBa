package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.*
import org.junit.Assert.*
import org.junit.Test

class CommentFilterContractTest {
    @Test fun eachConsumerAndProtocolAreRequiredAndPartialResultsRoundTripAsNegativeCache() {
        val c = CommentFilterContract
        val fields = listOf(c.pageNative, c.pageJson, c.floorNative, c.floorJson, c.protocol)
        val complete = buildHookSymbols { fields.forEach { this[it] = "descriptor-${it.cacheKey}" } }
        assertTrue(c.capabilities(complete).getValue(HookFeatureKey.COMMENT_LEVEL_FILTER).isSupported())
        fields.forEach { missing ->
            val partial = buildHookSymbols { fields.filterNot { it == missing }.forEach { this[it] = "descriptor-${it.cacheKey}" } }
            val parsed = HookSymbols.fromJson(partial.toJson())!!
            assertEquals(partial, parsed)
            assertFalse(c.capabilities(parsed).getValue(HookFeatureKey.COMMENT_LEVEL_FILTER).isSupported())
            assertNull(c.resolve(javaClass.classLoader!!, parsed))
        }
        assertTrue(c.isCacheValid(buildHookSymbols {}, javaClass.classLoader!!))
        assertFalse(c.isCacheValid(complete, javaClass.classLoader!!))
    }
}
