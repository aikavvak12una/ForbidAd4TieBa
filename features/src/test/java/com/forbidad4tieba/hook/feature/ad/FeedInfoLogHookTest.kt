package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.FeatureCatalog
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.FeedInfoLogSchema
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedInfoLogHookTest {
    @Test
    fun detailedLoggingOffDoesNotPlanTheFeedInfoLogInstaller() {
        val plan = FeatureCatalog.symbolPlan(
            Constants.TARGET_PACKAGE,
            HookSymbols.unsupported(),
            SettingsSnapshot.bootstrap(),
        )

        assertFalse(plan.entries.any { it.id == "FeedInfoLogHook" })
    }

    @Test
    fun samplerAcceptsTheInitialBurstThenUsesItsInterval() {
        val sampler = FeedInfoLogSampler(initialSamples = 3, interval = 5)

        val accepted = (1..12).filter { sampler.shouldSample() }

        assertEquals(listOf(1, 2, 3, 5, 10), accepted)
    }

    @Test
    fun schemaDiscoveryIsPreparedByTheSymbolContract() {
        val root = File(checkNotNull(System.getProperty("project.root")))
        val hookSource = File(
            root,
            "features/src/main/java/com/forbidad4tieba/hook/feature/ad/FeedInfoLogHook.kt",
        ).readText()
        val contractSource = File(
            root,
            "host/src/main/java/com/forbidad4tieba/hook/symbol/contract/FeedContract.kt",
        ).readText()

        assertFalse(hookSource.contains("declaredFields"))
        assertFalse(hookSource.contains("getMethod"))
        assertFalse(hookSource.contains("buildCardJson"))
        assertFalse(hookSource.contains("FeedInfoLogSchema.from"))
        assertTrue(hookSource.contains("FeedInfoLogFormatter(symbols.schema)"))
        assertTrue(contractSource.contains("FeedInfoLogSchema.from"))
    }

    @Test
    fun invalidBindShapeReturnsAnErrorAndABoundedSummary() {
        val schema = FeedInfoLogSchema.from(
            javaClass.getDeclaredMethod("bindInvalid", Card::class.java, Card::class.java),
            null,
        )
        val card = Card(
            title = "card",
            count = 1,
            items = emptyList(),
            metadata = emptyMap(),
            unknown = Unknown("hidden"),
        )
        val json = JSONObject(FeedInfoLogFormatter(schema).format(card))

        assertTrue(schema.truncated)
        assertFalse(schema.discoveryError.isNullOrBlank())
        assertTrue(json.getBoolean("_truncated"))
        assertEquals("Card", json.getString("_class"))
    }

    @Test
    fun knownSchemaUsesThePrecomputedTemplateAccessorWithoutHostToString() {
        val item = Item("one")
        val card = Card(
            title = "card",
            count = 1,
            items = listOf(item),
            metadata = mapOf("source" to "fixture"),
            unknown = Unknown("hidden"),
        )
        val text = formatterFor(Card::class.java).format(card)
        val json = JSONObject(text)

        assertEquals("card", json.getString("title"))
        assertEquals(1, json.getInt("count"))
        assertEquals("item-one", json.getJSONArray("items").getJSONObject(0).getString("templateKey"))
        assertEquals(1, item.templateKeyReads)
        assertEquals(0, card.toStringCalls)
        assertEquals(0, item.toStringCalls)
    }

    @Test
    fun unknownObjectsAreSummariesAndNeverExposeFieldsOrCallToString() {
        val unknown = Unknown("secret")
        val card = Card(
            title = "card",
            count = 1,
            items = emptyList(),
            metadata = mapOf("unknown" to unknown),
            unknown = unknown,
        )
        val json = JSONObject(formatterFor(Card::class.java).format(card))
        val direct = json.getJSONObject("unknown")
        val nested = json.getJSONObject("metadata").getJSONObject("unknown")

        assertEquals("Unknown", direct.getString("_class"))
        assertEquals("Unknown", nested.getString("_class"))
        assertFalse(direct.has("secret"))
        assertFalse(nested.has("secret"))
        assertEquals(0, unknown.toStringCalls)
    }

    @Test
    fun reflectionBudgetStopsFieldReadsAndMarksTheResultTruncated() {
        val value = ManyFields("one", "two", "three", "four", "five", "six")
        val schema = FeedInfoLogSchema.from(
            javaClass.getDeclaredMethod("bindManyFields", ManyFields::class.java),
            null,
        )
        val formatter = FeedInfoLogFormatter(
            schema,
            FeedInfoLogLimits(
                maxFieldsPerObject = 6,
                maxReflectionReads = 2,
                maxOutputChars = 6_000,
            ),
        )

        val json = JSONObject(formatter.format(value))

        assertTrue(json.getBoolean("_truncated"))
        assertEquals(4, json.length())
    }

    @Test
    fun nestedCollectionsShareGlobalBudgetsAndStillProduceValidJson() {
        val card = NestedCard(
            groups = listOf(
                mapOf("a" to "1", "b" to "2", "c" to "3"),
                mapOf("d" to "4"),
            ),
        )
        val schema = FeedInfoLogSchema.from(
            javaClass.getDeclaredMethod("bindNestedCard", NestedCard::class.java),
            null,
        )
        val formatter = FeedInfoLogFormatter(
            schema,
            FeedInfoLogLimits(
                maxListItems = 2,
                maxMapEntries = 2,
                maxReflectionReads = 8,
                maxObjects = 8,
                maxDepth = 4,
                maxOutputChars = 6_000,
            ),
        )

        val json = JSONObject(formatter.format(card))
        val groups = json.getJSONArray("groups")

        assertTrue(json.getBoolean("_truncated"))
        assertEquals(2, groups.length())
        assertTrue(groups.getJSONObject(0).has("_truncated"))
        assertTrue(groups.getJSONObject(1).has("_truncated"))
    }

    @Test
    fun outputLimitFallsBackToAValidSummary() {
        val card = Card(
            title = "x".repeat(1_000),
            count = 1,
            items = emptyList(),
            metadata = emptyMap(),
            unknown = Unknown("hidden"),
        )
        val formatter = FeedInfoLogFormatter(
            formatterSchema(Card::class.java),
            FeedInfoLogLimits(maxOutputChars = 200, maxStringChars = 1_000),
        )

        val text = formatter.format(card)
        val json = JSONObject(text)

        assertTrue(text.length <= 200)
        assertTrue(json.getBoolean("_truncated"))
        assertEquals("Card", json.getString("_class"))
    }

    @Test
    fun stringLimitMarksTruncationBeforeTheOutputLimitIsReached() {
        val card = Card(
            title = "x".repeat(300),
            count = 1,
            items = emptyList(),
            metadata = emptyMap(),
            unknown = Unknown("hidden"),
        )
        val formatter = FeedInfoLogFormatter(
            formatterSchema(Card::class.java),
            FeedInfoLogLimits(maxStringChars = 20, maxOutputChars = 6_000),
        )

        val json = JSONObject(formatter.format(card))

        assertTrue(json.getBoolean("_truncated"))
        assertTrue(json.getString("title").endsWith("..."))
    }

    private fun formatterFor(type: Class<*>): FeedInfoLogFormatter =
        FeedInfoLogFormatter(formatterSchema(type))

    private fun formatterSchema(type: Class<*>): FeedInfoLogSchema {
        require(type == Card::class.java) { "Unsupported fixture: $type" }
        val bind = javaClass.getDeclaredMethod("bindCard", Card::class.java)
        return FeedInfoLogSchema.from(bind, "templateKey")
    }

    private fun bindCard(card: Card) = Unit
    private fun bindManyFields(value: ManyFields) = Unit
    private fun bindNestedCard(value: NestedCard) = Unit
    private fun bindInvalid(first: Card, second: Card) = Unit

    interface Template {
        fun templateKey(): String
    }

    class Card(
        @JvmField val title: String,
        @JvmField val count: Int,
        @JvmField val items: List<Item>,
        @JvmField val metadata: Map<String, Any?>,
        @JvmField val unknown: Any,
    ) {
        var toStringCalls = 0

        override fun toString(): String {
            toStringCalls++
            return "Card(...)"
        }
    }

    class Item(@JvmField val value: String) : Template {
        var templateKeyReads = 0
        var toStringCalls = 0

        override fun templateKey(): String {
            templateKeyReads++
            return "item-$value"
        }

        override fun toString(): String {
            toStringCalls++
            return "Item(...)"
        }
    }

    class Unknown(@JvmField val secret: String) {
        var toStringCalls = 0

        override fun toString(): String {
            toStringCalls++
            return "Unknown(...)"
        }
    }

    class ManyFields(
        @JvmField val one: String,
        @JvmField val two: String,
        @JvmField val three: String,
        @JvmField val four: String,
        @JvmField val five: String,
        @JvmField val six: String,
    )

    class NestedCard(@JvmField val groups: List<Map<String, Any?>>)
}
