package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.annotation.TargetApi
import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RecordingCanvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The liquid-glass surface drawn behind the host bottom tab bar.
 *
 * Ported from liuran001/WeChat-LiquidGlass (MIT), `LiquidGlassPanel`, which in turn reproduces the
 * KernelSU manager's floating bar effect stack:
 *
 * ```
 * vibrancy()                       // colorControls(saturation = 1.5)
 * blur(4.dp, 4.dp)
 * lens(refractionHeight = 24.dp, refractionAmount = 24.dp)
 * onDrawSurface = { drawRect(surfaceContainer.copy(0.4f)) }
 * ```
 *
 * The backdrop is captured into a [RenderNode] and run through saturation -> blur -> refraction,
 * then the surface wash and edge highlight are painted over it. The refraction shader is Kyant0's
 * rounded-rect SDF lens (Apache-2.0), the same one KernelSU vendors. Its defining property is the
 * early-out: anything further than `refractionHeight` from the edge passes through untouched, so
 * only a band around the rim bends and the middle stays a plain blurred view of what is behind it.
 *
 * Requires AGSL ([Build.VERSION_CODES.TIRAMISU]); the caller must not instantiate this below 33.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal class LiquidGlassPanel(
    context: Context,
    backdrop: ViewGroup,
    private val density: Float,
    night: Boolean,
    private val config: BottomTabLiquidGlassConfig = BottomTabLiquidGlassConfig.DEFAULT,
) : View(context) {

    companion object {
        /** KernelSU: lens(refractionHeight = 24.dp, refractionAmount = 24.dp). */
        const val REFRACTION_DP = 24f
        /** KernelSU: blur(4.dp, 4.dp). */
        const val BLUR_DP = 4f
        /** KernelSU: vibrancy() -> colorControls(saturation = 1.5f). */
        const val SATURATION = 1.5f

        const val SDF_SOURCE = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y; else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x; else return radii.w;
    }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

        const val LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
""" + SDF_SOURCE + """
float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) { return content.eval(coord); }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
            + depthEffect * normalize(centeredCoord));
    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

        /**
         * KernelSU's InteractiveHighlight bloom: a radial falloff that tracks the droplet.
         *
         * The alpha is carried as a plain float rather than a `layout(color)` uniform because an
         * AGSL shader must return a premultiplied colour - passing an un-premultiplied white would
         * make the peak add rgb 1.0 and blow the capsule out to solid white.
         */
        const val HIGHLIGHT_SHADER = """
uniform float alpha;
uniform float radius;
uniform float2 position;
half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    half a = half(alpha * intensity);
    return half4(a, a, a, a);
}
"""
    }

    private val backdropRef = WeakReference(backdrop)

    /** The lens samples outside its own bounds, so the capture is grown by the refraction amount. */
    private val pad: Int = (40f * density).roundToInt()

    private class Capture(name: String) {
        val node = RenderNode(name)
        var generation = -1L
        var width = 0
        var height = 0
        var x = 0
        var y = 0
        var scaleX = 0f
        var scaleY = 0f
        var effect: RenderEffect? = null
        var effectWidth = 0
        var effectHeight = 0
    }

    private val capture = Capture("tbLiquidGlass")

    /** Separate display list used when the droplet reuses this blurred surface. */
    private val embeddedCapture = Capture("tbLiquidGlassEmbedded")
    /** All three materials sample this bounded page layer instead of replaying the host three times. */
    private val sourceCapture = Capture("tbLiquidGlassSource").apply {
        node.setUseCompositingLayer(true, null)
    }
    private var backdropGeneration = 0L

    // Reused every frame: onDraw runs on each traversal and allocating here would churn the heap.
    private val selfPos = IntArray(2)
    private val srcPos = IntArray(2)
    private val captureScale = FloatArray(2)
    private val visible = Rect()

    private val saturate: RenderEffect
    private var lens: RuntimeShader? = null

    private var baseColor = 0
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val embeddedClip = Path()
    private val embeddedInset = (LiquidGlassBarMetrics.DROPLET_INSET_DP * density).roundToInt()
    private var lighting: LiquidGlassLighting? = null
    private var tilt: LiquidGlassTilt? = null
    private var lightListener: ((Float) -> Unit)? = null
    private var lightAngle = LiquidGlassTilt.REST_ANGLE
    private val shadowNode = RenderNode("tbLiquidGlassShadow")
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val opacityPaint = Paint()
    private val shadowPad = (30f * density).roundToInt()
    private val shadowEffect = if (config.shadowEnabled) {
        RenderEffect.createBlurEffect(10f * density, 10f * density, Shader.TileMode.DECAL)
    } else null
    private var shadowDirty = true
    private var containerAlpha = 1f

    private var highlightShader: RuntimeShader? = null
    private val bloom = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.PLUS }
    private val washPlus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        blendMode = BlendMode.PLUS
    }
    private var interaction = 0f
    private var interactionX = 0f

    /** Press progress and the droplet's centre, in this view's coordinates. */
    fun setInteraction(progress: Float, centreX: Float) {
        if (interaction != progress || interactionX != centreX) {
            interaction = progress
            interactionX = centreX
            invalidate()
        }
    }

    fun setLightListener(listener: (Float) -> Unit) {
        lightListener = listener
        listener(lightAngle)
    }

    /** Content changes request a capture; a tilt-only redraw can reuse the existing backdrop. */
    fun refreshBackdrop() {
        backdropGeneration++
        invalidate()
    }

    /** The explicit layer includes the outer shadow while the host fades the bar. */
    fun setContainerAlpha(value: Float): Boolean {
        val next = value.coerceIn(0f, 1f)
        val changed = containerAlpha != next
        if (changed) {
            containerAlpha = next
            invalidate()
        }
        updateTilt()
        return changed
    }

    private fun updateTilt() {
        tilt?.setActive(isAttachedToWindow && isShown && windowVisibility == VISIBLE &&
            hasWindowFocus() && containerAlpha > 0f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateTilt()
        refreshBackdrop()
    }

    override fun onDetachedFromWindow() {
        tilt?.setActive(false)
        capture.node.discardDisplayList()
        embeddedCapture.node.discardDisplayList()
        sourceCapture.node.discardDisplayList()
        capture.generation = -1L
        embeddedCapture.generation = -1L
        sourceCapture.generation = -1L
        shadowNode.discardDisplayList()
        shadowDirty = true
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateTilt()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateTilt()
        if (visibility == VISIBLE) refreshBackdrop()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateTilt()
        if (hasWindowFocus) refreshBackdrop()
    }

    /** Cleared permanently once the shader pipeline throws; the panel then draws the flat wash. */
    var isSupported: Boolean = false
        private set

    /**
     * Set while the backdrop is being captured.
     *
     * Re-drawing the host's pages into the capture node can make them post layout and scroll
     * callbacks of their own. Those are echoes of this draw, not real content changes, and treating
     * them as such re-arms the redraw window forever - so the observers ignore them.
     */
    @Volatile
    var isCapturing: Boolean = false
        private set

    init {
        val matrix = ColorMatrix().apply { setSaturation(SATURATION) }
        saturate = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))

        isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (isSupported) {
            try {
                if (config.refractionEnabled) lens = RuntimeShader(LENS_SHADER)
                if (config.highlightEnabled) {
                    highlightShader = RuntimeShader(HIGHLIGHT_SHADER)
                    lighting = LiquidGlassLighting(density, (-PI / 4).toFloat())
                    if (config.tiltEnabled) {
                        tilt = LiquidGlassTilt(context, { display?.rotation ?: 0 }) { angle ->
                            lightAngle = angle
                            invalidate()
                            lightListener?.invoke(angle)
                        }
                    }
                }
            } catch (t: Throwable) {
                isSupported = false
                XposedCompat.log("[LiquidGlassPanel] lens shader rejected: ${t.message}")
                XposedCompat.log(t)
            }
        }
        setTheme(night)
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    /** KernelSU: containerColor = surfaceContainer.copy(0.4f). */
    fun setTheme(night: Boolean) {
        baseColor = if (night) 0xFF111111.toInt() else 0xFFF7F7F7.toInt()
        surfacePaint.color = if (night) 0x662C2C2E else 0x66F2F2F7
        shadowPaint.color = if (night) 0x33000000 else 0x1A000000
        shadowDirty = true
        refreshBackdrop()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clip.reset()
        val r = min(w, h) * 0.5f
        clip.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, Path.Direction.CW)
        embeddedClip.reset()
        val innerHeight = (h - embeddedInset * 2).coerceAtLeast(1).toFloat()
        val innerRadius = min(w.toFloat(), innerHeight) * 0.5f
        embeddedClip.addRoundRect(0f, 0f, w.toFloat(), innerHeight,
            innerRadius, innerRadius, Path.Direction.CW)
        shadowDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0 || containerAlpha <= 0f) return
        val save = if (containerAlpha < 1f) {
            opacityPaint.alpha = (containerAlpha * 255f).roundToInt()
            canvas.saveLayer(-shadowPad.toFloat(), -shadowPad.toFloat(),
                (w + shadowPad).toFloat(), (h + shadowPad).toFloat(), opacityPaint)
        } else canvas.save()
        drawOuterShadow(canvas, w, h)
        LiquidGlassGeom.cumulativeScale(this, captureScale)
        drawPanel(canvas, w, h, min(w, h) * 0.5f, capture, captureScale[0], captureScale[1], clip, 0)
        lighting?.draw(canvas, w, h, lightAngle, 0.75f)
        canvas.restoreToCount(save)
    }

    /** KernelSU's second backdrop is 56dp high inside the 64dp outer surface. */
    fun drawEmbedded(canvas: Canvas) {
        val w = width
        val h = height - embeddedInset * 2
        if (w <= 0 || h <= 0) return
        val save = canvas.save()
        canvas.translate(0f, embeddedInset.toFloat())
        drawPanel(canvas, w, h, min(w, h) * 0.5f, embeddedCapture, 1f, 1f, embeddedClip, embeddedInset)
        canvas.restoreToCount(save)
    }

    private fun drawOuterShadow(canvas: Canvas, w: Int, h: Int) {
        if (!config.shadowEnabled || !canvas.isHardwareAccelerated) return
        if (shadowDirty || !shadowNode.hasDisplayList()) {
            val nw = w + shadowPad * 2
            val nh = h + shadowPad * 2
            shadowNode.setPosition(0, 0, nw, nh)
            val c = shadowNode.beginRecording(nw, nh)
            c.drawRoundRect(shadowPad.toFloat(), shadowPad.toFloat(),
                (shadowPad + w).toFloat(), (shadowPad + h).toFloat(), h * 0.5f, h * 0.5f, shadowPaint)
            shadowNode.endRecording()
            shadowNode.setRenderEffect(shadowEffect)
            shadowDirty = false
        }
        val save = canvas.save()
        canvas.translate(-shadowPad.toFloat(), -shadowPad.toFloat())
        canvas.drawRenderNode(shadowNode)
        canvas.restoreToCount(save)
    }

    private fun drawPanel(
        canvas: Canvas,
        w: Int,
        h: Int,
        radius: Float,
        target: Capture,
        captureScaleX: Float,
        captureScaleY: Float,
        shape: Path,
        originY: Int,
    ) {
        if (isSupported && canvas.isHardwareAccelerated) {
            isCapturing = true
            try {
                drawGlass(canvas, w, h, radius, target, captureScaleX, captureScaleY, shape, originY)
            } catch (t: Throwable) {
                isSupported = false
                XposedCompat.log("[LiquidGlassPanel] glass draw failed, flat fallback: ${t.message}")
                XposedCompat.log(t)
            } finally {
                isCapturing = false
            }
        }

        // Surface wash and rim highlight sit on top of the refracted backdrop - this is what
        // carries legibility, not a heavy blur.
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radius, radius, surfacePaint)
        drawInteractiveHighlight(canvas, w, h, shape)
    }

    /**
     * KernelSU's InteractiveHighlight, drawn over the pill while the droplet is held.
     *
     * A white wash plus a radial bloom that tracks the droplet, both in Plus blend, fading in with
     * press progress. KernelSU writes this as `color * intensity` over a `layout(color)` uniform;
     * that uniform arrives un-premultiplied, while an AGSL shader has to return a premultiplied
     * colour, so carrying the alpha as a plain float is what keeps the peak at the intended 12%
     * instead of blowing out to solid white.
     */
    private fun drawInteractiveHighlight(canvas: Canvas, w: Int, h: Int, shape: Path) {
        val p = interaction
        val shader = highlightShader
        if (p <= 0.01f || shader == null || !canvas.isHardwareAccelerated) return
        val save = canvas.save()
        canvas.clipPath(shape)
        // drawRect(White.copy(0.06f * progress), blendMode = Plus). Paint colours are premultiplied
        // by Skia, so this one already lands at the 6% KernelSU asks for.
        washPlus.alpha = (0x0F * p).roundToInt()
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), washPlus)

        // KernelSU: White.copy(0.12f * progress), radius = size.minDimension * 1.2
        shader.setFloatUniform("alpha", 0.12f * p)
        shader.setFloatUniform("radius", min(w, h) * 1.2f)
        shader.setFloatUniform("position", interactionX.coerceIn(0f, w.toFloat()), h * 0.5f)
        bloom.shader = shader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bloom)
        canvas.restoreToCount(save)
    }

    /** Draws the unfiltered page band in another material's unscaled screen coordinates. */
    fun drawBackdrop(canvas: Canvas, screenX: Int, screenY: Int, targetPad: Int): Boolean {
        if (!isSupported || !canvas.isHardwareAccelerated) return false
        val source = recordBackdrop() ?: return false
        drawSource(canvas, source, screenX, screenY, targetPad)
        return true
    }

    private fun drawSource(canvas: Canvas, source: Capture, screenX: Int, screenY: Int, targetPad: Int) {
        val save = canvas.save()
        canvas.translate((source.x - pad - screenX + targetPad).toFloat(),
            (source.y - pad - screenY + targetPad).toFloat())
        canvas.drawRenderNode(source.node)
        canvas.restoreToCount(save)
    }

    /** A RenderNode may lose its display list when HWUI trims a background window. */
    private fun recordBackdrop(): Capture? {
        val pager = backdropRef.get() ?: return null
        val w = width
        val h = height
        if (w <= 0 || h <= 0 || pager.width <= 0) return null
        LiquidGlassGeom.unscaledScreenPos(this, selfPos)
        val source = sourceCapture
        val x = selfPos[0]
        val y = selfPos[1]
        if (source.node.hasDisplayList() && source.generation == backdropGeneration &&
            source.width == w && source.height == h && source.x == x && source.y == y
        ) return source

        val nw = w + pad * 2
        val nh = h + pad * 2
        val node = source.node
        node.setPosition(0, 0, nw, nh)
        val rc = node.beginRecording(nw, nh)
        val wasCapturing = isCapturing
        isCapturing = true
        try {
            rc.drawColor(baseColor)
            var drewAny = false
            for (i in 0 until pager.childCount) {
                val page = pager.getChildAt(i) ?: continue
                if (page.visibility != VISIBLE ||
                    !page.getGlobalVisibleRect(visible) || visible.isEmpty
                ) continue
                page.getLocationOnScreen(srcPos)
                val dx = (pad - (x - srcPos[0])).toFloat()
                val dy = (pad - (y - srcPos[1])).toFloat()
                val save = rc.save()
                rc.translate(dx, dy)
                rc.clipRect(-dx, -dy, -dx + nw, -dy + nh)
                page.draw(rc)
                rc.restoreToCount(save)
                drewAny = true
            }
            if (!drewAny) {
                pager.getLocationOnScreen(srcPos)
                rc.translate((pad - (x - srcPos[0])).toFloat(), (pad - (y - srcPos[1])).toFloat())
                pager.draw(rc)
            }
        } finally {
            try {
                node.endRecording()
            } finally {
                isCapturing = wasCapturing
            }
        }
        source.generation = backdropGeneration
        source.width = w
        source.height = h
        source.x = x
        source.y = y
        return source
    }

    private fun drawGlass(
        canvas: Canvas,
        w: Int,
        h: Int,
        radius: Float,
        target: Capture,
        captureScaleX: Float,
        captureScaleY: Float,
        shape: Path,
        originY: Int,
    ) {
        val shader = lens
        val source = recordBackdrop() ?: return
        val x = source.x
        val y = source.y + originY
        val nw = w + pad * 2
        val nh = h + pad * 2
        val node = target.node
        val needsCapture = !node.hasDisplayList() || target.generation != backdropGeneration ||
            target.width != w || target.height != h || target.x != x || target.y != y ||
            target.scaleX != captureScaleX || target.scaleY != captureScaleY
        if (needsCapture) {
            node.setPosition(0, 0, nw, nh)
            val rc: RecordingCanvas = node.beginRecording(nw, nh)
            try {
                if (abs(captureScaleX - 1f) > 0.001f || abs(captureScaleY - 1f) > 0.001f) {
                    rc.scale(1f / captureScaleX, 1f / captureScaleY, nw * 0.5f, nh * 0.5f)
                }
                drawSource(rc, source, x, y, pad)
            } finally {
                node.endRecording()
            }
            target.generation = backdropGeneration
            target.width = w
            target.height = h
            target.x = x
            target.y = y
            target.scaleX = captureScaleX
            target.scaleY = captureScaleY
        }

        // Outer and embedded surfaces have different heights. Cache each effect separately so
        // switching between them does not rebuild both native effect chains on every frame.
        var effect = target.effect
        if (effect == null || target.effectWidth != w || target.effectHeight != h) {
            val blur = BLUR_DP * density
            effect = if (config.blurEnabled) {
                RenderEffect.createBlurEffect(blur, blur, saturate, Shader.TileMode.CLAMP)
            } else saturate
            if (shader != null) {
                shader.setFloatUniform("size", w.toFloat(), h.toFloat())
                shader.setFloatUniform("offset", -pad.toFloat(), -pad.toFloat())
                shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
                shader.setFloatUniform("refractionHeight", REFRACTION_DP * density)
                shader.setFloatUniform("refractionAmount", -REFRACTION_DP * density)
                shader.setFloatUniform("depthEffect", 0f)
                effect = RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(shader, "content"), effect,
                )
            }
            target.effect = effect
            target.effectWidth = w
            target.effectHeight = h
        }
        node.setRenderEffect(effect)

        val save = canvas.save()
        canvas.clipPath(shape)
        canvas.translate(-pad.toFloat(), -pad.toFloat())
        canvas.drawRenderNode(node)
        canvas.restoreToCount(save)
    }
}
