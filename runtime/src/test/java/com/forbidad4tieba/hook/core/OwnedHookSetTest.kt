package com.forbidad4tieba.hook.core

import org.junit.Assert.*
import org.junit.Test

class OwnedHookSetTest {
    @Test fun failedInstallLeavesNoReservationAndSuccessfulRetryIsDeduplicated() {
        val hooks = OwnedHookSet<String, Int> { }
        assertThrows(IllegalStateException::class.java) { hooks.install("target") { error("install failure") } }
        assertEquals(0, hooks.size())
        assertEquals(7, hooks.install("target") { 7 })
        assertEquals(7, hooks.install("target") { error("duplicate installation") })
        assertEquals(1, hooks.size())
    }

    @Test fun rollbackAfterNthFailureProcessesEveryHandleInReverseOrder() {
        val released = ArrayList<Int>()
        val hooks = OwnedHookSet<Int, Int> { released += it }
        for (i in 1..3) hooks.install(i) { i }
        assertThrows(IllegalStateException::class.java) { hooks.install(4) { error("fourth failed") } }
        assertTrue(hooks.rollback().isEmpty())
        assertEquals(listOf(3, 2, 1), released)
        assertEquals(0, hooks.size())
        assertEquals(1, hooks.install(1) { 1 })
    }

    @Test fun unhookFailureRetainsOnlyThatHandleAndDoesNotBlockOtherOwners() {
        val released = ArrayList<Int>()
        var fail = true
        val hooks = OwnedHookSet<Int, Int> {
            released += it
            if (it == 2 && fail) error("unhook failure")
        }
        val independent = OwnedHookSet<Int, Int> { error("other feature must remain installed") }
        independent.install(1) { 8 }
        for (i in 1..3) hooks.install(i) { i }
        assertEquals(listOf(2), hooks.rollback().map { it.first })
        assertEquals(listOf(3, 2, 1), released)
        assertEquals(1, hooks.size())
        assertEquals(2, hooks.install(2) { error("must not duplicate a surviving hook") })
        assertEquals(1, independent.size())
        fail = false
        assertTrue(hooks.rollback().isEmpty())
        assertEquals(0, hooks.size())
    }
}
