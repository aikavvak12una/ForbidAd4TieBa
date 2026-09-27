package com.forbidad4tieba.hook.feature.ui.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class LiquidGlassInteractionTest {
    @Test fun rubberBandIsBoundedSymmetricAndReturnsToRest() {
        for (distance in listOf(0f, 1f, 20f, 300f, 900f)) {
            val offset = DropletController.rubberBandOffset(distance, 300f, 2f)
            assertTrue(offset in 0f..8f)
            assertEquals(-offset, DropletController.rubberBandOffset(-distance, 300f, 2f), 0.0001f)
        }
        assertEquals(0f, DropletController.rubberBandOffset(0f, 300f, 2f), 0f)
        assertEquals(0f, DropletController.rubberBandOffset(50f, 0f, 2f), 0f)
        val returning = LiquidGlassSpring(1f, 300f, 0.5f, 250f)
        returning.animateTo(0f)
        repeat(120) { returning.update(1f / 60f) }
        assertEquals(0f, returning.value, 0f)
        assertTrue(!returning.isRunning)
    }

    @Test fun gravityNoiseDoesNotInvalidateAStationaryHighlight() {
        assertEquals(LiquidGlassTilt.REST_ANGLE, LiquidGlassTilt.quantizedAngle(0f, 0f), 0f)
        assertEquals(LiquidGlassTilt.REST_ANGLE, LiquidGlassTilt.quantizedAngle(0.04f, 0.03f), 0f)
        val upright = LiquidGlassTilt.quantizedAngle(0f, -1f)
        for (jitter in listOf(-0.01f, -0.005f, 0f, 0.005f, 0.01f)) {
            assertEquals(upright, LiquidGlassTilt.quantizedAngle(jitter, -1f), 0f)
        }
        assertEquals((PI / 2).toFloat(), LiquidGlassTilt.quantizedAngle(0f, 1f), 0.0001f)
        assertEquals(0f, LiquidGlassTilt.quantizedAngle(1f, 0f), 0.0001f)
    }
}
