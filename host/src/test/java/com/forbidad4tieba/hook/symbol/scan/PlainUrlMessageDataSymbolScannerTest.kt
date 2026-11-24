package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.*
import org.junit.Test

class PlainUrlMessageDataSymbolScannerTest {
    private val loader = javaClass.classLoader!!
    private fun restore(type: String? = "category", url: String = "destination", text: String = "caption") =
        PlainUrlMessageDataSymbolScanner.restore(loader, Payload::class.java.name, type, url, text)

    @Test fun conflictingRolesFailClosedWhileRepeatedConsumersAgree() {
        val roles = PlainUrlMessageDataSymbolScanner.Roles("category", "destination", "caption")
        assertNull(PlainUrlMessageDataSymbolScanner.selectRoles(emptyList()))
        assertNull(PlainUrlMessageDataSymbolScanner.selectRoles(listOf(roles, PlainUrlMessageDataSymbolScanner.Roles("category", "caption", "destination"))))
        assertEquals(roles, PlainUrlMessageDataSymbolScanner.selectRoles(listOf(roles, roles)))
    }

    @Test fun renamedFieldsIgnoreSameTypedDistractorsWithoutConstructingThePayload() {
        Payload.constructed = 0
        val target = requireNotNull(restore())
        assertEquals(listOf("category", "destination", "caption"), listOf(target.typeField, target.urlField, target.textField).map { it.name })
        assertEquals(0, Payload.constructed)
    }

    @Test fun missingAliasedOrWronglyTypedRolesCannotPublishTargets() {
        assertNull(restore(type = null))
        assertNull(restore(type = "missing"))
        assertNull(restore(text = "destination"))
        assertNull(restore(type = "caption", text = "extraString"))
    }

    @Suppress("UNUSED_PARAMETER")
    class Payload(category: Int, destination: String, caption: String, style: Int, alternate: String?) {
        companion object { var constructed = 0 }
        init { constructed++; error("Symbol restoration must not construct the payload") }
        @JvmField val extraNumber = 987
        @JvmField val extraString = "distractor"
        @JvmField var category = category
        @JvmField var destination = destination
        @JvmField var caption = caption
    }
}
