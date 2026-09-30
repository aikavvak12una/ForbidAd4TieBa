package com.forbidad4tieba.hook.config

import org.junit.Assert.*
import org.junit.Test

class ModelScoreSettingsTest {
    @Test fun thresholdStoragePreservesSixDecimalHalfUpRoundingAndPlainNotation() {
        val values = listOf(
            ModelScoreSettings.ModelScoreThreshold("alpha", 0.1234565),
            ModelScoreSettings.ModelScoreThreshold("beta", 10000000.0),
        )
        val stored = ModelScoreSettings.serializeModelScoreThresholds(values)
        assertEquals("alpha=0.123457\nbeta=10000000", stored)
        assertEquals(listOf(
            ModelScoreSettings.ModelScoreThreshold("alpha", 0.123457), values[1],
        ), ModelScoreSettings.parseModelScoreThresholds(stored))
        assertEquals(0.123457, ModelScoreSettings.roundModelScoreThreshold(0.1234565), 0.0)
        assertEquals("", ModelScoreSettings.formatModelScoreThresholdValue(null))
    }

    @Test fun percentileStorageRetainsLegacySyntaxAndRejectsUnsupportedValues() {
        val parsed = ModelScoreSettings.parseModelScoreAutoPercentiles(
            "alpha=P5; beta:p10\nalpha=20; rejected=7; =15; bad=text",
        )
        assertEquals(linkedMapOf("alpha" to 20, "beta" to 10), parsed)
        assertEquals("alpha=20\nbeta=10", ModelScoreSettings.serializeModelScoreAutoPercentiles(parsed))
        assertEquals(parsed, ModelScoreSettings.parseModelScoreAutoPercentiles(
            ModelScoreSettings.serializeModelScoreAutoPercentiles(parsed)))
        assertEquals(5, ModelScoreSettings.normalizeModelScoreAutoPercentile(7))
        assertEquals(15, ModelScoreSettings.normalizeModelScoreAutoPercentile(15))
        assertEquals("", ModelScoreSettings.serializeModelScoreAutoPercentiles(mapOf("" to 5, "bad" to 7)))
    }

    @Test fun statisticsStorageAndLimitsRemainCompatible() {
        assertEquals("tbhook_model_score_stats.tsv", ModelScoreSettings.MODEL_SCORE_STATS_FILE_NAME)
        assertEquals(5000, ModelScoreSettings.DEFAULT_MODEL_SCORE_STATS_POST_LIMIT)
        assertEquals(1000, ModelScoreSettings.MIN_MODEL_SCORE_STATS_POST_LIMIT)
        assertEquals(1000, ModelScoreSettings.MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT)
        assertArrayEquals(intArrayOf(5, 10, 15, 20), ModelScoreSettings.SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES)
    }

    @Test
    fun parseModelScoreThresholdsAcceptsSupportedSeparatorsAndKeepsLastDuplicateValue() {
        val thresholds = ModelScoreSettings.parseModelScoreThresholds(
            """
            alpha=0.25
            beta:1.5; alpha = 0.75
            """.trimIndent(),
        )

        assertEquals(listOf("alpha", "beta"), thresholds.map { it.key })
        assertEquals(0.75, thresholds[0].threshold, 0.0)
        assertEquals(1.5, thresholds[1].threshold, 0.0)
    }

    @Test
    fun parseModelScoreThresholdsIgnoresInvalidValuesAndBlankKeys() {
        val thresholds = ModelScoreSettings.parseModelScoreThresholds(
            """
            =0.1
            negative=-1
            nan=NaN
            infinite=Infinity
            bad=text
            ok=0
            spaced : 2.5
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ModelScoreSettings.ModelScoreThreshold("ok", 0.0),
                ModelScoreSettings.ModelScoreThreshold("spaced", 2.5),
            ),
            thresholds,
        )
    }

    @Test
    fun parseModelScoreThresholdsReturnsEmptyListForBlankInput() {
        assertTrue(ModelScoreSettings.parseModelScoreThresholds(null).isEmpty())
        assertTrue(ModelScoreSettings.parseModelScoreThresholds("   ").isEmpty())
    }
}
