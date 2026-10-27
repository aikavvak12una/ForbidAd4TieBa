package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.contract.PerformanceContract
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test

class PerformanceRuntimeSymbolScannerTest {
    private val loader = javaClass.classLoader!!

    @Test fun targetSignaturesRejectWrongVisibilityStaticnessReturnAndParameters() {
        PerformanceRuntimeTarget.entries.forEach { target ->
            val modifiers = Modifier.PUBLIC or (if (target.isStatic) Modifier.STATIC else 0)
            assertTrue(target.name, target.matchesSignature(modifiers, target.returnTypeName, target.parameterTypeNames))
            assertFalse(target.name, target.matchesSignature(modifiers xor Modifier.STATIC, target.returnTypeName, target.parameterTypeNames))
            assertFalse(target.name, target.matchesSignature(modifiers and Modifier.PUBLIC.inv(), target.returnTypeName, target.parameterTypeNames))
            assertFalse(target.name, target.matchesSignature(modifiers, "java.lang.Object", target.parameterTypeNames))
            assertFalse(target.name, target.matchesSignature(modifiers, target.returnTypeName, target.parameterTypeNames + "java.lang.Object"))
        }
    }

    @Test fun aRetainedMethodRequiresBothLiveConsumerAndItsSemanticAnchor() {
        PerformanceRuntimeTarget.entries.forEach { target ->
            val strings = setOfNotNull(target.semanticKey)
            val consumer = setOf("host.InitializationOrVideoConsumer")
            assertTrue(target.name, PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 1, consumer, strings))
            assertFalse(target.name, PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 0, consumer, strings))
            assertFalse(target.name, PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 2, consumer, strings))
            assertFalse(target.name, PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 1, emptySet(), strings))
            assertFalse(target.name, PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 1, setOf(target.className), strings))
            if (target.semanticKey != null) {
                assertFalse(target.name, PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 1, consumer, setOf("another_channel_switch")))
            }
        }
    }

    @Test fun partialCachesRestoreOnlyPresentVerifiedTargetsWithoutInvokingThem() {
        val names = PerformanceRuntimeTarget.entries.map { it.name }
        val restored = PerformanceRuntimeSymbolScanner.restore(loader, names)
        assertEquals(PerformanceRuntimeTarget.entries.toSet(), restored.keys)
        restored.forEach { (target, method) -> assertTrue(target.name, target.matches(method)) }
        assertTrue(PerformanceRuntimeSymbolScanner.isCacheValid(loader, names))
        assertTrue(PerformanceRuntimeSymbolScanner.isCacheValid(loader, null))
        assertTrue(PerformanceRuntimeSymbolScanner.isCacheValid(loader, emptyList()))
        assertFalse(PerformanceRuntimeSymbolScanner.isCacheValid(loader, names + names.first()))
        for (retired in listOf("AD_KS", "VIDEO_PREFETCH", "VIDEO_OPEN")) {
            assertFalse(retired, PerformanceRuntimeSymbolScanner.isCacheValid(loader, names + retired))
            assertEquals(restored, PerformanceRuntimeSymbolScanner.restore(loader, names + retired))
        }
        val symbols = buildHookSymbols { this[PerformanceContract.runtimeMethods] = listOf("AD_CSJ", "AD_BAIDU") }
        val roundtrip = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        assertEquals(setOf(PerformanceRuntimeTarget.AD_CSJ, PerformanceRuntimeTarget.AD_BAIDU),
            PerformanceContract.resolveRuntimeSymbols(loader, roundtrip).keys)
    }

    @Test fun oneMissingOrStructurallyInvalidOwnerDoesNotDiscardOtherTargets() {
        val names = PerformanceRuntimeTarget.entries.map { it.name }
        val missing = object : ClassLoader(loader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == PerformanceRuntimeTarget.AD_GDT.className) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        val invalid = object : ClassLoader(loader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == PerformanceRuntimeTarget.AD_GDT.className) return WrongOwner::class.java
                return super.loadClass(name, resolve)
            }
        }
        for (broken in listOf(missing, invalid)) {
            assertEquals(PerformanceRuntimeTarget.entries.toSet() - PerformanceRuntimeTarget.AD_GDT,
                PerformanceRuntimeSymbolScanner.restore(broken, names).keys)
            assertFalse(PerformanceRuntimeSymbolScanner.isCacheValid(broken, names))
        }
    }

    class WrongOwner { fun isOn(): Boolean = true }
}
