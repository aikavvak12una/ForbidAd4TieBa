package com.forbidad4tieba.hook.core

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailedLogSessionTest {
    @Test
    fun inactiveSessionIgnoresEntries() {
        val store = store()

        assertFalse(
            store.append(
                source = DetailedLogSource.MODULE,
                level = "INFO",
                tag = "TbHook",
                message = "ignored",
            ),
        )
        assertNull(store.snapshot())
    }

    @Test
    fun startCreatesFreshColdStartSession() {
        var now = 100L
        val store = store(clock = { now })
        store.start("first", """{"runtime":"first"}""")
        now = 101L
        store.append(DetailedLogSource.MODULE, "INFO", "TbHook", "first entry")

        now = 200L
        store.start("second", """{"runtime":"second"}""")
        val snapshot = requireNotNull(store.snapshot())

        assertEquals(200L, snapshot.sessionStartMillis)
        assertEquals("second", snapshot.processName)
        assertEquals("""{"runtime":"second"}""", snapshot.runtimeEnvironment)
        assertTrue(snapshot.entries.isEmpty())
        assertEquals(0L, snapshot.droppedEntryCount)
    }

    @Test
    fun entryCountAndCharacterLimitsDropOldestEntries() {
        var now = 0L
        val countBounded = store(
            maxEntries = 2,
            maxTotalCharacters = 100,
            maxEntryCharacters = 50,
            clock = { ++now },
        )
        countBounded.start("main", "{}")
        countBounded.append(DetailedLogSource.MODULE, "I", "T", "one")
        countBounded.append(DetailedLogSource.MODULE, "I", "T", "two")
        countBounded.append(DetailedLogSource.MODULE, "I", "T", "three")

        val countSnapshot = requireNotNull(countBounded.snapshot())
        assertEquals(listOf("two", "three"), countSnapshot.entries.map { it.message })
        assertEquals(1L, countSnapshot.droppedEntryCount)

        val characterBounded = store(
            maxEntries = 10,
            maxTotalCharacters = 12,
            maxEntryCharacters = 8,
        )
        characterBounded.start("main", "{}")
        characterBounded.append(DetailedLogSource.TIEBA, "I", "T", "123456789")
        characterBounded.append(DetailedLogSource.TIEBA, "I", "T", "abcdefghi")

        val characterSnapshot = requireNotNull(characterBounded.snapshot())
        assertEquals(1, characterSnapshot.entries.size)
        assertTrue(characterSnapshot.entries.single().characterCount <= 8)
        assertEquals(1L, characterSnapshot.droppedEntryCount)
    }

    @Test
    fun snapshotDoesNotChangeWhenNewEntriesArrive() {
        val store = store()
        store.start("main", "{}")
        store.append(DetailedLogSource.MODULE, "INFO", "TbHook", "first")
        val firstSnapshot = requireNotNull(store.snapshot())

        store.append(DetailedLogSource.TIEBA, "WARN", "Host", "second")

        assertEquals(listOf("first"), firstSnapshot.entries.map { it.message })
        assertEquals(2, requireNotNull(store.snapshot()).entries.size)
    }

    @Test
    fun fileNameAndFormatterIncludeSessionMetadataAndBothSources() {
        val zoneId = ZoneId.of("UTC")
        val sessionStart = Instant.parse("2026-07-23T04:00:00Z").toEpochMilli()
        val savedAt = Instant.parse("2026-07-23T04:05:06Z").toEpochMilli()
        val snapshot = DetailedLogSnapshot(
            sessionStartMillis = sessionStart,
            processName = "com.baidu.tieba",
            runtimeEnvironment = """
                {
                  "tiebaVersionName": "22.8.5.0",
                  "runtimeKind": "LSPosed"
                }
            """.trimIndent(),
            entries = listOf(
                DetailedLogEntry(
                    timestampMillis = sessionStart + 1,
                    source = DetailedLogSource.MODULE,
                    level = "DEBUG",
                    tag = "TbHook",
                    message = "module line",
                ),
                DetailedLogEntry(
                    timestampMillis = sessionStart + 2,
                    source = DetailedLogSource.TIEBA,
                    level = "INFO",
                    tag = "HostTag",
                    message = "host line",
                ),
            ),
            droppedEntryCount = 3,
        )

        assertEquals(
            "FA4TB_20260723_040506.log",
            DetailedLogFileFormatter.fileName(savedAt, zoneId),
        )
        val output = DetailedLogFileFormatter.format(snapshot, savedAt, zoneId)
        assertTrue(output.contains("session_start=2026-07-23 04:00:00.000 Z"))
        assertTrue(output.contains("save_time=2026-07-23 04:05:06.000 Z"))
        assertTrue(output.contains("process=com.baidu.tieba"))
        assertTrue(
            output.contains(
                """
                    runtimeEnvironment=
                    {
                      "tiebaVersionName": "22.8.5.0",
                      "runtimeKind": "LSPosed"
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(output.contains("entry_count=2"))
        assertTrue(output.contains("dropped_count=3"))
        assertTrue(output.contains("[MODULE][DEBUG][TbHook] module line"))
        assertTrue(output.contains("[TIEBA][INFO][HostTag] host line"))
    }

    @Test fun cleanupRecognizesGeneratedExportsAndSystemCollisionSuffixesOnly() {
        val name = DetailedLogFileFormatter.fileName(0L, ZoneId.of("UTC"))
        assertTrue(DetailedLogFileFormatter.isExportFileName(name))
        assertTrue(DetailedLogFileFormatter.isExportFileName(name.replace(".log", " (2).log")))
        for (other in listOf("notes.log", "FA4TB_notes.log", "../$name", "$name.bak", name.replace(".log", ".png")))
            assertFalse(DetailedLogFileFormatter.isExportFileName(other))
    }

    @Test
    fun detailedLogSessionRetainsAtMostTenThousandEntries() {
        DetailedLogSession.start("main", "{}")
        repeat(10_001) { index ->
            DetailedLogSession.recordModule("INFO", "TbHook", "entry-$index")
        }

        val snapshot = requireNotNull(DetailedLogSession.snapshot())
        assertEquals(10_000, snapshot.entries.size)
        assertEquals("entry-1", snapshot.entries.first().message)
        assertEquals(1L, snapshot.droppedEntryCount)
    }

    @Test fun ordinaryFloodCannotEvictBoundedDiagnosticEvidence() {
        DetailedLogSession.start("main", "{}")
        repeat(57) { DetailedLogSession.recordDiagnostic("CloudDialog", "{\"index\":$it}") }
        repeat(20_000) { DetailedLogSession.recordTieba("INFO", "Host", "noise-$it") }
        val snapshot = requireNotNull(DetailedLogSession.snapshot())
        assertEquals(10_000, snapshot.entries.size)
        assertEquals(57, snapshot.entries.count { it.source == DetailedLogSource.DIAGNOSTIC })
        assertEquals(0L, snapshot.droppedDiagnosticCount)
        assertEquals(10_057L, snapshot.droppedEntryCount)
        assertTrue(snapshot.entries.sumOf { it.characterCount } <= 2_000_000)
    }

    @Test fun diagnosticTrafficHasItsOwnCountAndCharacterCeilings() {
        val store = DetailedLogSessionStore(10, 100, 50,
            maxDiagnosticEntries = 2, maxDiagnosticCharacters = 25)
        store.start("main", "{}")
        store.append(DetailedLogSource.MODULE, "I", "T", "ordinary")
        repeat(5) { store.append(DetailedLogSource.DIAGNOSTIC, "I", "T", "{\"i\":$it}") }
        val snapshot = requireNotNull(store.snapshot())
        assertEquals(listOf("ordinary", "{\"i\":3}", "{\"i\":4}"), snapshot.entries.map { it.message })
        assertEquals(3L, snapshot.droppedDiagnosticCount)
        // A larger valid record replaces both earlier diagnostic entries to fit the character cap.
        assertTrue(store.append(DetailedLogSource.DIAGNOSTIC, "I", "T", "{\"value\":\"123456789\"}"))
        val next = requireNotNull(store.snapshot())
        assertEquals(1, next.entries.count { it.source == DetailedLogSource.DIAGNOSTIC })
        assertEquals(5L, next.droppedDiagnosticCount)
    }

    @Test fun protectedEvidenceStillCountsAgainstTheGlobalCharacterLimit() {
        val store = DetailedLogSessionStore(10, 40, 30,
            maxDiagnosticEntries = 2, maxDiagnosticCharacters = 15)
        store.start("main", "{}")
        store.append(DetailedLogSource.DIAGNOSTIC, "I", "T", "{\"key\":123}")
        repeat(10) { store.append(DetailedLogSource.TIEBA, "I", "T", "ordinary-$it") }
        val snapshot = requireNotNull(store.snapshot())
        assertEquals(1, snapshot.entries.count { it.source == DetailedLogSource.DIAGNOSTIC })
        assertTrue(snapshot.entries.sumOf { it.characterCount } <= 40)
    }

    @Test fun oversizeDiagnosticIsRejectedWithoutTruncatingItsJsonOrEvictingOthers() {
        val store = DetailedLogSessionStore(10, 100, 30,
            maxDiagnosticEntries = 2, maxDiagnosticCharacters = 40)
        store.start("main", "{}")
        store.append(DetailedLogSource.DIAGNOSTIC, "I", "T", "{}")
        assertFalse(store.append(DetailedLogSource.DIAGNOSTIC, "I", "T", "{\"text\":\"${"x".repeat(100)}\"}"))
        // Default INFO/tag labels must be charged before accepting a payload near the limit.
        assertFalse(store.append(DetailedLogSource.DIAGNOSTIC, "", "", "{\"text\":\"${"x".repeat(18)}\"}"))
        val snapshot = requireNotNull(store.snapshot())
        assertEquals(listOf("{}"), snapshot.entries.map { it.message })
        assertEquals(2L, snapshot.droppedDiagnosticCount)
        assertEquals(2L, snapshot.droppedEntryCount)
    }

    @Test fun mergedCaptureOrderSurvivesClockRollbackAndColdStartClearsBothQueues() {
        var now = 200L
        val store = DetailedLogSessionStore(10, 100, 30, clock = { now-- },
            maxDiagnosticEntries = 2, maxDiagnosticCharacters = 30)
        store.start("main", "{}")
        store.append(DetailedLogSource.TIEBA, "I", "T", "first")
        store.append(DetailedLogSource.DIAGNOSTIC, "I", "T", "{}")
        store.append(DetailedLogSource.MODULE, "I", "T", "third")
        assertEquals(listOf("first", "{}", "third"), requireNotNull(store.snapshot()).entries.map { it.message })
        store.start("next", "{}")
        val next = requireNotNull(store.snapshot())
        assertTrue(next.entries.isEmpty())
        assertEquals(0L, next.droppedDiagnosticCount)
    }

    private fun store(
        maxEntries: Int = 10,
        maxTotalCharacters: Int = 1_000,
        maxEntryCharacters: Int = 100,
        clock: () -> Long = { 1L },
    ): DetailedLogSessionStore {
        return DetailedLogSessionStore(
            maxEntries = maxEntries,
            maxTotalCharacters = maxTotalCharacters,
            maxEntryCharacters = maxEntryCharacters,
            clock = clock,
        )
    }
}
