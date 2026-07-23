package com.forbidad4tieba.hook.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

internal enum class DetailedLogSource {
    MODULE,
    TIEBA,
}

internal data class DetailedLogEntry(
    val timestampMillis: Long,
    val source: DetailedLogSource,
    val level: String,
    val tag: String,
    val message: String,
) {
    val characterCount: Int
        get() = level.length + tag.length + message.length
}

internal data class DetailedLogSnapshot(
    val sessionStartMillis: Long,
    val processName: String,
    val runtimeEnvironment: String,
    val entries: List<DetailedLogEntry>,
    val droppedEntryCount: Long,
)

internal class DetailedLogSessionStore(
    private val maxEntries: Int,
    private val maxTotalCharacters: Int,
    private val maxEntryCharacters: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val entries = ArrayDeque<DetailedLogEntry>()
    private var active = false
    private var sessionStartMillis = 0L
    private var processName = ""
    private var runtimeEnvironment = ""
    private var totalCharacters = 0
    private var droppedEntryCount = 0L

    init {
        require(maxEntries > 0)
        require(maxTotalCharacters > 0)
        require(maxEntryCharacters > 0)
    }

    @Synchronized
    fun start(
        processName: String,
        runtimeEnvironment: String,
    ) {
        entries.clear()
        totalCharacters = 0
        droppedEntryCount = 0
        sessionStartMillis = clock()
        this.processName = processName
        this.runtimeEnvironment = runtimeEnvironment
        active = true
    }

    @Synchronized
    fun append(
        source: DetailedLogSource,
        level: String,
        tag: String,
        message: String,
    ): Boolean {
        if (!active) return false

        val entry = boundedEntry(
            timestampMillis = clock(),
            source = source,
            level = level,
            tag = tag,
            message = message,
        )
        while (
            entries.isNotEmpty() &&
            (entries.size >= maxEntries || totalCharacters + entry.characterCount > maxTotalCharacters)
        ) {
            val removed = entries.removeFirst()
            totalCharacters -= removed.characterCount
            droppedEntryCount += 1
        }
        entries.addLast(entry)
        totalCharacters += entry.characterCount
        return true
    }

    @Synchronized
    fun snapshot(): DetailedLogSnapshot? {
        if (!active) return null
        return DetailedLogSnapshot(
            sessionStartMillis = sessionStartMillis,
            processName = processName,
            runtimeEnvironment = runtimeEnvironment,
            entries = entries.toList(),
            droppedEntryCount = droppedEntryCount,
        )
    }

    private fun boundedEntry(
        timestampMillis: Long,
        source: DetailedLogSource,
        level: String,
        tag: String,
        message: String,
    ): DetailedLogEntry {
        var remaining = minOf(maxEntryCharacters, maxTotalCharacters)
        val boundedLevel = truncate(level.ifBlank { "INFO" }, minOf(remaining, MAX_LEVEL_CHARACTERS))
        remaining -= boundedLevel.length
        val boundedTag = truncate(tag.ifBlank { "-" }, minOf(remaining, MAX_TAG_CHARACTERS))
        remaining -= boundedTag.length
        val boundedMessage = truncate(message, remaining)
        return DetailedLogEntry(
            timestampMillis = timestampMillis,
            source = source,
            level = boundedLevel,
            tag = boundedTag,
            message = boundedMessage,
        )
    }

    private fun truncate(value: String, maxCharacters: Int): String {
        if (maxCharacters <= 0) return ""
        if (value.length <= maxCharacters) return value
        if (maxCharacters <= TRUNCATED_SUFFIX.length) return value.take(maxCharacters)
        return value.take(maxCharacters - TRUNCATED_SUFFIX.length) + TRUNCATED_SUFFIX
    }

    private companion object {
        const val MAX_LEVEL_CHARACTERS = 32
        const val MAX_TAG_CHARACTERS = 256
        const val TRUNCATED_SUFFIX = "...[truncated]"
    }
}

internal object DetailedLogSession {
    private const val MAX_ENTRIES = 10_000
    private const val MAX_TOTAL_CHARACTERS = 2_000_000
    private const val MAX_ENTRY_CHARACTERS = 16_384

    private val store = DetailedLogSessionStore(
        maxEntries = MAX_ENTRIES,
        maxTotalCharacters = MAX_TOTAL_CHARACTERS,
        maxEntryCharacters = MAX_ENTRY_CHARACTERS,
    )

    fun start(
        processName: String,
        runtimeEnvironment: String,
    ) {
        store.start(processName, runtimeEnvironment)
    }

    fun recordModule(level: String, tag: String, message: String) {
        store.append(DetailedLogSource.MODULE, level, tag, message)
    }

    fun recordTieba(level: String, tag: String, message: String): Boolean {
        return store.append(DetailedLogSource.TIEBA, level, tag, message)
    }

    fun snapshot(): DetailedLogSnapshot? = store.snapshot()
}

internal object DetailedLogFileFormatter {
    private val fileNameFormatter = DateTimeFormatter
        .ofPattern("yyyyMMdd_HHmmss", Locale.US)
    private val lineTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX", Locale.US)

    fun fileName(
        timestampMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val time = fileNameFormatter.withZone(zoneId).format(Instant.ofEpochMilli(timestampMillis))
        return "FA4TB_$time.log"
    }

    fun format(
        snapshot: DetailedLogSnapshot,
        savedAtMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return buildString {
            appendLine("FA4TB detailed log")
            appendLine("session_start=${formatTimestamp(snapshot.sessionStartMillis, zoneId)}")
            appendLine("save_time=${formatTimestamp(savedAtMillis, zoneId)}")
            appendLine("process=${snapshot.processName}")
            appendLine("runtimeEnvironment=")
            appendLine(snapshot.runtimeEnvironment)
            appendLine("entry_count=${snapshot.entries.size}")
            appendLine("dropped_count=${snapshot.droppedEntryCount}")
            appendLine("---")
            snapshot.entries.forEach { entry ->
                append(formatTimestamp(entry.timestampMillis, zoneId))
                append(" [")
                append(entry.source.name)
                append("][")
                append(entry.level)
                append("][")
                append(entry.tag)
                append("] ")
                append(entry.message)
                appendLine()
            }
        }
    }

    private fun formatTimestamp(timestampMillis: Long, zoneId: ZoneId): String {
        return lineTimeFormatter.withZone(zoneId).format(Instant.ofEpochMilli(timestampMillis))
    }
}
