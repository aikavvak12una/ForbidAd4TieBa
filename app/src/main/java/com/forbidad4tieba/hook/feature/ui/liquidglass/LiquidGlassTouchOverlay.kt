package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.graphics.Matrix
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/**
 * Receives touches at the glass host, above the native wrapper's small layout slot.
 *
 * Like Android's TouchDelegate, this forwards a complete native gesture with an adjusted origin.
 * The capsule's matrices decide hit testing; frozen DOWN matrices keep press/rubber-band animation
 * from moving the coordinate system under the finger. No host listener or global dispatch hook is
 * replaced. Native clicks, long presses and accessibility actions stay owned by the host tabs.
 */
internal class LiquidGlassTouchOverlay(
    private val panel: LiquidGlassPanel,
    private val droplet: DropletPanel,
    private val wrapper: ViewGroup,
    private val row: ViewGroup,
    private val controller: DropletController,
) : View(panel.context) {
    private val global = Matrix()
    private val inverse = Matrix()
    private val hitMatrix = Matrix()
    private val rowMatrix = Matrix()
    private val nativeMatrix = Matrix()
    private val point = FloatArray(2)
    private var active = false
    private var ownsGesture = false
    private var nativeTarget: View? = null
    private var nativeCancelled = false
    private var nativeOffsetX = 0f
    private var nativeOffsetY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f

    init {
        setWillNotDraw(true)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) cancelInteraction()
    }

    private fun isAvailable(): Boolean = active && isShown && windowVisibility == VISIBLE &&
        panel.isShown && wrapper.isShown && wrapper.alpha > 0f

    /** Local(source) -> global -> local(target), including every translation, scale and scroll. */
    private fun transformTo(target: View, out: Matrix): Boolean {
        out.reset()
        transformMatrixToGlobal(out)
        global.reset()
        target.transformMatrixToGlobal(global)
        if (!global.invert(inverse)) return false
        out.postConcat(inverse)
        return true
    }

    private fun map(matrix: Matrix, x: Float, y: Float) {
        point[0] = x
        point[1] = y
        matrix.mapPoints(point)
    }

    private fun hits(view: View, x: Float, y: Float): Boolean {
        if (!view.isShown || view.alpha <= 0f || !transformTo(view, hitMatrix)) return false
        map(hitMatrix, x, y)
        return LiquidGlassGeom.containsCapsule(point[0], point[1], view.width.toFloat(), view.height.toFloat())
    }

    private fun hitsSurface(x: Float, y: Float): Boolean =
        hits(panel, x, y) || hits(droplet, x, y)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (ownsGesture) cancelInteraction()
            if (!isAvailable() || !hitsSurface(event.x, event.y) || !begin(event)) return false
        } else if (!ownsGesture) {
            return false
        }

        lastX = event.x
        lastY = event.y
        // A tap must end on the glass. A claimed drag lands at the visible droplet even if the
        // pointer has left the bar, so its release must still reach the controller.
        if (!isAvailable() || event.pointerCount != 1 || nativeTarget?.parent !== row ||
            (event.actionMasked == MotionEvent.ACTION_UP && !controller.isDragging &&
                !hitsSurface(event.x, event.y))
        ) {
            cancelInteraction()
            return true
        }

        map(rowMatrix, event.x, event.y)
        val dragging = controller.onTouch(event, point[0])
        if (dragging) {
            // Send a real CANCEL, not just isPressed=false: native click/long-press callbacks must
            // also be removed when the horizontal drag takes ownership.
            cancelNative(event)
        } else if (!nativeCancelled) {
            dispatchNative(event)
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            finish()
            // Native View may post its click. Reconcile afterwards, including a consumed long
            // press or an aborted vertical gesture which deliberately did not change selection.
            post {
                if (!ownsGesture) {
                    for (i in 0 until row.childCount) {
                        if (row.getChildAt(i)?.isSelected == true) {
                            controller.animateToIndex(i, immediate = false)
                            break
                        }
                    }
                }
            }
        }
        return true
    }

    private fun begin(event: MotionEvent): Boolean {
        if (!transformTo(row, rowMatrix)) return false
        map(rowMatrix, event.x, event.y)
        val tab = row.getChildAt(LiquidGlassGeom.tabIndexAt(row, point[0])) ?: return false
        if (!transformTo(tab, nativeMatrix) || tab.height <= 0) return false
        map(nativeMatrix, event.x, event.y)
        nativeOffsetX = point[0].coerceIn(0f, tab.width - 1f) - point[0]
        nativeOffsetY = point[1].coerceIn(0f, tab.height - 1f) - point[1]
        nativeTarget = tab
        nativeCancelled = false
        ownsGesture = true
        downTime = event.downTime
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun dispatchNative(source: MotionEvent, cancel: Boolean = false) {
        val target = nativeTarget ?: return
        val copy = MotionEvent.obtain(source)
        try {
            if (cancel) copy.action = MotionEvent.ACTION_CANCEL
            copy.transform(nativeMatrix)
            copy.offsetLocation(nativeOffsetX, nativeOffsetY)
            target.dispatchTouchEvent(copy)
        } finally {
            copy.recycle()
        }
    }

    private fun cancelNative(event: MotionEvent) {
        if (nativeCancelled) return
        nativeCancelled = true
        dispatchNative(event, cancel = true)
    }

    fun cancelInteraction() {
        if (ownsGesture) {
            val cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, lastX, lastY, 0)
            try {
                cancelNative(cancel)
            } finally {
                cancel.recycle()
                finish()
            }
        }
        controller.cancelInteraction()
    }

    private fun finish() {
        ownsGesture = false
        nativeTarget = null
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onDetachedFromWindow() {
        cancelInteraction()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelInteraction()
    }
}
