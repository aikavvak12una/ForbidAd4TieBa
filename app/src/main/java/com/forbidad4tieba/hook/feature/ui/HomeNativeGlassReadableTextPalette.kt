package com.forbidad4tieba.hook.feature.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.forbidad4tieba.hook.config.ConfigManager
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class HomeNativeGlassTextRole {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    LINK,
    ACTION,
    DISABLED,
    ACCENT_BLUE,
    ACCENT_RED,
}

data class HomeNativeGlassReadableTextPalette(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val link: Int,
    val action: Int,
    val disabled: Int,
    val accentBlue: Int,
    val accentRed: Int,
) {
    fun colorFor(role: HomeNativeGlassTextRole): Int {
        return when (role) {
            HomeNativeGlassTextRole.PRIMARY -> primary
            HomeNativeGlassTextRole.SECONDARY -> secondary
            HomeNativeGlassTextRole.TERTIARY -> tertiary
            HomeNativeGlassTextRole.LINK -> link
            HomeNativeGlassTextRole.ACTION -> action
            HomeNativeGlassTextRole.DISABLED -> disabled
            HomeNativeGlassTextRole.ACCENT_BLUE -> accentBlue
            HomeNativeGlassTextRole.ACCENT_RED -> accentRed
        }
    }

    fun serialize(): String {
        return listOf(
            primary,
            secondary,
            tertiary,
            link,
            action,
            disabled,
            accentBlue,
            accentRed,
        ).joinToString(separator = "|") { color ->
            "%08X".format(color.toLong() and 0xFFFFFFFFL)
        }
    }

    data class Serialized(
        val light: String,
        val dark: String,
    )

    private data class BaseColors(
        val primary: Int,
        val secondary: Int,
        val tertiary: Int,
        val link: Int,
        val action: Int,
        val disabled: Int,
        val accentBlue: Int,
        val accentRed: Int,
    )

    companion object {
        private const val SAMPLE_EDGE = 96
        private const val MAX_SAMPLE_COUNT = 4096
        private const val MIN_PIXEL_ALPHA = 32
        private const val CONTRAST_PRIMARY = 4.8
        private const val CONTRAST_SECONDARY = 3.8
        private const val CONTRAST_TERTIARY = 3.2
        private const val CONTRAST_LINK = 4.5
        private const val CONTRAST_DISABLED = 3.0

        private val DARK_TEXT_BASE = BaseColors(
            primary = Color.rgb(32, 35, 42),
            secondary = Color.rgb(70, 78, 92),
            tertiary = Color.rgb(98, 108, 125),
            link = Color.rgb(23, 87, 202),
            action = Color.rgb(30, 97, 219),
            disabled = Color.rgb(130, 140, 156),
            accentBlue = Color.rgb(36, 103, 224),
            accentRed = Color.rgb(206, 44, 40),
        )

        private val LIGHT_TEXT_BASE = BaseColors(
            primary = Color.rgb(248, 250, 253),
            secondary = Color.rgb(224, 231, 240),
            tertiary = Color.rgb(194, 204, 218),
            link = Color.rgb(143, 186, 255),
            action = Color.rgb(128, 177, 255),
            disabled = Color.rgb(151, 162, 179),
            accentBlue = Color.rgb(139, 183, 255),
            accentRed = Color.rgb(255, 150, 142),
        )

        fun parse(raw: String?): HomeNativeGlassReadableTextPalette? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split('|')
            if (parts.size != 8) return null
            val colors = IntArray(parts.size)
            for (index in parts.indices) {
                colors[index] = parseColor(parts[index].trim()) ?: return null
            }
            return HomeNativeGlassReadableTextPalette(
                primary = colors[0],
                secondary = colors[1],
                tertiary = colors[2],
                link = colors[3],
                action = colors[4],
                disabled = colors[5],
                accentBlue = colors[6],
                accentRed = colors[7],
            )
        }

        fun computeSerialized(
            context: Context,
            blurCachePath: String,
            sourcePath: String,
            tintAlphaPercent: Int,
        ): Serialized {
            val path = usablePath(blurCachePath) ?: usablePath(sourcePath)
            if (path == null) return Serialized(light = "", dark = "")
            val bitmap = HomeNativeGlassImageCache.decodeSampledBitmap(path, SAMPLE_EDGE, SAMPLE_EDGE)
                ?: return Serialized(
                    light = defaultPalette(darkMode = false).serialize(),
                    dark = defaultPalette(darkMode = true).serialize(),
                )
            return try {
                Serialized(
                    light = createPalette(
                        sampleLuminance(bitmap, darkMode = false, tintAlphaPercent = tintAlphaPercent),
                        darkMode = false,
                    ).serialize(),
                    dark = createPalette(
                        sampleLuminance(bitmap, darkMode = true, tintAlphaPercent = tintAlphaPercent),
                        darkMode = true,
                    ).serialize(),
                )
            } finally {
                runCatching { bitmap.recycle() }
            }
        }

        private fun usablePath(path: String): String? {
            val trimmed = path.trim()
            if (trimmed.isEmpty()) return null
            val file = File(trimmed)
            return if (runCatching { file.isFile && file.length() > 0L }.getOrDefault(false)) {
                trimmed
            } else {
                null
            }
        }

        private fun defaultPalette(darkMode: Boolean): HomeNativeGlassReadableTextPalette {
            return createPalette(
                doubleArrayOf(if (darkMode) 0.12 else 0.88),
                darkMode = darkMode,
            )
        }

        private fun createPalette(
            backgroundLuminanceSamples: DoubleArray,
            darkMode: Boolean,
        ): HomeNativeGlassReadableTextPalette {
            val samples = if (backgroundLuminanceSamples.isNotEmpty()) {
                backgroundLuminanceSamples
            } else {
                doubleArrayOf(if (darkMode) 0.12 else 0.88)
            }
            samples.sort()
            val guard = doubleArrayOf(
                percentile(samples, 0.20),
                percentile(samples, 0.50),
                percentile(samples, 0.80),
            )
            val whiteScore = worstContrast(Color.WHITE, guard)
            val blackScore = worstContrast(Color.BLACK, guard)
            val preferLightText = if (kotlin.math.abs(whiteScore - blackScore) < 0.35) {
                darkMode
            } else {
                whiteScore > blackScore
            }
            val base = if (preferLightText) LIGHT_TEXT_BASE else DARK_TEXT_BASE
            return HomeNativeGlassReadableTextPalette(
                primary = ensureContrast(base.primary, guard, CONTRAST_PRIMARY, preferLightText),
                secondary = ensureContrast(base.secondary, guard, CONTRAST_SECONDARY, preferLightText),
                tertiary = ensureContrast(base.tertiary, guard, CONTRAST_TERTIARY, preferLightText),
                link = ensureContrast(base.link, guard, CONTRAST_LINK, preferLightText),
                action = ensureContrast(base.action, guard, CONTRAST_LINK, preferLightText),
                disabled = ensureContrast(base.disabled, guard, CONTRAST_DISABLED, preferLightText),
                accentBlue = ensureContrast(base.accentBlue, guard, CONTRAST_LINK, preferLightText),
                accentRed = ensureContrast(base.accentRed, guard, CONTRAST_LINK, preferLightText),
            )
        }

        private fun sampleLuminance(
            bitmap: Bitmap,
            darkMode: Boolean,
            tintAlphaPercent: Int,
        ): DoubleArray {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return doubleArrayOf()
            var step = 1
            while ((width / step) * (height / step) > MAX_SAMPLE_COUNT) {
                step++
            }
            val result = ArrayList<Double>(min(MAX_SAMPLE_COUNT, width * height))
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    val color = bitmap.getPixel(x, y)
                    if (Color.alpha(color) >= MIN_PIXEL_ALPHA) {
                        result.add(relativeLuminance(applyMaterialOverlay(color, darkMode, tintAlphaPercent)))
                    }
                    x += step
                }
                y += step
            }
            return result.toDoubleArray()
        }

        private fun applyMaterialOverlay(color: Int, darkMode: Boolean, tintAlphaPercent: Int): Int {
            val baseAlpha = tintAlphaPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            )
            val overlayAlpha = if (darkMode) {
                val lightBase = ConfigManager.APPLE_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT.coerceAtLeast(1)
                (baseAlpha * ConfigManager.APPLE_HOME_NATIVE_GLASS_DARK_TINT_ALPHA_PERCENT.toFloat() /
                    lightBase.toFloat()).toInt()
            } else {
                baseAlpha
            }.coerceIn(0, 255)
            val overlay = if (darkMode) 0 else 255
            return Color.rgb(
                blendChannel(Color.red(color), overlay, overlayAlpha),
                blendChannel(Color.green(color), overlay, overlayAlpha),
                blendChannel(Color.blue(color), overlay, overlayAlpha),
            )
        }

        private fun blendChannel(base: Int, overlay: Int, alpha: Int): Int {
            return ((base * (255 - alpha)) + (overlay * alpha)) / 255
        }

        private fun ensureContrast(
            color: Int,
            backgroundLuminance: DoubleArray,
            targetContrast: Double,
            preferLightText: Boolean,
        ): Int {
            if (worstContrast(color, backgroundLuminance) >= targetContrast) {
                return opaque(color)
            }
            val target = if (preferLightText) Color.WHITE else Color.BLACK
            var best = color
            var bestContrast = worstContrast(color, backgroundLuminance)
            for (step in 1..16) {
                val candidate = blendRgb(color, target, step / 16f)
                val contrast = worstContrast(candidate, backgroundLuminance)
                if (contrast > bestContrast) {
                    best = candidate
                    bestContrast = contrast
                }
                if (contrast >= targetContrast) return opaque(candidate)
            }
            val targetContrastValue = worstContrast(target, backgroundLuminance)
            return opaque(if (targetContrastValue >= bestContrast) target else best)
        }

        private fun blendRgb(from: Int, to: Int, ratio: Float): Int {
            val clampedRatio = ratio.coerceIn(0f, 1f)
            val inverse = 1f - clampedRatio
            return Color.rgb(
                (Color.red(from) * inverse + Color.red(to) * clampedRatio).toInt().coerceIn(0, 255),
                (Color.green(from) * inverse + Color.green(to) * clampedRatio).toInt().coerceIn(0, 255),
                (Color.blue(from) * inverse + Color.blue(to) * clampedRatio).toInt().coerceIn(0, 255),
            )
        }

        private fun worstContrast(color: Int, backgroundLuminance: DoubleArray): Double {
            if (backgroundLuminance.isEmpty()) return 21.0
            val textLuminance = relativeLuminance(color)
            var worst = Double.MAX_VALUE
            for (luminance in backgroundLuminance) {
                val contrast = contrastRatio(textLuminance, luminance)
                if (contrast < worst) worst = contrast
            }
            return worst
        }

        private fun contrastRatio(a: Double, b: Double): Double {
            val lighter = max(a, b)
            val darker = min(a, b)
            return (lighter + 0.05) / (darker + 0.05)
        }

        private fun relativeLuminance(color: Int): Double {
            val red = linearColorChannel(Color.red(color))
            val green = linearColorChannel(Color.green(color))
            val blue = linearColorChannel(Color.blue(color))
            return 0.2126 * red + 0.7152 * green + 0.0722 * blue
        }

        private fun linearColorChannel(value: Int): Double {
            val channel = value.coerceIn(0, 255) / 255.0
            return if (channel <= 0.03928) {
                channel / 12.92
            } else {
                ((channel + 0.055) / 1.055).pow(2.4)
            }
        }

        private fun percentile(sorted: DoubleArray, fraction: Double): Double {
            if (sorted.isEmpty()) return 0.5
            val index = ((sorted.size - 1) * fraction.coerceIn(0.0, 1.0)).toInt()
            return sorted[index.coerceIn(0, sorted.size - 1)]
        }

        private fun opaque(color: Int): Int {
            return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
        }

        private fun parseColor(value: String): Int? {
            val hex = value.removePrefix("#")
            val normalized = when (hex.length) {
                6 -> "FF$hex"
                8 -> hex
                else -> return null
            }
            return runCatching { normalized.toLong(16).toInt() }.getOrNull()
        }
    }
}
