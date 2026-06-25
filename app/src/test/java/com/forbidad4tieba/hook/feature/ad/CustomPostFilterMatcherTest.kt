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

    private fun runtimeRules(
        thresholds: List<ConfigManager.ModelScoreThreshold> = emptyList(),
        lottery: Boolean = false,
    ): CustomPostFilterMatcher.RuntimeRules {
        return CustomPostFilterMatcher.RuntimeRules(
            vote = false,
            video = false,
            reply = false,
            hot = false,
            goods = false,
            gameBooking = false,
            help = false,
            score = false,
            lottery = lottery,
            live = false,
            recommendForum = false,
            unfollowedForum = false,
            forumKeyword = false,
            forumKeywords = emptyList(),
            modelScore = thresholds.isNotEmpty(),
            modelScoreThresholds = thresholds,
        )
    }
}
