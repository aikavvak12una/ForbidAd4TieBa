package com.forbidad4tieba.hook.feature.comment

import org.junit.Assert.*
import org.junit.Test
import com.forbidad4tieba.hook.config.CommentLevelFilterSettings

class CommentFilterVisitsTest {
    @Test fun temporaryEnableReusesRulesWithoutChangingGlobalSettingsAndDisablingShortcutClearsIt() {
        val global = CommentLevelFilterSettings(false, 8, true, true)
        val owner = Any()
        try {
            CommentFilterOverrides.responseThreadId = { it as? String }
            CommentFilterOverrides.visits.toggle(owner, "101", global.enabled)
            val effective = CommentFilterOverrides.settings("101", global, true)
            assertEquals(global.copy(enabled = true), effective)
            assertTrue(effective.hides(7, false, false))
            assertFalse(effective.hides(7, true, false))
            assertFalse(effective.hides(7, false, true))
            assertFalse(global.enabled)
            assertSame(global, CommentFilterOverrides.settings("202", global, true))
            assertSame(global, CommentFilterOverrides.settings("101", global, false))
            assertSame(global, CommentFilterOverrides.settings("101", global, true))
        } finally {
            CommentFilterOverrides.visits.clear()
            CommentFilterOverrides.responseThreadId = null
        }
    }
    @Test fun explicitToggleOverridesBothGlobalDirectionsOnlyForThisVisit() {
        for (global in listOf(false, true)) {
            val visits = CommentFilterVisits()
            val owner = Any()
            assertEquals(global, visits.enabled(owner, "101", global))
            assertEquals(!global, visits.toggle(owner, "101", global))
            repeat(3) { assertEquals(!global, visits.forResponse("101", global)) }
            assertEquals(global, visits.forResponse("202", global))
            assertEquals(global, visits.forResponse(null, global))
            assertEquals(global, visits.toggle(owner, "101", global))
            visits.toggle(owner, "101", global)
            visits.remove(owner)
            assertEquals(global, visits.forResponse("101", global))
            assertEquals(global, visits.enabled(Any(), "101", global))
        }
    }
    @Test fun reusedPageAndAmbiguousOwnersCannotInheritAnotherVisit() {
        val visits = CommentFilterVisits()
        val first = Any()
        val second = Any()
        visits.toggle(first, "101", false)
        visits.observe(second, "101")
        assertFalse(visits.forResponse("101", false))
        visits.remove(second)
        assertTrue(visits.forResponse("101", false))
        visits.observe(first, "202")
        assertFalse(visits.forResponse("101", false))
        assertFalse(visits.forResponse("202", false))
        visits.toggle(first, "202", false)
        visits.clear()
        assertFalse(visits.forResponse("202", false))
    }
}
