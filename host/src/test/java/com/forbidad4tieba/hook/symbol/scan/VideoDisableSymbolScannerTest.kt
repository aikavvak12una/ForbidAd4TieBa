package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.VideoDisableTarget
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

    @Test fun virtualDispatchEvidenceAcceptsCompatibleAbstractInterfacesButNotPrivateStaticOrOtherOverloads() {
        val target = VideoDisableTarget.PREBOOT_WORK
        assertTrue(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PUBLIC or Modifier.ABSTRACT, "void", emptyList()))
        assertTrue(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PROTECTED, "void", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PUBLIC or Modifier.STATIC, "void", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PRIVATE, "void", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PUBLIC, "void", listOf("int")))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(target, Modifier.PUBLIC, "boolean", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(VideoDisableTarget.PREBOOT_DELIVER, Modifier.PUBLIC,
            "void", VideoDisableTarget.PREBOOT_DELIVER.parameterTypeNames))

    }

}
