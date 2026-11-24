package com.forbidad4tieba.hook.feature.ui

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Persistence, source identity and page lifetime remain independent of host layout. */
class CollectionSearchPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun failedWritesPreserveCompleteDataAndDoNotPublishPartialContent() {
        val file = temporary.newFile().apply { writeText("old") }
        assertThrows(IOException::class.java) {
            CollectionSearchCacheFile.write(file, { it.write("partial".toByteArray()); throw IOException("full") }) {
                _, _ -> fail("Partial write must not publish")
            }
        }
        assertEquals("old", file.readText())
        assertThrows(IOException::class.java) {
            CollectionSearchCacheFile.write(file, { it.write("new".toByteArray()) }) { _, _ -> throw IOException("rename") }
        }
        assertEquals("old", file.readText())
        CollectionSearchCacheFile.write(file, "complete")
        assertEquals("complete", file.readText())
        assertFalse(File(file.parentFile, file.name + ".tmp").exists())
    }

    @Test fun filteringKeepsSourcePositionsAndAnEmptyQueryDoesNotReadRows() {
        val shared = Any()
        val source = listOf(Any(), shared, shared)
        val result = CollectionSearchIndex.filter(source, listOf("needle")) { if (it === shared) "needle" else "other" }
        assertArrayEquals(intArrayOf(1, 2), result.positions)
        result.items.forEachIndexed { i, row -> assertSame(source[result.positions[i]], row) }
        assertEquals(source, CollectionSearchIndex.filter(source, emptyList()) { error("No query") }.items)
    }

    @Test fun snapshotsAreImmutableAndIncompleteEmptyResultsCannotEraseCompleteData() {
        val cache = CollectionSearchMemoryCache()
        val input = arrayListOf<Any>("old")
        cache.put("a", input, true)
        val snapshot = cache.get("a")!!
        input.clear()
        assertEquals(listOf("old"), snapshot.items)
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.items as MutableList<*>).clear() }
        cache.put("a", emptyList(), false)
        assertSame(snapshot, cache.get("a"))
        cache.put("a", emptyList(), true)
        assertTrue(cache.get("a")!!.items.isEmpty())
    }

    @Test fun duplicateCompletionCannotFinishANewerRequestOrAuthorizeAnIncompleteCache() {
        val page = CollectionSearchPageSession("a")
        val first = checkNotNull(page.beginFullLoad("a"))
        assertTrue(page.finishFullLoad(first, "a"))
        val next = checkNotNull(page.beginFullLoad("a"))
        assertFalse(page.finishFullLoad(first, "a"))
        assertTrue(page.owns(next))
        assertNull(next.completeCacheAccount(complete = false, hasRawPages = true))
        assertNull(next.completeCacheAccount(complete = true, hasRawPages = false))
        val partial = checkNotNull(page.beginFirstPage("a", force = true))
        assertNull(partial.completeCacheAccount(complete = true, hasRawPages = true))
    }

    @Test fun diskIntentIsConsumedOnceAndFullLoadInvalidatesTheOldRestore() {
        val page = CollectionSearchPageSession("a")
        val disk = checkNotNull(page.beginDiskRestore(false, false))
        assertTrue(page.joinDiskRestore(true, true))
        assertEquals(CollectionSearchPageSession.DiskRestoreIntent(true, true), page.finishDiskRestore(disk, "a"))
        assertNull(page.finishDiskRestore(disk, "a"))
        val other = CollectionSearchPageSession("a")
        val stale = checkNotNull(other.beginDiskRestore(true, true))
        other.beginFullLoad("a")
        assertNull(other.finishDiskRestore(stale, "a"))
    }

    @Test fun pageOrAccountReplacementCannotBorrowOldRequestIdentity() {
        val sessions = CollectionSearchSessions()
        val a = Any(); val b = Any()
        val owner = sessions.ensure(a, "account")
        val request = checkNotNull(owner.beginFullLoad("account"))
        val other = sessions.ensure(b, "account")
        val equalToken = checkNotNull(other.beginFullLoad("account"))
        assertEquals(request.token, equalToken.token)
        assertFalse(sessions.accepts(b, request, "account"))
        assertFalse(sessions.accepts(a, request, "new-account"))
        sessions.clear(a)
        val reopened = sessions.ensure(a, "account")
        reopened.beginFullLoad("account")
        assertFalse(sessions.accepts(a, request, "account"))
    }

    @Test fun closingRejectsUiDeliveryButPreservesItsLegallyCompleteCacheOrigin() {
        val page = CollectionSearchPageSession("a")
        val full = checkNotNull(page.beginFullLoad("a"))
        page.close()
        assertFalse(page.finishFullLoad(full, "a"))
        assertNull(page.beginFullLoad("a"))
        assertEquals("a", full.completeCacheAccount(complete = true, hasRawPages = true))
    }
}
