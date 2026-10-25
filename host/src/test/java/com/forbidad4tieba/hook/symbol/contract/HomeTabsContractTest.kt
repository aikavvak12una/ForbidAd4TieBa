package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.*
import org.junit.Test

class HomeTabsContractTest {
    private val loader = javaClass.classLoader!!

    @Test fun parserRoundTripsAndRestoresWithTheLegacyTargets() {
        val symbols = checkNotNull(HookSymbols.fromJson(symbols().toJson()))
        assertEquals("decode", symbols[HomeTabsContract.homeTabParseMethod])
        val targets = checkNotNull(HomeTabsContract.resolveHomeTabSymbols(loader, symbols))
        assertEquals(Home::class.java.getDeclaredMethod("decode"), targets.parseMethod)
        assertTrue(HomeTabsContract.isCacheValid(symbols, loader))
        assertTrue(HomeTabsContract.capabilities(symbols).getValue(HookFeatureKey.FIXED_HOME_TOP_TABS).isSupported())
        assertEquals(listOf(HookFeatureKey.FIXED_HOME_TOP_TABS), SymbolContracts.featuresForPoint("HomeTabHook.Fixed"))
    }

    @Test fun everyFixedDependencyHasMatchingCapabilityAndPointDiagnostics() {
        HomeTabsContract.fixedRequired.fields.forEach { missing ->
            val partial = buildHookSymbols {
                HomeTabsContract.fixedRequired.fields.filterNot { it == missing }.forEach {
                    @Suppress("UNCHECKED_CAST")
                    this[it as SymbolField<String?>] = "fixture"
                }
            }
            val status = HomeTabsContract.capabilities(partial).getValue(HookFeatureKey.FIXED_HOME_TOP_TABS)
            assertFalse(status.isSupported())
            assertEquals(listOf(missing.cacheKey), status.missingCritical)
            assertEquals(status.missingCritical, HomeTabsContract.points(partial).single { it.name == "HomeTabHook.Fixed" }.missing)
        }
    }

    @Test fun missingOrInvalidParserDoesNotRemoveTheOrdinaryTargets() {
        for (parser in listOf(null, "absent", "wrongResult", "withParameter", "staticParser")) {
            val symbols = symbols(parser)
            val targets = checkNotNull(HomeTabsContract.resolveHomeTabSymbols(loader, symbols))
            assertNull("parser=$parser", targets.parseMethod)
            assertEquals("rebuild", targets.rebuildMethod.name)
            assertTrue(HomeTabsContract.capabilities(symbols).getValue(HookFeatureKey.SIMPLIFY_HOME_TOP_TABS).isSupported())
            assertEquals(parser == null, HomeTabsContract.isCacheValid(symbols, loader))
        }
        assertFalse(HomeTabsContract.capabilities(symbols(null)).getValue(HookFeatureKey.FIXED_HOME_TOP_TABS).isSupported())
    }

    @Test fun customListReturnCannotAcceptTheArrayListProjection() {
        assertFalse(HomeTabsContract.isValidParseMethod(Home::class.java.getDeclaredMethod("customList"), Home::class.java))
        assertTrue(HomeTabsContract.isValidParseMethod(Home::class.java.getDeclaredMethod("decode"), Home::class.java))
    }

    private fun symbols(parser: String? = "decode") = buildHookSymbols {
        this[HomeTabsContract.homeTabClass] = Home::class.java.name
        this[HomeTabsContract.homeTabRebuildMethod] = "rebuild"
        this[HomeTabsContract.homeTabListField] = "items"
        this[HomeTabsContract.homeTabParseMethod] = parser
        this[HomeTabsContract.homeTabItemTypeField] = "kind"
        this[HomeTabsContract.homeTabItemCodeField] = "code"
        this[HomeTabsContract.homeTabItemNameField] = "title"
        this[HomeTabsContract.homeTabItemUrlField] = "url"
    }

    private class Item {
        @JvmField var kind = 1
        @JvmField var code = "recommend"
        @JvmField var title = "Title"
        @JvmField var url = ""
    }
    private class CustomList : ArrayList<Item>()
    @Suppress("UNUSED_PARAMETER")
    private class Home {
        @JvmField val items: List<Item> = emptyList()
        fun factory(type: Int, name: String, code: String, main: Boolean): Item = Item()
        fun rebuild() = Unit
        fun decode(): List<Item> = emptyList()
        fun wrongResult(): Int = 0
        fun withParameter(value: Int): List<Item> = emptyList()
        fun customList() = CustomList()
        companion object { @JvmStatic fun staticParser(): List<Item> = emptyList() }
    }
}
