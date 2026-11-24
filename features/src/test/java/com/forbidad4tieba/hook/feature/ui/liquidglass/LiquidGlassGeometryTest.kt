package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.view.Gravity
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class LiquidGlassGeometryTest {
    @Test
    fun placementAndMinimumControlsRemainInsideTheHostBounds() {
        assertEquals(60, LiquidGlassBarMetrics.adjustedSize(100, 60, 160, -100))
        assertEquals(100, LiquidGlassBarMetrics.adjustedSize(100, 60, 160, 0))
        assertEquals(160, LiquidGlassBarMetrics.adjustedSize(100, 60, 160, 100))
        assertEquals(80, LiquidGlassBarMetrics.adjustedSize(100, 60, 160, -50))
        assertNull(LiquidGlassBarMetrics.adjustedSize(100, 120, 119, 0))

        val inset = LiquidGlassBarMetrics.placement(
            1136, 1184, 1280, 96, 128, 80, 2f,
            BottomTabLiquidGlassConfig(positionAdjustment = -100), 0,
        )!!
        assertEquals(1136, inset.top + inset.height)
        assertNull(
            LiquidGlassBarMetrics.placement(
                60, 1280, 1280, 0, 128, 80, 2f, BottomTabLiquidGlassConfig.DEFAULT, 0,
            ),
        )

        assertTrue(bounds(width = 480)!!.width in 1..480)
        assertNull(bounds(width = 400))
        assertNull(bounds(height = 200))
        for (density in listOf(1f, 2f, 3f)) {
            fun px(dp: Int) = (dp * density).roundToInt()
            val box = PostReplyGlassGeometry.calculate(
                px(360), px(640), 0, 0, 0, px(360), px(640),
                0, 0, px(24), px(68), 3, px(42), density, BottomTabLiquidGlassConfig.DEFAULT,
            )!!
            val split = PostReplyGlassGeometry.separate(box.width, 3, density)!!
            val rightMargin = px(360) - box.left - box.width
            assertTrue(abs(box.left - rightMargin) <= 1)
            assertTrue(split.actionsContentWidth / 3 >= px(24))
            assertTrue(split.inputWidth > 0 && split.actionsLeft < box.width)
        }
    }

    @Test
    fun nativeNormalizedGravitySettlesInsteadOfCancellingEveryDraw() {
        assertFalse(PostReplyGlassGeometry.needsVerticalCenter(Gravity.START or Gravity.CENTER_VERTICAL))
        assertFalse(PostReplyGlassGeometry.needsVerticalCenter(Gravity.END or Gravity.CENTER_VERTICAL))
        assertTrue(PostReplyGlassGeometry.needsVerticalCenter(Gravity.START or Gravity.TOP))
    }

    @Test
    fun hitTestingAcceptsOnlyTheFiniteCapsuleInterior() {
        for (height in listOf(96f, 128f, 192f)) {
            assertTrue(LiquidGlassGeom.containsCapsule(350f, 1f, 700f, height))
            assertTrue(LiquidGlassGeom.containsCapsule(1f, height / 2, 700f, height))
            assertTrue(LiquidGlassGeom.containsCapsule(699f, height / 2, 700f, height))
        }
        for ((x, y) in listOf(
            1f to 1f,
            699f to 1f,
            -1f to 64f,
            700f to 64f,
            350f to 128f,
            Float.NaN to 30f,
        )) {
            assertFalse("$x,$y", LiquidGlassGeom.containsCapsule(x, y, 700f, 128f))
        }
        assertFalse(LiquidGlassGeom.containsCapsule(0f, 0f, 0f, 120f))
    }

    @Test
    fun pressExpansionAndSideInsetsCannotEscapeTheWindow() {
        for (position in listOf(-200f, 0f, 100f, 500f)) {
            val scale = LiquidGlassGeom.fittingScale(2.7f, 160f, 320f)
            val x = DropletController.clampTranslation(position, 160f, 0f, 320f, scale)
            val bulge = (scale - 1f) * 80f
            assertTrue(x - bulge >= -0.001f)
            assertTrue(x + 160f + bulge <= 320.001f)
        }

        val full = bounds(width = 1280, windowRight = 1280, safeLeft = 90, safeRight = 48)!!
        val inset = bounds(
            width = 1142,
            hostLeft = 90,
            windowRight = 1280,
            safeLeft = 90,
            safeRight = 48,
        )!!
        assertEquals(full.width, inset.width)
        assertEquals(full.left, inset.left + 90)
        assertTrue(full.left >= 90)
        assertTrue(full.left + full.width <= 1232)
    }

    private fun bounds(
        width: Int = 720,
        height: Int = 1280,
        hostLeft: Int = 0,
        windowRight: Int = 720,
        safeLeft: Int = 0,
        safeRight: Int = 0,
    ) = PostReplyGlassGeometry.calculate(
        width, height, hostLeft, 0, 0, windowRight, 1280,
        safeLeft, safeRight, 0, 136, 3, 84, 2f, BottomTabLiquidGlassConfig.DEFAULT,
    )
}
