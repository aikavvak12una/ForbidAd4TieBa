package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.scan.PbTopEnterBarSymbolScannerTest.RenamedController
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class PbTopEnterBarContractTest {
    private val owner = PbTopEnterBarContract
    private val loader = javaClass.classLoader!!
    private fun complete() = buildHookSymbols {
        this[owner.controllerClass] = RenamedController::class.java.name
        this[owner.initializeMethod] = "initializeForum"
        this[owner.containerField] = "forum"
        this[owner.navigationBarField] = "navigation"
        this[InputMemeBarContract.inputMemeBarControllerClass] = "fixture"
        this[InputMemeBarContract.inputMemeBarEnableMethod] = "enabled"
    }

    @Test fun cachedDescriptorsRoundTripAndRestoreWithOneCapabilityOwner() {
        val symbols = complete()
        val restored = HookSymbols.fromJson(symbols.toJson())!!
        assertEquals(symbols, restored)
        assertNotNull(owner.resolve(loader, restored))
        assertTrue(owner.isCacheValid(restored, loader))
        assertEquals(HookPointState.FOUND, owner.points(restored).single().state)
        assertEquals(listOf(HookFeatureKey.HIDE_PB_TOP_ENTER_BAR), SymbolContracts.featuresForPoint("PbTopEnterBarBlockHook"))
    }

    @Test fun everyRequiredDependencyDisablesOnlyThisCapabilityWhenMissing() {
        owner.required.fields.forEach { field ->
            val json = JSONObject(complete().toJson()).apply { remove(field.cacheKey) }
            val partial = HookSymbols.fromJson(json.toString())!!
            val status = owner.capabilities(partial).getValue(HookFeatureKey.HIDE_PB_TOP_ENTER_BAR)
            assertFalse(status.isSupported())
            assertEquals(listOf(field.cacheKey), status.missingCritical)
            assertEquals(status.missingCritical, owner.points(partial).single().missing)
            assertNull(owner.resolve(loader, partial))
            assertFalse(owner.isCacheValid(partial, loader))
            assertTrue(InputMemeBarContract.capabilities(partial).getValue(HookFeatureKey.HIDE_INPUT_MEME_BAR).isSupported())
        }
    }

    @Test fun negativeCacheStaysUsableAndStaleFieldsFailRestoration() {
        assertTrue(owner.isCacheValid(buildHookSymbols {}, loader))
        val stale = HookSymbols.fromJson(JSONObject(complete().toJson())
            .put(owner.containerField.cacheKey, "wrongContainer").toString())!!
        assertNull(owner.resolve(loader, stale))
        assertFalse(owner.isCacheValid(stale, loader))
        val brokenLoader = object : ClassLoader(loader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == RenamedController::class.java.name) throw LinkageError("fixture")
                return super.loadClass(name, resolve)
            }
        }
        assertNull(owner.resolve(brokenLoader, complete()))
    }
}
