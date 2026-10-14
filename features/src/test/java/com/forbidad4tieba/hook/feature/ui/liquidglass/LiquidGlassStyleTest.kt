package com.forbidad4tieba.hook.feature.ui.liquidglass

import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassStyleTest {
    private val config = BottomTabLiquidGlassConfig.DEFAULT.copy(blurPercent = 25)

    @Test fun tabAndReplyUseTheSameMaterialInBothSkins() {
        for (night in listOf(false, true)) {
            val tab = LiquidGlassStyle.params(620, 112, 2f, night, config)
            val reply = LiquidGlassStyle.params(660, 104, 2f, night, config, 386, 402)
            assertEquals(tab, reply.copy(inputWidth = 0, actionsLeft = 0, touchX = tab.touchX))
            assertEquals(386, reply.inputWidth)
            assertEquals(402, reply.actionsLeft)
        }
    }

    @Test fun logicalDpiKeepsTheReferenceOpticsAndPadding() {
        for (density in listOf(1f, 1.5f, 2f, 2.5f, 3f, 4f)) {
            val p = LiquidGlassStyle.params((300 * density).toInt(), (56 * density).toInt(), density, false, config)
            assertEquals(4f, p.blurRadius / density, 0.001f)
            assertEquals(24f, p.refractionHeight / density, 0.001f)
            assertEquals(24f, p.refractionAmount / density, 0.001f)
            assertEquals(1.08f, p.saturation, 0f)
            assertEquals(1.04f, p.contrast, 0f)
            assertFalse(p.depthEffect)
            assertTrue(LiquidGlassStyle.margin(p) >= 40f * density)
        }
    }

    @Test fun switchesDisableTheirShaderWorkForEverySurface() {
        val disabled = config.copy(blurEnabled = false, liquidGlassEnabled = false)
        for (droplet in listOf(false, true)) {
            val p = LiquidGlassStyle.params(620, 112, 2f, false, disabled, droplet = droplet, press = 1f)
            assertEquals(0f, p.blurRadius, 0f)
            assertEquals(0f, p.refractionAmount, 0f)
            assertEquals(0f, p.dispersion, 0f)
            assertEquals(0f, p.highlightAlpha, 0f)
            assertEquals(0f, p.glowProgress, 0f)
        }
        assertFalse(disabled.needsBackdrop)
        val flat = LiquidGlassStyle.params(620, 112, 2f, false, disabled)
        assertEquals(255, flat.tint ushr 24)
        assertEquals(0, LiquidGlassStyle.margin(flat))
    }

    @Test fun normalBlurCanBeUsedWithoutGlassAndGlassKeepsItsIntrinsicBlur() {
        val blurred = config.copy(liquidGlassEnabled = false)
        val blur = LiquidGlassStyle.params(620, 112, 2f, false, blurred, press = 1f)
        assertTrue(blurred.needsBackdrop)
        assertEquals(8f, blur.blurRadius, 0f)
        assertEquals(0f, blur.refractionAmount, 0f)
        assertEquals(0f, blur.highlightAlpha, 0f)
        assertEquals(0f, blur.glowProgress, 0f)
        val clear = config.copy(blurEnabled = false)
        val glass = LiquidGlassStyle.params(620, 112, 2f, false, clear, press = 1f)
        assertTrue(clear.needsBackdrop)
        assertEquals(8f, glass.blurRadius, 0f)
        assertEquals(LiquidGlassStyle.params(620, 112, 2f, false, config, press = 1f), glass)
        assertEquals(48f, glass.refractionAmount, 0f)
        assertTrue(glass.highlightAlpha > 0f)
    }

    @Test fun blurSliderControlsBothBarsAndModesWithoutBlurringTheLensTwice() {
        for (density in listOf(1f, 2f, 3f)) for (glass in listOf(false, true)) {
            for (percent in listOf(0, 25, 50, 100)) {
                val choice = config.copy(liquidGlassEnabled = glass, blurPercent = percent)
                val tab = LiquidGlassStyle.params(720, 112, density, false, choice)
                val reply = LiquidGlassStyle.params(672, 96, density, false, choice, 394, 410)
                assertEquals(16f * percent / 100f * density, tab.blurRadius, 0.001f)
                assertEquals(tab.blurRadius, reply.blurRadius, 0f)
                val lens = LiquidGlassStyle.params(112, 98, density, false, choice, droplet = true, press = 1f)
                assertEquals(0f, lens.blurRadius, 0f)
                assertEquals(1f, lens.saturation, 0f)
                assertEquals(1f, lens.contrast, 0f)
                assertEquals(if (percent == 0) 1f else 1.08f, tab.saturation, 0f)
                assertEquals(if (percent == 0) 1f else 1.04f, tab.contrast, 0f)
                assertEquals(tab.saturation, reply.saturation, 0f)
                assertEquals(tab.contrast, reply.contrast, 0f)
                assertEquals(glass || percent > 0, choice.needsBackdrop)
            }
        }
    }

    @Test fun zeroBlurKeepsBothBarsTranslucentWithoutCapturingThePage() {
        val clear = config.copy(blurEnabled = true, liquidGlassEnabled = false, blurPercent = 0)
        assertFalse(clear.needsBackdrop)
        for (night in listOf(false, true)) for (split in listOf(false, true)) {
            fun material(percent: Int) = LiquidGlassStyle.params(
                672, 96, 2f, night, clear.copy(blurPercent = percent),
                inputWidth = if (split) 394 else 0,
                actionsLeft = if (split) 410 else 0,
            )
            val zero = material(0)
            assertTrue("Zero blur must still show the page through the surface", (zero.tint ushr 24) in 1..254)
            assertEquals(material(1).tint, zero.tint)
            assertEquals(material(100).tint, zero.tint)
            assertEquals(0f, zero.blurRadius, 0f)
            assertEquals(0f, zero.refractionAmount, 0f)
            assertEquals(1f, zero.saturation, 0f)
            assertEquals(1f, zero.contrast, 0f)
            assertEquals(0, LiquidGlassStyle.margin(zero))
        }
    }

    @Test fun surfaceOverlayPreservesEachSkinAndSupportsAnOpaqueFallback() {
        assertEquals(0xB3FFFFFF.toInt(), LiquidGlassStyle.surfaceTint(night = false, translucent = true))
        assertEquals(0xB3000000.toInt(), LiquidGlassStyle.surfaceTint(night = true, translucent = true))
        assertEquals(0xFFFFFFFF.toInt(), LiquidGlassStyle.surfaceTint(night = false, translucent = false))
        assertEquals(0xFF000000.toInt(), LiquidGlassStyle.surfaceTint(night = true, translucent = false))
    }

    @Test fun pressedInnerMaterialRecedesWhileTheLensExpands() {
        val rest = LiquidGlassStyle.tabBackdropScale(0f)
        val held = LiquidGlassStyle.tabBackdropScale(1f)
        assertEquals(1f, rest, 0f)
        assertEquals(0.95f, held, 0f)
        assertTrue(held < rest)
        // The clear margin grows at every intermediate pressure, at every physical density.
        for (density in listOf(1f, 1.5f, 2f, 3f, 4f)) {
            var previous = 0f
            for (step in 0..10) {
                val press = step / 10f
                val lens = 49f * density * (1f + (DropletMotion.HELD_SCALE - 1f) * press)
                val inner = 49f * density * LiquidGlassStyle.tabBackdropScale(press)
                val margin = (lens - inner) / density
                assertTrue(margin >= previous)
                previous = margin
            }
            assertTrue(previous > 49f * (DropletMotion.HELD_SCALE - 1f))
        }
        assertEquals(rest, LiquidGlassStyle.tabBackdropScale(-1f), 0f)
        assertEquals(held, LiquidGlassStyle.tabBackdropScale(2f), 0f)
    }

    @Test fun selectionTintGivesWayToTheClearDepthLensOnPress() {
        for (night in listOf(false, true)) {
            val rest = LiquidGlassStyle.params(96, 88, 2f, night, config, droplet = true)
            val held = LiquidGlassStyle.params(96, 88, 2f, night, config, droplet = true, press = 1f)
            assertEquals(0f, rest.refractionAmount, 0f)
            assertEquals(0f, rest.highlightAlpha, 0f)
            assertEquals(20f, held.refractionHeight, 0f)
            assertEquals(28f, held.refractionAmount, 0f)
            assertEquals(0.5f, held.dispersion, 0f)
            assertEquals(1f, held.highlightAlpha, 0f)
            assertEquals(0, held.tint ushr 24)
            assertTrue((rest.tint ushr 24) > (held.pressedTint ushr 24))
            assertTrue(held.depthEffect)
            assertEquals(0f, held.blurRadius, 0f)
        }
    }
}
