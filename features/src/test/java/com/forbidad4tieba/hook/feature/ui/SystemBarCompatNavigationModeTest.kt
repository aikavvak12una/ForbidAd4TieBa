package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarCompatNavigationModeTest {
    private open class PostActivity
    private class DerivedPostActivity : PostActivity()
    private class OtherActivity

    @Test fun postWindowEligibilityDependsOnValidatedActivityAndSetting() {
        // The entire post window opts in before the reply row exists and keeps that scope when
        // the loading row is replaced or the native editor takes over.
        assertTrue(SystemBarCompatHook.isFloatingPostReplyActivity(
            PostActivity::class.java, PostActivity::class.java, enabled = true))
        assertTrue(SystemBarCompatHook.isFloatingPostReplyActivity(
            DerivedPostActivity::class.java, PostActivity::class.java, enabled = true))
        assertFalse(SystemBarCompatHook.isFloatingPostReplyActivity(
            OtherActivity::class.java, PostActivity::class.java, enabled = true))
        assertFalse(SystemBarCompatHook.isFloatingPostReplyActivity(
            PostActivity::class.java, null, enabled = true))
        assertFalse(SystemBarCompatHook.isFloatingPostReplyActivity(
            PostActivity::class.java, PostActivity::class.java, enabled = false))
    }

    @Test
    fun secureNavigationModeWinsWhenOemFrameworkOverlayIsStale() {
        assertTrue(SystemBarCompatHook.isGestureNavigationMode(secureMode = 2, frameworkMode = 0))
        assertFalse(SystemBarCompatHook.isGestureNavigationMode(secureMode = 0, frameworkMode = 2))
    }

    @Test
    fun frameworkModeIsUsedWhenSecureModeIsUnavailableOrInvalid() {
        assertTrue(SystemBarCompatHook.isGestureNavigationMode(secureMode = null, frameworkMode = 2))
        assertTrue(SystemBarCompatHook.isGestureNavigationMode(secureMode = 3, frameworkMode = 2))
        assertFalse(SystemBarCompatHook.isGestureNavigationMode(secureMode = null, frameworkMode = 0))
    }
}
