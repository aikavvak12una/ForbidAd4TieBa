package com.forbidad4tieba.hook.feature.ad

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal object BlockCountStats {
    private const val KEY_AD_TOTAL = "block_stats_ad_total"
    private const val KEY_CUSTOM_POST_TOTAL = "block_stats_custom_post_total"
    private const val FLUSH_DELAY_MS = 2000L
    private const val PERSISTENCE_FAILURE_LIMIT = 3

    private val pendingAdCount = AtomicLong(0L)
    private val pendingCustomPostCount = AtomicLong(0L)
    private val flushScheduled = AtomicBoolean(false)
    private val activeBackgroundTasks = AtomicInteger(0)
    private val fileLock = Any()
    private val executorLock = Any()
    private var persistenceFailureCount = 0
    private var persistenceDisabled = false
    private var executor: ScheduledExecutorService? = null

    fun hotReloadBusyReason(): String? {
        if (pendingAdCount.get() > 0L || pendingCustomPostCount.get() > 0L) {
            return "block count stats pending"
        }
        if (flushScheduled.get()) return "block count stats flush scheduled"
        if (activeBackgroundTasks.get() > 0) return "block count stats task active"
        return null
    }

    fun prepareForHotReload(): Boolean {
        val service = synchronized(executorLock) {
            executor.also { executor = null }
        } ?: return true
        service.shutdownNow()
        return try {
            service.awaitTermination(300L, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    fun recordAd(count: Int = 1) {
        record(count, pendingAdCount)
    }

    fun recordCustomPost(count: Int = 1) {
        record(count, pendingCustomPostCount)
    }

    fun snapshot(context: Context): Snapshot {
        val prefs = ConfigManager.getModuleStatePrefs(context)
        return Snapshot(
            adCount = prefs.getLong(KEY_AD_TOTAL, 0L) + pendingAdCount.get(),
            customPostCount = prefs.getLong(KEY_CUSTOM_POST_TOTAL, 0L) + pendingCustomPostCount.get(),
        )
    }

    private fun record(count: Int, pending: AtomicLong) {
        if (count <= 0) return
        pending.addAndGet(count.toLong())
        if (ConfigManager.getAppContext() != null && !isPersistenceDisabled()) scheduleFlush()
    }

    private fun scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return
        scheduleBackground(FLUSH_DELAY_MS) {
            try {
                flushPending()
            } finally {
                flushScheduled.set(false)
                if (hasPending() && !isPersistenceDisabled()) scheduleFlush()
            }
        }
    }

    private fun scheduleBackground(delayMs: Long, block: () -> Unit) {
        activeBackgroundTasks.incrementAndGet()
        try {
            executor().schedule({
                try {
                    block()
                } finally {
                    activeBackgroundTasks.decrementAndGet()
                }
            }, delayMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            activeBackgroundTasks.decrementAndGet()
            throw t
        }
    }

    private fun executor(): ScheduledExecutorService {
        return synchronized(executorLock) {
            val current = executor
            if (current != null && !current.isShutdown) return current
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "tbhook-block-count-stats").apply {
                    isDaemon = true
                }
            }.also { executor = it }
        }
    }

    private fun flushPending() {
        val adDelta = pendingAdCount.getAndSet(0L)
        val customPostDelta = pendingCustomPostCount.getAndSet(0L)
        if (adDelta <= 0L && customPostDelta <= 0L) return

        val context = ConfigManager.getAppContext()
        if (context == null) {
            restorePending(adDelta, customPostDelta)
            return
        }

        synchronized(fileLock) {
            if (persistenceDisabled) {
                restorePending(adDelta, customPostDelta)
                return
            }
            val prefs = ConfigManager.getModuleStatePrefs(context)
            val persisted = prefs.edit()
                .putLong(KEY_AD_TOTAL, prefs.getLong(KEY_AD_TOTAL, 0L) + adDelta)
                .putLong(KEY_CUSTOM_POST_TOTAL, prefs.getLong(KEY_CUSTOM_POST_TOTAL, 0L) + customPostDelta)
                .commit()
            if (persisted) {
                persistenceFailureCount = 0
                return
            }

            persistenceFailureCount += 1
            if (persistenceFailureCount >= PERSISTENCE_FAILURE_LIMIT) {
                persistenceDisabled = true
                restorePending(adDelta, customPostDelta)
                XposedCompat.logW(
                    "[BlockCountStats] persistence disabled after " +
                        "$persistenceFailureCount consecutive failures"
                )
            } else {
                restorePending(adDelta, customPostDelta)
                XposedCompat.logW(
                    "[BlockCountStats] flush failed " +
                        "($persistenceFailureCount/$PERSISTENCE_FAILURE_LIMIT)"
                )
            }
        }
    }

    private fun restorePending(adDelta: Long, customPostDelta: Long) {
        if (adDelta > 0L) pendingAdCount.addAndGet(adDelta)
        if (customPostDelta > 0L) pendingCustomPostCount.addAndGet(customPostDelta)
    }

    private fun hasPending(): Boolean {
        return pendingAdCount.get() > 0L || pendingCustomPostCount.get() > 0L
    }

    private fun isPersistenceDisabled(): Boolean {
        return synchronized(fileLock) { persistenceDisabled }
    }

    data class Snapshot(
        val adCount: Long,
        val customPostCount: Long,
    )
}
