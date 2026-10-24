package com.forbidad4tieba.hook.feature.ui.liquidglass

import org.junit.Assert.*
import org.junit.Test

class PostReplyAutoHideStateTest {
    @Test fun accumulatesBrowseDirectionAndRequiresTheSmallerReverseThreshold() {
        val state = PostReplyAutoHideState()
        assertNull(state.onScroll(100, 1000, 128))
        assertEquals(true, state.onScroll(28, 1016, 128))
        assertTrue(state.hidden)
        assertNull(state.onScroll(-23, 1900, 24))
        assertEquals(false, state.onScroll(-1, 1916, 24))
    }

    @Test fun oppositeDirectionDiscardsThePreviousAccumulation() {
        val state = PostReplyAutoHideState()
        assertNull(state.onScroll(100, 1000, 128))
        assertNull(state.onScroll(-10, 1016, 24))
        assertNull(state.onScroll(28, 1032, 128))
        assertEquals(true, state.onScroll(100, 1048, 128))
    }

    @Test fun rapidReversalWaitsForTheHomeBarsActionInterval() {
        val state = PostReplyAutoHideState()
        assertEquals(true, state.onScroll(128, 1000, 128))
        assertNull(state.onScroll(-24, 1016, 24))
        assertNull(state.onScroll(-1, 1799, 24))
        assertEquals(false, state.onScroll(-1, 1800, 24))
    }

    @Test fun editorExitOrDisablementStartsVisibleWithoutOldScrollDebt() {
        val state = PostReplyAutoHideState()
        state.onScroll(128, 1000, 128)
        state.hideThresholdPx = 256
        state.showThresholdPx = 48
        state.reset()
        assertFalse(state.hidden)
        assertEquals(0, state.accumulatedDy)
        assertEquals(0, state.hideThresholdPx)
        assertEquals(0, state.showThresholdPx)
        assertNull(state.onScroll(0, 2000, 128))
        assertNull(state.onScroll(127, 2000, 128))
        assertEquals(true, state.onScroll(1, 2016, 128))
    }

    @Test fun nativeControlReplacementCanRemainHiddenUntilReverseScrolling() {
        val state = PostReplyAutoHideState()
        state.reset(initiallyHidden = true)
        assertNull(state.onScroll(300, 1000, 128))
        assertTrue(state.hidden)
        assertEquals(false, state.onScroll(-24, 2000, 24))
    }
}
