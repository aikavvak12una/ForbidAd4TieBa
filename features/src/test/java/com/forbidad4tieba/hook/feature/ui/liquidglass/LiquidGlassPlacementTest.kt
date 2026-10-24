package com.forbidad4tieba.hook.feature.ui.liquidglass

import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class LiquidGlassPlacementTest {
    @Test fun backgroundPhaseUsesTheSameEvenTexelGridAcrossPositiveAndNegativeOrigins() {
        for (factor in listOf(2, 4)) {
            for (offset in listOf(-9.25f, -3f, 0f, 1.25f, 6f, 129f, 1678f)) {
                val residual = LiquidGlassGeom.samplingResidual(offset, factor)
                val anchor = (offset - residual) / factor
                assertEquals(0f, anchor % 2f, 0f)
                assertTrue(kotlin.math.abs(residual) <= factor)
                assertEquals(offset, anchor * factor + residual, 0f)
            }
        }
        assertEquals(0f, LiquidGlassGeom.samplingResidual(1.25f, 1), 0f)
    }


    private fun placement(hostHeight: Int = 1280, hostBottom: Int = 1280, navigation: Int = 0,
        baseline: Int = 128, minimum: Int = 80, density: Float = 2f,
        width: Int = 0, height: Int = 0, position: Int = 0, top: Int = 0) =
        LiquidGlassBarMetrics.placement(hostHeight, hostBottom, 1280, navigation, baseline, minimum, density,
            BottomTabLiquidGlassConfig(width, height, position), top)

    @Test fun centredSizeUsesIndependentRangesAndRejectsImpossibleBounds() {
        assertEquals(60, LiquidGlassBarMetrics.adjustedSize(100, 60, 160, -100))
        assertEquals(100, LiquidGlassBarMetrics.adjustedSize(100, 60, 160, 0))
        assertEquals(160, LiquidGlassBarMetrics.adjustedSize(100, 60, 160, 100))
        assertEquals(80, LiquidGlassBarMetrics.adjustedSize(100, 60, 160, -50))
        assertEquals(130, LiquidGlassBarMetrics.adjustedSize(100, 60, 160, 50))
        assertNull(LiquidGlassBarMetrics.adjustedSize(100, 120, 119, 0))
    }

    @Test fun leftPositionEndReachesTheScreenBottomIncludingNavigation() {
        for (navigation in listOf(0, 48, 96)) {
            val home = placement(navigation = navigation, position = -100)!!
            assertEquals(1280, home.top + home.height)
            val reply = placement(navigation = navigation, baseline = 96, minimum = 72, position = -100)!!
            assertEquals(16, 1280 - reply.top - reply.height)
        }
    }

    @Test fun sizeChangesKeepTheSelectedBottomEdgeFixed() {
        for (navigation in listOf(0, 48, 96)) for (position in listOf(-100, -50, 0, 50, 100)) {
            val base = placement(navigation = navigation, position = position)!!
            var previous = 0
            for (height in -100..100) {
                val next = placement(navigation = navigation, height = height, position = position)!!
                assertEquals(base.top + base.height, next.top + next.height)
                assertTrue(next.height >= previous)
                assertTrue(next.top >= 0 && next.top + next.height <= 1280)
                previous = next.height
            }
        }
    }

    @Test fun insetHostsStillKeepTheCompleteBarInsideTheirActualBounds() {
        val box = placement(hostHeight = 1136, hostBottom = 1184, navigation = 96, position = -100)!!
        assertEquals(1136, box.top + box.height)
        assertNull(placement(hostHeight = 60, hostBottom = 1280))
        assertNull(placement(top = 1240))
        assertNull(placement(density = Float.NaN))
    }

    @Test fun defaultGeometryMatchesKernelSuIntrinsicTabsWithinParentPadding() {
        val examples = listOf(
            listOf(360, 4, 2, 148, 608),
            listOf(411, 4, 2, 152, 624),
            listOf(360, 5, 2, 118, 606),
            listOf(600, 4, 2, 152, 624),
            listOf(240, 4, 3, 132, 552),
        )
        for ((window, count, scale, expectedColumn, expectedBar) in examples) {
            val density = scale.toFloat()
            val height = LiquidGlassBarMetrics.barHeight(40 * scale, density)
            val column = LiquidGlassBarMetrics.columnWidth(window * scale, count, density, 0)
            assertEquals(expectedColumn, column)
            assertEquals(expectedBar, column * count + 8 * scale)
            assertEquals(64 * scale, height)
            assertEquals(56 * scale, LiquidGlassBarMetrics.dropletHeight(column, height, density))
        }
    }

    @Test fun widthAdjustmentRemainsMonotonicAcrossDensitiesAndTabCounts() {
        for (density in listOf(1f, 1.25f, 1.5f, 2f, 2.625f, 3f, 4f)) {
            fun px(dp: Int) = (dp * density).roundToInt()
            for (window in listOf(220, 260, 320, 360, 411, 600)) for (count in 1..5) {
                var previous = 0
                for (adjustment in -100..100) {
                    val column = LiquidGlassBarMetrics.columnWidth(px(window), count, density, adjustment)
                    assertTrue(column >= previous)
                    assertTrue(column * count + 2 * px(4) <= px(window))
                    if (adjustment == 0) assertTrue(column * count + 2 * px(4) <= px(window) - 2 * px(28))
                    previous = column
                }
                assertTrue(px(window) - (previous * count + 2 * px(4)) < count)
            }
        }
        assertEquals(0, LiquidGlassBarMetrics.columnWidth(0, 4, 2f, 0))
        assertEquals(0, LiquidGlassBarMetrics.columnWidth(720, 0, 2f, 0))
        assertEquals(0, LiquidGlassBarMetrics.columnWidth(400, 5, 2f, 0, 100))
    }

    @Test fun lensChangesHeightWithItsBarAndRetainsIndependentRounding() {
        for (density in listOf(1f, 1.5f, 2f, 2.625f, 3f, 4f)) {
            val baseline = (64 * density).roundToInt()
            val expectedLens = (56 * density).roundToInt()
            for (delta in listOf(-10, 0, 20)) {
                val height = baseline + delta
                val lens = LiquidGlassBarMetrics.dropletHeight(100, height, density, baseline)
                assertEquals(expectedLens + delta, lens)
                val inset = LiquidGlassBarMetrics.dropletInset(height, density, baseline)
                assertTrue(kotlin.math.abs(inset + lens / 2f - height / 2f) <= 0.5f)
            }
        }
        assertEquals(128, LiquidGlassBarMetrics.barHeight(80, 2f))
        assertEquals(156, LiquidGlassBarMetrics.barHeight(140, 2f))
    }

    @Test fun defaultNavigationAndFractionalRoundingAreUnchanged() {
        assertEquals(1096, placement()!!.top)
        assertEquals(1088, placement(navigation = 48)!!.top)
        assertEquals(1040, placement(navigation = 96)!!.top)
        assertEquals(1038, placement(baseline = 168, minimum = 100, density = 2.625f)!!.top)
        assertEquals(1028, placement(navigation = 63, baseline = 168, minimum = 100, density = 2.625f)!!.top)
        assertEquals(placement(navigation = 96)!!.top,
            placement(hostHeight = 1184, hostBottom = 1184, navigation = 96)!!.top)
        val standard = placement(hostHeight = 1232, hostBottom = 1280)!!
        val taller = placement(hostHeight = 1232, hostBottom = 1280, baseline = 192)!!
        assertEquals(standard.top + 64, taller.top + 96)
    }

    @Test fun pressExpansionAtAnEdgeCannotEscapeTheViewport() {
        assertEquals(1f, LiquidGlassGeom.centeredScale(1.4f, 180f, 100f, 0f, 280f), 0f)
        for (width in listOf(80f, 160f, 320f)) for (desired in listOf(0.95f, 1f, 1.4f, 2.7f)) {
            val scale = LiquidGlassGeom.fittingScale(desired, width, 320f)
            for (position in listOf(-200f, 0f, 100f, 500f)) {
                val x = DropletController.clampTranslation(position, width, 0f, 320f, scale)
                val bulge = (scale - 1f) * width / 2
                assertTrue(x - bulge >= -0.001f)
                assertTrue(x + width + bulge <= 320.001f)
            }
        }
    }
}
