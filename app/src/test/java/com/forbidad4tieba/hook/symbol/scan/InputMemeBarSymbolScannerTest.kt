package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputMemeBarSymbolScannerTest {
    @Test
    fun resolvesUniqueThreeParameterBooleanMethod() {
        val result = InputMemeBarSymbolScanner.scanResolvedClass(
            controllerClass = UniqueFixture::class.java,
            inputShowTypeClass = FakeInputShowType::class.java,
            logger = null,
        )

        assertEquals(UniqueFixture::class.java.name, result.controllerClass)
        assertEquals("isEnabled", result.enableMethod)
    }

    @Test
    fun failsClosedWhenCandidateIsMissing() {
        val result = InputMemeBarSymbolScanner.scanResolvedClass(
            controllerClass = TwoParameterFixture::class.java,
            inputShowTypeClass = FakeInputShowType::class.java,
            logger = null,
        )

        assertNull(result.controllerClass)
        assertNull(result.enableMethod)
    }

    @Test
    fun failsClosedWhenCandidatesAreAmbiguous() {
        val result = InputMemeBarSymbolScanner.scanResolvedClass(
            controllerClass = AmbiguousFixture::class.java,
            inputShowTypeClass = FakeInputShowType::class.java,
            logger = null,
        )

        assertNull(result.controllerClass)
        assertNull(result.enableMethod)
    }

    @Test
    fun ignoresLegacyTwoParameterMethod() {
        val result = InputMemeBarSymbolScanner.scanResolvedClass(
            controllerClass = MixedFixture::class.java,
            inputShowTypeClass = FakeInputShowType::class.java,
            logger = null,
        )

        assertEquals("threeParameters", result.enableMethod)
    }

    private class FakeInputShowType

    private class UniqueFixture {
        companion object {
            @JvmStatic
            fun isEnabled(context: Context, type: FakeInputShowType, force: Boolean): Boolean {
                return context.hashCode() + type.hashCode() > 0 || force
            }
        }
    }

    private class TwoParameterFixture {
        companion object {
            @JvmStatic
            fun legacy(context: Context, type: FakeInputShowType): Boolean {
                return context.hashCode() + type.hashCode() > 0
            }
        }
    }

    private class AmbiguousFixture {
        companion object {
            @JvmStatic
            fun first(context: Context, type: FakeInputShowType, force: Boolean): Boolean {
                return context.hashCode() + type.hashCode() > 0 || force
            }

            @JvmStatic
            fun second(context: Context, type: FakeInputShowType, force: Boolean): Boolean {
                return first(context, type, force)
            }
        }
    }

    private class MixedFixture {
        companion object {
            @JvmStatic
            fun twoParameters(context: Context, type: FakeInputShowType): Boolean {
                return context.hashCode() + type.hashCode() > 0
            }

            @JvmStatic
            fun threeParameters(context: Context, type: FakeInputShowType, force: Boolean): Boolean {
                return twoParameters(context, type) || force
            }
        }
    }
}
