// Copyright 2026, compose-miuix-ui contributors
// miuix/Kyant0 layered backdrop (Apache-2.0), adapted to shared native page capture.
// Source revisions, local changes and full notices: app/src/main/assets/open-source-licenses.txt.
package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderNode
import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.ref.WeakReference
import kotlin.math.min
import kotlin.math.roundToInt

/** One page capture feeds the outer bar and the independent, smaller tab backdrop inside its lens. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal class LiquidGlassPanel(
    context: Context,
    backdrop: ViewGroup,
    private var density: Float,
    private var night: Boolean,
    private val config: BottomTabLiquidGlassConfig = BottomTabLiquidGlassConfig.DEFAULT,
) : View(context) {
    private val backdropRef = WeakReference(backdrop)
    private var dependent = WeakReference<View>(null)
    private val renderer = LiquidGlassRenderer(config.liquidGlassEnabled)
    private var tabRenderer: LiquidGlassRenderer? = null
    private val source = RenderNode("LiquidGlassSharedPageBackdrop")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val alphaPaint = Paint()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPath = Path()
    private val actionsShadowPath = Path()
    private var shadowMargin = (2 * LiquidGlassStyle.SHADOW_RADIUS_DP + LiquidGlassStyle.SHADOW_OFFSET_Y_DP) * density
    private var shadowDirty = true
    private val pagePosition = IntArray(2)
    private val anchor = Matrix()
    private val screenToLocal = Matrix()
    private val point = FloatArray(2)
    private val samplingValues = FloatArray(9)
    private val previousSampling = FloatArray(9) { Float.NaN }
    private val tabClip = Path()
    private var requestedGeneration = 0L
    private var capturedGeneration = -1L
    private var captureRevision = 0L
    private var materialRevision = 0L
    private var materialCaptureRevision = -1L
    private var tabRevision = 0L
    private var tabCaptureRevision = -1L
    private var tabOffsetX = Float.NaN
    private var tabInset = -1
    private var selectionHeight = 0
    private var selectionInset = 0
    private var captureX = 0
    private var captureY = 0
    private var captureWidth = 0
    private var captureHeight = 0
    private var captureMargin = 0
    private var inputWidth = 0
    private var actionsLeft = 0
    private var containerAlpha = 1f
    private var interaction = 0f
    private var interactionX = 0f
    private var glowProgress = 0f
    private var samplingX = 0f
    private var samplingY = 0f
    private var captureBroken = false
    private var ready = false

    val isSupported: Boolean get() = renderer.isAvailable && !captureBroken
    var isCapturing = false
        private set

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        ready = true
    }

    fun setDependent(view: View) { dependent = WeakReference(view) }

    fun setTabLensGeometry(height: Int, inset: Int) {
        if (selectionHeight == height && selectionInset == inset) return
        selectionHeight = height
        selectionInset = inset
        tabCaptureRevision = -1L
        invalidate()
    }

    fun updateDensity(value: Float) {
        if (density == value) return
        density = value
        shadowMargin = (2 * LiquidGlassStyle.SHADOW_RADIUS_DP + LiquidGlassStyle.SHADOW_OFFSET_Y_DP) * value
        shadowDirty = true
        source.discardDisplayList()
        capturedGeneration = -1L
        tabCaptureRevision = -1L
        materialCaptureRevision = -1L
        refreshBackdrop()
        invalidate()
    }

    fun refreshBackdrop() {
        // Layout, scrolling and the pre-draw dirty check can all request the same next capture.
        if (!config.needsBackdrop || requestedGeneration != capturedGeneration) return
        requestedGeneration++
        invalidate()
        dependent.get()?.invalidate()
    }

    fun setTheme(value: Boolean) {
        if (night == value) return
        night = value
        shadowDirty = true
        refreshBackdrop()
        invalidate()
    }

    fun setSeparatedSurfaces(leftWidth: Int, rightLeft: Int): Boolean {
        if (inputWidth == leftWidth && actionsLeft == rightLeft) return false
        require((leftWidth == 0 && rightLeft == 0) || (leftWidth > 0 && rightLeft > leftWidth))
        inputWidth = leftWidth
        actionsLeft = rightLeft
        shadowDirty = true
        invalidate()
        return true
    }

    fun setInteraction(progress: Float, centreX: Float, glow: Float = progress) {
        if (interaction == progress && interactionX == centreX && glowProgress == glow) return
        interaction = progress
        interactionX = centreX
        glowProgress = glow
        invalidate()
    }

    fun setContainerAlpha(value: Float): Boolean {
        val next = value.coerceIn(0f, 1f)
        if (containerAlpha == next) return false
        val wasVisible = containerAlpha > 0f
        containerAlpha = next
        invalidate()
        // Lifecycle callbacks cover focus/visibility; a fade changes ownership only at zero.
        if (wasVisible != (next > 0f)) updateLightSource()
        return true
    }

    private fun updateLightSource() {
        if (!ready) return
        if (config.liquidGlassEnabled && isAttachedToWindow && isShown &&
            windowVisibility == VISIBLE && hasWindowFocus() && containerAlpha > 0f && isSupported) {
            LiquidGlassLightSource.register(this)
        } else LiquidGlassLightSource.unregister(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLightSource()
        refreshBackdrop()
    }

    override fun onDetachedFromWindow() {
        LiquidGlassLightSource.unregister(this)
        source.discardDisplayList()
        renderer.release()
        tabRenderer?.release()
        capturedGeneration = -1L
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateLightSource()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateLightSource()
        if (ready && visibility == VISIBLE) refreshBackdrop()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateLightSource()
        if (hasWindowFocus) refreshBackdrop()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shadowDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0 || containerAlpha <= 0f || isCapturing) return
        val save = if (containerAlpha < 1f) {
            alphaPaint.alpha = (containerAlpha * 255f).roundToInt()
            canvas.saveLayer(-shadowMargin, -shadowMargin, width + shadowMargin, height + shadowMargin, alphaPaint)
        } else canvas.save()
        drawShadow(canvas)
        if (config.needsBackdrop) {
            val p = parameters(height)
            val margin = LiquidGlassStyle.margin(p)
            if (canvas.isHardwareAccelerated && ensureBackdrop(margin) && updateSampling()) {
                if (!renderer.draw(canvas, width, height, p, materialRevision, recordMaterial)) drawSurfaceTint(canvas)
            } else drawSurfaceTint(canvas)
        } else drawSurfaceTint(canvas, parameters(height).tint)
        canvas.restoreToCount(save)
    }

    /** A cached shape uses the hardware canvas shadow; no page capture or animation is added. */
    private fun drawShadow(canvas: Canvas) {
        if (shadowDirty) {
            shadowDirty = false
            shadowPath.reset()
            actionsShadowPath.reset()
            val split = inputWidth > 0 && actionsLeft < width
            val w = if (split) inputWidth.toFloat() else width.toFloat()
            val h = height.toFloat()
            val radius = min(w, h) / 2f
            shadowPath.addRoundRect(0f, 0f, w, h, radius, radius, Path.Direction.CW)
            if (split) {
                val rightRadius = min((width - actionsLeft).toFloat(), h) / 2f
                actionsShadowPath.addRoundRect(actionsLeft.toFloat(), 0f, width.toFloat(), h,
                    rightRadius, rightRadius, Path.Direction.CW)
            }
            shadowPaint.setShadowLayer(LiquidGlassStyle.SHADOW_RADIUS_DP * density, 0f,
                LiquidGlassStyle.SHADOW_OFFSET_Y_DP * density,
                if (night) LiquidGlassStyle.DARK_SHADOW_COLOR else LiquidGlassStyle.LIGHT_SHADOW_COLOR)
        }
        val save = canvas.save()
        // Keep the translucent glass and the gap between reply capsules free of a solid underlay.
        canvas.clipOutPath(shadowPath)
        canvas.clipOutPath(actionsShadowPath)
        canvas.drawPath(shadowPath, shadowPaint)
        if (!actionsShadowPath.isEmpty) canvas.drawPath(actionsShadowPath, shadowPaint)
        canvas.restoreToCount(save)
    }

    private fun parameters(h: Int, hidden: Boolean = false) = LiquidGlassStyle.params(
        width, h, density, night, config, inputWidth, actionsLeft,
        press = interaction, touchX = interactionX, glow = glowProgress, drawHighlight = !hidden,
        lightAngle = if (config.liquidGlassEnabled) LiquidGlassLightSource.angle else LiquidGlassStyle.DEFAULT_LIGHT_ANGLE,
    )

    private val recordMaterial: (Canvas, Float, Int, FloatArray) -> Unit = { canvas, padding, factor, residual ->
        residual[0] = LiquidGlassGeom.samplingResidual(samplingX - pagePosition[0], factor)
        residual[1] = LiquidGlassGeom.samplingResidual(samplingY - pagePosition[1], factor)
        val save = canvas.save()
        canvas.translate(padding, padding)
        canvas.concat(screenToLocal)
        canvas.translate(residual[0], residual[1])
        canvas.translate((captureX - captureMargin).toFloat(), (captureY - captureMargin).toFloat())
        canvas.drawRenderNode(source)
        canvas.restoreToCount(save)
    }

    private fun updateSampling(): Boolean {
        screenToLocal.reset()
        transformMatrixToGlobal(screenToLocal)
        point[0] = 0f
        point[1] = 0f
        screenToLocal.mapPoints(point)
        samplingX = point[0]
        samplingY = point[1]
        if (!screenToLocal.invert(screenToLocal)) return false
        screenToLocal.getValues(samplingValues)
        if (materialCaptureRevision != captureRevision || !samplingValues.contentEquals(previousSampling)) {
            samplingValues.copyInto(previousSampling)
            materialCaptureRevision = captureRevision
            materialRevision++
        }
        return true
    }

    /** The capture is anchored to layout, so breathing/dragging samples this band without rereading the page. */
    private fun ensureBackdrop(margin: Int): Boolean {
        if (!isSupported || isCapturing) return false
        val page = backdropRef.get() ?: return false
        val owner = parent as? View ?: return false
        if (width <= 0 || height <= 0 || page.width <= 0 || page.height <= 0) return false
        anchor.reset()
        owner.transformMatrixToGlobal(anchor)
        anchor.preTranslate((left - owner.scrollX).toFloat(), top - owner.scrollY + translationY)
        point[0] = 0f
        point[1] = 0f
        anchor.mapPoints(point)
        val x = point[0].roundToInt()
        val y = point[1].roundToInt()
        if (source.hasDisplayList() && capturedGeneration == requestedGeneration &&
            captureX == x && captureY == y && captureWidth == width && captureHeight == height && captureMargin == margin) return true
        val w = width + 2 * margin
        val h = height + 2 * margin
        try {
            source.setPosition(0, 0, w, h)
            val canvas = source.beginRecording(w, h)
            try {
                capture {
                    canvas.drawColor(if (night) 0xFF111111.toInt() else 0xFFF7F7F7.toInt())
                    page.getLocationOnScreen(pagePosition)
                    canvas.translate((margin + pagePosition[0] - x - page.scrollX).toFloat(),
                        (margin + pagePosition[1] - y - page.scrollY).toFloat())
                    page.draw(canvas)
                }
            } finally { source.endRecording() }
        } catch (failure: Exception) {
            captureBroken = true
            source.discardDisplayList()
            updateLightSource()
            XposedCompat.log("[LiquidGlass] page capture disabled: ${failure.message}")
            XposedCompat.log(failure)
            return false
        }
        capturedGeneration = requestedGeneration
        captureX = x
        captureY = y
        captureWidth = width
        captureHeight = height
        captureMargin = margin
        captureRevision++
        return true
    }

    private val recordTabMaterial: (Canvas, Float, Int, FloatArray) -> Unit = { canvas, padding, factor, residual ->
        residual[0] = LiquidGlassGeom.samplingResidual(captureX + tabOffsetX - pagePosition[0], factor)
        residual[1] = LiquidGlassGeom.samplingResidual((captureY + tabInset - pagePosition[1]).toFloat(), factor)
        val save = canvas.save()
        canvas.translate(padding - captureMargin - tabOffsetX, padding - captureMargin - tabInset)
        canvas.translate(residual[0], residual[1])
        canvas.drawRenderNode(source)
        canvas.restoreToCount(save)
    }

    /** Both page and inner material keep their window scale behind the expanding lens. */
    fun drawPageForDroplet(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val margin = LiquidGlassStyle.margin(parameters(height))
        canvas.drawColor(if (night) 0xFF111111.toInt() else 0xFFF7F7F7.toInt())
        if (!ensureBackdrop(margin)) return
        val raw = canvas.save()
        canvas.translate((captureX - captureMargin).toFloat(), (captureY - captureMargin).toFloat())
        canvas.drawRenderNode(source)
        canvas.restoreToCount(raw)
    }

    /** Cached inner-height material; press transforms never request another page capture. */
    fun drawTabBackdrop(canvas: Canvas) {
        if (width <= 0 || height <= 0 || !source.hasDisplayList()) return
        val inset = selectionInset
        val innerHeight = selectionHeight
        if (innerHeight <= 0) return
        val p = parameters(innerHeight, hidden = true)
        if (tabCaptureRevision != captureRevision || tabOffsetX != translationX || tabInset != inset) {
            tabCaptureRevision = captureRevision
            tabOffsetX = translationX
            tabInset = inset
            tabRevision++
        }
        val hiddenRenderer = tabRenderer ?: LiquidGlassRenderer(drawHighlight = false).also { tabRenderer = it }
        val hidden = canvas.save()
        canvas.translate(captureX + translationX, (captureY + inset).toFloat())
        hiddenRenderer.draw(canvas, width, innerHeight, p, tabRevision, recordTabMaterial)
        canvas.restoreToCount(hidden)
    }

    fun clipTabBackdrop(canvas: Canvas) {
        val inset = selectionInset
        val x = captureX + translationX
        val y = (captureY + inset).toFloat()
        val h = selectionHeight.toFloat()
        if (h <= 0f) return
        val radius = min(width.toFloat(), h) / 2f
        tabClip.reset()
        tabClip.addRoundRect(x, y, x + width, y + h, radius, radius, Path.Direction.CW)
        canvas.clipPath(tabClip)
    }

    /** Capture-induced host notifications cannot open another capture window. */
    fun capture(block: () -> Unit) {
        val previous = isCapturing
        isCapturing = true
        try { block() } finally { isCapturing = previous }
    }

    private fun drawSurfaceTint(canvas: Canvas, tint: Int = LiquidGlassStyle.surfaceTint(night, translucent = false)) {
        paint.color = tint
        val split = inputWidth > 0 && actionsLeft < width
        val w = if (split) inputWidth.toFloat() else width.toFloat()
        val r = min(w, height.toFloat()) / 2f
        canvas.drawRoundRect(0f, 0f, w, height.toFloat(), r, r, paint)
        if (split) {
            val rr = min((width - actionsLeft).toFloat(), height.toFloat()) / 2f
            canvas.drawRoundRect(actionsLeft.toFloat(), 0f, width.toFloat(), height.toFloat(), rr, rr, paint)
        }
    }
}
