package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNativeGlassRefreshSessionTest {
    @Test fun chromeConstructorRefreshPrecedesThePostAndCoalescesReentrantWrites() {
        val ui = Fixture(HomeNativeGlassViewRole.TOP_CHROME)
        ui.attached = false
        ui.onApply = { ui.session.request() }
        ui.session.request()
        assertEquals(listOf("page:apply"), ui.applied)
        assertEquals(1, ui.queue.size)
        ui.attached = true
        ui.session.onAttached()
        assertEquals(1, ui.queue.size)
        ui.runNext()
        assertEquals(2, ui.applied.size)
        assertTrue(ui.queue.isEmpty())
    }

    @Test fun ordinaryRefreshReleasesItsSlotBeforeTheHostWritesAgain() {
        val ui = Fixture(HomeNativeGlassViewRole.FEED_CARD)
        ui.onApply = {
            ui.onApply = {}
            ui.session.request()
        }
        ui.session.request()
        ui.session.request()
        assertEquals(1, ui.queue.size)
        ui.runNext()
        assertEquals(1, ui.queue.size)
        ui.runNext()
        assertEquals(2, ui.applied.size)
    }

    @Test fun searchBootstrapIsOnceButEveryAttachRetainsItsIndependentRefresh() {
        val ui = Fixture(HomeNativeGlassViewRole.SEARCH_BOX)
        ui.attached = false
        ui.session.onListenerInstalled(alreadyAttached = false)
        ui.session.request()
        assertEquals(listOf(0L, 160L), ui.delays())
        ui.attached = true
        ui.session.onAttached()
        ui.session.request()
        assertEquals(listOf(0L, 160L, 0L), ui.delays())
        ui.drain()
        ui.session.onAttached()
        assertEquals(listOf(0L), ui.delays())
    }

    @Test fun alreadyAttachedSearchKeepsItsInitialPostBeforeBootstrap() {
        val ui = Fixture(HomeNativeGlassViewRole.SEARCH_BOX)
        ui.session.onListenerInstalled(alreadyAttached = true)
        ui.session.request()
        assertEquals(listOf(0L, 0L, 160L), ui.delays())
    }

    @Test fun cardComponentBootstrapCannotBeRestartedByRepeatedPredraws() {
        val ui = Fixture(HomeNativeGlassViewRole.CARD_COMPONENT)
        repeat(10) { ui.session.request() }
        assertEquals(listOf(0L, 160L), ui.delays())
        ui.drain()
        ui.session.request()
        assertTrue(ui.queue.isEmpty())
    }

    @Test fun postSurfaceRefreshOnAnAlreadyAttachedViewCoalescesWithTheConstructorRequest() {
        val ui = Fixture(HomeNativeGlassViewRole.PB_SURFACE)
        ui.session.onListenerInstalled(alreadyAttached = true)
        ui.session.request()
        assertEquals(1, ui.queue.size)
        ui.runNext()
        ui.session.onAttached()
        assertEquals(1, ui.queue.size)
    }

    @Test fun detachedChromeSkipsItsLatePostAndCanRefreshOnReattach() {
        val ui = Fixture(HomeNativeGlassViewRole.BOTTOM_TAB)
        ui.session.request()
        ui.attached = false
        ui.runNext()
        assertEquals(1, ui.applied.size)
        ui.attached = true
        ui.session.onAttached()
        ui.runNext()
        assertEquals(3, ui.applied.size)
    }

    @Test fun disabledFeatureDoesNotQueueOrApplyNewContentWork() {
        val ui = Fixture(HomeNativeGlassViewRole.PB_ITEM_FRAME)
        ui.session.request()
        ui.enabled = false
        ui.session.request()
        ui.session.onAttached()
        assertEquals(1, ui.queue.size)
        ui.runNext()
        assertTrue(ui.applied.isEmpty())
        ui.enabled = true
        ui.session.request()
        ui.runNext()
        assertEquals(1, ui.applied.size)
    }

    @Test fun disabledChromeStillReceivesTheExistingRestoreCallback() {
        val ui = Fixture(HomeNativeGlassViewRole.TOP_CHROME)
        ui.enabled = false
        ui.session.request()
        ui.runNext()
        assertEquals(listOf("page:restore", "page:restore"), ui.applied)
    }

    @Test fun lateCancelledWorkCannotTouchAReplacementSession() {
        val old = Fixture(HomeNativeGlassViewRole.SEARCH_BOX)
        old.session.request()
        val late = old.queue.first().second
        old.session.close()
        assertTrue(old.queue.isEmpty())
        val replacement = Fixture(HomeNativeGlassViewRole.SEARCH_BOX)
        replacement.session.request()
        late.run()
        old.session.onAttached()
        assertTrue(old.applied.isEmpty())
        assertTrue(old.queue.isEmpty())
        replacement.drain()
        assertEquals(2, replacement.applied.size)
    }

    @Test fun queuedRefreshUsesTheViewsCurrentPageAndTheme() {
        val ui = Fixture(HomeNativeGlassViewRole.PAGE)
        ui.session.request()
        ui.page = "replacement-dark-page"
        ui.runNext()
        assertEquals(listOf("replacement-dark-page:apply"), ui.applied)
    }

    private class Fixture(role: HomeNativeGlassViewRole) {
        var enabled = true
        var attached = true
        var page = "page"
        var onApply: () -> Unit = {}
        val queue = ArrayList<Pair<Long, Runnable>>()
        val applied = ArrayList<String>()
        val session = HomeNativeGlassRefreshSession(
            role, { enabled }, { attached },
            post = { task, delay -> queue.add(delay to task); Unit },
            cancel = { task -> queue.removeAll { it.second === task }; Unit },
            apply = {
                applied.add("$page:${if (enabled) "apply" else "restore"}")
                onApply()
            },
        )

        fun delays(): List<Long> = queue.map { it.first }
        fun runNext() = queue.removeAt(0).second.run()
        fun drain() { while (queue.isNotEmpty()) runNext() }
    }
}
