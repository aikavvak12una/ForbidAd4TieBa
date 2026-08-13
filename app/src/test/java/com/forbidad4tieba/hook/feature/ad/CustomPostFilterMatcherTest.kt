package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPostFilterMatcherTest {
    @Test
    fun modelScoreUsesPersistedThresholdLoadedAtStartup() {
        val modelKey = CustomPostModelScoreCatalog.MSD_SCORE

        val decision = CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("extra" to "msd_score:0.2"),
            runtimeRules(
                thresholds = listOf(ConfigManager.ModelScoreThreshold(modelKey, 0.5)),
            ),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_model_score:$modelKey=0.2<threshold=0.5", decision.reason)
    }

    @Test
    fun lotteryBlocksBrandLotteryCardType() {
        val decision = CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf(
                "thread_type" to "76",
                "card_type" to "brandLotteryAd",
                "title" to "【寻欧启示】来玩鸣潮抽专属大奖",
            ),
            runtimeRules(lottery = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:lottery:card_type=brandLotteryAd", decision.reason)
    }

    @Test
    fun lotteryBlocksThreadTypeWithLotteryTextMarkers() {
        val decision = CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf(
                "thread_type" to "76",
                "title" to "来玩鸣潮抽专属大奖",
                "abstract" to "【活动时间】6月8日-7月9日 【开奖时间】7月15日 【活动奖品】 苹果17",
            ),
            runtimeRules(lottery = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:lottery:thread_type=76,text=lottery_markers", decision.reason)
    }

    @Test
    fun lotteryKeepsThreadTypeWithoutLotteryTextMarkers() {
        val decision = CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf(
                "thread_type" to "76",
                "title" to "普通互动帖子",
                "abstract" to "聊聊版本体验和角色配队",
            ),
            runtimeRules(lottery = true),
        )

        assertEquals(false, decision.blocked)
    }

    @Test
    fun recommendForumBlocksSidewayListTemplateKey() {
        val decision = CustomPostFilterMatcher.decideByTemplateKey(
            "sideway_list",
            runtimeRules(recommendForum = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:recommend_forum:template_key=sideway_list", decision.reason)
    }

    @Test
    fun recommendForumBlocksSidewayListViaRecommendCardKey() {
        val decision = CustomPostFilterMatcher.decideByRecommendCardTemplateKey(
            "sideway_list",
            runtimeRules(recommendForum = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:recommend_forum:template_key=sideway_list", decision.reason)
    }

    @Test
    fun replyBlocksCommentForwardCardType() {
        val decision = CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf(
                "card_type" to "commentForwardCard",
                "thread_type" to "81",
                "page_from" to "recommend",
                "title" to "回复：9月1号放大招，三兄弟开始“断卡行动”",
            ),
            runtimeRules(reply = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:reply:card_type=commentForwardCard", decision.reason)
    }

    @Test
    fun replyDisabledKeepsCommentForwardCard() {
        val decision = CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf(
                "card_type" to "commentForwardCard",
                "thread_type" to "81",
            ),
            runtimeRules(reply = false),
        )

        assertEquals(false, decision.blocked)
    }

    @Test
    fun replyKeepsNormalCardType() {
        val decision = CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf(
                "card_type" to "normal",
                "thread_type" to "0",
            ),
            runtimeRules(reply = true),
        )

        assertEquals(false, decision.blocked)
    }

    @Test
    fun replyStillBlocksLegacyFeedOriginMountTemplateKey() {
        val decision = CustomPostFilterMatcher.decideByTemplateKey(
            "feed_origin_mount",
            runtimeRules(reply = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:reply:template_key=feed_origin_mount", decision.reason)
    }

    @Test
    fun recommendForumDisabledKeepsSidewayList() {
        val decision = CustomPostFilterMatcher.decideByTemplateKey(
            "sideway_list",
            runtimeRules(recommendForum = false),
        )

        assertEquals(false, decision.blocked)
    }

    @Test
    fun voteBlocksCardVoteTemplateKey() {
        val decision = CustomPostFilterMatcher.decideByTemplateKey(
            "card_vote",
            runtimeRules(vote = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:vote:template_key=card_vote", decision.reason)
    }

    @Test
    fun voteBlocksCardMultiVoteTemplateKey() {
        val decision = CustomPostFilterMatcher.decideByTemplateKey(
            "card_multi_vote",
            runtimeRules(vote = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:vote:template_key=card_multi_vote", decision.reason)
    }

    @Test
    fun voteBlocksFeedDiscussTemplateKey() {
        val decision = CustomPostFilterMatcher.decideByTemplateKey(
            "feed_discuss",
            runtimeRules(vote = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:vote:template_key=feed_discuss", decision.reason)
    }

    @Test
    fun voteBlocksFeedPkTemplateKey() {
        val decision = CustomPostFilterMatcher.decideByTemplateKey(
            "feed_pk",
            runtimeRules(vote = true),
        )

        assertTrue(decision.blocked)
        assertEquals("custom_post_type:vote:template_key=feed_pk", decision.reason)
    }

    @Test
    fun voteDisabledKeepsFeedDiscussTemplateKey() {
        val decision = CustomPostFilterMatcher.decideByTemplateKey(
            "feed_discuss",
            runtimeRules(vote = false),
        )

        assertEquals(false, decision.blocked)
    }

    private fun runtimeRules(
        thresholds: List<ConfigManager.ModelScoreThreshold> = emptyList(),
        lottery: Boolean = false,
        recommendForum: Boolean = false,
        reply: Boolean = false,
        vote: Boolean = false,
    ): CustomPostFilterMatcher.RuntimeRules {
        return CustomPostFilterMatcher.RuntimeRules(
            vote = vote,
            video = false,
            reply = reply,
            hot = false,
            goods = false,
            gameBooking = false,
            help = false,
            score = false,
            lottery = lottery,
            live = false,
            recommendForum = recommendForum,
            unfollowedForum = false,
            forumKeyword = false,
            forumKeywords = emptyList(),
            modelScore = thresholds.isNotEmpty(),
            modelScoreThresholds = thresholds,
        )
    }
}
