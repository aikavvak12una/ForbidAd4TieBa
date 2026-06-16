package com.forbidad4tieba.hook.feature.ui

import android.os.SystemClock
import com.forbidad4tieba.hook.symbol.model.PbScrollCoalesceSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.ref.WeakReference

/**
 * Coalesces PB comment scroll callbacks when the visible list state has not changed.
 */
object PbScrollCoalesceHook {
    private const val COALESCE_WINDOW_MS = 96L
    private const val BOTTOM_GUARD_REMAINING_ITEMS = 20

    @Volatile private var hooked = false
    @Volatile private var lastListenerRef: WeakReference<Any>? = null
    private var lastListenerState = ScrollState()

    internal fun hook(targets: PbScrollCoalesceSymbols) {
        if (!ConfigManager.isPbScrollCoalesceEnabled) {
            XposedCompat.log("[PbScrollCoalesceHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return
        val scrollMethod = targets.scrollMethod

        try {
            mod.hook(scrollMethod).intercept { chain ->
                if (!ConfigManager.isPbScrollCoalesceEnabled) {
                    return@intercept chain.proceed()
                }
                val listener = chain.thisObject ?: return@intercept chain.proceed()
                val firstVisible = chain.args.getOrNull(1) as? Int ?: return@intercept chain.proceed()
                val visibleCount = chain.args.getOrNull(2) as? Int ?: return@intercept chain.proceed()
                val totalCount = chain.args.getOrNull(3) as? Int ?: return@intercept chain.proceed()

                if (shouldSkip(listener, firstVisible, visibleCount, totalCount)) {
                    return@intercept null
                }
                chain.proceed()
            }
            XposedCompat.log(
                "[PbScrollCoalesceHook] hook INSTALLED: " +
                    "${ReflectionUtils.methodSignature(scrollMethod)}, windowMs=$COALESCE_WINDOW_MS",
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbScrollCoalesceHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun shouldSkip(
        listener: Any,
        firstVisible: Int,
        visibleCount: Int,
        totalCount: Int,
    ): Boolean {
        if (firstVisible < 0 || visibleCount <= 0 || totalCount <= visibleCount) return false

        val remaining = totalCount - (firstVisible + visibleCount)
        if (remaining <= BOTTOM_GUARD_REMAINING_ITEMS) return false

        val now = SystemClock.uptimeMillis()
        val state = stateFor(listener)
        val sameState = state.firstVisible == firstVisible &&
            state.visibleCount == visibleCount &&
            state.totalCount == totalCount
        val skip = sameState && now - state.lastProceedAt < COALESCE_WINDOW_MS
        if (!skip) {
            state.firstVisible = firstVisible
            state.visibleCount = visibleCount
            state.totalCount = totalCount
            state.lastProceedAt = now
        }
        return skip
    }

    private fun stateFor(listener: Any): ScrollState {
        if (lastListenerRef?.get() === listener) return lastListenerState
        val state = ScrollState()
        lastListenerState = state
        lastListenerRef = WeakReference(listener)
        return state
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private data class ScrollState(
        var firstVisible: Int = -1,
        var visibleCount: Int = -1,
        var totalCount: Int = -1,
        var lastProceedAt: Long = 0L,
    )
}
