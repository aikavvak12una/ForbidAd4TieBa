package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.*
import org.junit.Test
import tbclient.LayoutFactory
import tbclient.Personalized.DataRes

class BannerAdSymbolScannerTest {
    private val complete = BannerAdSymbolScanner.Evidence(
        hasParameterAnchors = true,
        readsLayout = true,
        looksUpFactory = true,
        dispatchesConversion = true,
        composesLayoutLogParams = true,
        ownerHasResponse = true,
        responseConsumerCount = 1,
        localCallersOnly = true,
    )

    @Test fun everyMissingSemanticAnchorRejectsTheCandidate() {
        assertTrue(BannerAdSymbolScanner.accepts(complete))
        val invalid = listOf(
            complete.copy(hasParameterAnchors = false),
            complete.copy(readsLayout = false),
            complete.copy(looksUpFactory = false),
            complete.copy(dispatchesConversion = false),
            complete.copy(composesLayoutLogParams = false),
            complete.copy(ownerHasResponse = false),
            complete.copy(responseConsumerCount = 0),
            complete.copy(responseConsumerCount = 2),
            complete.copy(localCallersOnly = false),
        )
        invalid.forEach { assertFalse(it.toString(), BannerAdSymbolScanner.accepts(it)) }
    }

    @Test fun renamedTargetWinsOverASameShapeDistractorWithoutEvidence() {
        val renamed = method("renamedConverter")
        val distractor = method("oldConverter")
        assertEquals(renamed, BannerAdSymbolScanner.select(listOf(
            BannerAdSymbolScanner.Candidate(distractor, complete.copy(readsLayout = false)),
            BannerAdSymbolScanner.Candidate(renamed, complete),
        ), null))
        assertNull(BannerAdSymbolScanner.select(listOf(
            BannerAdSymbolScanner.Candidate(distractor, complete),
            BannerAdSymbolScanner.Candidate(renamed, complete),
        ), null))
        assertNull(BannerAdSymbolScanner.select(emptyList(), null))
    }

    @Test fun signatureAndPersonalizedOwnerAreValidatedIndependentlyOfEvidence() {
        val invalidMethods = listOf("wrongParameters", "boxedBoolean", "wrongResult", "missingPayload", "staticConverter")
            .map { name -> BannerConversionFixture::class.java.declaredMethods.single { it.name == name } } +
            NonPersonalizedConverterFixture::class.java.getDeclaredMethod(
                "convert", LayoutFactory::class.java, Map::class.java, java.lang.Boolean.TYPE,
            )
        val valid = method("renamedConverter")
        invalidMethods.forEach { invalid ->
            assertFalse(invalid.name, BannerAdSymbolScanner.valid(invalid))
            assertEquals(invalid.name, valid, BannerAdSymbolScanner.select(listOf(
                BannerAdSymbolScanner.Candidate(invalid, complete),
                BannerAdSymbolScanner.Candidate(valid, complete),
            ), null))
        }
    }

    @Test fun factoryDispatchRequiresTheErasedInterfaceSignatureAndExactResult() {
        val result = BannerTemplateFixture::class.java
        val erased = GenericBannerFactoryFixture::class.java.getDeclaredMethod("convert", Any::class.java, Any::class.java)
        assertTrue(BannerAdSymbolScanner.isFactoryDispatch(erased, result))
        assertFalse(BannerAdSymbolScanner.isFactoryDispatch(erased, MissingPayloadFixture::class.java))
        val typed = TypedBannerFactoryFixture::class.java.getDeclaredMethod("convert", LayoutFactory::class.java, Map::class.java)
        assertFalse(BannerAdSymbolScanner.isFactoryDispatch(typed, result))
        val concrete = ConcreteBannerFactoryFixture::class.java.getDeclaredMethod("convert", Any::class.java, Any::class.java)
        assertFalse(BannerAdSymbolScanner.isFactoryDispatch(concrete, result))
    }

    @Test fun protocolFieldsRejectMissingStaticOrChangedTypeSlots() {
        assertEquals(String::class.java, BannerAdSymbolScanner.field(ProtocolFieldFixture::class.java, "layout", String::class.java).type)
        assertThrows(NoSuchFieldException::class.java) {
            BannerAdSymbolScanner.field(ProtocolFieldFixture::class.java, "missing", String::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            BannerAdSymbolScanner.field(ProtocolFieldFixture::class.java, "layout", List::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            BannerAdSymbolScanner.field(ProtocolFieldFixture::class.java, "staticLayout", String::class.java)
        }
    }

    private fun method(name: String) = BannerConversionFixture::class.java.getDeclaredMethod(
        name, LayoutFactory::class.java, Map::class.java, java.lang.Boolean.TYPE,
    )

    class ProtocolFieldFixture {
        @JvmField var layout: String? = null
        companion object { @JvmField var staticLayout: String? = null }
    }
}

class BannerConversionFixture {
    @JvmField var response: DataRes? = null
    fun renamedConverter(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean): BannerTemplateFixture? = null
    fun oldConverter(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean): BannerTemplateFixture? = null
    fun wrongParameters(layout: Any, log: Map<Any, Any>, cached: Boolean): BannerTemplateFixture? = null
    fun boxedBoolean(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean?): BannerTemplateFixture? = null
    fun wrongResult(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean): Any? = null
    fun missingPayload(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean): MissingPayloadFixture? = null
    companion object {
        @JvmStatic fun staticConverter(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean): BannerTemplateFixture? = null
    }
}

class NonPersonalizedConverterFixture {
    fun convert(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean): BannerTemplateFixture? = null
}

interface BannerTemplateFixture {
    fun key(): String
    fun payload(): Any?
}

interface MissingPayloadFixture {
    fun key(): String
}

interface GenericBannerFactoryFixture {
    fun convert(origin: Any?, log: Any?): BannerTemplateFixture?
}

interface TypedBannerFactoryFixture {
    fun convert(origin: LayoutFactory, log: Map<Any, Any>): BannerTemplateFixture?
}

class ConcreteBannerFactoryFixture {
    fun convert(origin: Any?, log: Any?): BannerTemplateFixture? = null
}
