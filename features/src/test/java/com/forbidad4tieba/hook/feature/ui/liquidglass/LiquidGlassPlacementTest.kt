package com.forbidad4tieba.hook.feature.ui.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassPlacementTest {
    @Test fun explicitGapIncludesConsumedInsetsOnlyOnce() {
        for (gap in listOf(0, 20, 80, 120)) {
            val full = LiquidGlassBarMetrics.floatingTop(1280, 1280, 1280, 96, 160, 2f, gap)
            val inset = LiquidGlassBarMetrics.floatingTop(1184, 1184, 1280, 96, 160, 2f, gap)
            assertEquals(full, inset)
            assertEquals(gap * 2, 1184 - full - 160)
        }
    }

    @Test fun configuredWidthIncludesPaddingAndAdaptsToWindowAndTabCount() {
        for (available in listOf(640, 720, 1080)) {
            for (count in 1..5) {
                for (percent in listOf(60, 80, 100)) {
                    val column = LiquidGlassBarMetrics.configuredColumnWidth(available, count, 2f, percent)
                    val expected = (available * percent / 100f).toInt()
                    val actual = column * count + 24
                    org.junit.Assert.assertTrue("$actual vs $expected", actual in (expected - count + 1)..expected)
                }
            }
        }
    }

    @Test fun noNavigationAreaLeavesTwentyEightDpAtTheScreenBottom() {
        for (density in listOf(1f, 2f, 3f)) {
            val height = (64 * density).toInt()
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
