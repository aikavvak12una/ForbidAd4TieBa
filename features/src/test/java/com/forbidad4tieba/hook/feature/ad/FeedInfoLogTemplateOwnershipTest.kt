package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.FeedInfoLogSchema
import java.lang.reflect.Method
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedInfoLogTemplateOwnershipTest {
    @Test
    fun sameNamedMethodOnAnUnrelatedObjectMustNotChangeNicknameState() {
        val component = Component("title")
        val author = AuthorState("fixture-author")
        val card = Card(component, author)

        val json = JSONObject(format(card))

        assertEquals("The legitimate template getter must still be sampled", 1, component.templateReads)
        assertEquals("title", json.getJSONObject("component").getString("templateKey"))
        assertEquals("An unrelated b() must not run; nickname='${author.nickname}'", 0, author.unexpectedReads)
        assertEquals("Formatting must preserve the shared nickname state", "fixture-author", author.nickname)
        assertEquals("fixture-author", json.getJSONObject("author").getString("nickname"))
    }

    @Test
    fun legitimateTemplateInterfaceGetterIsCollectedThroughTheFormatter() {
        val component = Component("feed_head")

        val json = JSONObject(format(Card(component, AuthorState("fixture-author"))))

        assertEquals(1, component.templateReads)
        assertEquals("feed_head", json.getJSONObject("component").getString("templateKey"))
    }

    @Test
    fun inheritedAndOverriddenTemplateImplementationsStillSupplyTheirKeys() {
        val components = listOf(
            InheritedComponent("inherited") to "inherited",
            OverridingComponent("derived") to "override-derived",
        )

        for ((component, expectedKey) in components) {
            val json = JSONObject(format(Card(component, AuthorState("fixture-author"))))

            assertEquals(1, component.templateReads)
            assertEquals(expectedKey, json.getJSONObject("component").getString("templateKey"))
        }
    }

    @Test
    fun missingTemplateAccessorKeepsFieldDiagnosticsWithoutCallingAnyGetter() {
        val component = Component("title")
        val author = AuthorState("fixture-author")

        val json = JSONObject(format(Card(component, author), null))

        assertEquals(0, component.templateReads)
        assertEquals(0, author.unexpectedReads)
        assertEquals("title", json.getJSONObject("component").getString("key"))
        assertEquals("fixture-author", json.getJSONObject("author").getString("nickname"))
        assertFalse(json.getJSONObject("component").has("templateKey"))
        assertFalse(json.getJSONObject("author").has("templateKey"))
    }

    @Test
    fun staticAccessorCannotRunButItsOwnerFieldsRemainAvailable() {
        AuthorState.staticReads = 0
        val author = AuthorState("fixture-author")
        val method = AuthorState::class.java.getMethod("staticKey")

        val json = formatAuthor(author, method)

        assertEquals(0, AuthorState.staticReads)
        assertEquals("fixture-author", json.getString("nickname"))
        assertFalse(json.has("templateKey"))
    }

    @Test
    fun wrongReturnTypeCannotChangeTheCompatibleOwnerState() {
        val author = AuthorState("fixture-author")
        val method = AuthorState::class.java.getMethod("numberKey")

        val json = formatAuthor(author, method)

        assertEquals(0, author.unexpectedReads)
        assertEquals("fixture-author", author.nickname)
        assertEquals("fixture-author", json.getString("nickname"))
        assertFalse(json.has("templateKey"))
    }

    private fun format(card: Card, templateMethod: Method? = Template::class.java.getMethod("b")): String {
        val bind = javaClass.getDeclaredMethod("bindCard", Card::class.java)
        val schema = FeedInfoLogSchema.from(bind, templateMethod)
        return FeedInfoLogFormatter(schema).format(card)
    }

    private fun formatAuthor(author: AuthorState, templateMethod: Method): JSONObject {
        val bind = javaClass.getDeclaredMethod("bindAuthor", AuthorState::class.java)
        val schema = FeedInfoLogSchema.from(bind, templateMethod)
        val json = JSONObject(FeedInfoLogFormatter(schema).format(author))
        assertTrue(schema.truncated)
        assertFalse(schema.discoveryError.isNullOrBlank())
        return json
    }

    @Suppress("UNUSED_PARAMETER")
    private fun bindCard(card: Card) = Unit

    @Suppress("UNUSED_PARAMETER")
    private fun bindAuthor(author: AuthorState) = Unit

    interface Template {
        fun b(): String
    }

    class Card(
        @JvmField val component: Component,
        @JvmField val author: AuthorState,
    )

    open class Component(@JvmField val key: String) : Template {
        @JvmField var templateReads = 0

        override fun b(): String {
            templateReads++
            return key
        }
    }

    class InheritedComponent(key: String) : Component(key)

    class OverridingComponent(key: String) : Component(key) {
        override fun b(): String = "override-${super.b()}"
    }

    /** Has the same method signature without belonging to the template contract. */
    class AuthorState(@JvmField var nickname: String) {
        @JvmField var unexpectedReads = 0

        fun b(): String {
            unexpectedReads++
            nickname = ""
            return "unrelated-state-change"
        }

        fun numberKey(): Int {
            unexpectedReads++
            nickname = ""
            return 1
        }

        companion object {
            @JvmField var staticReads = 0

            @JvmStatic
            fun staticKey(): String {
                staticReads++
                return "invalid-static"
            }
        }
    }
}
