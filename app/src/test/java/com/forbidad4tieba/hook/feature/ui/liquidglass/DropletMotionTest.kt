package com.forbidad4tieba.hook.feature.ui.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DropletMotionTest {
    @Test
    fun adjacentTabsHaveTheSameDeformationInBothDirections() {
        val right = trajectory(0f, 1f)
        val left = trajectory(1f, 0f)
        right.zip(left).forEachIndexed { frame, (forward, backward) ->
            assertEquals("horizontal scale at frame $frame", forward.first, backward.first, 0.0001f)
            assertEquals("vertical scale at frame $frame", forward.second, backward.second, 0.0001f)
        }
    }

    @Test
    fun longJumpsRemainSymmetricAndReturnToRest() {
        val right = trajectory(0f, 2f)
        val left = trajectory(2f, 0f)
        right.zip(left).forEachIndexed { frame, (forward, backward) ->
            assertEquals("horizontal scale at frame $frame", forward.first, backward.first, 0.0001f)
            assertEquals("vertical scale at frame $frame", forward.second, backward.second, 0.0001f)
        }
        assertEquals(1f, right.last().first, 0f)
        assertEquals(1f, right.last().second, 0f)
    }

    @Test
    fun longJumpCannotRelaxWhileStillOutsideTheBarsArrivalThreshold() {
        val threshold = DropletController.releaseThreshold(5)
        assertEquals(0.1f, threshold, 0f)
        // The previous four-tab transition relaxed with 0.3201 tab units left to travel.
        assertTrue(0.32011417f > threshold)
        assertTrue(0.08f < threshold)
        assertEquals(0.05f, DropletController.releaseThreshold(3), 0f)
        assertEquals(0.001f, DropletController.releaseThreshold(1), 0f)
    }

    @Test
    fun dragVelocityUsesTheTabRangeAndHandlesSingleTabRows() {
        assertEquals(2f, DropletController.velocityRange(3, 1f), 0f)
        assertEquals(4f, DropletController.velocityRange(3, 2f), 0f)
        assertEquals(1f, DropletController.velocityRange(1, 0f), 0f)
    }

    @Test
    fun deformationIsBoundedAndDisappearsAtZeroVelocity() {
        for (velocity in listOf(-1000f, -20f, -1f, 0f, 1f, 20f, 1000f)) {
            val x = DropletController.horizontalScale(1f, velocity)
            val y = DropletController.verticalScale(1f, velocity)
            assertTrue("horizontal bound for $velocity", x in 1f..1.25f)
            assertTrue("vertical bound for $velocity", y in 0.8f..1f)
        }
        assertEquals(1f, DropletController.horizontalScale(1f, 0f), 0f)
        assertEquals(1f, DropletController.verticalScale(1f, 0f), 0f)
    }

    private fun trajectory(from: Float, to: Float): List<Pair<Float, Float>> {
        val position = LiquidGlassSpring(1f, 1000f, 0.001f, from)
        val velocity = LiquidGlassSpring(0.5f, 300f, 0.01f, 0f)
        val scaleX = LiquidGlassSpring(0.6f, 250f, 0.001f, 1f)
        val scaleY = LiquidGlassSpring(0.7f, 250f, 0.001f, 1f)
        position.animateTo(to)
        scaleX.animateTo(DropletController.PRESSED_SCALE)
        scaleY.animateTo(DropletController.PRESSED_SCALE)
        val dt = 1f / 60f
        val range = DropletController.velocityRange(3, abs(to - from))
        var releasePending = true
        return List(90) {
            val before = position.value
            position.update(dt)
            velocity.animateTo((position.value - before) / dt / range)
            velocity.update(dt)
            scaleX.update(dt)
            scaleY.update(dt)
            val result = DropletController.horizontalScale(scaleX.value, velocity.value) to
                DropletController.verticalScale(scaleY.value, velocity.value)
            if (releasePending && abs(position.value - to) <= DropletController.releaseThreshold(3)) {
                releasePending = false
                scaleX.animateTo(1f)
                scaleY.animateTo(1f)
            }
            result
        }
    }
}
