package com.forbidad4tieba.hook.feature.ui.liquidglass

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A single critically-parameterised spring, matching Compose's
 * `spring(dampingRatio, stiffness, visibilityThreshold)`.
 *
 * Ported from liuran001/WeChat-LiquidGlass (MIT), `Spring`. KernelSU's bar is animated entirely by
 * springs rather than duration-based interpolators - five of them, each with its own damping and
 * stiffness - so reproducing the feel means reproducing the physics, not approximating it with an
 * overshoot interpolator.
 *
 * Integrated semi-implicitly, which stays stable at the stiffnesses used here (up to 1000) as long
 * as steps are clamped to a sane frame time.
 */
internal class LiquidGlassSpring(
    dampingRatio: Float,
    private val stiffness: Float,
    private val threshold: Float,
    initial: Float,
) {
    private val damping = 2f * dampingRatio * sqrt(stiffness)

    var value: Float = initial
        private set
    var target: Float = initial
        private set
    private var velocity = 0f

    var isRunning: Boolean = false
        private set

    fun animateTo(newTarget: Float) {
        if (target != newTarget) {
            target = newTarget
            isRunning = true
        }
    }

    fun snapTo(newValue: Float) {
        value = newValue
        target = newValue
        velocity = 0f
        isRunning = false
    }

    /** Advances by [dt] seconds; returns true while still in motion. */
    fun update(dt: Float): Boolean {
        if (!isRunning) return false
        // Sub-step so a dropped frame cannot blow the integrator up.
        var remaining = min(dt, 0.064f)
        while (remaining > 0f) {
            val step = min(remaining, 1f / 240f)
            remaining -= step
            val accel = -stiffness * (value - target) - damping * velocity
            velocity += accel * step
            value += velocity * step
        }
        if (abs(value - target) < threshold && abs(velocity) < threshold * 10f) {
            value = target
            velocity = 0f
            isRunning = false
        }
        return isRunning
    }
}
