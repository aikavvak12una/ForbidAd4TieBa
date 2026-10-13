// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted from miuix-blur 0.9.4, BlurEffect.kt; see app/src/main/assets/open-source-licenses.txt.
package com.forbidad4tieba.hook.feature.ui.liquidglass

import kotlin.math.exp

/** The reference blur's Gaussian kernel and continuous resolution transitions. */
internal object LiquidGlassBlur {
    data class Levels(val low: Int, val high: Int, val mix: Float)
    data class Kernel(val offsets: FloatArray, val weights: FloatArray)

    private val boundaries = floatArrayOf(3.5496478f, 9.5f, 20f, 44.10215f)
    private val boxVariance = floatArrayOf(0f, 3.0265f, 7.5625f, 9f, 202.696f)

    fun levels(radius: Float): Levels {
        val sigma = radius * 0.45f
        for (i in boundaries.indices) {
            val lo = boundaries[i] * 0.88f
            val hi = boundaries[i] * 1.12f
            if (sigma > lo && sigma < hi) {
                val t = ((sigma - lo) / (hi - lo)).coerceIn(0f, 1f)
                val mix = t * t * (3f - 2f * t)
                return if (mix <= 0.001f) Levels(i, i, 0f) else Levels(i, i + 1, mix)
            }
        }
        val variance = sigma * sigma
        val level = when {
            variance >= 1945f -> 4
            variance > 400f -> 3
            variance >= 90.25f -> 2
            variance >= 12.6f -> 1
            else -> 0
        }
        return Levels(level, level, 0f)
    }

    fun kernel(radius: Float, level: Int): Kernel {
        val scale = (1 shl level).toFloat()
        val sigma = radius * 0.45f
        val variance = ((sigma * sigma - boxVariance[level]) / (scale * scale)).coerceAtLeast(0.1f)
        if (radius <= 0f || variance <= 0.25f) return Kernel(FloatArray(0), FloatArray(0))
        val raw = DoubleArray(14) { exp(-0.5 * it.toDouble() * it / variance) }
        var total = raw[0]
        for (i in 1..13) total += raw[i] * 2.0
        for (i in raw.indices) raw[i] /= total
        val offsets = FloatArray(7)
        val weights = FloatArray(7)
        val halfCentre = raw[0] * 0.5
        offsets[0] = (raw[1] / (halfCentre + raw[1])).toFloat()
        var count = 1
        var i = 2
        while (i < 14 && count < 7) {
            val a = raw[i]
            val b = raw[i + 1]
            val combined = a + b
            if (combined < 0.002) break
            offsets[count] = ((a * i + b * (i + 1)) / combined).toFloat()
            weights[count] = combined.toFloat()
            count++
            i += 2
        }
        var sum = 0f
        for (j in 1 until count) sum += weights[j]
        weights[0] = (0.5f - sum).coerceAtLeast(0f)
        return Kernel(offsets.copyOf(count), weights.copyOf(count))
    }
}
