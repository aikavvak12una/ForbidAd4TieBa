package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.*
import org.junit.Test

class CollectionSearchIndexTest {
    @Test fun everyMatchedRowKeepsItsOriginalPositionIncludingRepeatedObjects() {
        val shared = Any()
        val source = listOf(Any(), shared, Any(), shared, Any())
        val result = CollectionSearchIndex.filter(source, listOf("needle", "two")) {
            if (it === shared) "needle and two" else "only needle"
        }
        assertEquals(listOf(shared, shared), result.items)
        assertArrayEquals(intArrayOf(1, 3), result.positions)
        result.items.forEachIndexed { index, item -> assertSame(item, source[result.positions[index]]) }
        assertEquals(5, source.size)
    }

    @Test fun emptyQueryAvoidsTextAccessAndLargeIndexesAreExact() {
        val source = List(4096) { Any() }
        val all = CollectionSearchIndex.filter(source, emptyList()) { error("No query needs no text") }
        assertEquals(source, all.items)
        assertArrayEquals(IntArray(source.size) { it }, all.positions)
        val none = CollectionSearchIndex.filter(source, listOf("missing")) { "text" }
        assertTrue(none.items.isEmpty())
        assertArrayEquals(intArrayOf(), none.positions)
    }
}
