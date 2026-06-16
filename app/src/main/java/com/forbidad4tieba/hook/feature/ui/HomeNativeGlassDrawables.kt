package com.forbidad4tieba.hook.feature.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val CARD_RUNTIME_TINT_ALPHA_SCALE = 1.5f
private const val CARD_ZERO_TINT_DARK_MODE_PERCENT = -20
private const val CARD_ZERO_TINT_LIGHT_MODE_PERCENT = 20
private const val APPLE_NOISE_ALPHA = 52
private const val APPLE_STROKE_ALPHA = 56
private const val APPLE_EDGE_HIGHLIGHT_ALPHA = 86
private const val APPLE_INNER_SHADOW_ALPHA = 22
private const val APPLE_AMBIENT_SHADOW_ALPHA = 18
private const val NOISE_TEXTURE_SIZE = 64

internal class CenterCropBitmapDrawable(
    private val bitmap: Bitmap,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val src = Rect()
    private val dst = Rect()

    override fun draw(canvas: Canvas) {
        dst.set(bounds)
        val viewWidth = dst.width()
        val viewHeight = dst.height()
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return

        if (bitmapWidth.toLong() * viewHeight > viewWidth.toLong() * bitmapHeight) {
            val srcWidth = (bitmapHeight.toLong() * viewWidth / viewHeight)
                .toInt()
                .coerceIn(1, bitmapWidth)
            val left = (bitmapWidth - srcWidth) / 2
            src.set(left, 0, left + srcWidth, bitmapHeight)
        } else {
            val srcHeight = (bitmapWidth.toLong() * viewHeight / viewWidth)
                .toInt()
                .coerceIn(1, bitmapHeight)
            val top = (bitmapHeight - srcHeight) / 2
            src.set(0, top, bitmapWidth, top + srcHeight)
        }
        canvas.drawBitmap(bitmap, src, dst, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = bitmap.width

    override fun getIntrinsicHeight(): Int = bitmap.height
}

internal class EnterForumCapsuleTintDrawable(
    val appliedColor: Int,
    val appliedRadius: Float,
) : GradientDrawable()

internal class PbReplyBarInputCapsuleTintDrawable(
    val appliedColor: Int,
    val appliedRadius: Float,
) : GradientDrawable()

internal class SubPbInputCapsuleTintDrawable(
    val appliedColor: Int,
    val appliedRadius: Float,
) : GradientDrawable()

internal class PbCommentGlassBackgroundDrawable(
    private val bitmap: Bitmap,
    tintAlphaPercent: Int,
) : Drawable() {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val overlayPaint = Paint()
    private val src = Rect()
    private val dst = Rect()
    private val baseTintAlpha = tintAlphaPercent.coerceIn(
        ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
    )
    private var drawableAlpha = 255

    override fun draw(canvas: Canvas) {
        dst.set(bounds)
        val viewWidth = dst.width()
        val viewHeight = dst.height()
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return

        if (bitmapWidth.toLong() * viewHeight > viewWidth.toLong() * bitmapHeight) {
            val srcWidth = (bitmapHeight.toLong() * viewWidth / viewHeight)
                .toInt()
                .coerceIn(1, bitmapWidth)
            val left = (bitmapWidth - srcWidth) / 2
            src.set(left, 0, left + srcWidth, bitmapHeight)
        } else {
            val srcHeight = (bitmapWidth.toLong() * viewHeight / viewWidth)
                .toInt()
                .coerceIn(1, bitmapHeight)
            val top = (bitmapHeight - srcHeight) / 2
            src.set(0, top, bitmapWidth, top + srcHeight)
        }

        bitmapPaint.alpha = drawableAlpha
        canvas.drawBitmap(bitmap, src, dst, bitmapPaint)
        val overlayAlpha = overlayAlpha()
        if (overlayAlpha <= 0) return
        overlayPaint.color = Color.argb(overlayAlpha, 255, 255, 255)
        canvas.drawRect(dst, overlayPaint)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bitmapPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = bitmap.width

    override fun getIntrinsicHeight(): Int = bitmap.height

    private fun overlayAlpha(): Int {
        val alpha = 0
        return (alpha * drawableAlpha / 255f).toInt().coerceIn(0, 255)
    }
}

internal class CardGlassDrawable(
    target: View,
    page: View,
    private val bitmap: Bitmap,
    private val radius: Float,
    tintAlphaPercent: Int,
    private val extraTintEnabled: Boolean,
    private val darkMode: Boolean,
    blurPercent: Int,
    private val strokeEnabled: Boolean,
    shadowStrengthPercent: Int,
) : Drawable() {
    private val targetRef = WeakReference(target)
    private val pageRef = WeakReference(page)
    private val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val noiseShader = BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val shaderMatrix = Matrix()
    private val noiseMatrix = Matrix()
    private val baseTintAlpha = tintAlphaPercent.coerceIn(
        ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
    )
    private val cardBlurPercent = blurPercent.coerceIn(
        ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
        ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
    )
    private val shadowStrengthPercent = shadowStrengthPercent.coerceIn(
        ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
        ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
    )
    private val runtimeTintAlphaPercent = runtimeTintAlphaPercent(baseTintAlpha, darkMode)
    private val materialIntensity = cardBlurPercent / 100f
    private val strokeWidthPx = (1.0f + materialIntensity * 0.8f).coerceIn(1f, 1.8f)
    private val ambientShadowStrokeWidthPx = (2.2f + materialIntensity * 2.4f).coerceIn(2f, 4.6f)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        this.shader = this@CardGlassDrawable.shader
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val edgeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ambientShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        shader = noiseShader
    }
    private val rect = RectF()
    private val insetRect = RectF()
    private val targetLocation = IntArray(2)
    private val pageLocation = IntArray(2)
    private var lastPageWidth = -1
    private var lastPageHeight = -1
    private var lastRelativeX = Int.MIN_VALUE
    private var lastRelativeY = Int.MIN_VALUE
    private var lastNoiseLeft = Float.NaN
    private var lastNoiseTop = Float.NaN
    private var cachedSoftShadowShader: LinearGradient? = null
    private var softShadowTop = Float.NaN
    private var softShadowBottom = Float.NaN
    private var softShadowAlpha = -1
    private var cachedAmbientShadowShader: LinearGradient? = null
    private var ambientShadowTop = Float.NaN
    private var ambientShadowBottom = Float.NaN
    private var ambientShadowAlpha = -1
    private var ambientShadowStrokeWidth = Float.NaN
    private var cachedEdgeHighlightShader: LinearGradient? = null
    private var edgeHighlightTop = Float.NaN
    private var edgeHighlightBottom = Float.NaN
    private var edgeHighlightAlpha = -1
    private var edgeHighlightStrokeWidth = Float.NaN
    private var drawableAlpha = 255
    private var pressTintEnabled = false

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        if (rect.width() <= 0f || rect.height() <= 0f) return

        val target = targetRef.get()
        val page = pageRef.get()
        if (target != null && page != null && page.width > 0 && page.height > 0) {
            updateBitmapShader(target, page)
            canvas.drawRoundRect(rect, radius, radius, bitmapPaint)
            drawMaterialOverlay(canvas)
            drawNoise(canvas)
            if (this@CardGlassDrawable.shadowStrengthPercent > 0) {
                drawSoftShadow(canvas)
                drawAmbientShadow(canvas)
            }
            if (strokeEnabled) {
                drawStroke(canvas)
                drawEdgeHighlight(canvas)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        bitmapPaint.alpha = drawableAlpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bitmapPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun setPressedTintEnabled(pressed: Boolean) {
        if (pressTintEnabled == pressed) return
        pressTintEnabled = pressed
        invalidateSelf()
    }

    fun matches(
        page: View,
        bitmap: Bitmap,
        radius: Float,
        tintAlphaPercent: Int,
        extraTintEnabled: Boolean,
        darkMode: Boolean,
        blurPercent: Int,
        strokeEnabled: Boolean,
        shadowStrengthPercent: Int,
    ): Boolean {
        return pageRef.get() === page &&
            this.bitmap === bitmap &&
            this.radius == radius &&
            baseTintAlpha == tintAlphaPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ) &&
            this.extraTintEnabled == extraTintEnabled &&
            this.darkMode == darkMode &&
            cardBlurPercent == blurPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ) &&
            this.strokeEnabled == strokeEnabled &&
            this.shadowStrengthPercent == shadowStrengthPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            )
    }

    private fun updateBitmapShader(target: View, page: View) {
        target.getLocationInWindow(targetLocation)
        page.getLocationInWindow(pageLocation)
        val relativeX = targetLocation[0] - pageLocation[0]
        val relativeY = targetLocation[1] - pageLocation[1]
        val pageWidth = page.width
        val pageHeight = page.height
        if (
            pageWidth == lastPageWidth &&
            pageHeight == lastPageHeight &&
            relativeX == lastRelativeX &&
            relativeY == lastRelativeY
        ) {
            return
        }
        val scale = max(
            pageWidth.toFloat() / bitmap.width.toFloat(),
            pageHeight.toFloat() / bitmap.height.toFloat(),
        )
        val dx = (pageWidth - bitmap.width * scale) * 0.5f - relativeX
        val dy = (pageHeight - bitmap.height * scale) * 0.5f - relativeY
        shaderMatrix.reset()
        shaderMatrix.setScale(scale, scale)
        shaderMatrix.postTranslate(dx, dy)
        shader.setLocalMatrix(shaderMatrix)
        lastPageWidth = pageWidth
        lastPageHeight = pageHeight
        lastRelativeX = relativeX
        lastRelativeY = relativeY
    }

    private fun drawMaterialOverlay(canvas: Canvas) {
        val overlayPercent = overlayTintAlphaPercent()
        val overlayAlpha = overlayAlpha(overlayPercent)
        if (overlayAlpha <= 0) return
        val materialOverlayRgb = if (overlayPercent < 0) Color.BLACK else Color.WHITE
        overlayPaint.color = Color.argb(
            overlayAlpha,
            Color.red(materialOverlayRgb),
            Color.green(materialOverlayRgb),
            Color.blue(materialOverlayRgb),
        )
        canvas.drawRoundRect(rect, radius, radius, overlayPaint)
    }

    private fun drawSoftShadow(canvas: Canvas) {
        val alpha = shadowAlpha(APPLE_INNER_SHADOW_ALPHA)
        if (alpha <= 0) return
        shadowPaint.shader = softShadowShader(alpha)
        canvas.drawRoundRect(rect, radius, radius, shadowPaint)
    }

    private fun drawAmbientShadow(canvas: Canvas) {
        val alpha = shadowAlpha(APPLE_AMBIENT_SHADOW_ALPHA)
        if (alpha <= 0) return
        val strokeWidth = ambientShadowStrokeWidthPx
        insetRect.set(rect)
        val inset = strokeWidth * 0.5f
        insetRect.inset(inset, inset)
        ambientShadowPaint.strokeWidth = strokeWidth
        ambientShadowPaint.shader = ambientShadowShader(alpha, strokeWidth)
        val insetRadius = (radius - inset).coerceAtLeast(0f)
        canvas.drawRoundRect(insetRect, insetRadius, insetRadius, ambientShadowPaint)
    }

    private fun drawNoise(canvas: Canvas) {
        val alpha = materialAlpha(APPLE_NOISE_ALPHA)
        if (alpha <= 0) return
        if (rect.left != lastNoiseLeft || rect.top != lastNoiseTop) {
            noiseMatrix.reset()
            noiseMatrix.setTranslate(rect.left, rect.top)
            noiseShader.setLocalMatrix(noiseMatrix)
            lastNoiseLeft = rect.left
            lastNoiseTop = rect.top
        }
        noisePaint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, noisePaint)
    }

    private fun drawStroke(canvas: Canvas) {
        val strokeWidth = strokeWidth()
        if (strokeWidth <= 0f) return
        val alpha = materialAlpha(APPLE_STROKE_ALPHA)
        if (alpha <= 0) return
        insetRect.set(rect)
        val inset = strokeWidth * 0.5f
        insetRect.inset(inset, inset)
        strokePaint.strokeWidth = strokeWidth
        strokePaint.shader = null
        strokePaint.color = Color.argb(alpha, 255, 255, 255)
        val insetRadius = (radius - inset).coerceAtLeast(0f)
        canvas.drawRoundRect(insetRect, insetRadius, insetRadius, strokePaint)
    }

    private fun drawEdgeHighlight(canvas: Canvas) {
        val strokeWidth = strokeWidth()
        if (strokeWidth <= 0f) return
        val alpha = materialAlpha(APPLE_EDGE_HIGHLIGHT_ALPHA)
        if (alpha <= 0) return
        insetRect.set(rect)
        val inset = strokeWidth * 0.5f
        insetRect.inset(inset, inset)
        edgeHighlightPaint.strokeWidth = strokeWidth
        edgeHighlightPaint.shader = edgeHighlightShader(alpha, strokeWidth)
        val insetRadius = (radius - inset).coerceAtLeast(0f)
        canvas.drawRoundRect(insetRect, insetRadius, insetRadius, edgeHighlightPaint)
    }

    private fun overlayTintAlphaPercent(): Int {
        var layerCount = 0
        if (extraTintEnabled) layerCount++
        if (pressTintEnabled) layerCount++
        if (layerCount <= 0) return 0
        return (runtimeTintAlphaPercent * layerCount).coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
    }

    private fun overlayAlpha(overlayPercent: Int): Int {
        val alpha = abs(overlayPercent).coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        ) * 255 / 100f
        return (alpha * drawableAlpha / 255f).toInt().coerceIn(0, 255)
    }

    private fun runtimeTintAlphaPercent(basePercent: Int, darkMode: Boolean): Int {
        val normalized = basePercent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
        val percent = if (normalized == ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT) {
            if (darkMode) CARD_ZERO_TINT_DARK_MODE_PERCENT else CARD_ZERO_TINT_LIGHT_MODE_PERCENT
        } else {
            (normalized * CARD_RUNTIME_TINT_ALPHA_SCALE).roundToInt()
        }
        return percent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
    }

    private fun materialAlpha(appleAlpha: Int): Int {
        return (appleAlpha * drawableAlpha / 255f).toInt().coerceIn(0, 255)
    }

    private fun shadowAlpha(appleAlpha: Int): Int {
        return (appleAlpha * shadowStrengthPercent / 100f * drawableAlpha / 255f)
            .toInt()
            .coerceIn(0, 255)
    }

    private fun strokeWidth(): Float {
        return strokeWidthPx
    }

    private fun softShadowShader(alpha: Int): LinearGradient {
        val cached = cachedSoftShadowShader
        if (
            cached != null &&
            softShadowAlpha == alpha &&
            softShadowTop == rect.top &&
            softShadowBottom == rect.bottom
        ) {
            return cached
        }
        return LinearGradient(
            0f,
            rect.top,
            0f,
            rect.bottom,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(alpha, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        ).also {
            cachedSoftShadowShader = it
            softShadowAlpha = alpha
            softShadowTop = rect.top
            softShadowBottom = rect.bottom
        }
    }

    private fun ambientShadowShader(alpha: Int, strokeWidth: Float): LinearGradient {
        val cached = cachedAmbientShadowShader
        if (
            cached != null &&
            ambientShadowAlpha == alpha &&
            ambientShadowStrokeWidth == strokeWidth &&
            ambientShadowTop == insetRect.top &&
            ambientShadowBottom == insetRect.bottom
        ) {
            return cached
        }
        return LinearGradient(
            0f,
            insetRect.top,
            0f,
            insetRect.bottom,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb((alpha * 0.52f).toInt().coerceIn(0, 255), 0, 0, 0),
                Color.argb(alpha, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.48f, 0.82f, 1f),
            Shader.TileMode.CLAMP,
        ).also {
            cachedAmbientShadowShader = it
            ambientShadowAlpha = alpha
            ambientShadowStrokeWidth = strokeWidth
            ambientShadowTop = insetRect.top
            ambientShadowBottom = insetRect.bottom
        }
    }

    private fun edgeHighlightShader(alpha: Int, strokeWidth: Float): LinearGradient {
        val cached = cachedEdgeHighlightShader
        if (
            cached != null &&
            edgeHighlightAlpha == alpha &&
            edgeHighlightStrokeWidth == strokeWidth &&
            edgeHighlightTop == insetRect.top &&
            edgeHighlightBottom == insetRect.bottom
        ) {
            return cached
        }
        return LinearGradient(
            0f,
            insetRect.top,
            0f,
            insetRect.bottom,
            intArrayOf(
                Color.argb(alpha, 255, 255, 255),
                Color.argb((alpha * 0.28f).toInt().coerceIn(0, 255), 255, 255, 255),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.34f, 1f),
            Shader.TileMode.CLAMP,
        ).also {
            cachedEdgeHighlightShader = it
            edgeHighlightAlpha = alpha
            edgeHighlightStrokeWidth = strokeWidth
            edgeHighlightTop = insetRect.top
            edgeHighlightBottom = insetRect.bottom
        }
    }

    companion object {
        private val noiseBitmap: Bitmap by lazy { createNoiseBitmap() }

        private fun createNoiseBitmap(): Bitmap {
            val pixels = IntArray(NOISE_TEXTURE_SIZE * NOISE_TEXTURE_SIZE)
            var seed = 0x13579BDF
            for (index in pixels.indices) {
                seed = seed * 1103515245 + 23126
                val alpha = 5 + (seed ushr 24 and 0x07)
                pixels[index] = Color.argb(alpha, 255, 255, 255)
            }
            return Bitmap.createBitmap(
                pixels,
                NOISE_TEXTURE_SIZE,
                NOISE_TEXTURE_SIZE,
                Bitmap.Config.ARGB_8888,
            )
        }
    }
}
