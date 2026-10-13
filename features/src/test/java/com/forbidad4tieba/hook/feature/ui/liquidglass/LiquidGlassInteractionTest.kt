package com.forbidad4tieba.hook.feature.ui.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class LiquidGlassInteractionTest {
    @Test fun nearFlatGravityDoesNotJitterTheHighlight() {
        for (noise in listOf(-0.04f, -0.01f, 0f, 0.01f, 0.04f)) {
            assertEquals(LiquidGlassStyle.DEFAULT_LIGHT_ANGLE, LiquidGlassLightSource.gravityAngle(noise, -noise), 0f)
        }
    }

    @Test fun gravityLightMovesOnlyAtThreeDegreeBoundaries() {
        for (degrees in -177..177 step 3) {
            val expected = (degrees * PI / 180).toFloat()
            for (noise in listOf(-1.4, 0.0, 1.4)) {
                val rad = (degrees + noise) * PI / 180
                assertEquals(expected, LiquidGlassLightSource.gravityAngle(cos(rad).toFloat(), sin(rad).toFloat()), 0.0001f)
            }
        }
    }

    @Test fun replyPressUsesBoundedFiniteTiming() {
        assertTrue(LiquidGlassStyle.PRESS_SCALE in 0.9f..1f)
        assertTrue(LiquidGlassStyle.PRESS_IN_MS < LiquidGlassStyle.PRESS_OUT_MS)
        assertTrue(LiquidGlassStyle.PRESS_OUT_MS < 400)
    }
}
