package com.forbidad4tieba.hook.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.forbidad4tieba.hook.config.ConfigManager
import java.io.File
import kotlin.math.max

internal data class HomeNativeGlassImageAnalysis(
    val paletteColors: List<Int>,
    val defaultTintColor: Int?,
    val tintAlphaPercent: Int,
)

internal object HomeNativeGlassImageAnalyzer {
    const val TINT_PALETTE_MIN_DISTANCE = 34

    private const val TINT_PALETTE_MAX_COLORS = 6
    private const val TINT_PALETTE_SAMPLE_EDGE = 96
    private const val TINT_PALETTE_MIN_PIXEL_ALPHA = 32
    private const val TINT_PALETTE_MIN_LUMA = 16
    private const val TINT_PALETTE_MAX_LUMA = 240
    private const val TINT_PALETTE_TARGET_LUMA = 144
    private const val TINT_PALETTE_TARGET_LUMA_RANGE = 128
    private const val TINT_PALETTE_MIN_HUE_DISTANCE = 20.0
    private const val TINT_PALETTE_HUE_SATURATION_FLOOR = 0.12
    private const val DEFAULT_TINT_SAMPLE_EDGE = 48
    private const val DEFAULT_TINT_MIN_LUMA = 24
    private const val DEFAULT_TINT_MAX_LUMA = 232
    private const val DEFAULT_TINT_CHROMA_BIAS = 32
    private const val AUTO_TINT_MID_LUMA = 128
    private const val AUTO_TINT_TRIGGER_DISTANCE = 72
    private const val AUTO_TINT_MAX_ABS_PERCENT = 22

    fun analyze(path: String, darkMode: Boolean): HomeNativeGlassImageAnalysis? {
        val file = File(path.trim())
        if (!file.isFile || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        val sampleEdge = max(TINT_PALETTE_SAMPLE_EDGE, DEFAULT_TINT_SAMPLE_EDGE)
        var sampleSize = 1
        while (max(width, height) / sampleSize > sampleEdge) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        return try {
            val averageLuma = computeAverageLuma(bitmap)
            HomeNativeGlassImageAnalysis(
                paletteColors = extractTintPalette(bitmap),
                defaultTintColor = extractDefaultTintColor(bitmap),
                tintAlphaPercent = averageLuma?.let {
                    autoTintAlphaPercent(it, darkMode)
                }
                    ?: ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            )
        } finally {
            bitmap.recycle()
        }
    }

    fun tintColorDistanceSquared(a: Int, b: Int): Int {
        val red = Color.red(a) - Color.red(b)
        val green = Color.green(a) - Color.green(b)
        val blue = Color.blue(a) - Color.blue(b)
        return red * red + green * green + blue * blue
    }

    private fun extractDefaultTintColor(bitmap: Bitmap): Int? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val step = (max(width, height) / DEFAULT_TINT_SAMPLE_EDGE).coerceAtLeast(1)
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
                if (alpha >= TINT_PALETTE_MIN_PIXEL_ALPHA) {
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
                    if (luma in DEFAULT_TINT_MIN_LUMA..DEFAULT_TINT_MAX_LUMA) {
                        val weight = (alpha * (chroma + DEFAULT_TINT_CHROMA_BIAS)).toLong()
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

    private fun computeAverageLuma(bitmap: Bitmap): Int? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val step = (max(width, height) / TINT_PALETTE_SAMPLE_EDGE).coerceAtLeast(1)
        var lumaSum = 0L
        var alphaSum = 0L
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getPixel(x, y)
                val alpha = color ushr 24
                if (alpha >= TINT_PALETTE_MIN_PIXEL_ALPHA) {
                    val red = color shr 16 and 0xFF
                    val green = color shr 8 and 0xFF
                    val blue = color and 0xFF
                    val luma = (red * 299 + green * 587 + blue * 114) / 1000
                    lumaSum += luma.toLong() * alpha
                    alphaSum += alpha.toLong()
                }
                x += step
            }
            y += step
        }
        if (alphaSum <= 0L) return null
        return (lumaSum / alphaSum).toInt().coerceIn(0, 255)
    }

    private fun autoTintAlphaPercent(
        averageLuma: Int,
        darkMode: Boolean,
    ): Int {
        val luma = averageLuma.coerceIn(0, 255)
        val distanceFromMid = if (luma >= AUTO_TINT_MID_LUMA) {
            luma - AUTO_TINT_MID_LUMA
        } else {
            AUTO_TINT_MID_LUMA - luma
        }
        if (distanceFromMid >= AUTO_TINT_TRIGGER_DISTANCE) {
            return ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
        }
        val strength = (
            (AUTO_TINT_TRIGGER_DISTANCE - distanceFromMid) *
                AUTO_TINT_MAX_ABS_PERCENT +
                AUTO_TINT_TRIGGER_DISTANCE / 2
            ) / AUTO_TINT_TRIGGER_DISTANCE
        val signedStrength = if (darkMode) -strength else strength
        return signedStrength.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
    }

