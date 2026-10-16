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
                1280, 0, 0, 0, 136, 3, (42 * density).roundToInt(), density, -1)!!
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
            safeLeft, safeRight, navigation, 136, 3, controlHeight, 2f, config.bottomGapDp)

    @Test fun replyUsesItsOwnHeightAndMarginsWithTheSharedCenter() {
        assertEquals(PostReplyGlassGeometry.Bounds(48, 1116, 624, 96), bounds())
        assertEquals(180, PostReplyGlassGeometry.contentBottomPadding(0, 1280, 1116, 2f))
        assertEquals(240, PostReplyGlassGeometry.contentBottomPadding(240, 1280, 1120, 2f))
    }

    @Test fun edgeToEdgeAndConsumedNavigationHaveTheSamePositionWhenTheBarFits() {
        for (navigation in listOf(48, 96)) {
            for (gap in listOf(-1, 120)) {
                val config = BottomTabLiquidGlassConfig(bottomGapDp = gap)
                assertEquals(bounds(navigation = navigation, config = config),
                    bounds(height = 1280 - navigation, navigation = navigation, config = config))
            }
        }
    }

    @Test fun manualReplyPlacementIgnoresNavigationWithoutRestoringNativeLayout() {
        for (gap in listOf(0, 28, 60, 120)) {
            val config = BottomTabLiquidGlassConfig(bottomGapDp = gap)
            val withoutNavigation = bounds(config = config)!!
            for (navigation in listOf(48, 96)) {
                assertEquals(withoutNavigation, bounds(navigation = navigation, config = config))
            }
        }
        val low = bounds(navigation = 96, config = BottomTabLiquidGlassConfig(bottomGapDp = 0))!!
        assertEquals(1172, low.top)
        assertTrue(low.top + low.height > 1280 - 96)
    }

    @Test fun manualReplyPlacementStillRespectsTheActualHostBounds() {
        val box = bounds(height = 1184, navigation = 96,
            config = BottomTabLiquidGlassConfig(bottomGapDp = 0))!!
        assertEquals(1184, box.top + box.height)
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

    @Test fun homeAndReplyCentersMatchWheneverTheHostCanFitThem() {
        for (density in listOf(1f, 1.25f, 2f, 2.625f, 3f, 4f)) {
            fun px(dp: Int) = (dp * density).roundToInt()
            for (navigation in listOf(0, px(24))) for (gap in listOf(-1, 0, 20, 40, 80, 120)) {
                val windowHeight = px(800)
                val homeHeight = px(60)
                val homeTop = LiquidGlassBarMetrics.floatingTop(windowHeight, windowHeight, windowHeight,
                    navigation, homeHeight, density, gap)
                for (consumed in listOf(0, navigation)) {
                    val hostTop = px(24)
                    val reply = PostReplyGlassGeometry.calculate(
                        px(411), windowHeight - hostTop - consumed, 0, hostTop,
                        0, px(411), windowHeight, 0, 0, navigation,
                        px(68), 3, px(42), density, gap, nativeHeight = px(48),
                    )!!
                    val center = homeTop + homeHeight / 2f
                    val hostBottom = windowHeight - consumed
                    if (center + reply.height / 2f <= hostBottom) {
                        assertEquals(center, hostTop + reply.top + reply.height / 2f, 0.5f)
                    } else {
                        assertEquals(hostBottom, hostTop + reply.top + reply.height)
                    }
                    val limit = if (gap < 0) windowHeight - navigation else hostBottom
                    assertTrue(hostTop + reply.top + reply.height <= limit)
                }
            }
        }
    }

    @Test fun densityAndWindowChangesKeepTheRatioWithoutShrinkingButtonsBelowTheirMinimum() {
        for (density in listOf(1f, 1.25f, 1.5f, 2f, 2.625f, 3f, 4f)) {
            fun px(dp: Int) = (dp * density).roundToInt()
            for (windowDp in listOf(280, 320, 360, 411, 600)) {
                val box = PostReplyGlassGeometry.calculate(px(windowDp), px(640), 0, 0, 0, px(windowDp),
                    px(640), 0, 0, px(24), px(68), 3, px(42), density, -1)!!
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
            0, 0, 0, 136, 0, 63, 2f, -1, nativeHeight = 96)!!
        assertEquals(bounds(), inputOnly)
        assertNull(PostReplyGlassGeometry.separate(inputOnly.width, 0, 2f))
    }

    @Test fun pendingAndNativeActionsShareBothSurfacesBeforeControlMeasurement() {
        for (width in listOf(480, 720, 1080)) {
            fun box(actions: Int, controlHeight: Int) = PostReplyGlassGeometry.calculate(
                width, 1280, 0, 0, 0, width, 1280, 0, 0, 0, 136,
                actions, controlHeight, 2f, -1, nativeHeight = 95)!!
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

    @Test fun visibleReplyKeepsItsStyleUntilHostHidingEditingOrDisablement() {
        // Window focus is deliberately not a presentation input: the system still draws
        // unfocused windows during exit/resume and while producing recent-task snapshots.
        assertTrue(PostReplyGlassGeometry.shouldFloat(true, true, false, false))
        assertFalse(PostReplyGlassGeometry.shouldFloat(false, true, false, false))
        assertFalse(PostReplyGlassGeometry.shouldFloat(true, false, false, false))
        assertFalse(PostReplyGlassGeometry.shouldFloat(true, true, true, false))
        assertFalse(PostReplyGlassGeometry.shouldFloat(true, true, false, true))
    }

    @Test fun tabWidthAndBlurDoNotChangeReplyGeometry() {
        for (width in listOf(100, 115, 200)) for (blur in listOf(0, 25, 100)) {
            assertEquals(bounds(), bounds(config = BottomTabLiquidGlassConfig(
                dropletWidthPercent = width, blurPercent = blur)))
        }
    }
}
