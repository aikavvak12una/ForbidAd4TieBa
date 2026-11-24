package com.forbidad4tieba.hook.feature.ad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsPersistenceTest {
    @Test
    fun persistenceDisablesOnlyAfterConsecutiveFailuresAndResetsExplicitly() {
        val state = StatsPersistenceState()
        repeat(2) { state.failed() }
        assertFalse(state.disabled)
        state.succeeded()
        assertEquals(0, state.failureCount)

        repeat(3) { state.failed() }
        assertTrue(state.disabled)
        state.succeeded()
        assertTrue(state.disabled)
        state.reset()
        assertFalse(state.disabled)
        assertEquals(0, state.failureCount)
    }
}
