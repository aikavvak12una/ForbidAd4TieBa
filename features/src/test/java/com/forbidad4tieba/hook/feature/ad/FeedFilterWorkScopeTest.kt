package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ModelScoreSettings
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.CustomPostCardFilterSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFilterWorkScopeTest {
    @Test
    fun disabledOrEmptyRulesDoNoFilteringWorkAndKeepListIdentity() {
        val input = listOf(TemplateItem("feed_card", Any()))
        for (settings in listOf(
            SettingsSnapshot.bootstrap(),
            SettingsSnapshot.bootstrap().copy(isCustomPostFilterEnabled = true),
            SettingsSnapshot.bootstrap().copy(
                isCustomPostFilterEnabled = true,
                isPostUserIdFilterEnabled = true,
                postUserIdList = emptySet(),
            ),
        )) {
            assertNull(settings.customPostRules)
            assertSame(input, filter(input, settings))
        }
        assertEquals(0, input.single().keyReads)
        assertEquals(0, input.single().payloadReads)
    }

    @Test
    fun headOnlyRulesReadPayloadsWithoutFortyUnusedTemplateKeys() {
        val unrelated = List(40) { TemplateItem("pb_floor", Any()) }
        val input = unrelated + listOf(unrelated.first(), null, Any())

        assertSame(input, filter(input, scoreSettings()))
        assertEquals(0, unrelated.sumOf { it.keyReads })
        assertEquals(40, unrelated.sumOf { it.payloadReads })
    }

    @Test
    fun unavailableOptionalTargetDisablesOnlyItsRules() {
        val settings = SettingsSnapshot.bootstrap().copy(
            isCustomPostFilterEnabled = true,
            isPostUserIdFilterEnabled = true,
            postUserIdList = setOf("123"),
            isPostVoteFilterEnabled = true,
        )
        val uidCard = Card(Data(listOf(Component("feed_head", mapOf("user_id" to "123")))))
        val voteCard = Card(Data(listOf(Component("card_vote", emptyMap()))))
        val input = listOf(uidCard, voteCard)
        val withoutHead = targets.copy(headParamsField = null)
        val uidOnly = settings.copy(isPostVoteFilterEnabled = false)

        assertSame(input, filter(input, uidOnly, targets = withoutHead))
        assertEquals(listOf(uidCard), filter(input, settings, targets = targets))
        assertEquals(listOf(uidCard), filter(input, settings, targets = withoutHead))
    }

    @Test
    fun everyBlockedOccurrenceKeepsItsObservationAndOriginalInput() {
        val high = scoreCard("0.8")
        val low = scoreCard("0.2")
        val input = listOf(high, low, low, high, null)
        val observations = mutableListOf<Double>()

        val output = filter(input, scoreSettings(), observations = observations)

        assertEquals(listOf(high, high, null), output)
        assertEquals(listOf(0.8, 0.2, 0.2, 0.8), observations)
        assertEquals(listOf(high, low, low, high, null), input)
        assertEquals(1, high.payloadReads)
        assertEquals(1, low.payloadReads)
    }

    private fun filter(
        input: List<*>,
        settings: SettingsSnapshot,
        observations: MutableList<Double> = mutableListOf(),
        targets: CustomPostCardFilterSymbols = this.targets,
    ): List<*> = CustomPostCardBlockHook.filterList(
        input,
        targets,
        "work-scope",
        rules = settings.customPostRules,
        recordModelScores = { observations += it.getValue("msd_score") },
    )

    private fun scoreSettings() = SettingsSnapshot.bootstrap().copy(
        isCustomPostFilterEnabled = true,
        isPostModelScoreFilterEnabled = true,
        postModelScoreThresholds = listOf(ModelScoreSettings.ModelScoreThreshold("msd_score", 0.5)),
    )

    private fun scoreCard(score: String) = TemplateItem(
        "unknown_card",
        Data(listOf(Head(mapOf("extra" to "msd_score:$score")))),
    )

    private val targets = feedFilterFixture(Data::class.java, Head::class.java)

    class TemplateItem(private val key: String?, private val data: Any?) : FeedTemplateFixture {
        var keyReads = 0
        var payloadReads = 0
        override fun templateKey(): String? {
            keyReads++
            return key
        }

        override fun payload(): Any? {
            payloadReads++
            return data
        }
    }

    class Card(private val data: Data) : FeedTemplateFixture {
        override fun templateKey() = "feed_card"
        override fun payload() = data
    }

    class Data(@JvmField val dataList: List<Any?>)
    class Head(@JvmField val params: Map<String, String>) : FeedTemplateFixture {
        override fun templateKey() = "feed_head"
    }

    class Component(
        private val key: String,
        @JvmField val params: Map<String, String>,
    ) : FeedTemplateFixture {
        override fun templateKey() = key
    }
}
