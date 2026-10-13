package com.forbidad4tieba.hook.feature.ui.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LiquidGlassBlurTest {
    @Test fun allDensityKernelsPreserveFlatColorWithoutInvalidSamplingWeights() {
        for (density in listOf(0.75f, 1f, 1.5f, 1.75f, 2f, 2.625f, 3f, 4f, 6f, 8f)) {
            val radius = 4f * density
            val levels = LiquidGlassBlur.levels(radius)
            for (level in levels.low..levels.high) {
                val kernel = LiquidGlassBlur.kernel(radius, level)
                if (kernel.weights.isEmpty()) continue
                assertEquals(1f, 2f * kernel.weights.sum(), 0.000001f)
                assertTrue(kernel.weights.size <= 7)
                kernel.weights.forEach { assertTrue(it.isFinite() && it > 0f && it <= 0.5f) }
                kernel.offsets.forEach { assertTrue(it.isFinite() && it in 0f..13f) }
                kernel.offsets.toList().zipWithNext().forEach { (a, b) -> assertTrue(a < b) }
            }
        }
    }

    @Test fun densityTransitionsBlendAdjacentResolutionsWithoutAnAbruptLevelSwitch() {
        for (boundary in listOf(3.5496478f, 9.5f, 20f, 44.10215f)) {
            var previous = Float.NaN
            for (step in -150..150) {
                val level = LiquidGlassBlur.levels(boundary * (1f + step / 1000f) / 0.45f)
                assertTrue(level.high - level.low in 0..1)
                assertTrue(level.mix in 0f..1f)
                val position = level.low + (level.high - level.low) * level.mix
                if (previous.isFinite()) assertTrue(abs(position - previous) < 0.007f)
                previous = position
            }
        }
    }

    @Test fun disabledBlurAllocatesNoKernelTaps() {
        assertEquals(0, LiquidGlassBlur.kernel(0f, 0).weights.size)
        assertEquals(LiquidGlassBlur.Levels(0, 0, 0f), LiquidGlassBlur.levels(0f))
    }
}
