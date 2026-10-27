package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.os.Bundle
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.scan.SwanPreloadSymbolScanner
import org.junit.Assert.*
import org.junit.Test

class BackgroundPreloadContractTest {
    private val loader = javaClass.classLoader!!
    private fun yy(names: List<String> = YyBackgroundTarget.entries.map { it.name }, ready: String? = "renamedReadiness") = buildHookSymbols {
        this[YyBackgroundContract.targets] = names
        this[YyBackgroundContract.readiness] = ready
    }

    @Test fun yyRoundTripRestoresRenamedPrivateReadinessWithoutInitializingTheHost() {
        val original = yy()
        val copy = requireNotNull(HookSymbols.fromJson(original.toJson()))
        val targets = requireNotNull(YyBackgroundContract.resolve(loader, copy))
        assertEquals("renamedReadiness", targets.readiness.name)
        assertEquals(YyBackgroundTarget.entries.toSet(), targets.members.keys)
        assertTrue(YyBackgroundContract.isCacheValid(copy, loader))
        assertEquals(HookFeatureState.FULL, YyBackgroundContract.capabilities(copy).getValue(HookFeatureKey.DISABLE_YY_BACKGROUND).state)
        for (missing in YyBackgroundTarget.entries) {
            val partial = yy(YyBackgroundTarget.entries.filter { it != missing }.map { it.name })
            assertNull(YyBackgroundContract.resolve(loader, partial))
            assertTrue(YyBackgroundContract.isCacheValid(partial, loader))
            assertEquals(HookFeatureState.DISABLED, YyBackgroundContract.capabilities(partial).getValue(HookFeatureKey.DISABLE_YY_BACKGROUND).state)
        }
        assertNull(YyBackgroundContract.resolve(loader, yy(ready = null)))
        assertFalse(YyBackgroundContract.isCacheValid(yy(ready = "missing"), loader))
        assertFalse(YyBackgroundContract.isCacheValid(yy(listOf("CONSTRUCTOR", "CONSTRUCTOR")), loader))
        assertFalse(YyBackgroundContract.isCacheValid(yy(listOf("UNKNOWN")), loader))
    }

    @Test fun swanRestoresOnlyExactCachedSignaturesAndKeepsMissingEvidence() {
        val owner = SwanFixture::class.java.name
        val symbols = buildHookSymbols {
            this[SwanPreloadContract.dispatch] = "$owner#renamedDispatch"
            this[SwanPreloadContract.schedule] = "$owner#renamedSchedule"
        }
        val copy = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        assertEquals(listOf("renamedDispatch", "renamedSchedule"), SwanPreloadContract.resolve(loader, copy)!!.map { it.name })
        assertTrue(SwanPreloadContract.isCacheValid(copy, loader))
        for (field in listOf(SwanPreloadContract.dispatch, SwanPreloadContract.schedule)) {
            val missing = buildHookSymbols {
                this[SwanPreloadContract.dispatch] = if (field == SwanPreloadContract.dispatch) null else "$owner#renamedDispatch"
                this[SwanPreloadContract.schedule] = if (field == SwanPreloadContract.schedule) null else "$owner#renamedSchedule"
            }
            assertNull(SwanPreloadContract.resolve(loader, missing))
            assertTrue(SwanPreloadContract.isCacheValid(missing, loader))
        }
        assertThrows(IllegalStateException::class.java) { SwanPreloadSymbolScanner.restore(loader, "$owner#wrongReturn", false) }
        assertThrows(IllegalStateException::class.java) { SwanPreloadSymbolScanner.restore(loader, "$owner#instanceDispatch", false) }
        val crossedOwners = buildHookSymbols {
            this[SwanPreloadContract.dispatch] = "$owner#renamedDispatch"
            this[SwanPreloadContract.schedule] = "${OtherSwanFixture::class.java.name}#schedule"
        }
        assertFalse(SwanPreloadContract.isCacheValid(crossedOwners, loader))
        assertNull(SwanPreloadContract.resolve(loader, crossedOwners))
    }

    class SwanFixture {
        fun instanceDispatch(context: Context, bundle: Bundle) = Unit
        companion object {
            @JvmStatic fun renamedDispatch(context: Context, bundle: Bundle) = Unit
            @JvmStatic fun renamedSchedule(context: Context, client: Any, bundle: Bundle) = Unit
            @JvmStatic fun wrongReturn(context: Context, bundle: Bundle) = true
        }
    }

    class OtherSwanFixture {
        companion object {
            @JvmStatic fun schedule(context: Context, client: Any, bundle: Bundle) = Unit
        }
    }
}
