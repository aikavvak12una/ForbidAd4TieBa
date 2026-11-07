package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.contract.PlainUrlContract
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.*
import org.junit.Test

class PlainUrlMessageDataSymbolScannerTest {
    private val loader = javaClass.classLoader!!
    @Test fun missingOrConflictingConsumerRolesFailClosedWhileIdenticalConsumersAgree() {
        val first = PlainUrlMessageDataSymbolScanner.Roles("category", "destination", "caption")
        val second = PlainUrlMessageDataSymbolScanner.Roles("category", "caption", "destination")
        assertNull(PlainUrlMessageDataSymbolScanner.selectRoles(emptyList()))
        assertNull(PlainUrlMessageDataSymbolScanner.selectRoles(listOf(first, second)))
        assertEquals(first, PlainUrlMessageDataSymbolScanner.selectRoles(listOf(first, first)))
    }

    @Test fun renamedPayloadDescriptorsIgnoreEarlierSameTypedDistractorsAndRoundTrip() {
        val symbols = buildHookSymbols {
            this[PlainUrlContract.plainUrlMessageDataClass] = RenamedPayload::class.java.name
            this[PlainUrlContract.plainUrlMessageDataTypeField] = "category"
            this[PlainUrlContract.plainUrlMessageDataUrlField] = "destination"
            this[PlainUrlContract.plainUrlMessageDataTextField] = "caption"
        }
        val restored = requireNotNull(PlainUrlContract.resolvePlainUrlMessageDataSymbols(
            loader, requireNotNull(HookSymbols.fromJson(symbols.toJson())),
        ))
        val payload = RenamedPayload(16, "user-id", "display text", 5, "alternate-url")
        assertEquals(RenamedPayload::class.java, restored.dataClass)
        assertEquals(16, restored.typeField.get(payload))
        assertEquals("user-id", restored.urlField.get(payload))
        assertEquals("display text", restored.textField.get(payload))
        assertNotEquals(payload.extraNumber, restored.typeField.get(payload))
        assertNotEquals(payload.extraString, restored.urlField.get(payload))
    }

    @Test fun incompleteStaleAliasedStaticAndWronglyTypedDescriptorsFailClosed() {
        fun restore(type: Class<*>, discriminator: String?, url: String?, text: String?) =
            PlainUrlMessageDataSymbolScanner.restore(loader, type.name, discriminator, url, text)
        assertNull(restore(RenamedPayload::class.java, null, "destination", "caption"))
        assertNull(restore(RenamedPayload::class.java, "retired", "destination", "caption"))
        assertNull(restore(RenamedPayload::class.java, "category", "destination", "destination"))
        assertNull(restore(RenamedPayload::class.java, "caption", "destination", "extraString"))
        assertNull(restore(StaticPayload::class.java, "category", "destination", "caption"))
        assertNull(restore(UnsupportedPayload::class.java, "category", "destination", "caption"))
        assertNull(PlainUrlMessageDataSymbolScanner.restore(loader, "missing.Payload", "category", "destination", "caption"))
    }

    @Test fun restorationDoesNotConstructThePayloadOrInvokeItsAccessors() {
        ConstructorTrap.constructed = 0
        val restored = PlainUrlMessageDataSymbolScanner.restore(loader, ConstructorTrap::class.java.name,
            "category", "destination", "caption")
        assertNotNull(restored)
        assertEquals(0, ConstructorTrap.constructed)
    }

    @Test fun aValidOptionalBrowserTargetCannotMaskMalformedPayloadCacheDescriptors() {
        val symbols = buildHookSymbols {
            this[PlainUrlContract.plainUrlBrowserHelperClass] = BrowserHelperFixture::class.java.name
            this[PlainUrlContract.plainUrlBrowserHelperStartWebActivityMethod] = "open"
            this[PlainUrlContract.plainUrlMessageDataClass] = RenamedPayload::class.java.name
            this[PlainUrlContract.plainUrlMessageDataTypeField] = "category"
            this[PlainUrlContract.plainUrlMessageDataUrlField] = "destination"
            this[PlainUrlContract.plainUrlMessageDataTextField] = "retired"
        }
        assertNotNull(PlainUrlContract.resolvePlainUrlBrowserHelperSymbols(loader, symbols))
        assertFalse(PlainUrlContract.isCacheValid(symbols, loader))
    }

    object BrowserHelperFixture {
        @JvmStatic @Suppress("UNUSED_PARAMETER")
        fun open(context: android.content.Context?, url: String) = Unit
    }

    class RenamedPayload(category: Int, destination: String, caption: String, style: Int, alternate: String?) {
        @JvmField val extraNumber: Int = 987
        @JvmField val extraString: String = "distractor"
        @JvmField var category: Int = category
        @JvmField var destination: String = destination
        @JvmField var caption: String = caption
        @JvmField var style: Int = style
        @JvmField var alternate: String? = alternate
    }

    @Suppress("UNUSED_PARAMETER")
    class StaticPayload(category: Int, destination: String, caption: String, style: Int, alternate: String?) {
        companion object { @JvmField var category: Int = 16 }
        @JvmField var destination: String = destination
        @JvmField var caption: String = caption
    }

    class UnsupportedPayload {
        @JvmField var category: Int = 16
        @JvmField var destination: String = "user-id"
        @JvmField var caption: String = "caption"
    }

    @Suppress("UNUSED_PARAMETER")
    class ConstructorTrap(category: Int, destination: String, caption: String, style: Int, alternate: String?) {
        companion object { var constructed: Int = 0 }
        init { constructed++; error("Payload construction is not part of symbol restoration") }
        @JvmField var category: Int = category
        @JvmField var destination: String = destination
        @JvmField var caption: String = caption
    }
}
