package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.CustomPostFilterRules
import com.forbidad4tieba.hook.config.ModelScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPostFilterMatcherTest {
    @Test
    fun helpRequiresTheExactStructuredMarkersAndItsOwnSwitch() {
        for (cardType in listOf("normal", "question", "question_good", "question_feelyou")) {
            val blocked = CustomPostFilterMatcher.decideByFeedHeadParams(
                mapOf("card_type" to cardType, "thread_type" to "0", "is_special_thread" to "1"),
                rules(help = true),
            )
            assertTrue(cardType, blocked.blocked)
            assertTrue(blocked.reason!!.startsWith("custom_post_type:help:"))
            assertFalse(cardType, CustomPostFilterMatcher.decideByFeedHeadParams(
                mapOf("card_type" to cardType, "thread_type" to "0", "is_special_thread" to "1"),
                rules(),
            ).blocked)
        }

        val valid = mapOf("card_type" to "question_good", "thread_type" to 0, "is_special_thread" to 1)
        for (invalid in listOf(
            valid - "card_type",
            valid - "thread_type",
            valid - "is_special_thread",
            valid + ("thread_type" to 81),
            valid + ("is_special_thread" to 0),
            valid + ("card_type" to "question_good_extra"),
        )) {
            assertSame(invalid.toString(), CustomPostFilterMatcher.KEEP,
                CustomPostFilterMatcher.decideByFeedHeadParams(invalid, rules(help = true)))
        }
        assertFalse(CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("card_type" to "normal", "thread_type" to "0", "is_special_thread" to "0",
                "title" to "求助：这个问题怎么处理？"),
            rules(help = true),
        ).blocked)
        assertTrue(CustomPostFilterMatcher.decideByTemplateKey("feed_input_guide", rules(help = true)).blocked)
        assertFalse(CustomPostFilterMatcher.decideByTemplateKey("feed_input_guide", rules()).blocked)
    }

    @Test
    fun specialCardTypesUseExactFieldsAndIndependentSwitches() {
        assertTrue(CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("card_type" to "brandLotteryAd", "thread_type" to "76"),
            rules(lottery = true),
        ).blocked)
        assertTrue(CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("thread_type" to "76", "title" to "活动抽奖", "abstract" to "开奖时间 奖品"),
            rules(lottery = true),
        ).blocked)
        assertFalse(CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("card_type" to "brandLotteryAd", "thread_type" to "76"),
            rules(),
        ).blocked)
        assertFalse(CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("thread_type" to "76", "title" to "普通互动帖子", "abstract" to "角色配队"),
            rules(lottery = true),
        ).blocked)

        assertTrue(CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("card_type" to "commentForwardCard", "thread_type" to "81"),
            rules(reply = true),
        ).blocked)
        assertFalse(CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("card_type" to "commentForwardCardExtra", "thread_type" to "81"),
            rules(reply = true),
        ).blocked)
        assertFalse(CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("card_type" to "commentForwardCard", "thread_type" to "81"),
            rules(),
        ).blocked)
    }

    @Test
    fun templateRulesUseExactKeysAndDoNotLeakAcrossSwitches() {
        val cases = listOf(
            Triple(
                listOf("card_vote", "card_multi_vote", "feed_discuss", "feed_pk"),
                "video",
                rules(vote = true),
            ),
            Triple(listOf("feed_origin_mount"), "sideway_list", rules(reply = true)),
            Triple(
                listOf(
                    "recommend_card_forum_attention",
                    "recommend_card_person_attention",
                    "recommend_card_list",
                    "sideway_list",
                ),
                "hot_topic_card",
                rules(recommendForum = true),
            ),
        )
        for ((blockedKeys, unrelatedKey, activeRules) in cases) {
            for (key in blockedKeys) {
                assertTrue(key, CustomPostFilterMatcher.decideByTemplateKey(key, activeRules).blocked)
                assertFalse(key, CustomPostFilterMatcher.decideByTemplateKey(key, rules()).blocked)
            }
            assertFalse(unrelatedKey,
                CustomPostFilterMatcher.decideByTemplateKey(unrelatedKey, activeRules).blocked)
            assertFalse(blockedKeys.first() + "_extra",
                CustomPostFilterMatcher.decideByTemplateKey(blockedKeys.first() + "_extra", activeRules).blocked)
        }
    }

    @Test
    fun userAndModelScoreRulesPreserveExactBusinessFieldSemantics() {
        val userRules = rules(userIds = setOf("123", "9007199254740993"))
        for (uid in listOf("123", " 00123 ", 123, 123L, "9007199254740993", 9007199254740993L)) {
            assertTrue("uid=$uid", CustomPostFilterMatcher.decideByFeedHeadParams(
                mapOf("user_id" to uid),
                userRules,
            ).blocked)
        }
        for (uid in listOf(null, "", "0", "12", "1234", "9123", "123.0", "-123", "123 456",
            "9007199254740992")) {
            assertSame("uid=$uid", CustomPostFilterMatcher.KEEP,
                CustomPostFilterMatcher.decideByFeedHeadParams(mapOf("user_id" to uid), userRules))
        }
        assertSame(CustomPostFilterMatcher.KEEP, CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("uid" to "123", "author_id" to "123", "thread_id" to "123"),
            userRules,
        ))

        val modelRules = rules(thresholds = listOf(ModelScoreSettings.ModelScoreThreshold("msd_score", 0.5)))
        val below = CustomPostFilterMatcher.decideByFeedHeadParams(mapOf("extra" to "msd_score:0.2"), modelRules)
        val above = CustomPostFilterMatcher.decideByFeedHeadParams(mapOf("extra" to "msd_score:0.8"), modelRules)
        assertTrue(below.blocked)
        assertFalse(above.blocked)
        assertEquals(0.8, above.modelScores!!.getValue("msd_score"), 0.0)
    }

    private fun rules(
        thresholds: List<ModelScoreSettings.ModelScoreThreshold> = emptyList(),
        lottery: Boolean = false,
        recommendForum: Boolean = false,
        reply: Boolean = false,
        vote: Boolean = false,
        help: Boolean = false,
        userIds: Set<String> = emptySet(),
    ) = CustomPostFilterRules(
        vote = vote,
        video = false,
        reply = reply,
        hot = false,
        goods = false,
        gameBooking = false,
        help = help,
        score = false,
        lottery = lottery,
        live = false,
        recommendForum = recommendForum,
        unfollowedForum = false,
        forumKeyword = false,
        forumKeywords = emptyList(),
        userId = userIds.isNotEmpty(),
        userIds = userIds,
        modelScore = thresholds.isNotEmpty(),
        modelScoreThresholds = thresholds,
    )
}
