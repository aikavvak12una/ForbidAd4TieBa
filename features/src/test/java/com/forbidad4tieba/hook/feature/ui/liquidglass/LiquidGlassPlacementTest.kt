package com.forbidad4tieba.hook.feature.ui.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun explicitGapIncludesConsumedInsetsOnlyOnce() {
        for (gap in listOf(0, 20, 80, 120)) {
            val full = LiquidGlassBarMetrics.floatingTop(1280, 1280, 1280, 96, 160, 2f, gap)
            val inset = LiquidGlassBarMetrics.floatingTop(1184, 1184, 1280, 96, 160, 2f, gap)
            assertEquals(full, inset)
            assertEquals(gap * 2, 1184 - full - 160)
        }
    }

    @Test fun configuredWidthKeepsCirclesAndAdaptsToDensityWindowAndTabCount() {
        for (density in listOf(1f, 1.25f, 1.5f, 2f, 2.625f, 3f, 4f)) {
            fun px(dp: Int) = (dp * density).roundToInt()
            for (windowDp in listOf(220, 260, 320, 360, 411, 600)) for (count in 1..5) {
                for (heightDp in listOf(56, 72)) {
                val height = px(heightDp)
                val column = LiquidGlassBarMetrics.columnWidth(px(windowDp), count, height, density, 100)
                val outerWidth = column * count + 2 * (LiquidGlassBarMetrics.HORIZONTAL_PADDING_DP * density).roundToInt()
                assertTrue(outerWidth <= px(windowDp))
                assertEquals(column, LiquidGlassBarMetrics.dropletHeight(column, height))
                if (windowDp >= 360 && heightDp == 56) assertTrue(column >= px(48))
                var previous = column
                for (percent in listOf(115, 150, 200)) {
                    val wider = LiquidGlassBarMetrics.columnWidth(px(windowDp), count, height, density, percent)
                    assertTrue(wider >= previous)
                    assertTrue(wider * count + 2 * (LiquidGlassBarMetrics.HORIZONTAL_PADDING_DP * density).roundToInt() <= px(windowDp))
                    assertEquals(column, LiquidGlassBarMetrics.dropletHeight(wider, height))
                    previous = wider
                }
                }
            }
        }
    }

    @Test fun unconstrainedWidthTracksTheActualLensHeightAndClampsInvalidPercentages() {
        assertEquals(98, LiquidGlassBarMetrics.columnWidth(1200, 4, 112, 2f, 100))
        assertEquals(113, LiquidGlassBarMetrics.columnWidth(1200, 4, 112, 2f, 115))
        assertEquals(196, LiquidGlassBarMetrics.columnWidth(1200, 4, 112, 2f, 200))
        assertEquals(98, LiquidGlassBarMetrics.columnWidth(1200, 4, 112, 2f, -1))
        assertEquals(196, LiquidGlassBarMetrics.columnWidth(1200, 4, 112, 2f, 999))
        assertEquals(0, LiquidGlassBarMetrics.columnWidth(0, 4, 112, 2f, 100))
        assertEquals(0, LiquidGlassBarMetrics.columnWidth(720, 0, 112, 2f, 100))
    }

    @Test fun tabBaseHeightIsSixtyDp() {
        assertEquals(60f, LiquidGlassBarMetrics.TAB_HEIGHT_DP, 0f)
    }

    @Test fun noNavigationAreaLeavesTwentyEightDpAtTheScreenBottom() {
        for (density in listOf(1f, 2f, 3f)) {
            val height = (LiquidGlassBarMetrics.TAB_HEIGHT_DP * density).roundToInt()
            val top = LiquidGlassBarMetrics.floatingTop(2400, 2400, 2400, 0, height, density)
            assertEquals((28 * density).toInt(), 2400 - top - height)
        }
    }

    @Test fun visibleNavigationAreaHasEightDpClearance() {
        for (navigation in listOf(48, 96)) {
            val top = LiquidGlassBarMetrics.floatingTop(1280, 1280, 1280, navigation, 128, 2f)
            assertEquals(16, 1280 - navigation - top - 128)
        }
    }

    @Test fun ancestorConsumedNavigationInsetIsNotCountedAgain() {
        val edgeToEdge = LiquidGlassBarMetrics.floatingTop(1280, 1280, 1280, 96, 128, 2f)
        val alreadyInset = LiquidGlassBarMetrics.floatingTop(1184, 1184, 1280, 96, 128, 2f)
        assertEquals(edgeToEdge, alreadyInset)
    }

    @Test fun statusBarOffsetAndLargerTextKeepTheSameBottomEdge() {
        val top = LiquidGlassBarMetrics.floatingTop(1232, 1280, 1280, 0, 128, 2f)
        val taller = LiquidGlassBarMetrics.floatingTop(1232, 1280, 1280, 0, 192, 2f)
        assertEquals(56, 1280 - (48 + top + 128))
        assertEquals(top + 128, taller + 192)
    }
}
