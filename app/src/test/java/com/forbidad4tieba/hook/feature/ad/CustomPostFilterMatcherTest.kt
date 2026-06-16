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

    private fun runtimeRules(
        thresholds: List<ConfigManager.ModelScoreThreshold>,
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
            live = false,
            recommendForum = false,
            unfollowedForum = false,
            forumKeyword = false,
            forumKeywords = emptyList(),
            modelScore = true,
            modelScoreThresholds = thresholds,
        )
    }
}
