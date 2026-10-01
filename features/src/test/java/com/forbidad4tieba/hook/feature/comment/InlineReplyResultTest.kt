package com.forbidad4tieba.hook.feature.comment

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InlineReplyResultTest {
    private fun response() = JSONObject("""{"error":{"errorno":0},"data":{"post":{"id":"7"},"thread":{"id":"9"},"page":{"current_page":1,"total_page":1,"has_more":0}}}""")
    @Test fun successfulEmptyAndNonemptyAreAcceptedButErrorsAreNotEmptyEvidence() {
        assertTrue(InlineReplyResult.accepts(response(), "7", "9", 0))
        assertTrue(InlineReplyResult.accepts(response(), "7", "9", 2))
        assertFalse(InlineReplyResult.accepts(JSONObject(), "7", "9", 0))
        val error = response().apply { getJSONObject("error").put("errorno", 1) }
        assertFalse(InlineReplyResult.accepts(error, "7", "9", 0))
        val missing = response().apply { remove("error") }
        assertFalse(InlineReplyResult.accepts(missing, "7", "9", 0))
    }
    @Test fun anotherPostThreadOrPageCannotHideTheCurrentEntry() {
        assertFalse(InlineReplyResult.accepts(response(), "8", "9", 0))
        assertFalse(InlineReplyResult.accepts(response(), "7", "10", 0))
        val later = response().apply { getJSONObject("data").getJSONObject("page").put("current_page", 2) }
        assertFalse(InlineReplyResult.accepts(later, "7", "9", 0))
    }
    @Test fun emptyPageWithMorePagesIsInconclusive() {
        val more = response().apply { getJSONObject("data").getJSONObject("page").put("has_more", 1).put("total_page", 2) }
        assertFalse(InlineReplyResult.accepts(more, "7", "9", 0))
        assertTrue(InlineReplyResult.accepts(more, "7", "9", 2))
        val unknown = response().apply { getJSONObject("data").getJSONObject("page").remove("has_more") }
        assertFalse(InlineReplyResult.accepts(unknown, "7", "9", 0))
    }
    @Test fun timeoutAndCancellationRejectLateSuccessfulResponses() {
        for (cancel in listOf(false, true)) {
            val attempt = InlineReplyAttempt("a", 1, 2, 0)
            if (cancel) attempt.cancel() else attempt.finish(null)
            attempt.finish(arrayListOf("late"))
            assertEquals(InlineReplyAttempt.State.FAILED, attempt.state)
            assertNull(attempt.replies)
        }
    }
    @Test fun identityAndTerminalResultAreStableAcrossDuplicates() {
        val attempt = InlineReplyAttempt("a", 1, 2, 0)
        assertTrue(attempt.matches("a", 1, 2))
        assertFalse(attempt.matches("b", 1, 2))
        assertFalse(attempt.matches("a", 2, 2))
        assertFalse(attempt.matches("a", 1, 0))
        attempt.finish(arrayListOf())
        attempt.finish(null)
        assertEquals(InlineReplyAttempt.State.READY, attempt.state)
        assertTrue(attempt.replies!!.isEmpty())
    }
}
