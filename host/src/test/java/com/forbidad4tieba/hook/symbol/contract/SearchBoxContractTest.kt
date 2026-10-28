package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.junit.Assert.*
import org.junit.Test

class SearchBoxContractTest {
    private val homeFields = listOf(SearchBoxContract.searchBoxViewClass, SearchBoxContract.homeSearchBoxOwnerClass,
        SearchBoxContract.homeSearchBoxInitMethod, SearchBoxContract.homeSearchBoxGetterMethod)

    private fun symbols(missing: SymbolField<String?>? = null, hint: Boolean = true) = buildHookSymbols {
        this[SearchBoxContract.searchBoxViewClass] = StableTiebaHookPoints.TB_SEARCH_BOX_VIEW_CLASS
        this[SearchBoxContract.homeSearchBoxOwnerClass] = StableTiebaHookPoints.HOME_SEARCH_BOX_OWNER_CLASS
        this[SearchBoxContract.homeSearchBoxInitMethod] = "renamedInit"
        this[SearchBoxContract.homeSearchBoxGetterMethod] = "renamedGetter"
        if (hint) this[SearchBoxContract.searchBoxSetHintMethod] = "setHintTextDataList"
        if (missing != null) this[missing] = null
    }

    @Test fun sharedDescriptorsRoundTripAndBothFeaturesHaveDiagnostics() {
        val full = symbols()
        assertEquals(full, HookSymbols.fromJson(full.toJson()))
        assertTrue(SearchBoxContract.capabilities(full).values.all { it.isSupported() })
        assertTrue(SearchBoxContract.points(full).all { it.state == HookPointState.FOUND })
        assertEquals(listOf(HookFeatureKey.HIDE_RECOMMEND_SEARCH_BOX),
            SymbolContracts.featuresForPoint("RecommendSearchBoxHook"))
    }

    @Test fun hintFailureDisablesOnlyTextBlockingAndEachHomeDependencyIsRequired() {
        val noHint = symbols(hint = false)
        val states = SearchBoxContract.capabilities(noHint)
        assertTrue(states.getValue(HookFeatureKey.HIDE_RECOMMEND_SEARCH_BOX).isSupported())
        assertFalse(states.getValue(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT).isSupported())
        assertEquals(HookPointState.FOUND, SearchBoxContract.points(noHint).single {
            it.name == "RecommendSearchBoxHook"
        }.state)
        homeFields.forEach { field ->
            val missing = symbols(missing = field)
            assertFalse(field.cacheKey, SearchBoxContract.capabilities(missing).values.any { it.isSupported() })
            assertNull(SearchBoxContract.resolveHomeSearchBoxSymbols(javaClass.classLoader!!, missing))
            assertTrue(SearchBoxContract.isCacheValid(missing, javaClass.classLoader!!))
        }
    }

    @Test fun missingDiscoveryIsCachedButCompleteInvalidDescriptorsAreRejected() {
        val loader = javaClass.classLoader!!
        assertTrue(SearchBoxContract.isCacheValid(buildHookSymbols {}, loader))
        assertNull(SearchBoxContract.resolveHomeSearchBoxSymbols(loader, symbols()))
        assertFalse(SearchBoxContract.isCacheValid(symbols(), loader))
    }

    @Test fun restorationRejectsStaticWrongReturnWrongParametersAndMissingMembers() {
        fun restore(init: String, getter: String) = SearchBoxContract.restoreHome(
            Controller::class.java, Box::class.java, Recycler::class.java, Container::class.java, init, getter,
        )
        assertEquals("renamedInit", restore("renamedInit", "renamedGetter").ownerInitMethod.name)
        for (init in listOf("staticInit", "wrongReturn", "wrongParams", "missing")) {
            assertTrue(init, runCatching { restore(init, "renamedGetter") }.isFailure)
        }
        for (getter in listOf("staticGetter", "wrongGetter", "missing")) {
            assertTrue(getter, runCatching { restore("renamedInit", getter) }.isFailure)
        }
    }

    private class Box
    private class Recycler
    private class Container
    @Suppress("UNUSED_PARAMETER")
    private class Controller {
        fun renamedInit(recycler: Recycler, container: Container) {}
        fun renamedGetter(): Box? = null
        fun wrongReturn(recycler: Recycler, container: Container): Boolean = false
        fun wrongParams(recycler: Recycler) {}
        fun wrongGetter(): String = ""
        companion object {
            @JvmStatic fun staticInit(recycler: Recycler, container: Container) {}
            @JvmStatic fun staticGetter(): Box? = null
        }
    }
}
