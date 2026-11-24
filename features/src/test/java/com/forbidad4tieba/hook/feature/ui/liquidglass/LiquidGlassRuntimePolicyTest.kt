package com.forbidad4tieba.hook.feature.ui.liquidglass

import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassRuntimePolicyTest {
    private val config = BottomTabLiquidGlassConfig.DEFAULT

    @Test
    fun disabledMaterialRequestsNoBackdropOrShaderWork() {
        val disabled = config.copy(blurEnabled = false, liquidGlassEnabled = false)
        for (droplet in listOf(false, true)) {
            val params = LiquidGlassStyle.params(
                620, 112, 2f, false, disabled, droplet = droplet, press = 1f,
            )
            assertEquals(0f, params.blurRadius, 0f)
            assertEquals(0f, params.refractionAmount, 0f)
            assertEquals(0f, params.dispersion, 0f)
            assertEquals(0f, params.highlightAlpha, 0f)
            assertEquals(0f, params.glowProgress, 0f)
        }
        assertFalse(disabled.needsBackdrop)
        val flat = LiquidGlassStyle.params(620, 112, 2f, false, disabled)
        assertEquals(255, flat.tint ushr 24)
        assertEquals(0, LiquidGlassStyle.margin(flat))
    }

    @Test
    fun zeroBlurKeepsThePageVisibleThroughBothBarsWithoutCapture() {
        val clear = config.copy(blurEnabled = true, liquidGlassEnabled = false, blurPercent = 0)
        assertFalse(clear.needsBackdrop)
        for (night in listOf(false, true)) {
            for (split in listOf(false, true)) {
                val zero = LiquidGlassStyle.params(
                    672, 96, 2f, night, clear,
                    inputWidth = if (split) 394 else 0,
                    actionsLeft = if (split) 410 else 0,
                )
                assertTrue((zero.tint ushr 24) in 1..254)
                assertEquals(0f, zero.blurRadius, 0f)
                assertEquals(0f, zero.refractionAmount, 0f)
                assertEquals(0, LiquidGlassStyle.margin(zero))
            }
        }
    }

    @Test
    fun selectionMotionConvergesAndCancellationClearsItsDebt() {
        val motion = held()
        motion.moveTo(300f)
        motion.advance(0.04f)
        val beforeReversal = motion.x
        motion.moveTo(0f)
        assertEquals(beforeReversal, motion.x, 0f)
        settle(motion)
        assertEquals(0f, motion.x, 0f)
        assertEquals(DropletMotion.HELD_SCALE, motion.scaleX, 0f)

        motion.moveTo(300f)
        motion.release()
        motion.advance(0.05f)
        motion.snapTo(0f)
        assertFalse(motion.isAnimating)
        assertFalse(motion.advance(0.5f))
        assertEquals(0f, motion.x, 0f)
        assertEquals(0f, motion.pressProgress, 0f)
        assertEquals(1f, motion.scaleX, 0f)
        assertEquals(1f, motion.scaleY, 0f)
    }

    @Test
    fun disabledFeedbackStillMovesSelectionWithoutDeformation() {
        val motion = DropletMotion(effectsEnabled = false)
        motion.setSpan(300f)
        motion.press()
        motion.moveTo(300f)
        motion.release()
        repeat(60) {
            motion.advance(1f / 60f)
            assertEquals(0f, motion.pressProgress, 0f)
            assertEquals(1f, motion.scaleX, 0f)
            assertEquals(1f, motion.scaleY, 0f)
        }
        assertEquals(300f, motion.x, 0f)
        assertFalse(motion.isAnimating)
    }

    @Test
    fun autoHideAccumulatesResetsAndRestoresNativeHiddenState() {
        val state = PostReplyAutoHideState()
        assertNull(state.onScroll(100, 1000, 128))
        assertEquals(true, state.onScroll(28, 1016, 128))
        assertNull(state.onScroll(-23, 1900, 24))
        assertEquals(false, state.onScroll(-1, 1916, 24))

        state.onScroll(128, 1000, 128)
        state.hideThresholdPx = 256
        state.showThresholdPx = 48
        state.reset()
        assertFalse(state.hidden)
        assertEquals(0, state.accumulatedDy)
        assertEquals(0, state.hideThresholdPx)
        assertEquals(0, state.showThresholdPx)

        state.reset(initiallyHidden = true)
        assertNull(state.onScroll(300, 1000, 128))
        assertTrue(state.hidden)
        assertEquals(false, state.onScroll(-24, 2000, 24))
    }

    private fun held(): DropletMotion = DropletMotion().apply {
        setSpan(300f)
        press()
        settle(this)
    }

    private fun settle(motion: DropletMotion) {
        repeat(180) {
            if (!motion.advance(1f / 60f)) return
        }
        throw AssertionError("A resting gesture must stop requesting frames")
    }
}
