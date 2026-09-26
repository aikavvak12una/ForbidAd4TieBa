package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoredCandidateSelectionTest {
    private data class Candidate(val name: String, val score: Int)

    @Test fun rejectsTiesRegardlessOfNamesOrInputOrder() {
        val candidates = listOf(Candidate("a", 300), Candidate("longerName", 300))
        for (order in listOf(candidates, candidates.reversed())) {
            assertNull(uniqueBestCandidate(order, 24) { it.score })
        }
    }

    @Test fun requiresSemanticMarginIncludingBoundary() {
        val best = Candidate("target", 300)
        assertNull(uniqueBestCandidate(listOf(best, Candidate("other", 277)), 24) { it.score })
        assertEquals(best, uniqueBestCandidate(listOf(Candidate("other", 276), best), 24) { it.score })
    }

    @Test fun handlesMissingAndSingleValidatedCandidate() {
        assertNull(uniqueBestCandidate(emptyList<Candidate>(), 24) { it.score })
        val only = Candidate("renamed", 200)
        assertEquals(only, uniqueBestCandidate(listOf(only), 24) { it.score })
    }
}
