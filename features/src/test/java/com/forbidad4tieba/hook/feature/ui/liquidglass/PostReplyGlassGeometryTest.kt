package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.view.Gravity
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class PostReplyGlassGeometryTest {
    @Test fun presetRatioSurvivesDensityChangesOnTheSameScreen() {
        for (density in listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)) {
            val box = PostReplyGlassGeometry.calculate(720, 1280, 0, 0, 0, 720,
                1280, 0, 0, 0, 136, 3, (42 * density).roundToInt(), density, BottomTabLiquidGlassConfig.DEFAULT)!!
            val split = PostReplyGlassGeometry.separate(box.width, 3, density)!!
            val usable = box.width - (PostReplyGlassGeometry.SURFACE_GAP_DP * density).roundToInt()
            assertEquals("density=$density", (usable * 0.618f).roundToInt(), split.inputWidth)
        }
    }

    @Test fun separateSurfacesKeepAnOpenGapAndSymmetricActionInsets() {
        val split = PostReplyGlassGeometry.separate(672, 3, 2f)!!
        assertEquals(405, split.inputWidth)
        assertEquals(421, split.actionsLeft)
        assertEquals(16, split.actionsLeft - split.inputWidth)
        assertEquals(235, split.actionsContentWidth)
        assertEquals(16, 672 - split.actionsLeft - split.actionsContentWidth)
        assertEquals(435, PostReplyGlassGeometry.minimumWidth(3, 2f))
        assertNull(PostReplyGlassGeometry.separate(434, 3, 2f))
        assertNotNull(PostReplyGlassGeometry.separate(435, 3, 2f))
    }

    @Test fun nativeNormalizedGravityMustSettleInsteadOfCancellingEveryDraw() {
        // LinearLayout.setGravity(CENTER_VERTICAL) stores START | CENTER_VERTICAL.
        assertFalse(PostReplyGlassGeometry.needsVerticalCenter(Gravity.START or Gravity.CENTER_VERTICAL))
        assertFalse(PostReplyGlassGeometry.needsVerticalCenter(Gravity.END or Gravity.CENTER_VERTICAL))
        assertTrue(PostReplyGlassGeometry.needsVerticalCenter(Gravity.START or Gravity.TOP))
    }

    private fun bounds(width: Int = 720, height: Int = 1280, hostLeft: Int = 0,
        windowRight: Int = 720, safeLeft: Int = 0, safeRight: Int = 0, navigation: Int = 0,
        controlHeight: Int = 84, config: BottomTabLiquidGlassConfig = BottomTabLiquidGlassConfig.DEFAULT) =
        PostReplyGlassGeometry.calculate(width, height, hostLeft, 0, 0, windowRight, 1280,
            safeLeft, safeRight, navigation, 136, 3, controlHeight, 2f, config)

    @Test fun replyUsesItsOwnHeightAndMarginsWithTheSharedCenter() {
        // The shared center follows the reference 64dp bar; reply controls keep their own height.
        assertEquals(PostReplyGlassGeometry.Bounds(48, 1112, 624, 96), bounds())
        assertEquals(184, PostReplyGlassGeometry.contentBottomPadding(0, 1280, 1112, 2f))
        assertEquals(240, PostReplyGlassGeometry.contentBottomPadding(240, 1280, 1120, 2f))
    }

    @Test fun edgeToEdgeAndConsumedNavigationHaveTheSameDefaultAndRaisedPosition() {
        for (navigation in listOf(48, 96)) for (position in listOf(0, 50, 100)) {
            val config = BottomTabLiquidGlassConfig(positionAdjustment = position)
            assertEquals(bounds(navigation = navigation, config = config),
                bounds(height = 1280 - navigation, navigation = navigation, config = config))
        }
    }

    @Test fun lowestReplyPositionMayRetainItsNaturalHeightDifference() {
        val config = BottomTabLiquidGlassConfig(positionAdjustment = -100)
        for (navigation in listOf(0, 48, 96)) {
            val box = bounds(navigation = navigation, config = config)!!
            assertEquals(16, 1280 - box.top - box.height)
        }
        val inset = bounds(height = 1184, navigation = 96, config = config)!!
        assertEquals(1184, inset.top + inset.height)
    }

    @Test fun landscapeCutoutAndSideNavigationAreCountedOnlyOnce() {
        val full = bounds(width = 1280, windowRight = 1280, safeLeft = 90, safeRight = 48)!!
        val inset = bounds(width = 1142, hostLeft = 90, windowRight = 1280,
            safeLeft = 90, safeRight = 48)!!
        assertEquals(full.width, inset.width)
        assertEquals(full.left, inset.left + 90)
        assertTrue(full.left >= 90)
        assertTrue(full.left + full.width <= 1232)
    }

    @Test fun narrowWindowsKeepControlsUsableAndImpossibleWindowsUseNativeLayout() {
        assertEquals(435, bounds(width = 480)!!.width)
        assertNull(bounds(width = 400))
        assertNull(bounds(height = 200))
    }

    @Test fun largerControlsExpandAroundTheSameCenter() {
        val large = bounds(controlHeight = 144)!!
        assertEquals(156, large.height)
        val normal = bounds()!!
        assertEquals(normal.top + normal.height / 2, large.top + large.height / 2)
    }

    @Test fun homeAndReplyShareTheSameTranslationFromTheirOwnDefaults() {
        for (density in listOf(1f, 1.25f, 2f, 2.625f, 3f, 4f)) {
            fun px(dp: Int) = (dp * density).roundToInt()
            for (navigation in listOf(0, px(24))) for (position in listOf(-100, -50, 0, 50, 100)) {
                val windowHeight = px(800)
                val homeHeight = px(64)
                val config = BottomTabLiquidGlassConfig(positionAdjustment = position)
                val home = LiquidGlassBarMetrics.placement(windowHeight, windowHeight, windowHeight,
                    navigation, homeHeight, homeHeight, density, config)!!
                for (consumed in listOf(0, navigation)) {
                    val hostTop = px(24)
                    val reply = PostReplyGlassGeometry.calculate(
                        px(411), windowHeight - hostTop - consumed, 0, hostTop, 0, px(411), windowHeight,
                        0, 0, navigation, px(68), 3, px(42), density, config, nativeHeight = px(48),
                    )!!
                    val centre = home.top + home.height / 2f
                    val hostBottom = windowHeight - consumed
                    if (centre + reply.height / 2f <= hostBottom)
                        assertEquals(centre, hostTop + reply.top + reply.height / 2f, 0.5f)
                    else assertEquals(hostBottom, hostTop + reply.top + reply.height)
                    assertTrue(hostTop + reply.top + reply.height <= hostBottom)
                }
            }
        }
    }

    @Test fun densityAndWindowChangesKeepTheRatioWithoutShrinkingButtonsBelowTheirMinimum() {
        for (density in listOf(1f, 1.25f, 1.5f, 2f, 2.625f, 3f, 4f)) {
            fun px(dp: Int) = (dp * density).roundToInt()
            for (windowDp in listOf(280, 320, 360, 411, 600)) {
                val box = PostReplyGlassGeometry.calculate(px(windowDp), px(640), 0, 0, 0, px(windowDp),
                    px(640), 0, 0, px(24), px(68), 3, px(42), density, BottomTabLiquidGlassConfig.DEFAULT)!!
                val split = PostReplyGlassGeometry.separate(box.width, 3, density)!!
                assertTrue(box.height in px(48)..(px(48) + 1))
                // Independently rounded minimum control widths can consume one margin pixel.
                val rightMargin = px(windowDp) - box.left - box.width
                assertTrue(box.left in (px(24) - 1)..px(24))
                assertTrue(rightMargin in (px(24) - 1)..px(24))
                assertTrue(kotlin.math.abs(box.left - rightMargin) <= 1)
                assertEquals(px(8), split.actionsLeft - split.inputWidth)
                assertTrue(split.actionsContentWidth / 3 >= px(24))
                assertTrue(box.left >= 0 && box.left + box.width <= px(windowDp))
                assertEquals(0.618f,
                    split.inputWidth.toFloat() / (box.width - px(8)), 0.003f)
            }
        }
    }

    @Test fun inputOnlyBarsKeepNativeHeightWithoutAnEmptyActionSurface() {
        val inputOnly = PostReplyGlassGeometry.calculate(720, 1280, 0, 0, 0, 720, 1280,
            0, 0, 0, 136, 0, 63, 2f, BottomTabLiquidGlassConfig.DEFAULT, nativeHeight = 96)!!
        assertEquals(bounds(), inputOnly)
        assertNull(PostReplyGlassGeometry.separate(inputOnly.width, 0, 2f))
    }

    @Test fun pendingAndNativeActionsShareBothSurfacesBeforeControlMeasurement() {
        for (width in listOf(480, 720, 1080)) {
            fun box(actions: Int, controlHeight: Int) = PostReplyGlassGeometry.calculate(
                width, 1280, 0, 0, 0, width, 1280, 0, 0, 0, 136,
                actions, controlHeight, 2f, BottomTabLiquidGlassConfig.DEFAULT, nativeHeight = 95)!!
            val pending = box(PostReplyGlassGeometry.PENDING_ACTION_COUNT, 63)
            val ready = box(3, 83)
            assertEquals(ready, pending)
            assertEquals(95, pending.height)
            val split = PostReplyGlassGeometry.separate(pending.width, PostReplyGlassGeometry.PENDING_ACTION_COUNT, 2f)!!
            assertEquals(PostReplyGlassGeometry.separate(ready.width, 3, 2f), split)
            assertEquals(16, split.actionsLeft - split.inputWidth)
            assertTrue(split.actionsContentWidth / PostReplyGlassGeometry.PENDING_ACTION_COUNT >= 48)
        }
    }

    @Test fun smallerManualHeightKeepsTheNativeReservationThroughControlReplacement() {
        assertEquals(36f, PostReplyGlassGeometry.HEIGHT_DP, 0f)
        for (controlHeight in listOf(0, 63, 75, 83)) {
            assertEquals(95, PostReplyGlassGeometry.height(controlHeight, 2f, nativeHeight = 95))
        }
        // The native slot is already padded; add spacing only to the intrinsic controls.
        assertEquals(132, PostReplyGlassGeometry.height(120, 2f, nativeHeight = 95))
        assertEquals(144, PostReplyGlassGeometry.height(83, 2f, nativeHeight = 144))
    }

    @Test fun loadingIconsReserveTheNumberLineAndItsGapBeforeDataArrives() {
        assertEquals(63, PostReplyGlassGeometry.actionContentHeight(38, 23, 2f))
        assertEquals(38, PostReplyGlassGeometry.actionContentHeight(38, 0, 2f))
        for (density in listOf(1f, 1.5f, 2f, 2.625f, 3f, 4f)) {
            val icon = (19 * density).roundToInt()
            val caption = (12 * density).roundToInt()
            val content = PostReplyGlassGeometry.actionContentHeight(icon, caption, density)
            val height = PostReplyGlassGeometry.height(content, density)
            val iconTop = (height - content) / 2
            assertTrue(iconTop >= 0)
            assertTrue(iconTop + icon + caption < height)
            assertTrue(kotlin.math.abs(iconTop - (height - iconTop - content)) <= 1)
        }
    }

    @Test fun loadingAndNativeColumnsUseTheSameRemainderPixels() {
        for (width in 435..1080) {
            val split = PostReplyGlassGeometry.separate(width, 3, 2f)!!
            val columns = (0..2).map { PostReplyGlassGeometry.actionColumnWidth(split.actionsContentWidth, it, 3) }
            assertEquals(split.actionsContentWidth, columns.sum())
            assertTrue(columns.max() - columns.min() <= 1)
            for (index in columns.indices) assertEquals(split.columnWidth(index, 3), columns[index])
        }
    }

    @Test fun visibleReplyKeepsItsStyleUntilHostHidingEditingOrDisablement() {
        // Window focus is deliberately not a presentation input: the system still draws
        // unfocused windows during exit/resume and while producing recent-task snapshots.
        assertTrue(PostReplyGlassGeometry.shouldFloat(true, true, false, false))
        assertFalse(PostReplyGlassGeometry.shouldFloat(false, true, false, false))
        assertFalse(PostReplyGlassGeometry.shouldFloat(true, false, false, false))
        assertFalse(PostReplyGlassGeometry.shouldFloat(true, true, true, false))
        assertFalse(PostReplyGlassGeometry.shouldFloat(true, true, false, true))
    }

    @Test fun replyWidthAffectsTheWholeGroupIndependentlyOfHomeAndBlur() {
        assertEquals(435, bounds(config = BottomTabLiquidGlassConfig(replyWidthAdjustment = -100))!!.width)
        assertEquals(624, bounds()!!.width)
        assertEquals(720, bounds(config = BottomTabLiquidGlassConfig(replyWidthAdjustment = 100))!!.width)
        for (adjustment in -100..100) {
            val choice = BottomTabLiquidGlassConfig(replyWidthAdjustment = adjustment)
            val box = bounds(config = choice)!!
            for (homeWidth in listOf(-100, 0, 100)) {
                assertEquals(box, bounds(config = choice.copy(homeWidthAdjustment = homeWidth)))
            }
            val split = PostReplyGlassGeometry.separate(box.width, 3, 2f)!!
            assertEquals(0.618f, split.inputWidth.toFloat() / (box.width - 16), 0.003f)
            for (blur in listOf(0, 25, 100)) assertEquals(box, bounds(config = choice.copy(blurPercent = blur)))
        }
    }

    @Test fun sharedHeightCanShrinkBelowTheNativeSlotWithoutMovingTheBottom() {
        fun box(height: Int) = PostReplyGlassGeometry.calculate(
            720, 1280, 0, 0, 0, 720, 1280, 0, 0, 0, 136, 3, 63, 2f,
            BottomTabLiquidGlassConfig(heightAdjustment = height), nativeHeight = 95,
        )!!
        val baseline = box(0)
        assertEquals(95, baseline.height)
        assertEquals(75, box(-100).height)
        assertTrue(box(100).height > baseline.height)
        for (adjustment in -100..100) {
            val next = box(adjustment)
            assertEquals(baseline.top + baseline.height, next.top + next.height)
            assertTrue(next.height >= 75 && next.top >= 136)
        }
    }


}
