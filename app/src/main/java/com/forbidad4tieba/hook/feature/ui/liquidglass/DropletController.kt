package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Press/drag behaviour for the droplet, ported from KernelSU's `DampedDragAnimation` by way of
 * liuran001/WeChat-LiquidGlass (MIT), `DropletDragController`.
 *
 * Five independent springs drive everything, with KernelSU's exact parameters:
 *
 * ```
 * value    spring(1.0, 1000)   position, in tab units
 * velocity spring(0.5,  300)   normalised speed, feeds the stretch
 * press    spring(1.0, 1000)   press progress
 * scaleX   spring(0.6,  250)
 * scaleY   spring(0.7,  250)
 * ```
 *
 * Two details matter as much as the numbers. Position is tracked in *tab units* (0..N-1) rather
 * than pixels, so velocity normalises by the tab count and feels identical at any screen density or
 * tab width. And release waits: the scale only relaxes once the droplet has nearly arrived, which
 * is what makes a flick read as a single motion instead of a slide plus a separate shrink.
 *
 * Taps still belong to the host - the gesture is only claimed once the finger has moved
 * horizontally past the touch slop.
 */
internal class DropletController(
    droplet: DropletPanel,
    tabRow: ViewGroup,
    private val density: Float,
    private val config: BottomTabLiquidGlassConfig = BottomTabLiquidGlassConfig.DEFAULT,
) {
    internal companion object {
        /** KernelSU: pressedScale = 78dp / 56dp. */
        const val PRESSED_SCALE = 78f / 56f
        const val STRETCH_LIMIT = 0.2f
        /** KernelSU: release() waits until within 2.5% of the range. */
        const val SETTLE_FRACTION = 0.025f

        /** KernelSU grows the glass by 16dp while held. */
        const val PILL_GROWTH_DP = 16f

        /** KernelSU's 4dp EaseOut rubber band, independent of the tab's moving local coordinates. */
        fun rubberBandOffset(distance: Float, width: Float, density: Float): Float {
            if (width <= 0f || !distance.isFinite()) return 0f
            val fraction = (abs(distance) / width).coerceIn(0f, 1f)
            if (fraction == 0f) return 0f
            // Invert the x coordinate of cubic-bezier(0, 0, 0.58, 1).
            var low = 0f
            var high = 1f
            repeat(14) {
                val t = (low + high) * 0.5f
                val x = 1.74f * t * t - 0.74f * t * t * t
                if (x < fraction) low = t else high = t
            }
            val t = (low + high) * 0.5f
            val eased = t * t * (3f - 2f * t)
            return 4f * density * eased * if (distance < 0f) -1f else 1f
        }

        // A tap across two columns should take the same shape as one across a single column.
        // Dragging keeps a distance of one so its deformation still follows the finger's speed.
        fun velocityRange(tabCount: Int, travelDistance: Float): Float =
            max(1f, tabCount - 1f) * max(1f, abs(travelDistance))

        /** Arrival is measured against the bar's range, independently of stretch normalisation. */
        fun releaseThreshold(tabCount: Int): Float =
            max((tabCount - 1f) * SETTLE_FRACTION, 0.001f)

        fun horizontalScale(pressedScale: Float, velocity: Float): Float {
            val along = (abs(velocity) / 10f * 0.75f).coerceAtMost(STRETCH_LIMIT)
            return pressedScale / (1f - along)
        }

        fun verticalScale(pressedScale: Float, velocity: Float): Float {
            val across = (abs(velocity) / 10f * 0.25f).coerceAtMost(STRETCH_LIMIT)
            return pressedScale * (1f - across)
        }
    }

    private val dropletRef = WeakReference(droplet)
    private val tabRowRef = WeakReference(tabRow)
    private var pillRef = WeakReference<LiquidGlassPanel>(null)
    private val touchSlop = ViewConfiguration.get(droplet.context).scaledTouchSlop

    private val valueSpring = LiquidGlassSpring(1f, 1000f, 0.001f, 0f)
    private val velocitySpring = LiquidGlassSpring(0.5f, 300f, 0.01f, 0f)
    private val pressSpring = LiquidGlassSpring(1f, 1000f, 0.001f, 0f)
    private val scaleXSpring = LiquidGlassSpring(0.6f, 250f, 0.001f, 1f)
    private val scaleYSpring = LiquidGlassSpring(0.7f, 250f, 0.001f, 1f)
    private val offsetSpring = LiquidGlassSpring(1f, 300f, 0.5f, 0f)
    private val highlightSpring = LiquidGlassSpring(0.5f, 300f, 0.001f, 0f)

    private var downX = 0f
    private var downY = 0f
    private var downRowX = 0f
    private var dragStartValue = 0f
    private var dragging = false
    private var touchActive = false
    private var releasePending = false
    private val rowBaseTranslationX = tabRow.translationX
    private var pillBaseTranslationX = 0f

    private var lastFrameNs = 0L
    private var frameScheduled = false
    private var lastSampleMs = 0L
    private var lastSampleValue = 0f
    private var rowOriginX = 0f
    private var tabWidth = 0f
    private var dropletWidth = 0f
    private var motionRange = velocityRange(tabCount(tabRow), 1f)

    private val frameCallback = Choreographer.FrameCallback { onFrame(it) }

    /** Claimed drags keep ownership even when the pointer leaves the glass. */
    val isDragging: Boolean get() = dragging

    fun setPill(pill: LiquidGlassPanel) {
        pillRef = WeakReference(pill)
        pillBaseTranslationX = pill.translationX
    }

    /**
     * The selection changed - the only thing allowed to move the droplet other than a finger.
     *
     * Ignored mid-drag so a page switch cannot yank the droplet out from under the finger.
     */
    fun animateToIndex(index: Int, immediate: Boolean) {
        if (dragging) return
        val row = tabRowRef.get()
        val target = index.toFloat().coerceIn(0f, tabCount(row) - 1f)
        dropletRef.get()?.setSelectedTabIndex(index)
        // Releasing a drag already aimed the spring here, and the resulting click bounces the
        // selection straight back; without this the droplet pops a second time after settling.
        if (!immediate && abs(valueSpring.target - target) < 0.01f) return
        if (immediate) {
            releasePending = false
            valueSpring.snapTo(target)
            velocitySpring.snapTo(0f)
            pressSpring.snapTo(0f)
            scaleXSpring.snapTo(1f)
            scaleYSpring.snapTo(1f)
            offsetSpring.snapTo(0f)
            highlightSpring.snapTo(0f)
            apply()
            return
        }
        press(abs(target - valueSpring.value))
        valueSpring.animateTo(target)
        velocitySpring.animateTo(0f)
        releasePending = true
        schedule()
    }

    /**
     * Feeds a glass-host touch with [rowX] mapped through the row's frozen DOWN matrix.
     * The mapping includes ancestor transforms without letting animation feed back into drag delta.
     *
     * Returns true to swallow the event, which only happens once the gesture has been claimed as a
     * horizontal drag. Everything else falls through to the host so ordinary taps keep working.
     */
    fun onTouch(event: MotionEvent, rowX: Float): Boolean {
        val row = tabRowRef.get() ?: return false
        val droplet = dropletRef.get() ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (droplet.visibility != View.VISIBLE || tabWidth <= 0f) return false
                // raw coordinates do not change when the rubber band translates the tab row.
                downX = event.rawX
                downY = event.rawY
                downRowX = rowX
                dragging = false
                touchActive = true
                val index = LiquidGlassGeom.tabIndexAt(row, rowX)
                if (index < 0) { touchActive = false; return false }
                dragStartValue = index.toFloat()
                press(abs(dragStartValue - valueSpring.value))
                valueSpring.animateTo(dragStartValue)
                highlightSpring.animateTo(1f)
                offsetSpring.snapTo(0f)
                schedule()
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!touchActive) return false
                if (!dragging) {
                    if (droplet.visibility != View.VISIBLE) return false
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) <= touchSlop || abs(dx) <= abs(dy)) return false
                    dragging = true
                    // The host's columns already saw the DOWN; clear their pressed state so none
                    // is left highlighted once the drag takes the gesture over.
                    for (i in 0 until row.childCount) {
                        row.getChildAt(i)?.isPressed = false
                    }
                    press()
                    schedule()
                }
                val width = tabWidth
                if (width > 0f) {
                    val distance = rowX - downRowX
                    val v = dragStartValue + distance / width
                    valueSpring.animateTo(v.coerceIn(0f, tabCount(row) - 1f))
                    offsetSpring.snapTo(distance)
                    schedule()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!touchActive) return false
                touchActive = false
                highlightSpring.animateTo(0f)
                offsetSpring.animateTo(0f)
                if (!dragging) {
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        valueSpring.animateTo(selectedIndex(row).coerceAtLeast(0).toFloat())
                    }
                    release()
                    return false
                }
                // Land under the rendered droplet, not its finger-driven spring target: they can
                // be in different tabs during a quick move or reversal.
                val count = tabCount(row)
                val index = if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    selectedIndex(row).coerceAtLeast(0)
                } else valueSpring.value.coerceIn(0f, count - 1f).roundToInt()
                valueSpring.animateTo(index.toFloat())
                velocitySpring.animateTo(0f)
                dragging = false
                release()
                // A parent can cancel a drag when it takes the gesture back. Cancellation must
                // settle the visual state without synthesising a host tab click.
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val tab = row.getChildAt(index)
                    if (tab != null && !tab.isSelected) tab.performClick()
                }
                return true
            }

            else -> return dragging
        }
    }

    /** Called when the bar hides or detaches, so a cancelled touch cannot leave a live frame loop. */
    fun cancelInteraction() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameScheduled = false
        dragging = false
        touchActive = false
        releasePending = false
        lastFrameNs = 0L
        lastSampleMs = 0L
        val index = tabRowRef.get()?.let(::selectedIndex) ?: -1
        valueSpring.snapTo(if (index >= 0) index.toFloat() else valueSpring.target)
        velocitySpring.snapTo(0f)
        pressSpring.snapTo(0f)
        scaleXSpring.snapTo(1f)
        scaleYSpring.snapTo(1f)
        offsetSpring.snapTo(0f)
        highlightSpring.snapTo(0f)
        apply()
    }

    /**
     * Publishes the laid-out geometry the droplet rides on.
     *
     * Reading it back off the row on every frame looked equivalent but goes stale exactly once: the
     * first layout pass runs before [LiquidGlassBarMetrics.applyHugWidth] has resized the columns,
     * so the droplet is parked using the host's original full-width columns and stays there - the
     * cold-start misplacement. Pushing the values in means a geometry change always re-applies.
     */
    fun setGeometry(rowOriginX: Float, tabWidth: Float, dropletWidth: Float) {
        if (this.rowOriginX == rowOriginX &&
            this.tabWidth == tabWidth &&
            this.dropletWidth == dropletWidth
        ) {
            return
        }
        this.rowOriginX = rowOriginX
        this.tabWidth = tabWidth
        this.dropletWidth = dropletWidth
        apply()
    }

    private fun press(travelDistance: Float = 1f) {
        releasePending = false
        lastSampleMs = 0L
        motionRange = velocityRange(tabCount(tabRowRef.get()), travelDistance)
        pressSpring.animateTo(if (config.pressEffectEnabled) 1f else 0f)
        scaleXSpring.animateTo(if (config.pressEffectEnabled) PRESSED_SCALE else 1f)
        scaleYSpring.animateTo(if (config.pressEffectEnabled) PRESSED_SCALE else 1f)
    }

    private fun release() {
        // KernelSU holds the pressed scale until the droplet has almost arrived.
        releasePending = true
        schedule()
    }

    private fun maybeFinishRelease() {
        if (!releasePending || dragging) return
        val threshold = releaseThreshold(tabCount(tabRowRef.get()))
        if (abs(valueSpring.value - valueSpring.target) > threshold) return
        releasePending = false
        pressSpring.animateTo(0f)
        scaleXSpring.animateTo(1f)
        scaleYSpring.animateTo(1f)
    }

    private fun schedule() {
        if (frameScheduled) return
        frameScheduled = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun onFrame(frameNs: Long) {
        frameScheduled = false
        val droplet = dropletRef.get()
        if (droplet == null || !droplet.isAttachedToWindow || droplet.visibility != View.VISIBLE ||
            droplet.windowVisibility != View.VISIBLE) {
            cancelInteraction()
            return
        }
        val dt = if (lastFrameNs == 0L) 1f / 60f else (frameNs - lastFrameNs) / 1e9f
        lastFrameNs = frameNs

        var running = valueSpring.update(dt)
        sampleVelocity()
        running = velocitySpring.update(dt) || running
        running = pressSpring.update(dt) || running
        running = scaleXSpring.update(dt) || running
        running = scaleYSpring.update(dt) || running
        running = offsetSpring.update(dt) || running
        running = highlightSpring.update(dt) || running

        apply()
        maybeFinishRelease()

        // Re-check after the release: it starts the press/scale springs going again, and testing the
        // pre-release flag would end the loop right then, leaving the droplet stuck at pressed size.
        running = running || pressSpring.isRunning || scaleXSpring.isRunning ||
            scaleYSpring.isRunning || valueSpring.isRunning || velocitySpring.isRunning

        if (running || releasePending) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    /**
     * KernelSU samples velocity over the value itself and normalises it by the range, so the
     * stretch is independent of tab width and screen density.
     */
    private fun sampleVelocity() {
        val now = SystemClock.uptimeMillis()
        if (lastSampleMs == 0L) {
            lastSampleMs = now
            lastSampleValue = valueSpring.value
            return
        }
        val dtMs = (now - lastSampleMs).toFloat()
        if (dtMs < 8f) return
        val perSecond = (valueSpring.value - lastSampleValue) * 1000f / dtMs
        velocitySpring.animateTo(perSecond / motionRange)
        lastSampleMs = now
        lastSampleValue = valueSpring.value
    }

    private fun apply() {
        val droplet = dropletRef.get() ?: return
        val row = tabRowRef.get() ?: return
        if (row.childCount == 0 || tabWidth <= 0f || dropletWidth <= 0f) return
        val first = row.getChildAt(0) ?: return
        droplet.setSelectedTabIndex(selectedIndex(row))

        val pill = pillRef.get()
        val panelOffset = if (config.pressEffectEnabled) {
            rubberBandOffset(offsetSpring.value, pill?.width?.toFloat() ?: 0f, density)
        } else 0f
        row.translationX = rowBaseTranslationX + panelOffset
        if (pill != null) pill.translationX = pillBaseTranslationX + panelOffset
        val originX = rowOriginX + first.left + (first.width - dropletWidth) * 0.5f
        droplet.translationX = originX + rowBaseTranslationX + valueSpring.value * tabWidth + panelOffset

        // Speed controls deformation; direction controls translation only. Using signed speed
        // made rightward transitions wide/flat and leftward transitions narrow/tall.
        droplet.scaleX = if (config.pressEffectEnabled) horizontalScale(scaleXSpring.value, velocitySpring.value) else 1f
        droplet.scaleY = if (config.pressEffectEnabled) verticalScale(scaleYSpring.value, velocitySpring.value) else 1f

        val p = pressSpring.value
        droplet.setProgress(p)
        // The droplet slides via translationX, which does not redraw it - re-capture every frame so
        // the refraction tracks what it passes over.
        droplet.refresh()

        // KernelSU grows the whole pill a little while dragging, and rides a highlight that follows
        // the droplet across it.
        if (pill != null && pill.width > 0) {
            val growth = 1f + (PILL_GROWTH_DP * density / pill.width) * p
            pill.scaleX = growth
            pill.scaleY = growth
            val centre = droplet.left + droplet.translationX + dropletWidth * 0.5f - pill.left - pill.translationX
            pill.setInteraction(highlightSpring.value.coerceIn(0f, 1f), centre)
        }
    }

    private fun tabCount(row: ViewGroup?): Int = max(1, row?.childCount ?: 0)

    private fun selectedIndex(row: ViewGroup): Int {
        for (i in 0 until row.childCount) {
            if (row.getChildAt(i)?.isSelected == true) return i
        }
        return -1
    }
}
