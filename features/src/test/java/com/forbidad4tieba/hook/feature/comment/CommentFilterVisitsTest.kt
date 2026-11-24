package com.forbidad4tieba.hook.feature.comment

import com.forbidad4tieba.hook.config.CommentLevelFilterSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentFilterVisitsTest {
    @Test
    fun temporaryOverrideHasOneResponseOwnerAndLeavesGlobalSettingsAlone() {
        val global = CommentLevelFilterSettings(false, 8, true, true)
        val visits = CommentFilterVisits()
        val owner = Any()

        assertEquals(false, visits.enabled(owner, "101", global.enabled))
        visits.toggle(owner, "101", global.enabled)
        assertEquals(true, visits.forResponse("101", global.enabled))
        assertEquals(false, visits.forResponse("202", global.enabled))
        assertEquals(false, visits.forResponse(null, global.enabled))
        assertFalse(global.enabled)

        val other = Any()
        visits.observe(other, "101")
        visits.remove(owner)
        assertEquals(false, visits.forResponse("101", global.enabled))
        assertSame(global, CommentFilterOverrides.settings("101", global, false))

        CommentFilterOverrides.visits.clear()
        try {
            CommentFilterOverrides.responseThreadId = { it as? String }
            CommentFilterOverrides.visits.toggle(owner, "101", global.enabled)
            val effective = CommentFilterOverrides.settings("101", global, true)
            assertTrue(effective.enabled)
            assertTrue(effective.hides(7, false, false))
            assertFalse(effective.hides(7, true, false))
            assertFalse(effective.hides(7, false, true))
            assertSame(global, CommentFilterOverrides.settings("202", global, true))
        } finally {
            CommentFilterOverrides.visits.clear()
            CommentFilterOverrides.responseThreadId = null
        }
    }
}
