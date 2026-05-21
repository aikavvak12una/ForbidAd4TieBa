package com.forbidad4tieba.hook.feature.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.forbidad4tieba.hook.config.ConfigManager
import java.io.File
import kotlin.math.max

object HomeNativeGlassDynamicTintCache {
    private const val SAMPLE_EDGE = 48
    private const val MIN_PIXEL_ALPHA = 32
    private const val MIN_LUMA = 24
    private const val MAX_LUMA = 232
    private const val CHROMA_BIAS = 32

    @Volatile private var cachedTint: CachedTint? = null

    private data class CachedTint(
        val path: String,
        val lastModified: Long,
        val length: Long,
        val color: Int?,
    )

    fun resolveAccentColor(): Int? {
        configuredTintColor()?.let { return it }
        return resolveSampledTintColor()
    }

    fun configuredTintColor(): Int? {
        val color = ConfigManager.homeNativeGlassTintColor
        if (color == ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR || Color.alpha(color) == 0) {
            return null
        }
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun resolveSampledTintColor(): Int? {
        val path = ConfigManager.homeNativeGlassBackgroundImagePath.trim()
        if (path.isEmpty()) return null
        val file = File(path)
        val length = runCatching { file.length() }.getOrDefault(0L)
        if (!runCatching { file.isFile && length > 0L }.getOrDefault(false)) {
            cachedTint = CachedTint(path, 0L, 0L, null)
            return null
        }
        val lastModified = runCatching { file.lastModified() }.getOrDefault(0L)
        val cached = cachedTint
        if (
            cached != null &&
            cached.path == path &&
            cached.lastModified == lastModified &&
            cached.length == length
        ) {
            return cached.color
        }
        val color = runCatching {
            val bitmap = HomeNativeGlassImageCache.decodeSampledBitmap(path, SAMPLE_EDGE, SAMPLE_EDGE)
            if (bitmap == null) {
                null
            } else {
                try {
                    extractTintColor(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }.getOrNull()
        cachedTint = CachedTint(path, lastModified, length, color)
        return color
    }

    private fun extractTintColor(bitmap: Bitmap): Int? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val step = (max(width, height) / SAMPLE_EDGE).coerceAtLeast(1)
        var weightedRed = 0L
        var weightedGreen = 0L
        var weightedBlue = 0L
        var weightSum = 0L
        var fallbackRed = 0L
        var fallbackGreen = 0L
        var fallbackBlue = 0L
        var fallbackCount = 0L
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getPixel(x, y)
                val alpha = color ushr 24
                if (alpha >= MIN_PIXEL_ALPHA) {
                    val red = color shr 16 and 0xFF
                    val green = color shr 8 and 0xFF
                    val blue = color and 0xFF
                    fallbackRed += red.toLong()
                    fallbackGreen += green.toLong()
                    fallbackBlue += blue.toLong()
                    fallbackCount++
                    val maxChannel = max(red, max(green, blue))
                    val minChannel = red.coerceAtMost(green).coerceAtMost(blue)
                    val chroma = maxChannel - minChannel
                    val luma = (red * 299 + green * 587 + blue * 114) / 1000
                    if (luma in MIN_LUMA..MAX_LUMA) {
                        val weight = (alpha * (chroma + CHROMA_BIAS)).toLong()
                        weightedRed += red.toLong() * weight
                        weightedGreen += green.toLong() * weight
                        weightedBlue += blue.toLong() * weight
                        weightSum += weight
                    }
                }
                x += step
            }
            y += step
        }
        if (weightSum > 0L) {
            return Color.rgb(
                (weightedRed / weightSum).toInt().coerceIn(0, 255),
                (weightedGreen / weightSum).toInt().coerceIn(0, 255),
                (weightedBlue / weightSum).toInt().coerceIn(0, 255),
            )
        }
        if (fallbackCount <= 0L) return null
        return Color.rgb(
            (fallbackRed / fallbackCount).toInt().coerceIn(0, 255),
            (fallbackGreen / fallbackCount).toInt().coerceIn(0, 255),
            (fallbackBlue / fallbackCount).toInt().coerceIn(0, 255),
        )
    }
}
