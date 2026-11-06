package com.forbidad4tieba.hook.symbol.scan

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tbclient.FrsPage.FrsRainInfo

class ForumRainSymbolScannerTest {
    private val complete = ForumRainSymbolScanner.Evidence(
        protocolReads = setOf("resource", "popup_url", "popup_info_str", "business_id", "report_info"),
        constructsResult = true,
        createsJson = true,
        callerCount = 1,
        callerReadsProtocol = true,
        callerStoresResult = true,
    )

    @Test fun requiresEveryProtocolReadAndBothConstructionSteps() {
        assertTrue(ForumRainSymbolScanner.accepts(complete))
        complete.protocolReads.forEach { field ->
            assertFalse(field, ForumRainSymbolScanner.accepts(complete.copy(protocolReads = complete.protocolReads - field)))
        }
        assertFalse(ForumRainSymbolScanner.accepts(complete.copy(constructsResult = false)))
        assertFalse(ForumRainSymbolScanner.accepts(complete.copy(createsJson = false)))
    }

    @Test fun requiresOneResponseCallerThatReadsAndStoresTheRain() {
        for (count in listOf(0, 2, 3)) {
            assertFalse("callers=$count", ForumRainSymbolScanner.accepts(complete.copy(callerCount = count)))
        }
        assertFalse(ForumRainSymbolScanner.accepts(complete.copy(callerReadsProtocol = false)))
        assertFalse(ForumRainSymbolScanner.accepts(complete.copy(callerStoresResult = false)))
    }

    @Test fun selectsOnlyOneStructurallyValidConverter() {
        val converter = method("convert")
        val alternate = method("alternate")
        assertEquals(converter, ForumRainSymbolScanner.select(listOf(converter), null))
        assertNull(ForumRainSymbolScanner.select(emptyList(), null))
        assertNull(ForumRainSymbolScanner.select(listOf(converter, alternate), null))
        for (name in listOf("wrongConstructor", "primitiveResult", "wrongParameter", "instanceConverter")) {
            val invalid = ForumRainMapperFixture::class.java.declaredMethods.single { it.name == name }
            assertFalse(name, ForumRainSymbolScanner.valid(invalid))
            assertEquals(name, converter, ForumRainSymbolScanner.select(listOf(invalid, converter), null))
        }
    }

    @Test fun restoresOnlyTheExactSavedConverterAndResultType() {
        val loader = javaClass.classLoader!!
        val owner = ForumRainMapperFixture::class.java.name
        val result = ForumRainModelFixture::class.java.name
        assertEquals(method("convert"), ForumRainSymbolScanner.restore(loader, owner, "convert", result))
        for (name in listOf("missing", "wrongParameter", "instanceConverter", "primitiveResult", "wrongConstructor")) {
            assertNull(name, ForumRainSymbolScanner.restore(loader, owner, name, result))
        }
        assertNull(ForumRainSymbolScanner.restore(loader, owner, "convert", Any::class.java.name))
        assertNull(ForumRainSymbolScanner.restore(loader, owner, "wrongConstructor", ForumRainWrongModelFixture::class.java.name))
        assertNull(ForumRainSymbolScanner.restore(loader, owner, null, result))
    }

    private fun method(name: String) = ForumRainMapperFixture::class.java.getDeclaredMethod(name, FrsRainInfo::class.java)
}

class ForumRainMapperFixture {
    fun instanceConverter(protocol: FrsRainInfo): ForumRainModelFixture? = null

    companion object {
        @JvmStatic fun convert(protocol: FrsRainInfo): ForumRainModelFixture? = null
        @JvmStatic fun alternate(protocol: FrsRainInfo): ForumRainModelFixture? = null
        @JvmStatic fun wrongConstructor(protocol: FrsRainInfo): ForumRainWrongModelFixture? = null
        @JvmStatic fun primitiveResult(protocol: FrsRainInfo): Int = 0
        @JvmStatic fun wrongParameter(protocol: Any): ForumRainModelFixture? = null
    }
}

class ForumRainModelFixture(
    resource: String,
    popupUrl: String,
    popupInfo: JSONObject,
    businessId: String,
    reportInfo: String,
)

class ForumRainWrongModelFixture(resource: String)
