package com.forbidad4tieba.hook.feature.ui

import android.view.View
import android.view.ViewConfiguration

internal object HomeTabAutoHideStrategy {
    const val ACTION_MIN_INTERVAL_MS = 800L
    const val ANIMATE_SCROLL_ACTIONS = true
    const val TRANSITION_DURATION_MS = 240L
    const val POST_ACTION_QUIET_MS = 450L
    const val PAGE_SWITCH_QUIET_MS = 700L

    private const val HIDE_THRESHOLD_DP = 128
    private const val SHOW_THRESHOLD_DP = 24
    private const val TOUCH_SLOP_MULTIPLIER = 2

    fun directionOf(dy: Int): Int = if (dy > 0) 1 else -1

    fun shouldHide(direction: Int): Boolean = direction > 0

    fun postActionQuietUntil(now: Long): Long = now + POST_ACTION_QUIET_MS

    fun pageSwitchQuietUntil(now: Long): Long = now + PAGE_SWITCH_QUIET_MS

    fun reachedScrollThreshold(view: View, state: ScrollAccumulator, direction: Int, dy: Int): Boolean {
        if (state.lastDirection != direction) {
            state.lastDirection = direction
            state.accumulatedDy = 0
        }
        state.accumulatedDy += if (dy > 0) dy else -dy
        return state.accumulatedDy >= thresholdPx(view, state, direction)
    }

    fun reset(state: ScrollAccumulator) {
        state.lastDirection = 0
        state.accumulatedDy = 0
    }

    private fun thresholdPx(view: View, state: ScrollAccumulator, direction: Int): Int {
        if (shouldHide(direction)) {
            val cached = state.hideThresholdPx
            if (cached > 0) return cached
            return calculateThresholdPx(view, HIDE_THRESHOLD_DP).also {
                state.hideThresholdPx = it
            }
        }
        val cached = state.showThresholdPx
        if (cached > 0) return cached
        return calculateThresholdPx(view, SHOW_THRESHOLD_DP).also {
            state.showThresholdPx = it
        }
    }

    private fun calculateThresholdPx(view: View, thresholdDp: Int): Int {
        val byDensity = (thresholdDp * view.resources.displayMetrics.density + 0.5f).toInt()
        val byTouchSlop = ViewConfiguration.get(view.context).scaledTouchSlop * TOUCH_SLOP_MULTIPLIER
        return maxOf(byDensity, byTouchSlop)
    }

    interface ScrollAccumulator {
        var lastDirection: Int
        var accumulatedDy: Int
        var hideThresholdPx: Int
        var showThresholdPx: Int
    }
}
