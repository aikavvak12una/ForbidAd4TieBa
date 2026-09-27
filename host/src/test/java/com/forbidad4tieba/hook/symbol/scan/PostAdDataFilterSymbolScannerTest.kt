package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostAdDataFilterSymbolScannerTest {
    @Test
    fun resolvesBothListAdapterDataPaths() {
        val result = PostAdDataFilterSymbolScanner.scanResolvedClasses(
            typeAdapterClass = TypeAdapterFixture::class.java,
            recyclerViewTypeAdapterClass = RecyclerViewTypeAdapterFixture::class.java,
            bdUniqueIdClass = FakeUniqueId::class.java,
            logger = null,
        )

        assertEquals("replaceItems", result.typeAdapterSetDataMethod)
        assertEquals("setRecyclerItems", result.recyclerViewTypeAdapterSetDataMethod)
        assertEquals(FakeItem::class.java.name, result.dataItemClass)
        assertEquals("getType", result.dataGetTypeMethod)
    }

    @Test
    fun keepsAvailableAdapterPathWhenTheOtherClassIsAbsent() {
        val result = PostAdDataFilterSymbolScanner.scanResolvedClasses(
            typeAdapterClass = null,
            recyclerViewTypeAdapterClass = RecyclerViewTypeAdapterFixture::class.java,
            bdUniqueIdClass = FakeUniqueId::class.java,
            logger = null,
        )

        assertNull(result.typeAdapterSetDataMethod)
        assertEquals("setRecyclerItems", result.recyclerViewTypeAdapterSetDataMethod)
        assertEquals(FakeItem::class.java.name, result.dataItemClass)
        assertEquals("getType", result.dataGetTypeMethod)
    }

    private class FakeUniqueId

    private interface FakeItem {
        fun getType(): FakeUniqueId
    }

    private class TypeAdapterFixture {
        fun replaceItems(items: List<FakeItem>) {
            items.size
        }
    }

    private class RecyclerViewTypeAdapterFixture {
        fun setRecyclerItems(items: List<FakeItem>) {
            items.size
        }
    }
}
