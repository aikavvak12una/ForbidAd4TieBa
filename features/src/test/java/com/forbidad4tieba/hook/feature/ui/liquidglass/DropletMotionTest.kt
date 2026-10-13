package com.forbidad4tieba.hook.feature.ui.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DropletMotionTest {
    private fun settle(motion: DropletMotion) {
        repeat(180) {
            if (!motion.advance(1f / 60f)) return
        }
        throw AssertionError("A resting gesture must stop requesting frames")
    }

    private fun held(span: Float = 300f) = DropletMotion().apply {
        setSpan(span)
        press()
        settle(this)
    }

    @Test fun stationaryHoldKeepsTheLensOpenWithoutRequestingFrames() {
        val motion = held()
        assertEquals(1f, motion.pressProgress, 0f)
        assertEquals(DropletMotion.HELD_SCALE, motion.scaleX, 0f)
        assertEquals(DropletMotion.HELD_SCALE, motion.scaleY, 0f)
        assertFalse(motion.isAnimating)
        assertFalse(motion.advance(1f))
    }

    @Test fun releaseKeepsTheLensExpandedUntilItApproachesItsDestination() {
        val motion = held()
        motion.moveTo(300f)
        motion.release()
        motion.advance(1f / 30f)
        assertTrue(motion.x < 150f)
        assertEquals(1f, motion.pressProgress, 0f)
        assertTrue(motion.scaleX > 1.3f)
        settle(motion)
        assertEquals(300f, motion.x, 0f)
        assertEquals(0f, motion.pressProgress, 0f)
        assertEquals(1f, motion.scaleX, 0f)
        assertEquals(1f, motion.scaleY, 0f)
    }

    @Test fun movementSpeedChangesStretchAndStoppingTheFingerRemovesIt() {
        val fast = held()
        val slow = held()
        fast.moveTo(300f)
        slow.moveTo(24f)
        fast.advance(1f / 30f)
        slow.advance(1f / 30f)
        assertTrue(fast.scaleX > slow.scaleX)
        settle(fast)
        settle(slow)
        assertEquals(DropletMotion.HELD_SCALE, fast.scaleX, 0f)
        assertEquals(DropletMotion.HELD_SCALE, slow.scaleX, 0f)
        assertFalse(fast.isAnimating)
    }

    @Test fun changingDirectionRetainsPositionAndEventuallySettlesAtTheNewTarget() {
        val motion = held()
        motion.moveTo(300f)
        motion.advance(0.04f)
        val before = motion.x
        motion.moveTo(0f)
        assertEquals(before, motion.x, 0f)
        assertEquals(0f, motion.targetX, 0f)
        settle(motion)
        assertEquals(0f, motion.x, 0f)
        assertEquals(DropletMotion.HELD_SCALE, motion.scaleX, 0f)
    }

    @Test fun logicalDensityAndFrameRateDoNotChangeTheTravelPath() {
        for (density in listOf(1f, 1.5f, 2f, 2.625f, 4f)) {
            val reference = held(300f * density)
            reference.moveTo(300f * density)
            reference.advance(0.16f)
            for (frames in listOf(5, 10, 20)) {
                val sampled = held(300f * density)
                sampled.moveTo(300f * density)
                repeat(frames) { sampled.advance(0.16f / frames) }
                assertEquals(reference.x / density, sampled.x / density, 0.001f)
                settle(sampled)
                assertEquals(300f, sampled.x / density, 0.001f)
            }
        }
    }

    @Test fun cancellationClearsEveryPendingMotionAndPressure() {
        val motion = held()
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

    @Test fun glowReleasesImmediatelyWhileTheTravellingLensRemainsExpanded() {
        val motion = held()
        motion.touchDown()
        settle(motion)
        motion.dragBy(160f)
        motion.moveTo(300f)
        motion.touchUp()
        motion.release()
        motion.advance(1f / 30f)
        assertTrue(motion.glowProgress < 1f)
        assertTrue(motion.accumulatedDrag in 0f..160f)
        assertEquals(1f, motion.pressProgress, 0f)
        settle(motion)
        assertEquals(0f, motion.glowProgress, 0f)
        assertEquals(0f, motion.accumulatedDrag, 0f)
        assertFalse(motion.isAnimating)
    }

    @Test fun disabledFeedbackStillMovesSelectionWithoutAnyDeformation() {
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

    @Test fun expandedLensCanLeaveTheBarButNeverTheWindow() {
        for (density in listOf(1f, 1.5f, 2f, 3f, 4f)) {
            val width = 44f * density
            val left = 4f * density
            val available = 280f * density
            for (scale in listOf(1f, DropletMotion.HELD_SCALE, 1.75f)) {
                for (x in listOf(-1000f, 0f, 60f, 240f, 1000f)) {
                    val position = DropletController.clampTranslation(x * density, width, left, available, scale)
                    val bulge = (scale - 1f) * width / 2f
                    assertTrue(position - bulge >= left - 0.001f)
                    assertTrue(position + width + bulge <= left + available + 0.001f)
                }
            }
        }
    }
}
