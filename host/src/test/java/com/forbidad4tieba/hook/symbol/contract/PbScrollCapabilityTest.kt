package com.forbidad4tieba.hook.symbol.contract

import com.baidu.tieba.pb.pb.main.PbFragment
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.status.HookFeatureStatusDeriver
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PbScrollCapabilityTest {
    private val loader = javaClass.classLoader!!
    private val evidence = linkedMapOf(
        "pbCommentScrollFragmentField" to "owner",
        "pbCommentScrollBottomListenerField" to "bottom",
        "pbCommentScrollBottomMethod" to "onScrollToBottom",
    )

    private fun fixture(
        type: Class<*> = Scroll::class.java,
        method: String = "position",
        changes: Map<String, String?> = emptyMap(),
    ): HookSymbols {
        val json = JSONObject(buildHookSymbols {}.toJson())
            .put("pbCommentScrollListenerClass", type.name)
            .put("pbCommentScrollMethod", method)
        evidence.forEach { (key, value) -> json.put(key, value) }
        changes.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
        return requireNotNull(HookSymbols.fromJson(json.toString()))
    }

    @Test fun everyEvidencePresenceCombinationPreservesTheRuntimeTargetAndFullCapability() {
        val full = fixture()
        val target = requireNotNull(AutoLoadMoreContract.resolvePbScrollCoalesceSymbols(loader, full)).scrollMethod
        repeat(8) { mask ->
            val changes = evidence.keys.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.associateWith { null }
            val symbols = fixture(changes = changes)
            val status = AutoLoadMoreContract.capabilities(symbols).getValue(HookFeatureKey.ENABLE_PB_SCROLL_COALESCE)
            assertEquals(HookFeatureState.FULL, status.state)
            assertTrue(status.missingCritical.isEmpty())
            assertTrue(status.missingOptional.isEmpty())
            assertEquals(target, AutoLoadMoreContract.resolvePbScrollCoalesceSymbols(loader, symbols)?.scrollMethod)
            assertTrue(AutoLoadMoreContract.isCacheValid(symbols, loader))
            assertEquals(HookFeatureStatusDeriver.derive(full), HookFeatureStatusDeriver.derive(symbols))
            assertEquals(listOf("AutoLoadMoreHook.Config", "PbScrollCoalesceHook", "PbCommentAutoLoadHook", "PbCommentAutoLoadHook.Batch"),
                AutoLoadMoreContract.points(symbols).map { it.name })
        }
    }

    @Test fun missingOrBlankRuntimeDescriptorsDisableOnlyScrollCoalescing() {
        val full = fixture()
        for (key in listOf("pbCommentScrollListenerClass", "pbCommentScrollMethod")) {
            for (value in listOf(null, "", " \t")) {
                val symbols = fixture(changes = mapOf(key to value))
                val status = AutoLoadMoreContract.capabilities(symbols).getValue(HookFeatureKey.ENABLE_PB_SCROLL_COALESCE)
                assertEquals(HookFeatureState.DISABLED, status.state)
                assertEquals(listOf(key), status.missingCritical)
                assertTrue(status.missingOptional.isEmpty())
                assertNull(AutoLoadMoreContract.resolvePbScrollCoalesceSymbols(loader, symbols))
                assertFalse(AutoLoadMoreContract.isCacheValid(symbols, loader))
                assertEquals(HookPointState.MISSING, AutoLoadMoreContract.points(symbols)
                    .single { it.name == "PbScrollCoalesceHook" }.state)
                assertEquals(HookFeatureStatusDeriver.derive(full) - HookFeatureKey.ENABLE_PB_SCROLL_COALESCE,
                    HookFeatureStatusDeriver.derive(symbols) - HookFeatureKey.ENABLE_PB_SCROLL_COALESCE)
            }
        }
    }

    @Test fun signatureMismatchMissingClassesAndAmbiguityCannotProduceAnInstallTarget() {
        for (method in listOf("absent", "wrongReturn", "wrongParameter", "staticPosition")) {
            val symbols = fixture(method = method)
            assertNull(method, AutoLoadMoreContract.resolvePbScrollCoalesceSymbols(loader, symbols))
            assertFalse(method, AutoLoadMoreContract.isCacheValid(symbols, loader))
        }
        assertNull(AutoLoadMoreContract.resolvePbScrollCoalesceSymbols(loader, fixture(AmbiguousScroll::class.java)))
        val missingClass = fixture(changes = mapOf("pbCommentScrollListenerClass" to "fixture.AbsentScroll"))
        assertNull(AutoLoadMoreContract.resolvePbScrollCoalesceSymbols(loader, missingClass))
        assertFalse(AutoLoadMoreContract.isCacheValid(missingClass, loader))
    }

    @Test fun structuralEvidenceStillRoundTripsAndRejectsInvalidCompleteCacheChains() {
        val full = fixture()
        val restored = requireNotNull(HookSymbols.fromJson(full.toJson()))
        evidence.forEach { (key, value) -> assertEquals(value, JSONObject(restored.toJson()).getString(key)) }
        assertTrue(AutoLoadMoreContract.isCacheValid(restored, loader))
        evidence.keys.forEach { key ->
            assertFalse(key, AutoLoadMoreContract.isCacheValid(fixture(changes = mapOf(key to "absent")), loader))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    class Scroll {
        @JvmField var owner: PbFragment? = null
        fun position(view: Any, first: Int, visible: Int, total: Int) = Unit
        fun wrongReturn(view: Any, first: Int, visible: Int, total: Int) = true
        fun wrongParameter(view: Any, first: Int, visible: Int, total: String) = Unit
        companion object {
            @JvmStatic fun staticPosition(view: Any, first: Int, visible: Int, total: Int) = Unit
        }
    }

    @Suppress("UNUSED_PARAMETER")
    class AmbiguousScroll {
        fun position(view: Any, first: Int, visible: Int, total: Int) = Unit
        fun position(view: String, first: Int, visible: Int, total: Int) = Unit
    }
}
