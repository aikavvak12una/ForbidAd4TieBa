package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.SettingsSnapshot
import java.net.URLEncoder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HotTopicFilterTest {
    @Test
    fun fullCardRouteMatchesWithoutClassifyingKeywordBearers() {
        for (schema in listOf(
            USER_TOPIC_SCHEMA,
            TOPIC_URL,
            portal(TOPIC_URL),
            portal(TOPIC_URL.replace("/naTopicDetail", "/%6EaTopicDetail")),
            portal(TOPIC_URL.replace("https://tieba.baidu.com", "HTTPS://TIEBA.BAIDU.COM")),
        )) {
            assertTrue(schema, HotTopicRoute.matches(schema))
        }
        for (schema in listOf(
            "https://tieba.baidu.com/p/123?title=naTopicDetail",
            "https://example.org/mo/q/hybrid-usergrow-base/naTopicDetail",
            "https://tieba.baidu.com.example.org/mo/q/hybrid-usergrow-base/naTopicDetail",
            "https://tieba.baidu.com/mo/q/hybrid-usergrow-base/naTopicDetailOther",
            portal(TOPIC_URL, page = "frs/frs"),
            portal(TOPIC_URL).replace("//router/", "//other/"),
            "tiebaapp://router/portal?params=bad-json",
            portal(TOPIC_URL) + "&params=%7B%7D",
        )) {
            assertFalse(schema, HotTopicRoute.matches(schema))
        }
    }

    @Test
    fun hotFilterUsesTheCardDestinationAndFailsOpenWhenSchemaIsUnavailable() {
        val topic = Card(Data(USER_TOPIC_SCHEMA, listOf(Component("feed_configurable_head"))))
        val ordinary = Card(Data(
            "tiebaapp://router/portal?params=%7B%22page%22%3A%22frs%2Ffrs%22%7D",
            listOf(Component("feed_configurable_head"), Component("feed_mount")),
        ))
        val rules = SettingsSnapshot.bootstrap().copy(
            isCustomPostFilterEnabled = true,
            isPostHotFilterEnabled = true,
        ).customPostRules

        assertEquals(listOf(ordinary), filter(listOf(topic, ordinary), rules))
        assertSame(ordinary, filter(listOf(ordinary), rules, targets.copy(schemaGetter = null)).single())
        assertSame(topic, filter(listOf(topic), rules,
            targets.copy(schemaGetter = Data::class.java.getMethod("failingSchema"))).single())
        assertEquals(emptyList<Card>(), filter(
            listOf(Card(Data("", emptyList()), "hot_card")),
            rules,
            targets.copy(schemaGetter = Data::class.java.getMethod("failingSchema")),
        ))

        val disabled = SettingsSnapshot.bootstrap().copy(
            isCustomPostFilterEnabled = true,
            isPostVoteFilterEnabled = true,
        ).customPostRules
        val disabledTopic = Card(Data(USER_TOPIC_SCHEMA, listOf(Component("feed_configurable_head"))))
        assertSame(disabledTopic, filter(listOf(disabledTopic), disabled).single())
        assertEquals(0, disabledTopic.data.schemaReads)
    }

    private fun filter(
        input: List<Card>,
        rules: com.forbidad4tieba.hook.config.CustomPostFilterRules?,
        filterTargets: com.forbidad4tieba.hook.symbol.model.CustomPostCardFilterSymbols = targets,
    ): List<*> = CustomPostCardBlockHook.filterList(
        input,
        filterTargets,
        "hot-topic",
        rules = rules,
        recordModelScores = {},
    )

    private val targets = feedFilterFixture(
        Data::class.java,
        Component::class.java,
        Data::class.java.getMethod("cardSchema"),
    )

    class Card(val data: Data, private val key: String = "common_card") : FeedTemplateFixture {
        override fun templateKey() = key
        override fun payload() = data
    }

    class Data(@JvmField val schema: String, @JvmField val dataList: List<Component>) {
        var schemaReads = 0
        fun cardSchema(): String {
            schemaReads++
            return schema
        }

        fun failingSchema(): String = error("schema unavailable")
    }

    class Component(
        private val key: String,
        @JvmField val schema: String = "",
        @JvmField val params: Map<String, String> = emptyMap(),
    ) : FeedTemplateFixture {
        override fun templateKey() = key
    }

    private fun portal(url: String, page: String = "h5/openWebView"): String =
        "tiebaapp://router/portal?params=" + URLEncoder.encode(
            JSONObject()
                .put("page", page)
                .put("pageParams", JSONObject().put("url", url))
                .toString(),
            "UTF-8",
        )

    private companion object {
        const val TOPIC_URL =
            "https://tieba.baidu.com/mo/q/hybrid-usergrow-base/naTopicDetail?topic_id=28365746"
        const val USER_TOPIC_SCHEMA =
            "tiebaapp://router/portal?params=%7B%22page%22:%22h5%2FopenWebView%22%2C%22pageParams%22:%7B%22url%22:%22https%3A%2F%2Ftieba.baidu.com%2Fmo%2Fq%2Fhybrid-usergrow-base%2FnaTopicDetail%3Fcustomfullscreen%3D1%26nonavigationbar%3D1%26loadingSignal%3D1%26topic_id%3D28365746%26topic_name%3D%E9%9D%A2%E7%8E%8B%E4%BA%89%E9%9C%B8%E8%B5%9B%2C%E8%B0%81%E6%98%AF%E4%BD%A0%E5%BF%83%E7%9B%AE%E4%B8%AD%E7%9A%84%E9%9D%A2%E7%8E%8B%26topic_source%3D1%22%7D%7D"
    }
}
