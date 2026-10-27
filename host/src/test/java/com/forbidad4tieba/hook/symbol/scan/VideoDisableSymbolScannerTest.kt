package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.VideoDisableTarget
import com.baidu.searchbox.player.kernel.AbsVideoKernel
import com.baidu.searchbox.player.kernel.EmptyKernel
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test

class VideoDisableSymbolScannerTest {
    @Test fun everyStableTargetRequiresItsExactConcreteSignature() {
        VideoDisableTarget.entries.forEach { target ->
            val visibility = if (target.isPrivate) Modifier.PRIVATE else Modifier.PUBLIC
            val flags = visibility or (if (target.isStatic) Modifier.STATIC else 0)
            assertTrue(target.name, target.matchesSignature(flags, target.returnTypeName, target.parameterTypeNames))
            assertFalse(target.name, target.matchesSignature(flags xor Modifier.STATIC, target.returnTypeName, target.parameterTypeNames))
            assertFalse(target.name, target.matchesSignature(flags or Modifier.ABSTRACT, target.returnTypeName, target.parameterTypeNames))
            assertFalse(target.name, target.matchesSignature(Modifier.PROTECTED or (flags and Modifier.STATIC), target.returnTypeName, target.parameterTypeNames))
            assertFalse(target.name, target.matchesSignature(flags, "java.lang.Object", target.parameterTypeNames))
            assertFalse(target.name, target.matchesSignature(flags, target.returnTypeName, target.parameterTypeNames + "java.lang.Object"))
        }
    }

    @Test fun sdkCallersMayBeInternalButOldViewEntrypointsRequireExternalConsumers() {
        for (target in VideoDisableTarget.entries) {
            assertFalse(target.name, target.hasConsumer(0, 0))
            assertTrue(target.name, target.hasConsumer(1, 1))
            assertEquals(target.name, !target.requiresExternalConsumer, target.hasConsumer(1, 0))
        }
        assertEquals(setOf(VideoDisableTarget.TB_START, VideoDisableTarget.TB_SET_PATH,
            VideoDisableTarget.CYBER_START, VideoDisableTarget.CYBER_SET_PATH), VideoDisableTarget.entries.filter { it.requiresExternalConsumer }.toSet())
    }

    @Test fun pcdnOwnerAndStartAmbiguityFailClosed() {
        val first = "com.baidu.tieba.video.fixture.First"
        val second = "com.baidu.tieba.video.fixture.Second"
        assertNull(VideoDisableSymbolScanner.uniquePcdnOwner(emptyList()))
        assertEquals(first, VideoDisableSymbolScanner.uniquePcdnOwner(listOf(first, first)))
        assertNull(VideoDisableSymbolScanner.uniquePcdnOwner(listOf(first, second)))
        assertNull(VideoDisableSymbolScanner.uniquePcdnStart(emptyList()))
        assertEquals("$first#start", VideoDisableSymbolScanner.uniquePcdnStart(listOf("$first#start")))
        assertNull(VideoDisableSymbolScanner.uniquePcdnStart(listOf("$first#start", "$first#another")))
        assertNull(VideoDisableSymbolScanner.uniquePcdnStart(listOf("$first#start", "$first#start")))
        assertTrue(VideoDisableSymbolScanner.isPcdnStartSignature(Modifier.PUBLIC or Modifier.STATIC, "void", listOf("boolean")))
        assertFalse(VideoDisableSymbolScanner.isPcdnStartSignature(Modifier.PUBLIC, "void", listOf("boolean")))
        assertFalse(VideoDisableSymbolScanner.isPcdnStartSignature(Modifier.PUBLIC or Modifier.STATIC, "boolean", listOf("boolean")))
        assertFalse(VideoDisableSymbolScanner.isPcdnStartSignature(Modifier.PUBLIC or Modifier.STATIC, "void", emptyList()))
    }

    @Test fun playbackDispatchRequiresOneIntVoidInstanceTarget() {
        assertNull(VideoDisableSymbolScanner.uniquePlayDispatch(emptyList()))
        assertEquals("owner#dispatch", VideoDisableSymbolScanner.uniquePlayDispatch(listOf("owner#dispatch")))
        assertNull(VideoDisableSymbolScanner.uniquePlayDispatch(listOf("owner#first", "owner#second")))
        assertTrue(VideoDisableSymbolScanner.isPlayDispatchSignature(Modifier.PUBLIC, "void", listOf("int")))
        assertFalse(VideoDisableSymbolScanner.isPlayDispatchSignature(Modifier.PUBLIC or Modifier.STATIC, "void", listOf("int")))
        assertFalse(VideoDisableSymbolScanner.isPlayDispatchSignature(Modifier.PUBLIC, "boolean", listOf("int")))
        assertFalse(VideoDisableSymbolScanner.isPlayDispatchSignature(Modifier.PUBLIC, "void", emptyList()))
    }

    @Test fun virtualDispatchEvidenceAcceptsCompatibleAbstractInterfacesButNotPrivateStaticOrOtherOverloads() {
        val target = VideoDisableTarget.BD_PREPARE
        assertTrue(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PUBLIC or Modifier.ABSTRACT, "void", emptyList()))
        assertTrue(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PROTECTED, "void", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PUBLIC or Modifier.STATIC, "void", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PRIVATE, "void", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PUBLIC, "void", listOf("int")))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PUBLIC, "boolean", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(VideoDisableTarget.PREBOOT_DELIVER, Modifier.PUBLIC,
            "void", VideoDisableTarget.PREBOOT_DELIVER.parameterTypeNames))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(VideoDisableTarget.MEDIA_PREFETCH, Modifier.PUBLIC,
            "void", VideoDisableTarget.MEDIA_PREFETCH.parameterTypeNames))
    }

    @Test fun emptyKernelSupportRejectsPrivateParameterizedAbstractAndUnrelatedConstruction() {
        val base = AbsVideoKernel::class.java
        assertTrue(VideoDisableSymbolScanner.isEmptyKernelConstructorValid(EmptyKernel::class.java.getDeclaredConstructor(), base))
        for (type in listOf(PrivateEmpty::class.java, ParameterizedEmpty::class.java, AbstractEmpty::class.java, UnrelatedEmpty::class.java)) {
            assertFalse(type.name, VideoDisableSymbolScanner.isEmptyKernelConstructorValid(type.declaredConstructors.first(), base))
        }
    }

    class PrivateEmpty private constructor() : AbsVideoKernel()
    class ParameterizedEmpty(val value: Int) : AbsVideoKernel()
    abstract class AbstractEmpty : AbsVideoKernel()
    class UnrelatedEmpty
}
