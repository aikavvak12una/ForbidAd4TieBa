package com.forbidad4tieba.hook.feature.ui.liquidglass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassHitTest {
    @Test fun visibleTopBottomAndEndPaddingBelongToTheCapsule() {
        for (height in listOf(96f, 128f, 192f)) {
            assertTrue(LiquidGlassGeom.containsCapsule(350f, 1f, 700f, height))
            assertTrue(LiquidGlassGeom.containsCapsule(350f, height - 1f, 700f, height))
            assertTrue(LiquidGlassGeom.containsCapsule(1f, height / 2, 700f, height))
            assertTrue(LiquidGlassGeom.containsCapsule(699f, height / 2, 700f, height))
        }
    }

    @Test fun TransparentCornersAndOutsideCoordinatesDoNotBelongToTheCapsule() {
        for ((x, y) in listOf(1f to 1f, 699f to 1f, 1f to 127f, 699f to 127f,
            -1f to 64f, 700f to 64f, 350f to 128f, Float.NaN to 30f)) {
            assertFalse("$x,$y", LiquidGlassGeom.containsCapsule(x, y, 700f, 128f))
        }
    }

    @Test fun OneTabOrTallShapesUseTheSameClampedCornerRadiusAsCanvas() {
        assertTrue(LiquidGlassGeom.containsCapsule(1f, 60f, 80f, 120f))
        assertFalse(LiquidGlassGeom.containsCapsule(1f, 1f, 80f, 120f))
        assertFalse(LiquidGlassGeom.containsCapsule(0f, 0f, 0f, 120f))
    }
}
