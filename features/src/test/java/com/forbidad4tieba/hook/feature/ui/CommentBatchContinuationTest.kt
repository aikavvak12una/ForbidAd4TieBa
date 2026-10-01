package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentBatchContinuationTest {
    @Test fun completionBeforeCommitWaitsForAcceptedData() {
        val request = Any()
        val batch = CommentBatchContinuation(request)
        batch.finish(request)
        assertFalse(batch.queue())
        batch.commit(request, true)
        assertTrue(batch.queue())
        assertTrue(batch.take())
    }

    @Test fun commitBeforeCompletionCannotOverlapRequests() {
        val request = Any()
        val batch = CommentBatchContinuation(request)
        batch.commit(request, true)
        assertFalse(batch.queue())
        batch.finish(request)
        assertTrue(batch.queue())
    }

    @Test fun lateResultsFromAnotherRequestCannotStartAnotherPage() {
        val request = Any()
        val batch = CommentBatchContinuation(request)
        batch.finish(Any())
        batch.commit(Any(), true)
        assertFalse(batch.queue())
        batch.commit(request, true)
        assertFalse(batch.queue())
    }

    @Test fun matchingRequiresIdentityEvenWhenTokensCompareEqual() {
        val request = listOf(1)
        val batch = CommentBatchContinuation(request)
        batch.finish(listOf(1))
        batch.commit(request, true)
        assertFalse(batch.queue())
    }

    @Test fun failedOrEmptyPageDoesNotLoop() {
        val request = Any()
        val failed = CommentBatchContinuation(request)
        failed.finish(request)
        assertFalse(failed.queue())
        val empty = CommentBatchContinuation(request)
        empty.finish(request)
        empty.commit(request, false)
        empty.commit(request, true)
        assertFalse(empty.queue())
    }

    @Test fun leavingPageChangingSortOrDisablingCancelsQueuedWork() {
        val request = Any()
        val batch = CommentBatchContinuation(request)
        batch.commit(request, true)
        batch.finish(request)
        assertTrue(batch.queue())
        batch.cancel()
        assertFalse(batch.take())
        batch.finish(request)
        batch.commit(request, true)
        assertFalse(batch.queue())
    }

    @Test fun duplicateCompletionAndCommitAllowOnlyOneExtraPage() {
        val request = Any()
        val batch = CommentBatchContinuation(request)
        repeat(3) { batch.finish(request); batch.commit(request, true) }
        assertTrue(batch.queue())
        assertFalse(batch.queue())
        assertTrue(batch.take())
        assertFalse(batch.take())
        batch.finish(request)
        batch.commit(request, true)
        assertFalse(batch.queue())
    }
}
