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
