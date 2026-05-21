package com.forbidad4tieba.hook.feature.ad

internal object CustomPostModelScoreCatalog {
    const val MSD_SCORE = "msd_score"
    const val MSD_DURATION_SCORE = "msd_duration_score"
    const val DNN_PB_DUR_CTR_0 = "dnn_pb_dur_ctr[0]"
    const val CUPAI_ALL_SCORES_1 = "cupai_all_scores[1]"
    const val CUPAI_ALL_SCORES_2 = "cupai_all_scores[2]"
    const val CUPAI_ALL_SCORES_3 = "cupai_all_scores[3]"
    const val CDNN_LTR = "cdnn_ltr"

    private val specs = linkedMapOf(
        MSD_SCORE to ScoreSpec(rawKey = "msd_score"),
        MSD_DURATION_SCORE to ScoreSpec(rawKey = "msd_duration_score"),
        DNN_PB_DUR_CTR_0 to ScoreSpec(rawKey = "dnn_pb_dur_ctr", index = 0),
        CUPAI_ALL_SCORES_1 to ScoreSpec(rawKey = "cupai_all_scores", index = 1),
        CUPAI_ALL_SCORES_2 to ScoreSpec(rawKey = "cupai_all_scores", index = 2),
        CUPAI_ALL_SCORES_3 to ScoreSpec(rawKey = "cupai_all_scores", index = 3),
        CDNN_LTR to ScoreSpec(rawKey = "cdnn_ltr"),
    )

    fun extractScores(rawExtra: String): Map<String, Double> {
        val features = parseExtraFeatures(rawExtra)
        if (features.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, Double>(specs.size)
        for ((modelKey, spec) in specs) {
            val rawValue = features[spec.rawKey] ?: continue
            val score = spec.index?.let { index ->
                vectorValue(rawValue, index)
            } ?: rawValue.trim().toDoubleOrNull()
            if (score != null && !score.isNaN() && !score.isInfinite()) {
                result[modelKey] = score
            }
        }
        return result
    }

    private fun parseExtraFeatures(rawExtra: String): Map<String, String> {
        val innerExtra = extractInnerExtra(rawExtra)
        if (innerExtra.isBlank()) return emptyMap()
        val result = HashMap<String, String>(32)
        for (segment in innerExtra.split(';')) {
            val separator = segment.indexOf(':')
            if (separator <= 0) continue
            val key = segment.substring(0, separator).trim()
            val value = segment.substring(separator + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    private fun extractInnerExtra(rawExtra: String): String {
        val source = rawExtra.replace("\\\"", "\"")
        val keyIndex = source.indexOf("\"extra\"")
        if (keyIndex < 0) return rawExtra
        val colonIndex = source.indexOf(':', keyIndex)
        if (colonIndex < 0) return rawExtra
        var valueStart = colonIndex + 1
        while (valueStart < source.length && source[valueStart].isWhitespace()) {
            valueStart += 1
        }
        if (valueStart >= source.length || source[valueStart] != '"') return rawExtra
        return readJsonString(source, valueStart + 1) ?: rawExtra
    }

    private fun readJsonString(source: String, start: Int): String? {
        val out = StringBuilder(source.length - start)
        var index = start
        while (index < source.length) {
            val ch = source[index]
            index += 1
            if (ch == '"') return out.toString()
            if (ch == '\\' && index < source.length) {
                val escaped = source[index]
                index += 1
                out.append(
                    when (escaped) {
                        '"', '\\', '/' -> escaped
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        else -> escaped
                    }
                )
            } else {
                out.append(ch)
            }
        }
        return null
    }

    private fun vectorValue(rawValue: String, index: Int): Double? {
        var start = 0
        var currentIndex = 0
        var pos = 0
        while (pos <= rawValue.length) {
            if (pos == rawValue.length || rawValue[pos] == ',') {
                if (currentIndex == index) {
                    return rawValue.substring(start, pos).trim().toDoubleOrNull()
                }
                currentIndex += 1
                start = pos + 1
            }
            pos += 1
        }
        return null
    }

    private data class ScoreSpec(
        val rawKey: String,
        val index: Int? = null,
    )
}
