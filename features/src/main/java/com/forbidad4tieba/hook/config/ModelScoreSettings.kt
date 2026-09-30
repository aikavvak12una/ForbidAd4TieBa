package com.forbidad4tieba.hook.config

import java.math.BigDecimal
import java.math.RoundingMode

/** Model-score storage format and numeric rules, shared by settings and filtering. */
object ModelScoreSettings {
    const val MODEL_SCORE_STATS_FILE_NAME = "tbhook_model_score_stats.tsv"
    const val DEFAULT_MODEL_SCORE_STATS_POST_LIMIT = 5000
    const val MIN_MODEL_SCORE_STATS_POST_LIMIT = 1000
    const val MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT = 1000
    const val DEFAULT_MODEL_SCORE_AUTO_PERCENTILE = 5
    val SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES = intArrayOf(5, 10, 15, 20)
    private const val MODEL_SCORE_THRESHOLD_SCALE = 6

    data class ModelScoreThreshold(
        val key: String,
        val threshold: Double,
    )

    fun parseModelScoreThresholds(raw: String?): List<ModelScoreThreshold> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = LinkedHashMap<String, Double>()
        for (token in raw.split('\n', ';')) {
            val line = token.trim()
            if (line.isEmpty()) continue
            val separator = line.indexOf('=').takeIf { it > 0 } ?: line.indexOf(':').takeIf { it > 0 } ?: continue
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim().toDoubleOrNull()
            if (key.isNotEmpty() && value != null && value >= 0.0 && !value.isNaN() && !value.isInfinite()) {
                result[key] = value
            }
        }
        return result.map { (key, threshold) -> ModelScoreThreshold(key, threshold) }
    }

    fun serializeModelScoreThresholds(thresholds: List<ModelScoreThreshold>): String {
        return thresholds.joinToString("\n") { "${it.key}=${formatModelScoreThresholdValue(it.threshold)}" }
    }

    fun roundModelScoreThreshold(value: Double): Double {
        return BigDecimal.valueOf(value)
            .setScale(MODEL_SCORE_THRESHOLD_SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun formatModelScoreThresholdValue(value: Double?): String {
        value ?: return ""
        val decimal = BigDecimal.valueOf(value)
            .setScale(MODEL_SCORE_THRESHOLD_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return decimal.toPlainString().ifEmpty { "0" }
    }

    fun normalizeModelScoreAutoPercentile(percentile: Int): Int {
        return if (percentile in SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES) {
            percentile
        } else {
            DEFAULT_MODEL_SCORE_AUTO_PERCENTILE
        }
    }

    fun parseModelScoreAutoPercentiles(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, Int>()
        for (token in raw.split('\n', ';')) {
            val line = token.trim()
            if (line.isEmpty()) continue
            val separator = line.indexOf('=').takeIf { it > 0 } ?: line.indexOf(':').takeIf { it > 0 } ?: continue
            val key = line.substring(0, separator).trim()
            val valueText = line.substring(separator + 1).trim().removePrefix("P").removePrefix("p")
            val percentile = valueText.toIntOrNull() ?: continue
            if (key.isNotEmpty() && percentile in SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES) {
                result[key] = percentile
            }
        }
        return result
    }

    fun serializeModelScoreAutoPercentiles(percentiles: Map<String, Int>): String {
        return percentiles.asSequence()
            .filter { (key, percentile) -> key.isNotBlank() && percentile in SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES }
            .joinToString("\n") { (key, percentile) -> "${key.trim()}=$percentile" }
    }
}
