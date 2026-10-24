package com.forbidad4tieba.hook.feature.ui.liquidglass

import com.forbidad4tieba.hook.feature.ui.HomeTabAutoHideStrategy

/** Uses the home bar's thresholds, direction changes and minimum action interval. */
internal class PostReplyAutoHideState : HomeTabAutoHideStrategy.ScrollAccumulator {
    override var lastDirection = 0
    override var accumulatedDy = 0
    override var hideThresholdPx = 0
    override var showThresholdPx = 0
    var hidden = false
        private set
    private var lastActionAt = 0L

    fun onScroll(dy: Int, now: Long, threshold: Int): Boolean? {
        if (dy == 0) return null
        val direction = HomeTabAutoHideStrategy.directionOf(dy)
        val target = HomeTabAutoHideStrategy.shouldHide(direction)
        if (hidden == target) { HomeTabAutoHideStrategy.reset(this); return null }
        if (!HomeTabAutoHideStrategy.advance(this, direction, dy, threshold) ||
            now - lastActionAt < HomeTabAutoHideStrategy.ACTION_MIN_INTERVAL_MS) return null
        hidden = target
        lastActionAt = now
        HomeTabAutoHideStrategy.reset(this)
        return target
    }

    fun reset(initiallyHidden: Boolean = false) {
        hidden = initiallyHidden
        lastActionAt = 0L
        hideThresholdPx = 0
        showThresholdPx = 0
        HomeTabAutoHideStrategy.reset(this)
    }
}
