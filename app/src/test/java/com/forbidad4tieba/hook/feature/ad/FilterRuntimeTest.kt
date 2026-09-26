package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import org.junit.Assert.*
import org.junit.Test

class FilterRuntimeTest {
    @Test fun snapshotPreparesRulesOnceAndCopyRebuildsOnlyTheNewSnapshot() {
        val first = SettingsSnapshot(isCustomPostFilterEnabled = true, isPostHelpFilterEnabled = true)
        assertSame(first.customPostRules, first.customPostRules)
        assertTrue(first.customPostRules!!.help)
        val second = first.copy(isPostHelpFilterEnabled = false, isPostVoteFilterEnabled = true)
        assertNotSame(first.customPostRules, second.customPostRules)
        assertFalse(second.customPostRules!!.help)
        assertTrue(second.customPostRules.vote)
        assertNull(second.copy(isCustomPostFilterEnabled = false).customPostRules)
        assertNull(SettingsSnapshot(isCustomPostFilterEnabled = true).customPostRules)
    }

    @Test fun unmatchedCardsShareKeepAndScoreMatchingOnlyReturnsObservations() {
        val rules = SettingsSnapshot(isCustomPostFilterEnabled = true, isPostHelpFilterEnabled = true).customPostRules!!
        assertSame(CustomPostFilterMatcher.KEEP, CustomPostFilterMatcher.decideByTemplateKey("unknown", rules))
        assertSame(CustomPostFilterMatcher.KEEP, CustomPostFilterMatcher.decideByFeedHeadParams(null, rules))
        val scoreRules = scoreSettings().customPostRules!!
        val kept = CustomPostFilterMatcher.decideByFeedHeadParams(mapOf("extra" to "msd_score:0.8"), scoreRules)
        assertFalse(kept.blocked)
        assertEquals(0.8, kept.modelScores!!["msd_score"]!!, 0.0)
        val blocked = CustomPostFilterMatcher.decideByFeedHeadParams(mapOf("extra" to "msd_score:0.2"), scoreRules)
        assertTrue(blocked.blocked)
        assertEquals(0.2, blocked.modelScores!!["msd_score"]!!, 0.0)
    }

    @Test fun boundaryRecordsKeptAndBlockedScoresInOriginalOrderWithoutChangingInput() {
        val high = card("msd_score:0.8")
        val low = card("msd_score:0.2")
        val input = listOf(high, low, high, null)
        val samples = mutableListOf<Double>()
        val filtered = CustomPostCardBlockHook.filterList(input, filter, "test",
            rules = scoreSettings().customPostRules,
            recordModelScores = { samples += it.getValue("msd_score") })
        assertEquals(listOf(high, high, null), filtered)
        assertEquals(listOf(high, low, high, null), input)
        assertEquals(listOf(0.8, 0.2, 0.8), samples)
        assertEquals(1, high.keyReads)
        assertEquals(1, high.payloadReads)
    }

    @Test fun noDeletionKeepsListIdentityAndDisabledRulesProduceNoStatistics() {
        val input = listOf(card("msd_score:0.8"))
        assertSame(input, CustomPostCardBlockHook.filterList(input, filter, "test",
            rules = scoreSettings().customPostRules, recordModelScores = {}))
        assertSame(input, CustomPostCardBlockHook.filterList(input, filter, "test",
            rules = null, recordModelScores = { error("Disabled rules must not produce observations") }))
    }

    private fun scoreSettings() = SettingsSnapshot(
        isCustomPostFilterEnabled = true,
        isPostModelScoreFilterEnabled = true,
        postModelScoreThresholds = listOf(ConfigManager.ModelScoreThreshold("msd_score", 0.5)),
    )

    private val filter = CustomPostCardBlockHook.RuntimeFilter("dataList", "templateKey", "payload", "params", null, null)
    private fun card(extra: String) = Card(Data(listOf(Head(mapOf("extra" to extra)))))
    class Card(private val data: Data) {
        var keyReads = 0
        var payloadReads = 0
        fun templateKey(): String { keyReads++; return "feed_card" }
        fun payload(): Data { payloadReads++; return data }
    }
    class Data(@JvmField val dataList: List<Head>)
    class Head(@JvmField val params: Map<String, String>) { fun templateKey() = "feed_head" }
}
