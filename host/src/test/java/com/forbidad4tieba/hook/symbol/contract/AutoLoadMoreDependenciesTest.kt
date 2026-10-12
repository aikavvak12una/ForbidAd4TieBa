package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AutoLoadMoreDependenciesTest {
    // Expected runtime roles are independent of the contract's dependency declarations.
    private val loadMoreOptional = listOf(
        "autoLoadMoreConfigClass", "autoLoadMoreConfigMethod", "pbCommentPreloadConfigMethodSpec", "pbCommentBatchSpec",
    )
    private val scrollRequired = listOf("pbCommentScrollListenerClass", "pbCommentScrollMethod")
    private val evidence = listOf("pbCommentScrollFragmentField", "pbCommentScrollBottomListenerField", "pbCommentScrollBottomMethod")
    private val keys = loadMoreOptional + scrollRequired + evidence
    private val pointRequirements = linkedMapOf(
        "AutoLoadMoreHook.Config" to listOf("autoLoadMoreConfigClass", "autoLoadMoreConfigMethod"),
        "PbScrollCoalesceHook" to scrollRequired,
        "PbCommentAutoLoadHook" to listOf("pbCommentPreloadConfigMethodSpec"),
        "PbCommentAutoLoadHook.Batch" to listOf("pbCommentPreloadConfigMethodSpec", "pbCommentBatchSpec"),
    )

    private fun fixture(missing: Set<String>, absent: String? = null): HookSymbols = buildHookSymbols {
        val fields = AutoLoadMoreContract.fields.associateBy { it.cacheKey }
        keys.forEach { key ->
            @Suppress("UNCHECKED_CAST")
            val field = fields.getValue(key) as SymbolField<String?>
            this[field] = if (key in missing) absent else "fixture.$key"
        }
    }

    @Test fun allPresenceCombinationsPreserveIndependentAndPrerequisitePointRoles() {
        repeat(1 shl keys.size) { mask ->
            val missing = keys.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            val symbols = fixture(missing)
            val capabilities = AutoLoadMoreContract.capabilities(symbols)
            val optional = loadMoreOptional.filter { it in missing }
            val critical = scrollRequired.filter { it in missing }
            assertEquals(listOf(HookFeatureKey.AUTO_LOAD_MORE, HookFeatureKey.ENABLE_PB_SCROLL_COALESCE), capabilities.keys.toList())
            assertEquals("load more mask=$mask", HookFeatureStatus(
                if (optional.isEmpty()) HookFeatureState.FULL else HookFeatureState.PARTIAL,
                missingOptional = optional,
            ), capabilities[HookFeatureKey.AUTO_LOAD_MORE])
            assertEquals("scroll mask=$mask", HookFeatureStatus(
                if (critical.isEmpty()) HookFeatureState.FULL else HookFeatureState.DISABLED,
                missingCritical = critical,
            ), capabilities[HookFeatureKey.ENABLE_PB_SCROLL_COALESCE])
            val points = AutoLoadMoreContract.points(symbols)
            assertEquals(pointRequirements.keys.toList(), points.map { it.name })
            for (point in points) {
                val expectedMissing = pointRequirements.getValue(point.name).filter { it in missing }
                assertEquals("${point.name} mask=$mask", expectedMissing, point.missing)
                val expectedState = when {
                    expectedMissing.isEmpty() -> HookPointState.FOUND
                    point.name == "PbCommentAutoLoadHook.Batch" -> HookPointState.OPTIONAL
                    else -> HookPointState.MISSING
                }
                assertEquals("${point.name} mask=$mask", expectedState, point.state)
                assertTrue(point.formatLine().contains("missing=${expectedMissing.joinToString(",").ifEmpty { "-" }} target="))
            }
        }
    }

    @Test fun blankDescriptorsHaveTheSameMissingRolesAsNullAndPartialCacheRoundTrips() {
        keys.forEach { key ->
            val missing = setOf(key)
            val absent = fixture(missing)
            listOf("", " \t").forEach { blank ->
                val symbols = fixture(missing, blank)
                assertEquals(AutoLoadMoreContract.capabilities(absent), AutoLoadMoreContract.capabilities(symbols))
                assertEquals(AutoLoadMoreContract.points(absent).map { it.name to it.missing },
                    AutoLoadMoreContract.points(symbols).map { it.name to it.missing })
            }
            val restored = requireNotNull(HookSymbols.fromJson(absent.toJson()))
            assertEquals(absent, restored)
            assertEquals(AutoLoadMoreContract.points(absent), AutoLoadMoreContract.points(restored))
            assertEquals(AutoLoadMoreContract.capabilities(absent), AutoLoadMoreContract.capabilities(restored))
        }
    }

    @Test fun cacheValidationNeverSkipsPresentIncompleteDescriptorGroups() {
        val loader = javaClass.classLoader!!
        assertTrue(AutoLoadMoreContract.isCacheValid(fixture(keys.toSet()), loader))
        for (present in keys) {
            assertFalse(present, AutoLoadMoreContract.isCacheValid(fixture(keys.toSet() - present), loader))
        }
        val config = buildHookSymbols {
            this[AutoLoadMoreContract.autoLoadMoreConfigClass] = Config::class.java.name
            this[AutoLoadMoreContract.autoLoadMoreConfigMethod] = "threshold"
        }
        assertTrue(AutoLoadMoreContract.isCacheValid(config, loader))
        assertEquals(Config::class.java.getDeclaredMethod("threshold"),
            AutoLoadMoreContract.resolveAutoLoadMoreSymbols(loader, config)?.configMethod)
    }

    @Test fun centralDiagnosticsUseTheContractProjectionInsteadOfRedefiningFields() {
        val source = File(checkNotNull(System.getProperty("project.root")),
            "host/src/main/java/com/forbidad4tieba/hook/HookSymbolDiagnostics.kt").readText()
        assertTrue("AutoLoadMoreContract.points(symbols)" in source)
        keys.forEach { key -> assertFalse("Diagnostic redefines $key", "AutoLoadMoreContract.$key" in source) }
    }

    class Config {
        fun threshold(): Int = 5
    }
}
