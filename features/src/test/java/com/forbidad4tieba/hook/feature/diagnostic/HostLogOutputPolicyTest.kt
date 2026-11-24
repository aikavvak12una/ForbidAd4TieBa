package com.forbidad4tieba.hook.feature.diagnostic

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class HostLogOutputPolicyTest {

    @Test fun credentialHeadersAndProtocolFieldsAreRemovedBeforeExport() {
        val fields = listOf(
            "Cookie: sid=synthetic-secret; other=value",
            "Set-Cookie: session=synthetic-secret; Path=/",
            "AUTHORIZATION: Bearer synthetic-secret",
            "Proxy-Authorization: Basic synthetic-secret",
            "{\"BDUSS\":\"synthetic-secret\",\"normal\":1}",
            "headers={cookie=[sid=synthetic-secret]}",
            "https://example.invalid/?tbs=synthetic-secret&normal=1",
            "url=BDUSS%3Dsynthetic-secret%26normal%3D1",
            "{\\\"STOKEN_BFESS\\\":\\\"synthetic-secret\\\"}",
        )
        fields.forEach { assertEquals(it, "<redacted>", HostLogContent.sanitize(it, 15_000)) }
    }

    @Test fun truncationCannotLeaveTheVisiblePrefixOfAMarkedCredential() {
        val message = "x".repeat(110) + " BDUSS=" + "s".repeat(100_000)
        assertEquals("<redacted>", HostLogContent.sanitize(message, 128))
        assertEquals(128, HostLogContent.sanitize("x".repeat(100_000), 128).length)
        assertTrue(HostLogContent.sanitize("x".repeat(100_000), 128).endsWith("...[truncated]"))
    }

    @Test fun entryBudgetSuppressesBurstsAndReportsOnTheNextWindow() {
        val budget = HostLogRateBudget(maxEntries = 2, maxCharacters = 100)
        assertEquals(0L, budget.acquire(10, 100))
        assertEquals(0L, budget.acquire(10, 100))
        repeat(7) { assertEquals(-1L, budget.acquire(10, 1_099)) }
        assertEquals(7L, budget.acquire(10, 1_100))
        assertEquals(0L, budget.acquire(10, 1_100))
    }

    @Test fun characterBudgetDoesNotDependOnMessageCount() {
        val budget = HostLogRateBudget(maxEntries = 100, maxCharacters = 100)
        assertEquals(0L, budget.acquire(80, 0))
        assertEquals(-1L, budget.acquire(21, 0))
        assertEquals(0L, budget.acquire(20, 0))
        assertEquals(-1L, budget.acquire(1, 0))
        assertEquals(2L, budget.acquire(100, 1_000))
    }

    @Test fun anOlderConcurrentTimestampDoesNotRefillTheBudget() {
        val budget = HostLogRateBudget(maxEntries = 1, maxCharacters = 100)
        assertEquals(0L, budget.acquire(1, 5_000))
        assertEquals(-1L, budget.acquire(1, 4_000))
        assertEquals(-1L, budget.acquire(1, 5_999))
        assertEquals(2L, budget.acquire(1, 6_000))
    }

    @Test fun separatePriorityBudgetRemainsAvailableDuringAnOrdinaryFlood() {
        val regular = HostLogRateBudget(maxEntries = 32, maxCharacters = 48 * 1024)
        val priority = HostLogRateBudget(maxEntries = 8, maxCharacters = 16 * 1024)
        repeat(32) { assertEquals(0L, regular.acquire(100, 0)) }
        assertEquals(-1L, regular.acquire(100, 0))
        repeat(8) { assertEquals(0L, priority.acquire(100, 0)) }
        assertEquals(-1L, priority.acquire(100, 0))
    }

    @Test fun concurrentProducersShareOneFiniteBudgetAndAccurateSuppressionCount() {
        val budget = HostLogRateBudget(maxEntries = 32, maxCharacters = 320)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(6)
        try {
            val calls = (1..6).map {
                executor.submit(Callable {
                    start.await()
                    var accepted = 0
                    repeat(1_000) { if (budget.acquire(10, 0) >= 0) accepted++ }
                    accepted
                })
            }
            start.countDown()
            assertEquals(32, calls.sumOf { it.get() })
            assertEquals(5_968L, budget.acquire(10, 1_000))
        } finally {
            executor.shutdownNow()
        }
    }
}
