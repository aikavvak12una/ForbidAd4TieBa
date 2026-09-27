package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionSearchPageSessionTest {
    @Test fun requestKeepsTheRawUserIdAndNormalizedSourceAccount() {
        assertNull(CollectionSearchPageSession.accountKey(null))
        assertEquals("__default__", CollectionSearchPageSession.accountKey(""))
        assertEquals("__default__", CollectionSearchPageSession.accountKey("  \t"))
        assertEquals("account-a", CollectionSearchPageSession.accountKey("  account-a  "))
        val session = CollectionSearchPageSession("account-a")
        assertNull(session.beginFullLoad("account-b"))
        assertFalse(session.fetchingAll)
        val request = checkNotNull(session.beginFullLoad("  account-a  "))
        assertEquals("  account-a  ", request.userId)
        assertEquals("account-a", request.sourceAccount)
    }

    @Test fun duplicateFullCompletionCannotFinishTheNextRequest() {
        val session = CollectionSearchPageSession("a")
        val first = checkNotNull(session.beginFullLoad("a"))
        assertNull(session.beginFullLoad("a"))
        assertTrue(session.finishFullLoad(first, "a"))
        assertFalse(session.owns(first))
        assertFalse(session.finishFullLoad(first, "a"))

        val second = checkNotNull(session.beginFullLoad("a"))
        assertFalse(session.finishFullLoad(first, "a"))
        assertTrue(session.fetchingAll)
        assertTrue(session.owns(second))
        assertTrue(session.finishFullLoad(second, "a"))
    }

    @Test fun forcedFirstPageRequestsRemainValidInExecutorOrder() {
        val session = CollectionSearchPageSession("a")
        val first = checkNotNull(session.beginFirstPage("a", force = false))
        assertNull(session.beginFirstPage("a", force = false))
        val forced = checkNotNull(session.beginFirstPage("a", force = true))
        assertTrue(session.owns(first))
        assertTrue(session.owns(forced))
        assertTrue(session.finishFirstPage(first, "a"))
        // The existing flag is released at each completion, even with a forced
        // request queued; request membership still protects each later result.
        assertFalse(session.syncingFirstPage)
        assertFalse(session.finishFirstPage(first, "a"))
        assertTrue(session.owns(forced))
        assertTrue(session.finishFirstPage(forced, "a"))
        assertFalse(session.owns(forced))
    }

    @Test fun onlyForcedFirstPageSyncCanJoinAnActiveFullLoad() {
        val session = CollectionSearchPageSession("a")
        val full = checkNotNull(session.beginFullLoad("a"))
        assertNull(session.beginFirstPage("a", force = false))
        assertNull(session.beginFirstPage("b", force = true))
        val forced = checkNotNull(session.beginFirstPage("a", force = true))
        assertTrue(session.finishFirstPage(forced, "a"))
        assertTrue(session.fetchingAll)
        assertTrue(session.owns(full))
    }

    @Test fun startingFullLoadInvalidatesDiskResultAndItsFollowupIntent() {
        val session = CollectionSearchPageSession("a")
        val disk = checkNotNull(session.beginDiskRestore(fetchOnMiss = true, userVisible = true))
        val full = checkNotNull(session.beginFullLoad("a"))
        assertFalse(session.diskRestoreInFlight)
        assertTrue(session.diskRestoreTried)
        assertFalse(session.joinDiskRestore(fetchOnMiss = true, userVisible = true))
        assertNull(session.finishDiskRestore(disk, "a"))
        assertTrue(session.fetchingAll)
        assertTrue(session.owns(full))
    }

    @Test fun firstPageMergeCanInvalidateDiskWithoutCancellingAnotherFirstPage() {
        val session = CollectionSearchPageSession("a")
        val disk = checkNotNull(session.beginDiskRestore(fetchOnMiss = true, userVisible = true))
        val first = checkNotNull(session.beginFirstPage("a", force = false))
        val next = checkNotNull(session.beginFirstPage("a", force = true))
        assertTrue(session.owns(disk))
        assertTrue(session.finishFirstPage(first, "a"))
        session.invalidateDiskRestore()
        assertNull(session.finishDiskRestore(disk, "a"))
        assertTrue(session.owns(next))
        assertTrue(session.finishFirstPage(next, "a"))
    }

    @Test fun diskRestoreMergesFetchAndNotificationIntentAndConsumesItOnce() {
        val session = CollectionSearchPageSession("a")
        val disk = checkNotNull(session.beginDiskRestore(fetchOnMiss = false, userVisible = false))
        assertNull(session.beginDiskRestore(fetchOnMiss = true, userVisible = true))
        assertTrue(session.joinDiskRestore(fetchOnMiss = true, userVisible = false))
        assertTrue(session.joinDiskRestore(fetchOnMiss = true, userVisible = true))
        assertTrue(session.joinDiskRestore(fetchOnMiss = false, userVisible = false))
        assertEquals(
            CollectionSearchPageSession.DiskRestoreIntent(fetchOnMiss = true, userVisible = true),
            session.finishDiskRestore(disk, "a"),
        )
        assertFalse(session.owns(disk))
        assertNull(session.finishDiskRestore(disk, "a"))
        assertFalse(session.joinDiskRestore(fetchOnMiss = true, userVisible = true))
        assertNull(session.beginDiskRestore(fetchOnMiss = true, userVisible = true))
    }

    @Test fun joiningWithoutFetchDoesNotTurnSilentRestoreIntoAVisibleMiss() {
        val session = CollectionSearchPageSession("a")
        val disk = checkNotNull(session.beginDiskRestore(fetchOnMiss = false, userVisible = false))
        session.joinDiskRestore(fetchOnMiss = false, userVisible = true)
        assertEquals(
            CollectionSearchPageSession.DiskRestoreIntent(fetchOnMiss = false, userVisible = false),
            session.finishDiskRestore(disk, "a"),
        )
    }

    @Test fun mismatchedAccountCompletionReleasesOnlyItsOwnRequestState() {
        val session = CollectionSearchPageSession("a")
        session.fullLoadRequested = true
        val full = checkNotNull(session.beginFullLoad("a"))
        val first = checkNotNull(session.beginFirstPage("a", force = true))
        assertFalse(session.finishFullLoad(full, "b"))
        assertFalse(session.fetchingAll)
        assertFalse(session.fullLoadRequested)
        assertTrue(session.owns(first))
        assertFalse(session.finishFirstPage(first, "b"))
        assertFalse(session.syncingFirstPage)

        val disk = checkNotNull(session.beginDiskRestore(fetchOnMiss = true, userVisible = true))
        assertNull(session.finishDiskRestore(disk, "b"))
        assertFalse(session.diskRestoreInFlight)
        assertFalse(session.diskRestoreTried)
        val retry = checkNotNull(session.beginDiskRestore(fetchOnMiss = false, userVisible = false))
        assertNull(session.finishDiskRestore(disk, "a"))
        assertTrue(session.owns(retry))
        assertEquals(CollectionSearchPageSession.DiskRestoreIntent(false, false), session.finishDiskRestore(retry, "a"))
    }

    @Test fun closingStopsAllUiWorkButKeepsCompletedFullCacheOriginWritable() {
        val session = CollectionSearchPageSession("a")
        val full = checkNotNull(session.beginFullLoad("a"))
        val first = checkNotNull(session.beginFirstPage("a", force = true))
        session.close()
        session.close()
        assertFalse(session.owns(full))
        assertFalse(session.owns(first))
        assertFalse(session.finishFullLoad(full, "a"))
        assertFalse(session.finishFirstPage(first, "a"))
        assertFalse(session.fetchingAll)
        assertFalse(session.syncingFirstPage)
        assertNull(session.beginFullLoad("a"))
        assertNull(session.beginFirstPage("a", force = true))
        assertNull(session.beginDiskRestore(fetchOnMiss = true, userVisible = true))
        assertEquals("a", full.completeCacheAccount(complete = true, hasRawPages = true))
    }

    @Test fun partialEmptyOrNonFullRequestsCannotPersistACompleteSnapshot() {
        val session = CollectionSearchPageSession("a")
        val disk = checkNotNull(session.beginDiskRestore(fetchOnMiss = false, userVisible = false))
        val first = checkNotNull(session.beginFirstPage("a", force = true))
        val full = checkNotNull(session.beginFullLoad("a"))
        assertNull(full.completeCacheAccount(complete = false, hasRawPages = true))
        assertNull(full.completeCacheAccount(complete = true, hasRawPages = false))
        assertNull(first.completeCacheAccount(complete = true, hasRawPages = true))
        assertNull(disk.completeCacheAccount(complete = true, hasRawPages = true))
        val unknown = checkNotNull(CollectionSearchPageSession(null).beginFullLoad(null))
        assertNull(unknown.completeCacheAccount(complete = true, hasRawPages = true))
    }
}
