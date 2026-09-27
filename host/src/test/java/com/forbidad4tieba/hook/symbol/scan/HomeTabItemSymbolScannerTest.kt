package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTabItemSymbolScannerTest {
    @Test fun resolvesItemTypeFromRenamedFactoryAndListDescriptor() {
        assertEquals(Item::class.java, HomeTabItemSymbolScanner.resolveHomeTabItemClass(Home::class.java, "items"))
    }

    @Test fun aMissingCachedListNameDoesNotFallBackToAnArbitraryList() {
        assertNull(HomeTabItemSymbolScanner.resolveHomeTabItemClass(Home::class.java, "b"))
    }

    @Test fun twoFactoriesAreAmbiguousEvenIfOneKeepsTheOldName() {
        assertNull(HomeTabItemSymbolScanner.resolveHomeTabItemClass(Ambiguous::class.java, "items"))
    }

    @Test fun listAndFactoryMustAgreeOnItemType() {
        assertNull(HomeTabItemSymbolScanner.resolveHomeTabItemClass(Mismatched::class.java, "items"))
    }

    private class Item
    private class DifferentItem

    @Suppress("UNUSED_PARAMETER")
    private class Home {
        @JvmField val items: List<Item> = emptyList()
        fun renamed(type: Int, name: String, code: String, main: Boolean): Item = Item()
    }

    @Suppress("UNUSED_PARAMETER")
    private class Ambiguous {
        @JvmField val items: List<Item> = emptyList()
        fun a(type: Int, name: String, code: String, main: Boolean): Item = Item()
        fun renamed(type: Int, name: String, code: String, main: Boolean): Item = Item()
    }

    @Suppress("UNUSED_PARAMETER")
    private class Mismatched {
        @JvmField val items: List<DifferentItem> = emptyList()
        fun renamed(type: Int, name: String, code: String, main: Boolean): Item = Item()
    }
}
