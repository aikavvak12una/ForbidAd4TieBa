package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

internal object CustomPostModelScoreStats {
    private const val MIN_BUCKET_COUNT = 8
    private const val MAX_BUCKET_COUNT = 64
    private const val FLUSH_DELAY_MS = 1200L
    private const val HISTOGRAM_TAIL_PERCENTILE = 0.90

    // 持久化失败退避：连续写盘失败达到这个次数后，
    // Disable persistence for this process after repeated write failures.
    private const val PERSISTENCE_FAILURE_LIMIT = 3
    // Trim only after enough new posts accumulate.
    private const val TRIM_POST_THRESHOLD_RATIO = 0.1
    private val lock = Any()
    private val fileLock = Any()
    private val pendingRecords = ArrayList<StoredRecord>(128)
    private val flushScheduled = AtomicBoolean(false)
    private val postIdGenerator = AtomicLong(System.currentTimeMillis())
    private var statsGeneration = 0L
    // Guarded by fileLock.
    private var persistenceFailureCount = 0
    private var persistenceDisabled = false
    private var postsSinceLastTrim = 0
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "tbhook-model-score-stats").apply {
            isDaemon = true
        }
    }

    fun record(scores: Map<String, Double>) {
        if (scores.isEmpty()) return
        if (isPersistenceDisabled()) return
        var added = false
        val postId = postIdGenerator.incrementAndGet()
        synchronized(lock) {
            val generation = statsGeneration
            for ((modelKey, score) in scores) {
                if (modelKey.isNotBlank() && !score.isNaN() && !score.isInfinite()) {
                    pendingRecords.add(StoredRecord(generation, postId, modelKey, score))
                    added = true
                }
            }
        }
        if (added) scheduleFlush()
    }

    fun clear() {
        synchronized(lock) {
            statsGeneration += 1
            pendingRecords.clear()
        }
        val context = ConfigManager.getAppContext() ?: return
        synchronized(fileLock) {
            persistenceFailureCount = 0
            persistenceDisabled = false
            postsSinceLastTrim = 0
            runCatching {
                File(context.filesDir, ConfigManager.MODEL_SCORE_STATS_FILE_NAME).delete()
            }.onFailure { t ->
                XposedCompat.logW("[CustomPostModelScoreStats] clear failed: ${t.message}")
            }
        }
    }

    fun trimToPostLimitAsync(postLimit: Int = ConfigManager.postModelScoreStatsPostLimit) {
        executor.execute {
            val context = ConfigManager.getAppContext() ?: return@execute
            val file = File(context.filesDir, ConfigManager.MODEL_SCORE_STATS_FILE_NAME)
            synchronized(fileLock) {
                runCatching {
                    trimStoredFileToPostLimit(file, postLimit)
                }.onFailure { t ->
                    XposedCompat.logW("[CustomPostModelScoreStats] trim failed: ${t.message}")
                }
            }
        }
    }

    fun applyAutoPercentileThresholdsAsync() {
        executor.execute {
            applyAutoPercentileThresholds()
        }
    }

    fun summary(modelKey: String): Summary {
        val values = readLimitedValues(modelKey)
        values.sort()
        if (values.isEmpty()) return Summary.empty()

        val count = values.size
        val average = values.sum() / count
        val min = values.first()
        val max = values.last()
        val percentileValues = LinkedHashMap<Int, Double>()
        for (percentile in ConfigManager.SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES) {
            percentileValues[percentile] = percentile(values, percentile / 100.0)
        }
        val displayMax = percentile(values, HISTOGRAM_TAIL_PERCENTILE)
        return Summary(
            sampleCount = count,
            percentileValues = percentileValues,
            average = average,
            min = min,
            max = max,
            displayMin = min,
            displayMax = displayMax.takeIf { it > min } ?: max,
            buckets = buildBuckets(values, min, max, displayMax),
        )
    }

    private fun scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return
        executor.schedule({
            var retryPending = true
            try {
                retryPending = flushPending()
            } finally {
                flushScheduled.set(false)
                val hasMore = synchronized(lock) { pendingRecords.isNotEmpty() }
                if (retryPending && hasMore) scheduleFlush()
            }
        }, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun flushPending(): Boolean {
        val records = synchronized(lock) {
            if (pendingRecords.isEmpty()) return true
            ArrayList(pendingRecords).also { pendingRecords.clear() }
        }
        val context = ConfigManager.getAppContext()
        if (context == null) {
            synchronized(lock) {
                pendingRecords.addAll(0, records)
            }
            return false
        }
        val file = File(context.filesDir, ConfigManager.MODEL_SCORE_STATS_FILE_NAME)
        synchronized(fileLock) {
            if (persistenceDisabled) {
                // Drop buffered records after persistence is disabled.
                synchronized(lock) { pendingRecords.clear() }
                return false
            }
            val activeRecords = synchronized(lock) {
                records.filter { it.generation == statsGeneration }
            }
            if (activeRecords.isEmpty()) return true
            val appended = runCatching {
                file.parentFile?.mkdirs()
                file.appendText(
                    activeRecords.joinToString(separator = "\n", postfix = "\n") { serializeRecord(it) },
                    Charsets.UTF_8
                )
            }
            appended.onFailure { t ->
                persistenceFailureCount += 1
                if (persistenceFailureCount >= PERSISTENCE_FAILURE_LIMIT) {
                    if (!persistenceDisabled) {
                        persistenceDisabled = true
                        XposedCompat.logW(
                            "[CustomPostModelScoreStats] persistence disabled after " +
                                "$persistenceFailureCount consecutive failures: ${t.message}"
                        )
                    }
                    // Drop buffered records after persistence is disabled.
                    synchronized(lock) { pendingRecords.clear() }
                    return false
                }
                synchronized(lock) {
                    pendingRecords.addAll(0, activeRecords)
                }
                XposedCompat.logW(
                    "[CustomPostModelScoreStats] flush failed " +
                        "($persistenceFailureCount/$PERSISTENCE_FAILURE_LIMIT): ${t.message}"
                )
                return true
            }
            // Reset failure count after a successful write.
            persistenceFailureCount = 0
            val newPostCount = activeRecords.map { it.postId }.distinct().size
            postsSinceLastTrim += newPostCount
            val trimThreshold = (ConfigManager.postModelScoreStatsPostLimit * TRIM_POST_THRESHOLD_RATIO).toInt()
                .coerceAtLeast(50)
            if (postsSinceLastTrim >= trimThreshold) {
                postsSinceLastTrim = 0
                runCatching {
                    trimStoredFileToPostLimit(file, ConfigManager.postModelScoreStatsPostLimit)
                }.onFailure { t ->
                    XposedCompat.logW("[CustomPostModelScoreStats] trim failed: ${t.message}")
                }
            }
        }
        return true
    }

    private fun applyAutoPercentileThresholds() {
        val context = ConfigManager.getAppContext() ?: return
        ConfigManager.refreshRuntimeSettings(context)
        val settings = ConfigManager.snapshot()
        if (!settings.isCustomPostFilterEnabled || !settings.isPostModelScoreFilterEnabled) return
        val autoPercentiles = settings.postModelScoreAutoPercentiles
        if (autoPercentiles.isEmpty()) return
        val applied = ArrayList<AppliedAutoThreshold>(autoPercentiles.size)
        val skippedBySampleCount = LinkedHashMap<String, Int>()
        for ((key, rawPercentile) in autoPercentiles) {
            val percentile = ConfigManager.normalizeModelScoreAutoPercentile(rawPercentile)
            val summary = summary(key)
            if (summary.sampleCount <= ConfigManager.MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT) {
                skippedBySampleCount[key] = summary.sampleCount
                continue
            }
            val rawValue = summary.percentileValue(percentile) ?: continue
            val value = ConfigManager.roundModelScoreThreshold(rawValue)
            if (value < 0.0 || value.isNaN() || value.isInfinite()) continue
            applied.add(AppliedAutoThreshold(key, percentile, value))
        }
        if (applied.isEmpty() && skippedBySampleCount.isEmpty()) return

        val prefs = ConfigManager.getPrefs(context)
        val merged = LinkedHashMap<String, Double>()
        for (threshold in ConfigManager.parseModelScoreThresholds(
            prefs.getString(ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS, "")
        )) {
            merged[threshold.key] = threshold.threshold
        }

        var changed = false
        for (key in skippedBySampleCount.keys) {
            if (merged.remove(key) != null) {
                changed = true
            }
        }
        for (threshold in applied) {
            if (merged[threshold.key] != threshold.value) {
                changed = true
                merged[threshold.key] = threshold.value
            }
        }
        val thresholds = merged.map { (key, threshold) ->
            ConfigManager.ModelScoreThreshold(key, threshold)
        }
        if (changed) {
            ConfigManager.postModelScoreThresholds = thresholds
            val persisted = prefs.edit()
                .putString(
                    ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS,
                    ConfigManager.serializeModelScoreThresholds(thresholds)
                )
                .commit()
            if (!persisted) {
                XposedCompat.logW("[CustomPostModelScoreStats] auto percentile threshold persist failed")
            }
        }
        if (applied.isNotEmpty()) {
            XposedCompat.log(
                "[CustomPostModelScoreStats] auto percentile effective: " +
                    applied.joinToString(", ") {
                        "${it.key}=P${it.percentile}:${ConfigManager.formatModelScoreThresholdValue(it.value)}"
                    }
            )
        }
        if (skippedBySampleCount.isNotEmpty()) {
            XposedCompat.log(
                "[CustomPostModelScoreStats] auto percentile pending samples: " +
                    skippedBySampleCount.entries.joinToString(", ") {
                        "${it.key}=${it.value}<=${ConfigManager.MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT}"
                    }
            )
        }
    }

    private fun isPersistenceDisabled(): Boolean {
        return synchronized(fileLock) { persistenceDisabled }
    }

    /**
     * 读取 [modelKey] 的已存储值和待写入值。
     * 按帖子数量限制处理，只让最新 [ConfigManager.postModelScoreStatsPostLimit] 个帖子参与统计。
     */
    private fun readLimitedValues(modelKey: String): ArrayList<Double> {
        val limit = ConfigManager.postModelScoreStatsPostLimit
        val storedRecords = readStoredRecordsForKey(modelKey)
        val pendingForKey = synchronized(lock) {
            pendingRecords.filter { it.modelKey == modelKey }
        }
        // Merge stored and pending records, keeping the newest unique post ids.
        val allRecords = ArrayList<StoredRecord>(storedRecords.size + pendingForKey.size)
        allRecords.addAll(storedRecords)
        allRecords.addAll(pendingForKey)
        if (allRecords.isEmpty()) return ArrayList()

        // Walk from the tail to collect the newest unique post ids.
        val keepPostIds = LinkedHashSet<Long>(limit)
        for (index in allRecords.indices.reversed()) {
            keepPostIds.add(allRecords[index].postId)
            if (keepPostIds.size >= limit) break
        }
        val result = ArrayList<Double>(keepPostIds.size)
        for (record in allRecords) {
            if (record.postId in keepPostIds) {
                result.add(record.value)
            }
        }
        return result
    }

    private fun readStoredRecordsForKey(modelKey: String): List<StoredRecord> {
        val result = ArrayList<StoredRecord>()
        val context = ConfigManager.getAppContext() ?: return result
        val file = File(context.filesDir, ConfigManager.MODEL_SCORE_STATS_FILE_NAME)
        if (!file.isFile) return result
        synchronized(fileLock) {
            runCatching {
                var legacyPostId = Long.MIN_VALUE
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        val parsed = parseStoredRecord(line, legacyPostId)
                        if (parsed != null) {
                            if (parsed.legacy) legacyPostId += 1
                            if (parsed.record.modelKey == modelKey) {
                                result.add(parsed.record)
                            }
                        }
                    }
                }
            }.onFailure { t ->
                XposedCompat.logW("[CustomPostModelScoreStats] read failed: ${t.message}")
            }
        }
        return result
    }

    private fun trimStoredFileToPostLimit(file: File, rawLimit: Int) {
        if (!file.isFile) return
        val limit = rawLimit.coerceAtLeast(ConfigManager.MIN_MODEL_SCORE_STATS_POST_LIMIT)
        val records = readStoredRecords(file)
        if (records.isEmpty()) {
            file.delete()
            return
        }
        // Walk from the tail to collect the newest unique post ids.
        val keepPostIds = LinkedHashSet<Long>(limit)
        for (index in records.indices.reversed()) {
            keepPostIds.add(records[index].postId)
            if (keepPostIds.size >= limit) break
        }
        val keptRecords = records.filter { it.postId in keepPostIds }
        if (keptRecords.size == records.size) return
        file.writeText(
            keptRecords.joinToString(separator = "\n", postfix = "\n") { serializeRecord(it) },
            Charsets.UTF_8
        )
    }

    private fun readStoredRecords(file: File): List<StoredRecord> {
        val result = ArrayList<StoredRecord>()
        var legacyPostId = Long.MIN_VALUE
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val parsed = parseStoredRecord(line, legacyPostId)
                if (parsed != null) {
                    result.add(parsed.record)
                    if (parsed.legacy) legacyPostId += 1
                }
            }
        }
        return result
    }

    private fun parseStoredRecord(line: String, legacyPostId: Long): ParsedStoredRecord? {
        val separator = line.indexOf('\t')
        if (separator <= 0) return null
        val secondSeparator = line.indexOf('\t', separator + 1)
        val postId: Long
        val modelKey: String
        val valueText: String
        val legacy: Boolean
        if (secondSeparator > separator) {
            postId = line.substring(0, separator).toLongOrNull() ?: legacyPostId
            modelKey = line.substring(separator + 1, secondSeparator)
            valueText = line.substring(secondSeparator + 1)
            legacy = false
        } else {
            postId = legacyPostId
            modelKey = line.substring(0, separator)
            valueText = line.substring(separator + 1)
            legacy = true
        }
        if (modelKey.isBlank()) return null
        val value = valueText.trim().toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return ParsedStoredRecord(StoredRecord(0L, postId, modelKey, value), legacy)
    }

    private fun serializeRecord(record: StoredRecord): String {
        return record.postId.toString() + '\t' + record.modelKey + '\t' + record.value.toString()
    }

    private fun buildBuckets(values: List<Double>, min: Double, max: Double, rawDisplayMax: Double): List<Bucket> {
        if (values.isEmpty()) return emptyList()
        val range = max - min
        if (range <= 0.0) {
            return listOf(Bucket(min, max, values.size, values.size, 1.0))
        }
        val bucketCount = (sqrt(values.size.toDouble()) * 1.5).toInt().coerceIn(MIN_BUCKET_COUNT, MAX_BUCKET_COUNT)
        val displayMax = rawDisplayMax.takeIf { it > min && it < max } ?: max
        val hasTailBucket = displayMax < max
        val regularBucketCount = if (hasTailBucket) bucketCount - 1 else bucketCount
        val counts = IntArray(bucketCount)
        for (value in values) {
            val index = if (hasTailBucket && value >= displayMax) {
                bucketCount - 1
            } else {
                (((value - min) / (displayMax - min)) * regularBucketCount)
                    .toInt()
                    .coerceIn(0, regularBucketCount - 1)
            }
            counts[index] += 1
        }
        val width = (displayMax - min) / regularBucketCount
        var cumulative = 0
        return counts.mapIndexed { index, count ->
            val start: Double
            val end: Double
            if (hasTailBucket && index == bucketCount - 1) {
                start = displayMax
                end = max
            } else {
                start = min + width * index
                end = if (index == regularBucketCount - 1) displayMax else start + width
            }
            cumulative += count
            Bucket(
                start = start,
                end = end,
                count = count,
                cumulativeCount = cumulative,
                cumulativeRatio = cumulative.toDouble() / values.size,
            )
        }
    }

    private fun percentile(sortedValues: List<Double>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return Double.NaN
        if (sortedValues.size == 1) return sortedValues[0]
        val scaledIndex = (sortedValues.size - 1) * percentile.coerceIn(0.0, 1.0)
        val lower = scaledIndex.toInt().coerceIn(0, sortedValues.lastIndex)
        val upper = (lower + 1).coerceAtMost(sortedValues.lastIndex)
        val fraction = scaledIndex - lower
        return sortedValues[lower] + (sortedValues[upper] - sortedValues[lower]) * fraction
    }

    data class Summary(
        val sampleCount: Int,
        val percentileValues: Map<Int, Double>,
        val average: Double?,
        val min: Double?,
        val max: Double?,
        val displayMin: Double?,
        val displayMax: Double?,
        val buckets: List<Bucket>,
    ) {
        fun percentileValue(percentile: Int): Double? {
            return percentileValues[ConfigManager.normalizeModelScoreAutoPercentile(percentile)]
        }

        companion object {
            fun empty(): Summary = Summary(
                sampleCount = 0,
                percentileValues = emptyMap(),
                average = null,
                min = null,
                max = null,
                displayMin = null,
                displayMax = null,
                buckets = emptyList(),
            )
        }
    }

    data class Bucket(
        val start: Double,
        val end: Double,
        val count: Int,
        val cumulativeCount: Int,
        val cumulativeRatio: Double,
    )

    private data class StoredRecord(
        val generation: Long,
        val postId: Long,
        val modelKey: String,
        val value: Double,
    )

    private data class AppliedAutoThreshold(
        val key: String,
        val percentile: Int,
        val value: Double,
    )

    private data class ParsedStoredRecord(
        val record: StoredRecord,
        val legacy: Boolean,
    )
}
