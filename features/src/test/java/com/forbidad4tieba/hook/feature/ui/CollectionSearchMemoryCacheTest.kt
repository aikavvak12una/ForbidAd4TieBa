package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.*
import org.junit.Test

class CollectionSearchMemoryCacheTest {
    @Test fun readsReuseAnImmutableSnapshotAndInputsCannotChangePublishedItems() {
        val cache = CollectionSearchMemoryCache()
        val item = Any()
        val input = arrayListOf(item)
        cache.put("a", input, true)
        val first = cache.get("a")!!
        input.clear()
        assertSame(first, cache.get("a"))
        assertSame(item, first.items.single())
        try {
            (first.items as MutableList<*>).clear()
            fail("Published collections must not be mutable")
        } catch (_: UnsupportedOperationException) { }
        cache.put("a", listOf("new"), false)
        assertSame(item, first.items.single())
        assertEquals(listOf("new"), cache.get("a")!!.items)
        assertFalse(cache.get("a")!!.fullReady)
    }

    @Test fun emptyIncompleteResultsKeepPriorDataAndEmptyCompleteResultsReplaceIt() {
        val cache = CollectionSearchMemoryCache()
        cache.put("a", listOf("old"), true)
        cache.put("a", emptyList(), false)
        assertEquals(listOf("old"), cache.get("a")!!.items)
        cache.put("b", emptyList(), false)
        assertNull(cache.get("b"))
        cache.put("a", emptyList(), true)
        assertTrue(cache.get("a")!!.fullReady)
        assertTrue(cache.get("a")!!.items.isEmpty())
    }

    @Test fun threeAccountLimitPreservesInsertionOrderAcrossReadsAndReplacements() {
        val cache = CollectionSearchMemoryCache()
        for (account in listOf("a", "b", "c")) cache.put(account, listOf(account), true)
        cache.get("a")
        cache.put("a", listOf("replacement"), true)
        cache.put("d", listOf("d"), true)
        assertNull(cache.get("a"))
        assertEquals(listOf("b"), cache.get("b")!!.items)
        assertEquals(listOf("c"), cache.get("c")!!.items)
        assertEquals(listOf("d"), cache.get("d")!!.items)
    }
}
