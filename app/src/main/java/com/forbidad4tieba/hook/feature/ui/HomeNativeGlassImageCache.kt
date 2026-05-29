package com.forbidad4tieba.hook.feature.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.forbidad4tieba.hook.config.ConfigManager
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object HomeNativeGlassImageCache {
    private const val CACHE_ROOT_DIR_NAME = "home_native_glass"
    private const val CACHE_EFFECT_DIR_NAME = "effects"
    private const val CACHE_FILE_PREFIX = "blur_"
    private const val CACHE_VERSION = 4
    private const val BLUR_CACHE_MAX_EDGE = 720
    private const val CARD_BLUR_MIN_RADIUS = 2
    private const val CARD_BLUR_MAX_RADIUS = 12
    private const val CARD_BLUR_MAX_ITERATIONS = 5
    private const val MATERIAL_SATURATION = 1.08f
    private const val MATERIAL_CONTRAST = 1.04f

    fun ensureBlurCache(
        context: Context,
        sourcePath: String,
        blurPercent: Int,
        appleMaterial: Boolean = true,
    ): String {
        val path = sourcePath.trim()
        if (path.isEmpty()) return ""
        val sourceFile = File(path)
        val sourceLength = runCatching { sourceFile.length() }.getOrDefault(0L)
        if (!runCatching { sourceFile.isFile && sourceLength > 0L }.getOrDefault(false)) return ""

        val appContext = context.applicationContext ?: context
        val cacheDir = persistentCacheDir(appContext)
        if (!runCatching { cacheDir.exists() || cacheDir.mkdirs() }.getOrDefault(false)) return ""

        val normalizedBlur = blurPercent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
        )
        val cacheKey = cacheKey(
            path = path,
            lastModified = runCatching { sourceFile.lastModified() }.getOrDefault(0L),
            length = sourceLength,
            blurPercent = normalizedBlur,
            appleMaterial = appleMaterial,
        )
        val cacheFile = File(cacheDir, "$CACHE_FILE_PREFIX$cacheKey.png")
        if (cacheFile.isFile && cacheFile.length() > 0L) {
            cleanupOldCaches(cacheDir, cacheFile.name)
            return cacheFile.absolutePath
        }

        val bitmap = decodeSampledBitmapForMaxEdge(path, BLUR_CACHE_MAX_EDGE) ?: return ""
        var cacheBitmap: Bitmap? = null
        try {
            val outputBitmap = createBlurredBitmap(bitmap, normalizedBlur, appleMaterial) ?: return ""
            cacheBitmap = outputBitmap
            val tempFile = File(cacheDir, "${cacheFile.name}.tmp")
            val written = runCatching {
                tempFile.outputStream().use { output ->
                    outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }.getOrDefault(false)
            if (!written || tempFile.length() <= 0L) {
                runCatching { tempFile.delete() }
                return ""
            }
            runCatching {
                if (cacheFile.exists()) cacheFile.delete()
                tempFile.renameTo(cacheFile)
            }.getOrDefault(false)
            if (!cacheFile.isFile || cacheFile.length() <= 0L) return ""
            cleanupOldCaches(cacheDir, cacheFile.name)
            return cacheFile.absolutePath
        } finally {
            if (cacheBitmap !== bitmap) {
                runCatching { cacheBitmap?.recycle() }
            }
            runCatching { bitmap.recycle() }
        }
    }

    fun decodeSampledBitmap(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetWidth = targetWidth.coerceAtLeast(1),
                targetHeight = targetHeight.coerceAtLeast(1),
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    fun decodeBitmap(path: String): Bitmap? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { BitmapFactory.decodeFile(trimmed) }.getOrNull()
    }

    private fun decodeSampledBitmapForMaxEdge(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, bounds)
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return null

        val scale = min(1.0f, maxEdge.toFloat() / max(sourceWidth, sourceHeight).toFloat())
        val targetWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        val decoded = decodeSampledBitmap(path, targetWidth, targetHeight) ?: return null
        if (decoded.width == targetWidth && decoded.height == targetHeight) return decoded
        val scaled = runCatching {
            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        }.getOrNull()
        runCatching { decoded.recycle() }
        val copy = scaled?.let { bitmap ->
            runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull()
        }
        if (copy !== scaled) {
            runCatching { scaled?.recycle() }
        }
        return copy
    }

    private fun createBlurredBitmap(source: Bitmap, blurPercent: Int, appleMaterial: Boolean): Bitmap? {
        val mutable = runCatching {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }.getOrNull() ?: return null
        val blurred = if (blurPercent <= ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT) {
            mutable
        } else {
            runCatching {
                blurBitmapInPlace(
                    mutable,
                    radius = cardBlurRadius(blurPercent),
                    iterations = cardBlurIterations(blurPercent),
                )
            }.getOrElse {
                runCatching { mutable.recycle() }
                return null
            }
        }
        if (!appleMaterial) return blurred
        return runCatching { applyMaterialColorTreatment(blurred) }.getOrDefault(blurred)
    }

    private fun applyMaterialColorTreatment(bitmap: Bitmap): Bitmap {
        val source = runCatching {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull() ?: return bitmap
        val matrix = ColorMatrix().apply {
            setSaturation(MATERIAL_SATURATION)
        }
        val contrastTranslate = 128f * (1f - MATERIAL_CONTRAST)
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    MATERIAL_CONTRAST, 0f, 0f, 0f, contrastTranslate,
                    0f, MATERIAL_CONTRAST, 0f, 0f, contrastTranslate,
                    0f, 0f, MATERIAL_CONTRAST, 0f, contrastTranslate,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        Canvas(bitmap).drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            },
        )
        runCatching { source.recycle() }
        return bitmap
    }

    private fun cardBlurRadius(blurPercent: Int): Int {
        val percent = blurPercent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
        )
        if (percent <= 0) return 0
        return (
            CARD_BLUR_MIN_RADIUS +
                percent * (CARD_BLUR_MAX_RADIUS - CARD_BLUR_MIN_RADIUS) /
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
            ).coerceIn(CARD_BLUR_MIN_RADIUS, CARD_BLUR_MAX_RADIUS)
    }

    private fun cardBlurIterations(blurPercent: Int): Int {
        val percent = blurPercent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
        )
        if (percent <= 0) return 0
        return (
            1 +
                percent * (CARD_BLUR_MAX_ITERATIONS - 1) /
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
            ).coerceIn(1, CARD_BLUR_MAX_ITERATIONS)
    }

    private fun blurBitmapInPlace(bitmap: Bitmap, radius: Int, iterations: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || radius <= 0 || iterations <= 0) return bitmap
        var input = IntArray(width * height)
        var output = IntArray(width * height)
        bitmap.getPixels(input, 0, width, 0, 0, width, height)
        repeat(iterations) {
            boxBlurHorizontal(input, output, width, height, radius)
            boxBlurVertical(output, input, width, height, radius)
        }
        bitmap.setPixels(input, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun boxBlurHorizontal(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = input[row + offset.coerceIn(0, width - 1)]
                alpha += color ushr 24
                red += color shr 16 and 0xFF
                green += color shr 8 and 0xFF
                blue += color and 0xFF
            }
            for (x in 0 until width) {
                output[row + x] = Color.argb(alpha / window, red / window, green / window, blue / window)
                val removeColor = input[row + (x - radius).coerceIn(0, width - 1)]
                val addColor = input[row + (x + radius + 1).coerceIn(0, width - 1)]
                alpha += (addColor ushr 24) - (removeColor ushr 24)
                red += (addColor shr 16 and 0xFF) - (removeColor shr 16 and 0xFF)
                green += (addColor shr 8 and 0xFF) - (removeColor shr 8 and 0xFF)
                blue += (addColor and 0xFF) - (removeColor and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = input[offset.coerceIn(0, height - 1) * width + x]
                alpha += color ushr 24
                red += color shr 16 and 0xFF
                green += color shr 8 and 0xFF
                blue += color and 0xFF
            }
            for (y in 0 until height) {
                output[y * width + x] = Color.argb(alpha / window, red / window, green / window, blue / window)
                val removeColor = input[(y - radius).coerceIn(0, height - 1) * width + x]
                val addColor = input[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                alpha += (addColor ushr 24) - (removeColor ushr 24)
                red += (addColor shr 16 and 0xFF) - (removeColor shr 16 and 0xFF)
                green += (addColor shr 8 and 0xFF) - (removeColor shr 8 and 0xFF)
                blue += (addColor and 0xFF) - (removeColor and 0xFF)
            }
        }
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sampleSize = 1
        while (
            sourceWidth / (sampleSize * 2) >= targetWidth &&
            sourceHeight / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun cacheKey(
        path: String,
        lastModified: Long,
        length: Long,
        blurPercent: Int,
        appleMaterial: Boolean,
    ): String {
        val raw = "$CACHE_VERSION|$path|$lastModified|$length|$blurPercent|$appleMaterial"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private fun persistentCacheDir(context: Context): File {
        return File(File(context.filesDir, CACHE_ROOT_DIR_NAME), CACHE_EFFECT_DIR_NAME)
    }

    private fun cleanupOldCaches(cacheDir: File, keepName: String) {
        runCatching {
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(CACHE_FILE_PREFIX) && file.name != keepName) {
                    file.delete()
                }
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        }
    }
}
