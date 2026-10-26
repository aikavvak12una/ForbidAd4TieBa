package com.forbidad4tieba.hook.feature.ad

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ModelScoreLogParsingTest {
    @Test fun filteringCurrentRowsPreservesLegacyIdsAndTheOriginalRecordOrder() {
        val lines = listOf("msd_score\t1.0", "other\t2.0", "42\tother\t3.0", "other\tbad",
            "17\tmsd_score\t0.4", "msd_score\t0.5", "18\tmsd_score\tNaN",
            "bad-id\tmsd_score\t0.7", "msd_score\t0.8", "19\tmsd_score_extra\t99")
        val all = arrayListOf<CustomPostModelScoreStats.StoredRecord>()
        val selected = arrayListOf<CustomPostModelScoreStats.StoredRecord>()
        CustomPostModelScoreStats.appendParsedRecords(lines.asSequence(), all, null)
        CustomPostModelScoreStats.appendParsedRecords(lines.asSequence(), selected, "msd_score")
        assertEquals(all.filter { it.modelKey == "msd_score" }, selected)
        assertEquals(listOf(Long.MIN_VALUE, 17L, Long.MIN_VALUE + 2, Long.MIN_VALUE + 3, Long.MIN_VALUE + 3), selected.map { it.postId })
        assertEquals(listOf(1.0, .4, .5, .7, .8), selected.map { it.value })
    }

    @Test fun aReadFailureKeepsRecordsAlreadyParsedForTheCallersExistingRecoveryPath() {
        val output = arrayListOf<CustomPostModelScoreStats.StoredRecord>()
        val lines = sequence {
            yield("1\tmsd_score\t0.5")
            yield("2\tother\t0.9")
            throw IOException("fixture read failure")
        }
        try {
            CustomPostModelScoreStats.appendParsedRecords(lines, output, "msd_score")
            fail("Read failure must still reach the existing logging/recovery boundary")
        } catch (_: IOException) { }
        assertEquals(listOf(.5), output.map { it.value })
    }
}
