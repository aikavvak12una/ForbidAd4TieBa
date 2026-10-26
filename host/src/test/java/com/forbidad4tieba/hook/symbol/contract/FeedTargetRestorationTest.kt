package com.forbidad4tieba.hook.symbol.contract

import org.junit.Assert.*
import org.junit.Test

class FeedTargetRestorationTest {
    @Test fun payloadRequiresTheCachedInterfaceMemberAndAnObjectReturn() {
        val key = Template::class.java.getMethod("key")
        assertEquals(Template::class.java.getMethod("payload"), FeedTargetRestoration.payload(key, "payload"))
        for (name in listOf(null, "", "missing", "scalar", "key")) assertNull(FeedTargetRestoration.payload(key, name))
    }

    @Test fun fieldsRequireTheExpectedOwnerTypeAndUniqueInstanceSlot() {
        val field = FeedTargetRestoration.instanceField(Child::class.java, "items", List::class.java)!!
        assertEquals(Base::class.java, field.declaringClass)
        assertEquals(listOf("kept"), field.get(Child()))
        assertNull(FeedTargetRestoration.instanceField(Child::class.java, "items", Map::class.java))
        assertNull(FeedTargetRestoration.instanceField(Child::class.java, "missing", List::class.java))
        assertNull(FeedTargetRestoration.instanceField(Shadow::class.java, "items", List::class.java))
        assertNull(FeedTargetRestoration.instanceField(StaticOnly::class.java, "items", List::class.java))
    }

    interface Template { fun key(): String; fun payload(): Any; fun scalar(): Int }
    open class Base { private val items = listOf("kept") }
    class Child : Base()
    class Shadow : Base() { @JvmField val items = emptyList<String>() }
    class StaticOnly { companion object { @JvmField val items = emptyList<String>() } }
}
