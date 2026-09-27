package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarCompatNavigationModeTest {
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