    private fun extractTintPalette(bitmap: Bitmap): List<Int> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return emptyList()
        val step = (max(width, height) / TINT_PALETTE_SAMPLE_EDGE).coerceAtLeast(1)
        val buckets = HashMap<Int, HomeNativeGlassColorBucket>()
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getPixel(x, y)
                val alpha = color ushr 24
                if (alpha >= TINT_PALETTE_MIN_PIXEL_ALPHA) {
                    val red = color shr 16 and 0xFF
                    val green = color shr 8 and 0xFF
                    val blue = color and 0xFF
                    val key = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
                    val bucket = buckets.getOrPut(key) { HomeNativeGlassColorBucket() }
                    bucket.weight += alpha.toLong()
                    bucket.red += red.toLong() * alpha
                    bucket.green += green.toLong() * alpha
                    bucket.blue += blue.toLong() * alpha
                }
                x += step
            }
            y += step
        }
        if (buckets.isEmpty()) return emptyList()
        var maxWeight = 0L
        buckets.values.forEach { bucket ->
            if (bucket.weight > maxWeight) maxWeight = bucket.weight
        }
        if (maxWeight <= 0L) return emptyList()
        val candidates = buckets.values.asSequence()
            .filter { it.weight > 0L }
            .map { bucket ->
                val red = (bucket.red / bucket.weight).toInt().coerceIn(0, 255)
                val green = (bucket.green / bucket.weight).toInt().coerceIn(0, 255)
                val blue = (bucket.blue / bucket.weight).toInt().coerceIn(0, 255)
                val maxChannel = max(red, max(green, blue))
                val minChannel = red.coerceAtMost(green).coerceAtMost(blue)
                val chroma = maxChannel - minChannel
                val luma = (red * 299 + green * 587 + blue * 114) / 1000
                val saturation = tintPaletteSaturation(maxChannel, minChannel, chroma)
                val populationScore = Math.sqrt(bucket.weight.toDouble() / maxWeight.toDouble())
                val saturationScore = saturation.coerceIn(0.0, 1.0)
                val toneScore = (
                    1.0 - Math.abs(luma - TINT_PALETTE_TARGET_LUMA).toDouble() /
                        TINT_PALETTE_TARGET_LUMA_RANGE
                    ).coerceIn(0.0, 1.0)
                val neutralPenalty = if (chroma < 8) 0.65 else 1.0
                HomeNativeGlassPaletteColor(
                    color = Color.rgb(red, green, blue),
                    score = (
                        populationScore * 0.52 +
                            saturationScore * 0.28 +
                            toneScore * 0.20
                        ) * neutralPenalty,
                    luma = luma,
                    saturation = saturation,
                    hue = tintPaletteHue(red, green, blue, maxChannel, chroma),
                )
            }
            .toList()
        val ranked = (
            candidates
                .filter { it.luma in TINT_PALETTE_MIN_LUMA..TINT_PALETTE_MAX_LUMA }
                .takeIf { it.isNotEmpty() }
                ?: candidates
            )
            .sortedByDescending { it.score }
        val minDistanceSquared = TINT_PALETTE_MIN_DISTANCE * TINT_PALETTE_MIN_DISTANCE
        val selected = ArrayList<HomeNativeGlassPaletteColor>(TINT_PALETTE_MAX_COLORS)
        val postponed = ArrayList<HomeNativeGlassPaletteColor>()
        for (candidate in ranked) {
            if (selected.any { tintColorDistanceSquared(it.color, candidate.color) < minDistanceSquared }) {
                continue
            }
            if (selected.any { tintPaletteHueDistanceTooSmall(it, candidate) }) {
                postponed.add(candidate)
                continue
            }
            selected.add(candidate)
            if (selected.size >= TINT_PALETTE_MAX_COLORS) break
        }
        if (selected.size < TINT_PALETTE_MAX_COLORS) {
            for (candidate in postponed) {
                if (selected.any { tintColorDistanceSquared(it.color, candidate.color) < minDistanceSquared }) {
                    continue
                }
                selected.add(candidate)
                if (selected.size >= TINT_PALETTE_MAX_COLORS) break
            }
        }
        return selected.map { it.color }
    }

    private fun tintPaletteSaturation(
        maxChannel: Int,
        minChannel: Int,
        chroma: Int,
    ): Double {
        if (chroma <= 0) return 0.0
        val denominator = 255 - Math.abs(maxChannel + minChannel - 255)
        if (denominator <= 0) return 0.0
        return (chroma.toDouble() / denominator.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun tintPaletteHue(
        red: Int,
        green: Int,
        blue: Int,
        maxChannel: Int,
        chroma: Int,
    ): Double {
        if (chroma <= 0) return 0.0
        val rawHue = when (maxChannel) {
            red -> ((green - blue).toDouble() / chroma.toDouble()) % 6.0
            green -> ((blue - red).toDouble() / chroma.toDouble()) + 2.0
            else -> ((red - green).toDouble() / chroma.toDouble()) + 4.0
        } * 60.0
        return if (rawHue < 0.0) rawHue + 360.0 else rawHue
    }

    private fun tintPaletteHueDistanceTooSmall(
        a: HomeNativeGlassPaletteColor,
        b: HomeNativeGlassPaletteColor,
    ): Boolean {
        if (
            a.saturation < TINT_PALETTE_HUE_SATURATION_FLOOR ||
            b.saturation < TINT_PALETTE_HUE_SATURATION_FLOOR
        ) {
            return false
        }
        val distance = Math.abs(a.hue - b.hue)
        val circularDistance = distance.coerceAtMost(360.0 - distance)
        return circularDistance < TINT_PALETTE_MIN_HUE_DISTANCE
    }

    private class HomeNativeGlassColorBucket {
        var weight: Long = 0L
        var red: Long = 0L
        var green: Long = 0L
        var blue: Long = 0L
    }

    private data class HomeNativeGlassPaletteColor(
        val color: Int,
        val score: Double,
        val luma: Int,
        val saturation: Double,
        val hue: Double,
    )
}
