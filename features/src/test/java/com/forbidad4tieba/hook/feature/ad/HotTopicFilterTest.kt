package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.config.ConfigManager
import org.json.JSONObject
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HotTopicFilterTest {
    @Test fun configurableTopicCardIsRemovedWhenHotFilterIsEnabled() {
        val topic = Card(Data(USER_TOPIC_SCHEMA, listOf(
            Component("feed_configurable_head"),
            Component("title"),
            Component("abstract"),
            Component("pic"),
            Component("feed_mount"),
        )))
        val ordinary = Card(Data("tiebaapp://router/portal?params=%7B%22page%22%3A%22frs%2Ffrs%22%7D",
            listOf(Component("feed_configurable_head"))))
        val input = listOf(ordinary, topic, ordinary, null)

        val filtered = CustomPostCardBlockHook.filterList(input, filter, "hot-topic-regression",
            rules = SettingsSnapshot(isCustomPostFilterEnabled = true, isPostHotFilterEnabled = true).customPostRules,
            recordModelScores = { error("Hot filtering must not collect model scores") })

        assertEquals(listOf(ordinary, ordinary, null), filtered)
        assertEquals(listOf(ordinary, topic, ordinary, null), input)
    }

    @Test fun disablingHotFilterKeepsTopicCardAndListIdentity() {
        val data = Data(USER_TOPIC_SCHEMA, listOf(Component("feed_configurable_head")))
        val input = listOf(Card(data))
        assertSame(input, CustomPostCardBlockHook.filterList(input, filter, "hot-topic-disabled",
            rules = SettingsSnapshot(isCustomPostFilterEnabled = true, isPostVoteFilterEnabled = true).customPostRules,
            recordModelScores = { error("Disabled hot filtering must not collect model scores") }))
        assertEquals(0, data.schemaReads)
    }

    @Test fun existingHotTemplatesAreFilteredThroughTheSameEntry() {
        val rules = SettingsSnapshot(isCustomPostFilterEnabled = true, isPostHotFilterEnabled = true).customPostRules!!
        assertTrue(rules.hot)
        for (key in listOf("recommend_info", "hot_card", "hot_topic_card", "multi_thread_card")) {
            val card = Card(Data("", emptyList()), key)
            assertEquals(emptyList<Card>(), CustomPostCardBlockHook.filterList(listOf(card), filter,
                "hot-template-control", rules = rules, recordModelScores = {}))
        }
    }

    @Test fun configurableHeaderAloneDoesNotMakeACardHot() {
        val input = listOf(Card(Data("tiebaapp://router/portal?params=%7B%22page%22%3A%22frs%2Ffrs%22%7D",
            listOf(Component("feed_configurable_head"), Component("feed_mount")))))
        assertSame(input, CustomPostCardBlockHook.filterList(input, filter, "ordinary-card-control",
            rules = SettingsSnapshot(isCustomPostFilterEnabled = true, isPostHotFilterEnabled = true).customPostRules,
            recordModelScores = {}))
    }

    @Test fun topicDestinationAcceptsTheObservedEncodingAndEquivalentWebRoutes() {
        for (schema in listOf(USER_TOPIC_SCHEMA, TOPIC_URL, portal(TOPIC_URL),
            portal(TOPIC_URL.replace("/naTopicDetail", "/%6EaTopicDetail")),
            portal(TOPIC_URL.replace("https://tieba.baidu.com", "HTTPS://TIEBA.BAIDU.COM")))) {
            assertTrue(schema, HotTopicRoute.matches(schema))
        }
    }

    @Test fun routeKeywordsInOtherDestinationsDoNotClassifyOrdinaryCardsAsHot() {
        for (url in listOf(
            "https://tieba.baidu.com/p/123?title=naTopicDetail",
            "https://example.org/mo/q/hybrid-usergrow-base/naTopicDetail",
            "https://tieba.baidu.com.example.org/mo/q/hybrid-usergrow-base/naTopicDetail",
            "https://tieba.baidu.com/mo/q/hybrid-usergrow-base/naTopicDetailOther",
            "https://tieba.baidu.com/redirect?url=" + URLEncoder.encode(TOPIC_URL, "UTF-8"),
        )) {
            assertEquals(url, false, HotTopicRoute.matches(url))
            assertEquals(url, false, HotTopicRoute.matches(portal(url)))
        }
        assertEquals(false, HotTopicRoute.matches(portal(TOPIC_URL, page = "frs/frs")))
        assertEquals(false, HotTopicRoute.matches(portal(TOPIC_URL).replace("//router/", "//other/")))
    }

    @Test fun malformedOrAmbiguousRoutesAreKept() {
        for (schema in listOf(null, "", "%", "tiebaapp://router/portal?params=bad-json",
            "tiebaapp://router/portal?params=%ZZ", "tiebaapp://router/portal?params=%7B%7D",
            portal(TOPIC_URL) + "&params=%7B%7D")) {
            assertEquals(schema, false, HotTopicRoute.matches(schema))
        }
    }

    @Test fun missingOrFailingSchemaAccessKeepsCardWhileExistingTemplatesStillWork() {
        val data = Data(USER_TOPIC_SCHEMA, listOf(Component("feed_configurable_head")))
        val input = listOf(Card(data))
        val rules = SettingsSnapshot(isCustomPostFilterEnabled = true, isPostHotFilterEnabled = true).customPostRules
        assertSame(input, CustomPostCardBlockHook.filterList(input, filter.copy(schemaGetter = null),
            "missing-schema", rules = rules, recordModelScores = {}))
        val failing = filter.copy(schemaGetter = Data::class.java.getMethod("failingSchema"))
        assertSame(input, CustomPostCardBlockHook.filterList(input, failing,
            "failing-schema", rules = rules, recordModelScores = {}))
        assertEquals(emptyList<Card>(), CustomPostCardBlockHook.filterList(listOf(Card(data, "hot_card")),
            failing, "template-before-schema", rules = rules, recordModelScores = {}))
    }

    @Test fun mountLinkDoesNotReplaceTheFullCardDestination() {
        val input = listOf(Card(Data("https://tieba.baidu.com/p/123", listOf(
            Component("feed_configurable_head"), Component("feed_mount", schema = USER_TOPIC_SCHEMA)))))
        assertSame(input, CustomPostCardBlockHook.filterList(input, filter, "mount-link-control",
            rules = SettingsSnapshot(isCustomPostFilterEnabled = true, isPostHotFilterEnabled = true).customPostRules,
            recordModelScores = {}))
    }

    @Test fun topicRemovalPreservesModelScoreObservations() {
        val input = listOf(Card(Data(USER_TOPIC_SCHEMA,
            listOf(Component("feed_head", params = mapOf("extra" to "msd_score:0.8"))))))
        val observed = mutableListOf<Double>()
        val rules = SettingsSnapshot(isCustomPostFilterEnabled = true, isPostHotFilterEnabled = true,
            isPostModelScoreFilterEnabled = true,
            postModelScoreThresholds = listOf(ConfigManager.ModelScoreThreshold("msd_score", 0.5))).customPostRules
        assertEquals(emptyList<Card>(), CustomPostCardBlockHook.filterList(input, filter, "hot-score-observation",
            rules = rules, recordModelScores = { observed += it.getValue("msd_score") }))
        assertEquals(listOf(0.8), observed)
    }

    private val filter = CustomPostCardBlockHook.RuntimeFilter("dataList", "templateKey", "payload", "params", null, null,
        Data::class.java.getMethod("cardSchema"))

    class Card(private val data: Data, private val key: String = "common_card") {
        fun templateKey() = key
        fun payload() = data
    }

    class Data(@JvmField val schema: String, @JvmField val dataList: List<Component>) {
        var schemaReads = 0
        fun cardSchema(): String { schemaReads++; return schema }
        fun failingSchema(): String = error("Simulated unavailable schema getter")
    }
    class Component(private val key: String, @JvmField val schema: String = "",
        @JvmField val params: Map<String, String> = emptyMap()) { fun templateKey() = key }

    private fun portal(url: String, page: String = "h5/openWebView"): String =
        "tiebaapp://router/portal?params=" + URLEncoder.encode(JSONObject()
            .put("page", page).put("pageParams", JSONObject().put("url", url)).toString(), "UTF-8")

    private companion object {
        const val TOPIC_URL = "https://tieba.baidu.com/mo/q/hybrid-usergrow-base/naTopicDetail?topic_id=28365746"
        // The full-card route from the reported 22.12.1.0 log, with Markdown escaping removed.
        const val USER_TOPIC_SCHEMA = "tiebaapp://router/portal?params=%7B%22page%22:%22h5%2FopenWebView%22%2C%22pageParams%22:%7B%22url%22:%22https%3A%2F%2Ftieba.baidu.com%2Fmo%2Fq%2Fhybrid-usergrow-base%2FnaTopicDetail%3Fcustomfullscreen%3D1%26nonavigationbar%3D1%26loadingSignal%3D1%26topic_id%3D28365746%26topic_name%3D%E9%9D%A2%E7%8E%8B%E4%BA%89%E9%9C%B8%E8%B5%9B%2C%E8%B0%81%E6%98%AF%E4%BD%A0%E5%BF%83%E7%9B%AE%E4%B8%AD%E7%9A%84%E9%9D%A2%E7%8E%8B%26topic_source%3D1%22%7D%7D"
    }
}
