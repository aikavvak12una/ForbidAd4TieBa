package com.forbidad4tieba.hook.feature.ad

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StatsPersistenceTest {
    @Test fun onlyThreeConsecutiveFailuresDisablePersistenceAndResetExplicitlyReenablesIt() {
        val state = StatsPersistenceState()
        repeat(2) { state.failed() }
        assertFalse(state.disabled)
        state.succeeded()
        assertEquals(0, state.failureCount)
        repeat(2) { state.failed() }
        assertFalse(state.disabled)
        state.failed()
        assertTrue(state.disabled)
        state.succeeded()
        assertTrue(state.disabled)
        state.reset()
        assertFalse(state.disabled)
        assertEquals(0, state.failureCount)
    }

    @Test fun blockCounterPersistenceReadDoesNotWaitForTheWriterLock() {
        val target = BlockCountStats
        val read = target.javaClass.getDeclaredMethod("isPersistenceDisabled").apply { isAccessible = true }
        whileWriterHoldsLock(field(target, "fileLock")) {
            assertEquals(false, read.invoke(target))
            BlockCountStats.recordAd()
        }
    }

    @Test fun scoreRecordingAndGenerationResetContinueWhileTheWriterLockIsBlocked() {
        val target = CustomPostModelScoreStats
        val scheduled = field(target, "flushScheduled") as AtomicBoolean
        // Hold the existing scheduled slot; this test never starts a persistence worker.
        val wasScheduled = scheduled.getAndSet(true)
        val pending = field(target, "pendingRecords") as MutableList<*>
        try {
            target.clear()
            val generation = target.javaClass.getDeclaredField("statsGeneration").apply { isAccessible = true }
            val before = generation.getLong(target)
            whileWriterHoldsLock(field(target, "fileLock")) {
                target.record(mapOf("score" to 0.5))
                assertEquals(1, pending.size)
                target.clear()
                assertTrue(pending.isEmpty())
                assertTrue(generation.getLong(target) > before)
                target.record(mapOf("score" to 0.8))
                assertEquals(1, pending.size)
            }
        } finally {
            pending.clear()
            scheduled.set(wasScheduled)
        }
    }

    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)!!

    private fun whileWriterHoldsLock(lock: Any, action: () -> Unit) {
        val writerEntered = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val writer = executor.submit {
            synchronized(lock) {
                writerEntered.countDown()
                releaseWriter.await()
            }
        }
        try {
            assertTrue(writerEntered.await(2, TimeUnit.SECONDS))
            executor.submit { action() }.get(2, TimeUnit.SECONDS)
        } finally {
            releaseWriter.countDown()
            writer.get(2, TimeUnit.SECONDS)
            executor.shutdownNow()
        }
    }
}
