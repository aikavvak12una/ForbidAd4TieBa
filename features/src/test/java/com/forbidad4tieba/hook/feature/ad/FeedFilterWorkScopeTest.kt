package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ModelScoreSettings
import com.forbidad4tieba.hook.config.SettingsSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class FeedFilterWorkScopeTest {
    @Test fun templateKeyRequirementsCoverEveryKeyBasedRuleAndExcludeHeadOnlyRules() {
        val base = SettingsSnapshot.bootstrap().copy(isCustomPostFilterEnabled = true)
        val keyCases = listOf(
            "card_vote" to base.copy(isPostVoteFilterEnabled = true),
            "video" to base.copy(isPostVideoFilterEnabled = true),
            "feed_origin_mount" to base.copy(isPostReplyFilterEnabled = true),
            "hot_topic_card" to base.copy(isPostHotFilterEnabled = true),
            "feed_link_store" to base.copy(isPostGoodsFilterEnabled = true),
            "feed_mount_book" to base.copy(isPostGameBookingFilterEnabled = true),
            "feed_input_guide" to base.copy(isPostHelpFilterEnabled = true),
            "feed_score" to base.copy(isPostScoreFilterEnabled = true),
            "recommend_card_list" to base.copy(isPostRecommendForumFilterEnabled = true),
        )
        for ((key, settings) in keyCases) {
            val rules = settings.customPostRules!!
            assertTrue(key, rules.needsTemplateKeyCheck)
            assertTrue(key, CustomPostFilterMatcher.decideByTemplateKey(key, rules).blocked)
            val component = TemplateItem(key, null)
            val card = TemplateItem("unknown_card", Data(listOf(component)))
            assertTrue(key, filter(listOf(card), settings).isEmpty())
            assertEquals(key, 1, card.keyReads)
            assertEquals(key, 1, component.keyReads)
        }

        val headOnlyCases = listOf(
            base.copy(isPostLotteryFilterEnabled = true),
            base.copy(isPostLiveFilterEnabled = true),
            base.copy(isPostUnfollowedForumFilterEnabled = true),
            base.copy(isPostForumKeywordFilterEnabled = true, postForumKeywordList = listOf("forum")),
            base.copy(isPostUserIdFilterEnabled = true, postUserIdList = setOf("123")),
            scoreSettings(),
        )
        for (settings in headOnlyCases) {
            val rules = settings.customPostRules!!
            assertFalse(rules.needsTemplateKeyCheck)
            assertTrue(rules.needsFeedHeadParamsCheck)
            for ((key, _) in keyCases) assertFalse(key, CustomPostFilterMatcher.decideByTemplateKey(key, rules).blocked)
        }
    }

    @Test fun headOnlyRulesReadFortyUnrelatedPayloadsWithoutFortyUnusedKeys() {
        val unrelated = List(40) { TemplateItem("pb_floor", Any()) }
        val input = unrelated + listOf(unrelated.first(), null, Any())

        assertSame(input, filter(input, scoreSettings()))
        assertEquals(0, unrelated.sumOf { it.keyReads })
        assertEquals(40, unrelated.sumOf { it.payloadReads })
    }

    @Test fun restoredHeadOwnerSkipsOtherComponentsButKeepsRepeatedScoreObservations() {
        val head = Head(mapOf("extra" to "msd_score:0.8"))
        val components = List(30) { TemplateItem("ordinary_component", Any()) }
        val card = TemplateItem("unknown_card", Data(components + head))
        val input = listOf(card, card, card)
        val samples = mutableListOf<Double>()

        assertSame(input, filter(input, scoreSettings(), samples = samples))
        assertEquals(listOf(0.8, 0.8, 0.8), samples)
        assertEquals(0, card.keyReads)
        assertEquals(1, card.payloadReads)
        assertEquals(1, head.keyReads)
        assertEquals(0, components.sumOf { it.keyReads })
        assertEquals(0, components.sumOf { it.payloadReads })
    }

    @Test fun headOwnerStillRequiresTheExactKeyAndAcceptsInheritedFields() {
        val wrongKey = Head(mapOf("extra" to "msd_score:0.2"), "other_head")
        val wrongCard = TemplateItem("unknown_card", Data(listOf(wrongKey)))
        val input = listOf(wrongCard)
        assertSame(input, filter(input, scoreSettings()))
        assertEquals(1, wrongKey.keyReads)

        val inheritedHead = InheritedHead(mapOf("extra" to "msd_score:0.2"))
        val card = TemplateItem("pb_floor", Data(listOf(inheritedHead)))
        assertTrue(filter(listOf(card), scoreSettings()).isEmpty())
        assertEquals(1, inheritedHead.keyReads)
        assertEquals(0, card.keyReads)
    }

    @Test fun adCallbacksKeepOuterKeysAndBothStatisticsCountEveryBlockedOccurrence() {
        val ad = TemplateItem("ad_card_video", Any())
        val high = scoreCard("msd_score:0.8")
        val low = scoreCard("msd_score:0.2")
        val input = listOf(ad, high, low, low, high)
        val samples = mutableListOf<Double>()
        val adBefore = pendingCount("pendingAdCount")
        val postBefore = pendingCount("pendingCustomPostCount")

        val output = filter(input, scoreSettings(), samples = samples,
            blockReason = { if (it == "ad_card_video") "ad:template_key:$it" else null })

        assertEquals(listOf(high, high), output)
        assertNotSame(input, output)
        assertEquals(listOf(ad, high, low, low, high), input)
        assertEquals(listOf(0.8, 0.2, 0.2, 0.8), samples)
        assertEquals(1L, pendingCount("pendingAdCount") - adBefore)
        assertEquals(2L, pendingCount("pendingCustomPostCount") - postBefore)
        assertEquals(1, ad.keyReads)
        assertEquals(0, ad.payloadReads)
        assertEquals(1, high.keyReads)
        assertEquals(1, high.payloadReads)
        assertEquals(1, low.keyReads)
        assertEquals(1, low.payloadReads)
    }

    @Test fun recommendationContainersKeepNestedFilteringAndRefreshAfterEachSubmission() {
        val nested = NestedData(mutableListOf(TemplateItem("ordinary_component", null)))
        val recommendation = Recommendation(nested)
        val card = TemplateItem("unknown_card", recommendation)
        val input = listOf(card, card)
        val settings = SettingsSnapshot.bootstrap().copy(
            isCustomPostFilterEnabled = true,
            isPostRecommendForumFilterEnabled = true,
        )
        val targets = targets.copy(
            recommendNestedDataMethod = Recommendation::class.java.getMethod("nestedData"),
            recommendNestedDataListField = NestedData::class.java.getField("dataList"),
        )

        assertSame(input, filter(input, settings, targets = targets))
        assertEquals(1, recommendation.reads)
        nested.dataList += TemplateItem("sideway_list", null)
        assertTrue(filter(input, settings, targets = targets).isEmpty())
        assertEquals(2, recommendation.reads)
        assertEquals(2, card.keyReads)
        assertEquals(2, card.payloadReads)
        assertEquals(2, nested.dataList.first().keyReads)
        assertEquals(1, nested.dataList.last().keyReads)
    }

    @Test fun componentListSubmissionsRetainDirectKeyFilteringAndInputIdentity() {
        val ordinary = TemplateItem("ordinary_component", null)
        val vote = TemplateItem("card_vote", null)
        val data = Data(listOf(ordinary, vote, null))
        val settings = SettingsSnapshot.bootstrap().copy(
            isCustomPostFilterEnabled = true,
            isPostVoteFilterEnabled = true,
        )

        assertEquals(listOf(ordinary, null), filter(data.dataList, settings))
        assertEquals(listOf(ordinary, vote, null), data.dataList)
        assertEquals(1, ordinary.keyReads)
        assertEquals(1, vote.keyReads)
        assertEquals(0, vote.payloadReads)
    }

    private fun filter(
        input: List<*>,
        settings: SettingsSnapshot,
        samples: MutableList<Double> = mutableListOf(),
        blockReason: ((String?) -> String?)? = null,
        targets: com.forbidad4tieba.hook.symbol.model.CustomPostCardFilterSymbols = this.targets,
    ): List<*> = CustomPostCardBlockHook.filterList(
        input, targets, "work-scope", rules = settings.customPostRules,
        templateKeyBlockReason = blockReason,
        recordModelScores = { samples += it.getValue("msd_score") },
    )

    private fun scoreSettings() = SettingsSnapshot.bootstrap().copy(
        isCustomPostFilterEnabled = true,
        isPostModelScoreFilterEnabled = true,
        postModelScoreThresholds = listOf(ModelScoreSettings.ModelScoreThreshold("msd_score", 0.5)),
    )

    private fun scoreCard(extra: String) = TemplateItem("unknown_card", Data(listOf(Head(mapOf("extra" to extra)))))

    private fun pendingCount(name: String): Long =
        (BlockCountStats::class.java.getDeclaredField(name).apply { isAccessible = true }.get(BlockCountStats) as AtomicLong).get()

    private val targets = feedFilterFixture(Data::class.java, Head::class.java)

    class TemplateItem(private val key: String?, private val data: Any?) : FeedTemplateFixture {
        var keyReads = 0
        var payloadReads = 0
        override fun templateKey(): String? { keyReads++; return key }
        override fun payload(): Any? { payloadReads++; return data }
    }

    class Data(@JvmField val dataList: List<Any?>)
    open class Head(@JvmField val params: Map<String, String>, private val key: String = "feed_head") : FeedTemplateFixture {
        var keyReads = 0
        override fun templateKey(): String { keyReads++; return key }
    }
    class InheritedHead(params: Map<String, String>) : Head(params)
    class NestedData(@JvmField val dataList: MutableList<TemplateItem>)
    class Recommendation(private val data: NestedData) {
        var reads = 0
        fun nestedData(): NestedData { reads++; return data }
    }
}
