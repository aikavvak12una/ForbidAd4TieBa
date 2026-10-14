// Copyright 2026, compose-miuix-ui contributors
// miuix/Kyant0 drag semantics (Apache-2.0), adapted to native host touch dispatch.
// Source revisions, local changes and full notices: app/src/main/assets/open-source-licenses.txt.
package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.os.Build
import android.view.Choreographer
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.sign

/** Owns the selection lens only; native tabs still own clicks, long presses and accessibility. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal class DropletController(
    droplet: DropletPanel,
    tabRow: ViewGroup,
    panel: LiquidGlassPanel,
    config: BottomTabLiquidGlassConfig = BottomTabLiquidGlassConfig.DEFAULT,
) {
    companion object {
        /** The expanded lens may cross the bar edge, but must remain inside the window. */
        fun clampTranslation(x: Float, width: Float, left: Float, available: Float, scale: Float): Float {
            if (available <= 0f || width <= 0f) return x
            val bulge = (scale - 1f) * width / 2f
            val minX = left + bulge
            val maxX = left + available - width - bulge
            return if (minX <= maxX) x.coerceIn(minX, maxX) else left + (available - width) / 2f
        }
    }

    private val dropletRef = WeakReference(droplet)
    private val rowRef = WeakReference(tabRow)
    private val panelRef = WeakReference(panel)
    private val touchSlop = ViewConfiguration.get(droplet.context).scaledTouchSlop
    private val screenMargin = 4f * droplet.resources.displayMetrics.density
    private val motion = DropletMotion(config.liquidGlassEnabled)
    private val density = droplet.resources.displayMetrics.density
    private var velocityTracker: VelocityTracker? = null
    private var frameMillis = 0L
    private var artworkCentreY = 0f
    private var originalRow: RowTransform? = null
    private var previousRowX = 0f
    private val sampleVelocity: (Float) -> Float = { x ->
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        val sample = MotionEvent.obtain(frameMillis, frameMillis, MotionEvent.ACTION_MOVE, x, 0f, 0)
        try { tracker.addMovement(sample) } finally { sample.recycle() }
        tracker.computeCurrentVelocity(1000)
        tracker.xVelocity
    }
    private val choreographer = Choreographer.getInstance()
    private var framePosted = false
    private var lastFrameNanos = 0L
    private var targetIndex = 0
    private var rowOriginX = 0f
    private var tabWidth = 0f
    private var dropletWidth = 0f
    private var downX = 0f
    private var downY = 0f
    private var downBarScale = 1f
    private var touchActive = false
    var isDragging: Boolean = false
        private set

    private val frameCallback = Choreographer.FrameCallback { time ->
        framePosted = false
        val lens = dropletRef.get()
        if (lens == null || !lens.isAttachedToWindow || !lens.isShown || !lens.hasWindowFocus()) {
            cancelInteraction()
        } else {
            val durationScale = ValueAnimator.getDurationScale()
            if (durationScale <= 0f) motion.snapTo(motion.targetX)
            else if (lastFrameNanos != 0L) {
                frameMillis = time / 1_000_000L
                motion.advance((time - lastFrameNanos) / 1_000_000_000f / durationScale, sampleVelocity)
            }
            lastFrameNanos = time
            applyFrame()
            if (motion.isAnimating) scheduleFrame() else lastFrameNanos = 0L
        }
    }

    fun animateToIndex(index: Int, immediate: Boolean) {
        if (isDragging) return
        val row = rowRef.get() ?: return
        val droplet = dropletRef.get() ?: return
        if (row.childCount == 0 || tabWidth <= 0f) return
        val next = index.coerceIn(0, row.childCount - 1)
        droplet.setSelectedTabIndex(next)
        val destination = tabPosition(row, next)
        val changed = targetIndex != next || abs(motion.targetX - destination) > 0.1f
        targetIndex = next
        if (immediate || !ValueAnimator.areAnimatorsEnabled()) {
            stopFrames()
            motion.snapTo(destination)
            applyFrame()
        } else if (changed) {
            motion.moveTo(destination, trackVelocity = touchActive)
            if (!touchActive) {
                motion.press()
                motion.release()
            }
            scheduleFrame()
        }
    }

    fun setGeometry(rowOriginX: Float, tabWidth: Float, dropletWidth: Float, artworkCentreY: Float) {
        if (this.rowOriginX == rowOriginX && this.tabWidth == tabWidth && this.dropletWidth == dropletWidth &&
            this.artworkCentreY == artworkCentreY) return
        this.rowOriginX = rowOriginX
        this.tabWidth = tabWidth
        this.dropletWidth = dropletWidth
        this.artworkCentreY = artworkCentreY
        val row = rowRef.get() ?: return
        if (originalRow == null) originalRow = RowTransform(row.scaleX, row.scaleY, row.pivotX, row.pivotY, row.translationX)
        motion.setSpan((tabWidth * (row.childCount - 1)).coerceAtLeast(tabWidth), tabWidth)
        if (!touchActive) {
            stopFrames()
            motion.snapTo(tabPosition(row, targetIndex))
            applyFrame()
        }
    }

    /** rowX comes from the overlay's frozen DOWN matrix, unaffected by visual lens expansion. */
    fun onTouch(event: MotionEvent, rowX: Float): Boolean {
        val row = rowRef.get() ?: return false
        val droplet = dropletRef.get() ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = LiquidGlassGeom.tabIndexAt(row, rowX)
                touchActive = droplet.isShown && tabWidth > 0f && index >= 0
                downX = event.rawX
                downY = event.rawY
                downBarScale = panelRef.get()?.scaleX ?: 1f
                previousRowX = rowX
                isDragging = false
                if (touchActive) {
                    velocityTracker?.clear()
                    animateToIndex(index, immediate = false)
                    motion.press()
                    motion.touchDown()
                    scheduleFrame()
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchActive) return false
                // Hit testing stays frozen, but the reference measures drag inside the breathing bar.
                val currentBarScale = panelRef.get()?.scaleX ?: downBarScale
                val delta = (rowX - previousRowX) * downBarScale / currentBarScale
                val wasInside = previousRowX >= 0f && previousRowX <= row.width
                previousRowX = rowX
                if (wasInside && rowX >= 0f && rowX <= row.width && delta != 0f) {
                    val first = tabPosition(row, 0)
                    val last = tabPosition(row, row.childCount - 1)
                    motion.moveTo((motion.targetX + delta).coerceIn(minOf(first, last), maxOf(first, last)))
                    motion.dragBy(delta)
                    scheduleFrame()
                }
                if (!isDragging) {
                    val dx = abs(event.rawX - downX)
                    val dy = abs(event.rawY - downY)
                    if (dx <= touchSlop || dx <= dy) return false
                    isDragging = true
                    for (i in 0 until row.childCount) row.getChildAt(i)?.isPressed = false
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!touchActive) return false
                val wasDragging = isDragging
                isDragging = false
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    animateToIndex(selectedIndex(row).coerceAtLeast(0), immediate = false)
                } else if (wasDragging) {
                    // Land at the finger's destination even when the damped lens is still catching up.
                    val center = motion.targetX + dropletWidth / 2f - rowOriginX - (originalRow?.translationX ?: 0f)
                    val index = LiquidGlassGeom.tabIndexAt(row, center).coerceAtLeast(0)
                    animateToIndex(index, immediate = false)
                    row.getChildAt(index)?.takeIf { !it.isSelected }?.performClick()
                }
                touchActive = false
                motion.touchUp()
                motion.release()
                scheduleFrame()
                return wasDragging
            }
            else -> return isDragging
        }
    }

    /** No callback survives a stationary hold, completed release, hidden page or lost window focus. */
    fun cancelInteraction() {
        stopFrames()
        touchActive = false
        isDragging = false
        velocityTracker?.recycle()
        velocityTracker = null
        val row = rowRef.get() ?: return
        val selected = selectedIndex(row)
        if (selected >= 0) targetIndex = selected
        motion.snapTo(tabPosition(row, targetIndex))
        applyFrame()
    }

    private fun scheduleFrame() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            stopFrames()
            motion.snapTo(motion.targetX)
            applyFrame()
        } else if (!framePosted && motion.isAnimating) {
            framePosted = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    private fun stopFrames() {
        if (framePosted) choreographer.removeFrameCallback(frameCallback)
        framePosted = false
        lastFrameNanos = 0L
    }

    private fun applyFrame() {
        val droplet = dropletRef.get() ?: return
        val panel = panelRef.get() ?: return
        val row = rowRef.get() ?: return
        val original = originalRow ?: return
        val scale = 1f + 16f * density / panel.width.coerceAtLeast(1) * motion.pressProgress
        val fraction = (motion.accumulatedDrag / panel.width.coerceAtLeast(1)).coerceIn(-1f, 1f)
        val offset = 4f * density * fraction.sign * easeOut(abs(fraction))
        panel.scaleX = scale
        panel.scaleY = scale
        panel.translationX = offset
        row.pivotX = if (scale == 1f) original.pivotX else panel.left + panel.width / 2f - rowOriginX - original.translationX
        row.pivotY = if (scale == 1f) original.pivotY else artworkCentreY
        row.scaleX = original.scaleX * scale
        row.scaleY = original.scaleY * scale
        row.translationX = original.translationX + offset
        panel.setInteraction(motion.pressProgress, motion.x - tabPosition(row, 0) + tabWidth / 2f + offset, motion.glowProgress)
        droplet.scaleX = motion.scaleX
        droplet.scaleY = motion.scaleY
        val available = (droplet.parent as? View)?.width?.toFloat() ?: 0f
        droplet.translationX = clampTranslation(motion.x + offset, dropletWidth, screenMargin,
            (available - 2f * screenMargin).coerceAtLeast(0f), motion.scaleX)
        droplet.setInteraction(motion.pressProgress, scale, artworkCentreY)
        droplet.refresh()
    }

    private fun tabPosition(row: ViewGroup, index: Int): Float {
        val tab = row.getChildAt(index) ?: return rowOriginX
        return rowOriginX + (originalRow?.translationX ?: 0f) + tab.left + (tab.width - dropletWidth) / 2f
    }

    private fun selectedIndex(row: ViewGroup): Int {
        for (i in 0 until row.childCount) if (row.getChildAt(i)?.isSelected == true) return i
        return -1
    }

    private data class RowTransform(val scaleX: Float, val scaleY: Float, val pivotX: Float, val pivotY: Float, val translationX: Float)

    /** CubicBezierEasing(0, 0, .58, 1), the reference's EaseOut curve. */
    private fun easeOut(x: Float): Float {
        if (x <= 0f || x >= 1f) return x
        var lo = 0f
        var hi = 1f
        repeat(16) {
            val t = (lo + hi) * 0.5f
            val bx = 1.74f * (1f - t) * t * t + t * t * t
            if (bx < x) lo = t else hi = t
        }
        val t = (lo + hi) * 0.5f
        return (3f - 2f * t) * t * t
    }
}
