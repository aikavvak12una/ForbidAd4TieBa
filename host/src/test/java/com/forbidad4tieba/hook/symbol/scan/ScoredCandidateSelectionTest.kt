package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoredCandidateSelectionTest {
    private data class Candidate(val name: String, val score: Int)

    @Test fun rejectsTiesRegardlessOfNamesOrInputOrder() {
        val candidates = listOf(Candidate("a", 300), Candidate("longerName", 300))
        for (order in listOf(candidates, candidates.reversed())) {
            assertNull(uniqueBestCandidate(order, 200, 24) { it.score })
        }
    }

    @Test fun requiresSemanticMarginIncludingBoundary() {
        val best = Candidate("target", 300)
        assertNull(uniqueBestCandidate(listOf(best, Candidate("other", 277)), 200, 24) { it.score })
        assertEquals(best, uniqueBestCandidate(listOf(Candidate("other", 276), best), 200, 24) { it.score })
    }

    @Test fun handlesMissingAndSingleValidatedCandidate() {
        assertNull(uniqueBestCandidate(emptyList<Candidate>(), 200, 24) { it.score })
        val only = Candidate("renamed", 200)
        assertEquals(only, uniqueBestCandidate(listOf(only), 200, 24) { it.score })
    }

    @Test fun rejectsWeakSingletonAndWeakWinnerEvenWithALargeGap() {
        val weak = Candidate("a", 199)
        assertNull(uniqueBestCandidate(listOf(weak), 200, 24) { it.score })
        assertNull(uniqueBestCandidate(listOf(weak, Candidate("other", 100)), 200, 24) { it.score })
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroGapCannotEnableNameOrOrderBasedTieBreaking() {
        uniqueBestCandidate(listOf(Candidate("a", 200)), 200, 0) { it.score }
    }

    @Test fun scoreDifferenceCannotOverflow() {
        val best = Candidate("renamed", Int.MAX_VALUE)
        assertEquals(best, uniqueBestCandidate(
            listOf(Candidate("a", Int.MIN_VALUE), best), 200, Int.MAX_VALUE,
        ) { it.score })
    }

    @Test fun legacyRuleSelectionAlsoRequiresThresholdAndSeparation() {
        fun select(matches: List<ScanMatch>) = ScanReflection.chooseUniqueScanMatch(
            "test", "test", matches, null, minScore = 140, minScoreGap = 10,
        )
        val renamed = ScanMatch("RenamedOwner", "renamed", "renamedField", 140)
        val oldName = ScanMatch("a", "b", "c", 140)
        assertNull(select(listOf(renamed.copy(score = 139))))
        assertNull(select(listOf(renamed, oldName)))
        assertNull(select(listOf(oldName, renamed)))
        assertNull(select(listOf(renamed, oldName.copy(score = 131))))
        assertEquals(renamed, select(listOf(oldName.copy(score = 130), renamed)))
    }
}
