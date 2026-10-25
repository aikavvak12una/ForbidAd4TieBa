package com.forbidad4tieba.hook.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

enum class DetailedLogSource {
    MODULE,
    TIEBA,
    DIAGNOSTIC,
}

data class DetailedLogEntry(
    val timestampMillis: Long,
    val source: DetailedLogSource,
    val level: String,
    val tag: String,
    val message: String,
    val sequence: Long = 0L,
) {
    val characterCount: Int
        get() = level.length + tag.length + message.length
}

data class DetailedLogSnapshot(
    val sessionStartMillis: Long,
    val processName: String,
    val runtimeEnvironment: String,
    val entries: List<DetailedLogEntry>,
    val droppedEntryCount: Long,
    val droppedDiagnosticCount: Long = 0L,
)

internal class DetailedLogSessionStore(
    private val maxEntries: Int,
    private val maxTotalCharacters: Int,
    private val maxEntryCharacters: Int,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxDiagnosticEntries: Int = 0,
    private val maxDiagnosticCharacters: Int = 0,
) {
    private val entries = ArrayDeque<DetailedLogEntry>()
    private val diagnosticEntries = ArrayDeque<DetailedLogEntry>()
    private var active = false
    private var sessionStartMillis = 0L
    private var processName = ""
    private var runtimeEnvironment = ""
    private var totalCharacters = 0
    private var droppedEntryCount = 0L
    private var diagnosticCharacters = 0
    private var droppedDiagnosticCount = 0L
    private var sequence = 0L

    init {
        require(maxEntries > 0)
        require(maxTotalCharacters > 0)
        require(maxEntryCharacters > 0)
        require(maxDiagnosticEntries in 0..maxEntries)
        require(maxDiagnosticCharacters in 0..maxTotalCharacters)
    }

    @Synchronized
    fun start(
        processName: String,
        runtimeEnvironment: String,
    ) {
        entries.clear()
        diagnosticEntries.clear()
        totalCharacters = 0
        diagnosticCharacters = 0
        droppedEntryCount = 0
        droppedDiagnosticCount = 0
        sequence = 0
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

        val diagnostic = source == DetailedLogSource.DIAGNOSTIC
        // Structured records must remain valid JSON: reject oversize records instead of cutting them.
        if (diagnostic && (maxDiagnosticEntries == 0 ||
                level.ifBlank { "INFO" }.length.toLong() + tag.ifBlank { "-" }.length + message.length >
                minOf(maxEntryCharacters, maxDiagnosticCharacters))) {
            droppedEntryCount++
            droppedDiagnosticCount++
            return false
        }
        val entry = boundedEntry(
            timestampMillis = clock(),
            source = source,
            level = level,
            tag = tag,
            message = message,
        )
        if (diagnostic) {
            while (diagnosticEntries.isNotEmpty() &&
                (diagnosticEntries.size >= maxDiagnosticEntries ||
                    diagnosticCharacters + entry.characterCount > maxDiagnosticCharacters)) {
                dropOldestDiagnostic()
            }
        }
        while (
            entries.size + diagnosticEntries.size >= maxEntries ||
            totalCharacters + entry.characterCount > maxTotalCharacters
        ) {
            if (entries.isNotEmpty()) {
                totalCharacters -= entries.removeFirst().characterCount
                droppedEntryCount++
            } else if (diagnostic && diagnosticEntries.isNotEmpty()) {
                dropOldestDiagnostic()
            } else {
                droppedEntryCount++
                return false
            }
        }
        if (diagnostic) {
            diagnosticEntries.addLast(entry)
            diagnosticCharacters += entry.characterCount
        } else entries.addLast(entry)
        totalCharacters += entry.characterCount
        return true
    }

    private fun dropOldestDiagnostic() {
        val size = diagnosticEntries.removeFirst().characterCount
        diagnosticCharacters -= size
        totalCharacters -= size
        droppedEntryCount++
        droppedDiagnosticCount++
    }

    @Synchronized
    fun snapshot(): DetailedLogSnapshot? {
        if (!active) return null
        return DetailedLogSnapshot(
            sessionStartMillis = sessionStartMillis,
            processName = processName,
            runtimeEnvironment = runtimeEnvironment,
            // Sequence preserves capture order even if wall-clock time moves backwards.
            entries = (entries + diagnosticEntries).sortedBy { it.sequence },
            droppedEntryCount = droppedEntryCount,
            droppedDiagnosticCount = droppedDiagnosticCount,
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
            sequence = ++sequence,
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

object DetailedLogSession {
    private const val MAX_ENTRIES = 10_000
    private const val MAX_TOTAL_CHARACTERS = 2_000_000
    private const val MAX_ENTRY_CHARACTERS = 16_384

    private val store = DetailedLogSessionStore(
        maxEntries = MAX_ENTRIES,
        maxTotalCharacters = MAX_TOTAL_CHARACTERS,
        maxEntryCharacters = MAX_ENTRY_CHARACTERS,
        maxDiagnosticEntries = 1_000,
        maxDiagnosticCharacters = 250_000,
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

    /** Bounded evidence retained independently of ordinary log traffic, within the same total cap. */
    fun recordDiagnostic(tag: String, message: String): Boolean =
        store.append(DetailedLogSource.DIAGNOSTIC, "INFO", tag, message)

    fun snapshot(): DetailedLogSnapshot? = store.snapshot()
}

object DetailedLogFileFormatter {
    private val exportName = Regex("FA4TB_[0-9]{8}_[0-9]{6}(?: \\([0-9]+\\))?\\.log")

    fun isExportFileName(name: String): Boolean = exportName.matches(name)

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
            appendLine("diagnostic_entry_count=${snapshot.entries.count { it.source == DetailedLogSource.DIAGNOSTIC }}")
            appendLine("diagnostic_dropped_count=${snapshot.droppedDiagnosticCount}")
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
