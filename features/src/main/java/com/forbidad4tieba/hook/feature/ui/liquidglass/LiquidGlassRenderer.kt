// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Native View adapter for the reference shaders; see app/src/main/assets/open-source-licenses.txt.
package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.annotation.TargetApi
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.os.Build
import com.forbidad4tieba.hook.core.XposedCompat
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** GPU-only background, Gaussian blur, refractive lens, surface and dual-light bloom, in that order. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal class LiquidGlassRenderer(
    liquidEffects: Boolean = true,
    selectionLens: Boolean = false,
    drawHighlight: Boolean = true,
) {
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // The reference brush dithers translucent shader output; omitting it washes out the depth lens.
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
        .apply { blendMode = BlendMode.PLUS }
    private val clip = Path()
    private val passes = arrayOfNulls<Pass>(2)
    private var lens: RuntimeShader? = null
    private var dispersionLens: RuntimeShader? = null
    private var bloom: Bloom? = null
    private var rightBloom: Bloom? = null
    private var glow: RuntimeShader? = null
    private var blurRadius = Float.NaN
    private var blurLevels = LiquidGlassBlur.Levels(0, 0, 0f)
    var isAvailable = true
        private set

    init {
        try {
            if (liquidEffects) {
                if (selectionLens) dispersionLens = RuntimeShader(LiquidGlassShaders.DISPERSION_LENS)
                else {
                    lens = RuntimeShader(LiquidGlassShaders.LENS)
                    glow = RuntimeShader(LiquidGlassShaders.GLOW)
                }
                if (drawHighlight) bloom = Bloom()
            }
        } catch (failure: RuntimeException) {
            disable(failure)
        }
    }

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        material: LiquidGlassStyle.Material,
        sourceRevision: Long,
        record: (Canvas, Float, Int, FloatArray) -> Unit,
    ): Boolean {
        if (!isAvailable || !canvas.isHardwareAccelerated || width <= 0 || height <= 0) return false
        try {
            if (blurRadius != material.blurRadius) {
                blurRadius = material.blurRadius
                blurLevels = LiquidGlassBlur.levels(blurRadius)
            }
            val levels = blurLevels
            val count = if (levels.low == levels.high) 1 else 2
            for (i in 0 until count) {
                val level = if (i == 0) levels.low else levels.high
                val pass = passes[i] ?: Pass().also { passes[i] = it }
                pass.prepare(width, height, level, material, sourceRevision, record)
            }
            val split = material.inputWidth > 0
            drawShape(canvas, 0, 0, if (split) material.inputWidth else width, height, material, levels)
            if (split) drawShape(canvas, 1, material.actionsLeft, width - material.actionsLeft,
                height, material, levels)
            return true
        } catch (failure: RuntimeException) {
            disable(failure)
            return false
        }
    }

    private fun drawShape(
        canvas: Canvas,
        index: Int,
        left: Int,
        width: Int,
        height: Int,
        p: LiquidGlassStyle.Material,
        levels: LiquidGlassBlur.Levels,
    ) {
        val save = canvas.save()
        canvas.translate(left.toFloat(), 0f)
        val radius = min(width, height) / 2f
        clip.reset()
        clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        val count = if (levels.low == levels.high) 1 else 2
        for (i in 0 until count) {
            val pass = passes[i] ?: continue
            val node = pass.lens(index, left, width, height, p)
            node.alpha = if (i == 0) 1f else levels.mix
            val layer = canvas.save()
            val origin = -(pass.padding / pass.scale).toInt() * pass.scale.toFloat()
            canvas.translate(origin - pass.residual[0], origin - pass.residual[1])
            canvas.scale(pass.scale.toFloat(), pass.scale.toFloat())
            canvas.drawRenderNode(node)
            canvas.restoreToCount(layer)
        }
        surfacePaint.color = p.tint
        canvas.drawPaint(surfacePaint)
        if (p.pressedTint ushr 24 != 0) {
            surfacePaint.color = p.pressedTint
            canvas.drawPaint(surfacePaint)
        }
        if (p.glowProgress > 0f) drawGlow(canvas, width, height, left, p)
        if (p.highlightAlpha > 0f) drawBloom(canvas, index, width, height, p)
        canvas.restoreToCount(save)
    }

    private fun drawGlow(canvas: Canvas, width: Int, height: Int, left: Int, p: LiquidGlassStyle.Material) {
        lightPaint.shader = null
        lightPaint.color = Color.WHITE
        lightPaint.alpha = (255f * 0.06f * p.glowProgress).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lightPaint)
        val shader = glow ?: return
        shader.setColorUniform("color", Color.argb((255f * 0.12f * p.glowProgress).roundToInt().coerceIn(0, 255), 255, 255, 255))
        shader.setFloatUniform("radius", min(width, height) * 1.2f)
        shader.setFloatUniform("position", (p.touchX - left).coerceIn(0f, width.toFloat()), height / 2f)
        lightPaint.alpha = 255
        lightPaint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lightPaint)
        lightPaint.shader = null
    }

    private fun drawBloom(canvas: Canvas, index: Int, width: Int, height: Int, p: LiquidGlassStyle.Material) {
        val first = bloom ?: return
        val current = if (index == 0) first else rightBloom ?: Bloom().also { rightBloom = it }
        current.prepare(width, height, p)
        lightPaint.color = Color.WHITE
        lightPaint.alpha = 255
        lightPaint.shader = current.shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lightPaint)
        lightPaint.shader = null
    }

    /** Each capsule retains its own shader uniforms; alternating widths must not invalidate a peer. */
    private class Bloom {
        val shader = RuntimeShader(LiquidGlassShaders.BLOOM).apply {
            setColorUniform("strokeColor", Color.WHITE)
            setFloatUniform("strokeAlphaMul", 0.12f)
            setColorUniform("lightColor1", Color.WHITE)
            setFloatUniform("lightIntensity1", 1f)
            val secondLength = sqrt(0.1f * 0.1f + 0.5f * 0.5f)
            setFloatUniform("lightDir2", 0f, 0.1f / secondLength, -0.5f / secondLength)
            setColorUniform("lightColor2", Color.WHITE)
            setFloatUniform("lightIntensity2", 0.4f)
        }
        private var width = 0
        private var height = 0
        private var density = Float.NaN
        private var alpha = Float.NaN
        private var angle = Float.NaN

        fun prepare(w: Int, h: Int, p: LiquidGlassStyle.Material) {
            if (width != w || height != h || density != p.density) {
                val halfW = w / 2f
                val halfH = h / 2f
                val radius = min(halfW, halfH)
                shader.setFloatUniform("halfView", halfW, halfH)
                shader.setFloatUniform("halfViewFloor", floor(halfW), floor(halfH))
                shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
                shader.setFloatUniform("strokeWidth", min(p.density, radius))
                val inner = 2f * p.density
                shader.setFloatUniform("innerBlurRadius", inner)
                shader.setFloatUniform("innerBlurRadiusSq", inner * inner)
                width = w
                height = h
                density = p.density
            }
            if (alpha != p.highlightAlpha) {
                shader.setFloatUniform("highlightAlpha", p.highlightAlpha)
                alpha = p.highlightAlpha
            }
            if (angle != p.lightAngle) {
                val firstLength = sqrt(1f + 0.05f * 0.05f)
                shader.setFloatUniform("lightDir1", cos(p.lightAngle) / firstLength, sin(p.lightAngle) / firstLength, -0.05f / firstLength)
                angle = p.lightAngle
            }
        }
    }

    private inner class Pass {
        val source = RenderNode("LiquidGlassFilteredBackdrop")
        val residual = FloatArray(2)
        private val shapes = arrayOf(RenderNode("LiquidGlassLeftLens"), RenderNode("LiquidGlassRightLens"))
        private val shapeKeys = arrayOfNulls<LensKey>(2)
        private val shapeOrigins = intArrayOf(-1, -1)
        private val cascade = arrayOfNulls<RenderNode>(2)
        private val downsample = arrayOfNulls<RuntimeShader>(2)
        private val downsampleKeys = arrayOfNulls<Triple<Int, Int, Int>>(2)
        private var width = 0
        private var height = 0
        private var revision = -1L
        private var filterRadius = Float.NaN
        private var filterSaturation = Float.NaN
        private var filterContrast = Float.NaN
        private var filterW = 0
        private var filterH = 0
        private var filterLevel = -1
        private var kernel = LiquidGlassBlur.Kernel(FloatArray(0), FloatArray(0))
        private var colorShader: RuntimeShader? = null
        private var horizontal: RuntimeShader? = null
        private var vertical: RuntimeShader? = null
        private var taps = -1
        var padding = 0f
            private set
        var scale = 1
            private set

        fun prepare(w: Int, h: Int, level: Int, p: LiquidGlassStyle.Material,
            frame: Long, record: (Canvas, Float, Int, FloatArray) -> Unit) {
            val nextScale = 1 shl level
            val nextPadding = maxOf(p.minimumPadding, if (p.blurRadius > 0f) 13f * nextScale else 0f)
            val texW = ((w + 2 * nextPadding).toInt() / nextScale).coerceAtLeast(1)
            val texH = ((h + 2 * nextPadding).toInt() / nextScale).coerceAtLeast(1)
            if (filterRadius != p.blurRadius || filterLevel != level) {
                kernel = LiquidGlassBlur.kernel(p.blurRadius, level)
            }
            if (filterRadius != p.blurRadius || filterSaturation != p.saturation || filterContrast != p.contrast ||
                filterW != texW || filterH != texH || filterLevel != level) {
                configureFilter(p, texW, texH)
                filterRadius = p.blurRadius
                filterSaturation = p.saturation
                filterContrast = p.contrast
                filterW = texW
                filterH = texH
                filterLevel = level
            }
            if (width != texW || height != texH || scale != nextScale || padding != nextPadding || revision != frame || !source.hasDisplayList()) {
                padding = nextPadding
                recordSource((w + 2 * padding).toInt(), (h + 2 * padding).toInt(), nextScale, record)
                width = texW
                height = texH
                scale = nextScale
                revision = frame
            }
        }

        private fun recordSource(fullW: Int, fullH: Int, factor: Int, record: (Canvas, Float, Int, FloatArray) -> Unit) {
            residual.fill(0f)
            if (factor <= 2) {
                recordNode(source, fullW / factor, fullH / factor) { canvas ->
                    canvas.scale(1f / factor, 1f / factor)
                    if (factor > 1) {
                        val phase = (padding / factor).toInt() * factor - padding
                        canvas.translate(phase, phase)
                    }
                    record(canvas, padding, factor, residual)
                }
                return
            }
            val half = cascade[0] ?: RenderNode("LiquidGlassHalfBackdrop").also { cascade[0] = it }
            recordNode(half, fullW / 2, fullH / 2) { canvas ->
                canvas.scale(0.5f, 0.5f)
                val phase = (padding / 2).toInt() * 2 - padding
                canvas.translate(phase, phase)
                record(canvas, padding, 2, residual)
            }
            if (factor == 16) {
                val eighth = cascade[1] ?: RenderNode("LiquidGlassEighthBackdrop").also { cascade[1] = it }
                downsampleStep(0, half, eighth, 4)
                downsampleStep(1, eighth, source, 2)
            } else downsampleStep(0, half, source, factor / 2)
        }

        private fun downsampleStep(slot: Int, input: RenderNode, output: RenderNode, factor: Int) {
            val previous = downsampleKeys[slot]
            if (previous == null || previous.first != input.width || previous.second != input.height || previous.third != factor) {
                val shader = if (previous?.third != factor) {
                    RuntimeShader(if (factor == 2) LiquidGlassShaders.DOWNSAMPLE_2X else LiquidGlassShaders.DOWNSAMPLE_4X)
                        .also { downsample[slot] = it }
                } else requireNotNull(downsample[slot])
                shader.setFloatUniform("maxCoord", input.width - 0.5f, input.height - 0.5f)
                input.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "child"))
                downsampleKeys[slot] = Triple(input.width, input.height, factor)
            }
            recordNode(output, input.width / factor, input.height / factor) { canvas ->
                canvas.scale(1f / factor, 1f / factor)
                canvas.drawRenderNode(input)
            }
        }

        private inline fun recordNode(node: RenderNode, w: Int, h: Int, draw: (Canvas) -> Unit) {
            node.setPosition(0, 0, w.coerceAtLeast(1), h.coerceAtLeast(1))
            val canvas = node.beginRecording(node.width, node.height)
            try { draw(canvas) } finally { node.endRecording() }
        }

        private fun configureFilter(p: LiquidGlassStyle.Material, w: Int, h: Int) {
            var effect: RenderEffect? = null
            val count = kernel.weights.size
            if (count > 0) {
                if (taps != count) {
                    horizontal = RuntimeShader(LiquidGlassShaders.blur(count))
                    vertical = RuntimeShader(LiquidGlassShaders.blur(count))
                    taps = count
                }
                for (axis in 0..1) {
                    val shader = (if (axis == 0) horizontal else vertical) ?: continue
                    val offsets = FloatArray(count * 2)
                    for (i in 0 until count) offsets[i * 2 + axis] = kernel.offsets[i]
                    shader.setFloatUniform("in_blurOffset", offsets)
                    shader.setFloatUniform("in_blurWeight", kernel.weights)
                    shader.setFloatUniform("in_maxCoord", w - 0.5f, h - 0.5f)
                    val next = RenderEffect.createRuntimeShaderEffect(shader, "child")
                    effect = if (effect == null) next else RenderEffect.createChainEffect(next, effect)
                }
            }
            // Restore a little color and contrast after blur, within this GPU pass chain.
            if (p.saturation != 1f || p.contrast != 1f) {
                val shader = colorShader ?: RuntimeShader(LiquidGlassShaders.COLOR_CONTROLS).also { colorShader = it }
                shader.setFloatUniform("in_brightness", 0f)
                shader.setFloatUniform("in_contrast", p.contrast)
                shader.setFloatUniform("in_saturation", p.saturation)
                val color = RenderEffect.createRuntimeShaderEffect(shader, "child")
                effect = if (effect == null) color else RenderEffect.createChainEffect(color, effect)
            }
            source.setRenderEffect(effect)
        }

        fun lens(index: Int, left: Int, w: Int, h: Int, p: LiquidGlassStyle.Material): RenderNode {
            val node = shapes[index]
            val texW = ((w + 2 * padding).toInt() / scale).coerceAtLeast(1)
            val texH = ((h + 2 * padding).toInt() / scale).coerceAtLeast(1)
            val resized = node.setPosition(0, 0, texW, texH)
            val origin = left * 32 + scale
            if (resized || !node.hasDisplayList() || shapeOrigins[index] != origin) {
                val canvas = node.beginRecording(texW, texH)
                try {
                    canvas.translate(-left.toFloat() / scale, 0f)
                    canvas.drawRenderNode(source)
                } finally { node.endRecording() }
                shapeOrigins[index] = origin
            }
            if (shapeKeys[index]?.matches(w, h, padding, scale, p) != true) {
                if (p.refractionHeight <= 0f || p.refractionAmount <= 0f) node.setRenderEffect(null)
                else {
                    val shader = (if (p.dispersion > 0f) dispersionLens else this@LiquidGlassRenderer.lens)
                        ?: return node
                    val radius = min(w, h) / (2f * scale)
                    shader.setFloatUniform("size", w.toFloat() / scale, h.toFloat() / scale)
                    shader.setFloatUniform("offset", -padding / scale, -padding / scale)
                    shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
                    shader.setFloatUniform("refractionHeight", p.refractionHeight / scale)
                    shader.setFloatUniform("refractionAmount", -p.refractionAmount / scale)
                    shader.setFloatUniform("depthEffect", if (p.depthEffect) 1f else 0f)
                    if (p.dispersion > 0f) shader.setFloatUniform("chromaticAberration", p.dispersion)
                    node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
                }
                shapeKeys[index] = LensKey(w, h, padding, scale, p.refractionHeight, p.refractionAmount, p.dispersion, p.depthEffect)
            }
            return node
        }

        fun release() {
            source.discardDisplayList()
            shapes.forEach { it.discardDisplayList() }
            cascade.forEach { it?.discardDisplayList() }
            revision = -1L
        }
    }

    private data class LensKey(val width: Int, val height: Int, val margin: Float, val scale: Int,
        val refractionHeight: Float, val amount: Float, val dispersion: Float, val depth: Boolean) {
        fun matches(w: Int, h: Int, padding: Float, factor: Int, p: LiquidGlassStyle.Material): Boolean =
            width == w && height == h && margin == padding && scale == factor &&
                refractionHeight == p.refractionHeight && amount == p.refractionAmount &&
                dispersion == p.dispersion && depth == p.depthEffect
    }

    private fun disable(failure: RuntimeException) {
        isAvailable = false
        release()
        XposedCompat.log("[LiquidGlass] GPU renderer unavailable: ${failure.message}")
        XposedCompat.log(failure)
    }

    fun release() { passes.forEach { it?.release() } }
}
