package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionSearchSessionsTest {
    @Test fun repeatedEntryForSameAccountPreservesQueryAndPendingRequest() {
        val sessions = CollectionSearchSessions()
        val page = Any()
        val session = sessions.ensure(page, "a")
        session.query = "query"
        val request = checkNotNull(session.beginFullLoad("a"))
        assertSame(session, sessions.ensure(page, "a"))
        assertEquals("query", sessions[page]?.query)
        assertTrue(sessions.accepts(page, request, "a"))
    }

    @Test fun accountReplacementPreservesQueryIntentButDropsOldDataAndRequests() {
        val sessions = CollectionSearchSessions()
        val page = Any()
        val old = sessions.ensure(page, "a").apply {
            query = "saved query"
            active = true
            indexMap = intArrayOf(2, 6)
            applying = true
            fullDataReady = true
            fullLoadRequested = true
            syncFooterVisible = true
        }
        val request = checkNotNull(old.beginFullLoad("a"))
        val replacement = sessions.ensure(page, "b")
        assertNotSame(old, replacement)
        assertEquals("saved query", replacement.query)
        assertTrue(replacement.active)
        assertTrue(replacement.shouldRestoreFullDataOnResume())
        assertArrayEquals(IntArray(0), replacement.indexMap)
        assertFalse(replacement.applying)
        assertFalse(replacement.fullDataReady)
        assertFalse(replacement.fullLoadRequested)
        assertFalse(replacement.syncFooterVisible)
        assertFalse(replacement.fetchingAll)
        assertFalse(replacement.diskRestoreTried)
        assertNull(sessions.current(page, request))
        assertFalse(sessions.isCurrent(page, old, "a"))
        assertFalse(sessions.accepts(page, request, "b"))
        assertNull(old.beginFullLoad("a"))
        assertEquals("a", request.completeCacheAccount(complete = true, hasRawPages = true))
    }

    @Test fun accountChangeRejectsWorkEvenBeforeReplacementIsCreated() {
        val sessions = CollectionSearchSessions()
        val page = Any()
        val session = sessions.ensure(page, "a")
        val request = checkNotNull(session.beginFirstPage("a", force = false))
        assertFalse(sessions.accepts(page, request, "b"))
        assertFalse(sessions.isCurrent(page, session, "b"))
        assertFalse(sessions.isCurrent(page, session, null))
        assertTrue(sessions.isCurrent(page, session, "a"))
    }

    @Test fun closedPageCannotDeliverToAReopenedPageWithTheSameAccount() {
        val sessions = CollectionSearchSessions()
        val page = Any()
        val old = sessions.ensure(page, "a")
        val disk = checkNotNull(old.beginDiskRestore(fetchOnMiss = true, userVisible = true))
        sessions.clear(page)
        assertNull(sessions[page])
        assertFalse(sessions.isCurrent(page, old, "a"))
        val replacement = sessions.ensure(page, "a")
        val current = checkNotNull(replacement.beginDiskRestore(fetchOnMiss = false, userVisible = false))
        assertFalse(sessions.accepts(page, disk, "a"))
        assertNull(old.finishDiskRestore(disk, "a"))
        assertTrue(sessions.accepts(page, current, "a"))
        assertTrue(replacement.diskRestoreInFlight)
        assertEquals("", replacement.query)
    }

    @Test fun sameTokenAndAccountDoNotMakeRequestsInterchangeableBetweenPages() {
        val sessions = CollectionSearchSessions()
        val pageA = Any()
        val pageB = Any()
        val sessionA = sessions.ensure(pageA, "a")
        val sessionB = sessions.ensure(pageB, "a")
        val requestA = checkNotNull(sessionA.beginFullLoad("a"))
        val requestB = checkNotNull(sessionB.beginFullLoad("a"))
        assertEquals(requestA.token, requestB.token)
        assertFalse(sessions.accepts(pageB, requestA, "a"))
        assertFalse(sessionB.finishFullLoad(requestA, "a"))
        assertTrue(sessions.accepts(pageB, requestB, "a"))
        assertTrue(sessionB.fetchingAll)
        assertTrue(sessionA.finishFullLoad(requestA, "a"))
        assertFalse(sessions.accepts(pageA, requestA, "a"))
    }

    @Test fun clearingPageRemovesItsAssociationsAndPreservesReboundAndOtherOwners() {
        val sessions = CollectionSearchSessions()
        val pageA = Any()
        val pageB = Any()
        val presenterA = Any()
        val adapterA = Any()
        val presenterB = Any()
        val reboundAdapter = Any()
        sessions.ensure(pageA, "a")
        val other = sessions.ensure(pageB, "a")
        sessions.bindPresenter(presenterA, pageA)
        sessions.bindAdapter(adapterA, pageA)
        sessions.bindPresenter(presenterB, pageB)
        sessions.bindAdapter(reboundAdapter, pageA)
        sessions.bindAdapter(reboundAdapter, pageB)
        assertSame(pageA, sessions.presenterOwner(presenterA))
        assertSame(pageA, sessions.adapterOwner(adapterA))
        sessions.clear(pageA)
        assertNull(sessions.presenterOwner(presenterA))
        assertNull(sessions.adapterOwner(adapterA))
        assertSame(pageB, sessions.presenterOwner(presenterB))
        assertSame(pageB, sessions.adapterOwner(reboundAdapter))
        assertSame(other, sessions[pageB])
    }
}
