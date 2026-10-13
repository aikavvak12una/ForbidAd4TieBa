package com.forbidad4tieba.hook.feature.ui.liquidglass

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Gesture state in row coordinates. Resting, including a stationary hold, requires no frames. */
internal class DropletMotion(private val effectsEnabled: Boolean = true) {
    companion object {
        const val HELD_SCALE = 78f / 56f
    }

    private val position = Channel(0f, sqrt(1000.0), 1.0, 0.1f)
    private val pressure = Channel(0f, sqrt(1000.0), 1.0, 0.001f)
    private val width = Channel(1f, sqrt(250.0), 0.6, 0.001f)
    private val height = Channel(1f, sqrt(250.0), 0.7, 0.001f)
    private val velocity = Channel(0f, sqrt(300.0), 0.5, 0.01f)
    private val glow = Channel(0f, sqrt(300.0), 0.5, 0.001f)
    private val dragOffset = Channel(0f, sqrt(300.0), 1.0, 0.5f)
    private var span = 1f
    private var releasePending = false
    private var trackVelocity = true

    val x: Float get() = position.value
    val targetX: Float get() = position.target
    val pressProgress: Float get() = pressure.value.coerceIn(0f, 1f)
    val glowProgress: Float get() = glow.value.coerceAtLeast(0f)
    val accumulatedDrag: Float get() = dragOffset.value
    val scaleX: Float get() = width.value / (1f - (velocity.value * 0.075f).coerceIn(-0.2f, 0.2f))
    val scaleY: Float get() = height.value * (1f - (velocity.value * 0.025f).coerceIn(-0.2f, 0.2f))
    val isAnimating: Boolean get() = releasePending || position.moving || pressure.moving ||
        width.moving || height.moving || velocity.moving || glow.moving || dragOffset.moving

    fun setSpan(value: Float, columnWidth: Float = value) {
        span = value.coerceAtLeast(1f)
        position.threshold = columnWidth.coerceAtLeast(1f) * 0.001f
    }

    fun moveTo(x: Float, trackVelocity: Boolean = true) {
        position.target = x
        this.trackVelocity = trackVelocity
        if (!trackVelocity) velocity.target = 0f
    }

    fun dragBy(delta: Float) { if (effectsEnabled) dragOffset.snapTo(dragOffset.value + delta) }

    fun touchDown() { if (effectsEnabled) glow.target = 1f }

    fun touchUp() {
        glow.target = 0f
        dragOffset.target = 0f
    }

    fun press() {
        releasePending = false
        if (!effectsEnabled) return
        pressure.target = 1f
        width.target = HELD_SCALE
        height.target = HELD_SCALE
    }

    /** Keep the lens open during travel; shrinking early hides its arrival at the chosen tab. */
    fun release() {
        releasePending = effectsEnabled
        releaseIfArrived()
    }

    fun snapTo(x: Float) {
        position.snapTo(x)
        pressure.snapTo(0f)
        width.snapTo(1f)
        height.snapTo(1f)
        velocity.snapTo(0f)
        glow.snapTo(0f)
        dragOffset.snapTo(0f)
        releasePending = false
    }

    fun advance(seconds: Float, velocitySample: ((Float) -> Float)? = null): Boolean {
        if (!seconds.isFinite() || seconds <= 0f) return isAnimating
        position.advance(seconds.toDouble())
        releaseIfArrived()
        pressure.advance(seconds.toDouble())
        width.advance(seconds.toDouble())
        height.advance(seconds.toDouble())
        glow.advance(seconds.toDouble())
        dragOffset.advance(seconds.toDouble())
        if (!effectsEnabled || !position.moving && trackVelocity) velocity.snapTo(0f)
        else if (trackVelocity) velocity.snapTo((velocitySample?.invoke(position.value) ?: position.velocity) / span)
        else velocity.advance(seconds.toDouble())
        return isAnimating
    }

    private fun releaseIfArrived() {
        if (!releasePending || abs(position.value - position.target) > span * 0.025f) return
        releasePending = false
        pressure.target = 0f
        width.target = 1f
        height.target = 1f
    }

    /** Exact damped-oscillator step; frame rate and skipped frames do not change the trajectory. */
    private class Channel(
        initial: Float,
        private val frequency: Double,
        private val damping: Double,
        var threshold: Float,
    ) {
        var value = initial
            private set
        var velocity = 0f
            private set
        var target = initial
        val moving: Boolean get() = abs(value - target) > threshold || abs(velocity) > threshold * 62.5f

        fun snapTo(next: Float) {
            value = next
            target = next
            velocity = 0f
        }

        fun advance(seconds: Double) {
            if (!moving) { snapTo(target); return }
            val offset = (value - target).toDouble()
            val speed = velocity.toDouble()
            val decay = exp(-damping * frequency * seconds)
            if (damping == 1.0) {
                val slope = speed + frequency * offset
                value = (target + decay * (offset + slope * seconds)).toFloat()
                velocity = (decay * (speed - frequency * slope * seconds)).toFloat()
            } else {
                val angular = frequency * sqrt(max(0.0, 1.0 - damping * damping))
                val sine = sin(angular * seconds)
                val cosine = cos(angular * seconds)
                val slope = (speed + damping * frequency * offset) / angular
                val wave = offset * cosine + slope * sine
                value = (target + decay * wave).toFloat()
                velocity = (decay * (angular * (slope * cosine - offset * sine) - damping * frequency * wave)).toFloat()
            }
            if (!moving) snapTo(target)
        }
    }
}
