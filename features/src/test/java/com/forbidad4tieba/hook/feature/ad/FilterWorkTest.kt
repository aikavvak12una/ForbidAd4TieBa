package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.SettingsSnapshot
import org.junit.Assert.*
import org.junit.Test

class FilterWorkTest {
    @Test fun userFilteringDoesNotReadUnrelatedPostFields() {
        val reads = ArrayList<Any?>()
        val params = object : HashMap<String, Any>() {
            override fun get(key: String): Any? { reads += key; return super.get(key) }
        }.apply { put("user_id", "123") }
        val rules = SettingsSnapshot.bootstrap().copy(isCustomPostFilterEnabled = true,
            isPostUserIdFilterEnabled = true, postUserIdList = setOf("123")).customPostRules!!
        assertTrue(CustomPostFilterMatcher.decideByFeedHeadParams(params, rules).blocked)
        assertEquals(listOf("user_id"), reads)
    }

    @Test fun scoreParsingKeepsDuplicateBlankVectorAndNonfiniteSemantics() {
        val raw = "ignored:large:value;msd_score:0.2;;msd_score:0.8;msd_score: ;" +
            "cupai_all_scores:0,1,2,3;dnn_pb_dur_ctr:0.4,0.7;cdnn_ltr:NaN;msd_duration_score:Infinity"
        assertEquals(linkedMapOf("msd_score" to .8, "dnn_pb_dur_ctr[0]" to .4,
            "cupai_all_scores[1]" to 1.0, "cupai_all_scores[2]" to 2.0, "cupai_all_scores[3]" to 3.0),
            CustomPostModelScoreCatalog.extractScores(raw))
        assertEquals(mapOf("msd_score" to .7), CustomPostModelScoreCatalog.extractScores("{\"extra\":\"msd_score:0.7;unused:1\"}"))
        assertTrue(CustomPostModelScoreCatalog.extractScores("unknown:1;broken;msd_score:").isEmpty())
    }

    @Test fun unrelatedPayloadWithCoincidentallyNamedFieldsIsNotATarget() {
        val input = listOf(object : FeedTemplateFixture {
            override fun templateKey() = "feed_card"
            override fun payload() = UnrelatedData(listOf(FilterRuntimeTest.Head(mapOf("extra" to "msd_score:0.2"))))
        })
        val rules = SettingsSnapshot.bootstrap().copy(isCustomPostFilterEnabled = true,
            isPostModelScoreFilterEnabled = true).customPostRules
        assertSame(input, CustomPostCardBlockHook.filterList(input,
            feedFilterFixture(FilterRuntimeTest.Data::class.java, FilterRuntimeTest.Head::class.java), "unknown-owner",
            rules = rules, recordModelScores = { error("An unrelated owner must not supply statistics") }))
    }
    class UnrelatedData(@JvmField val dataList: List<FeedTemplateFixture>)
}
