package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemSplashPolicyTest {
    @Test fun onlyTiebaInSystemNightModeChangesColor() {
        assertEquals(0xff121212.toInt(), SystemSplashPolicy.background("com.baidu.tieba") { 0x21 })
        assertNull(SystemSplashPolicy.background("com.baidu.tieba") { 0x11 })
        assertNull(SystemSplashPolicy.background("com.baidu.tieba") { 0x01 })
        for (unrelated in listOf("com.twitter.android", "com.baidu.tieba.other")) {
            assertNull(SystemSplashPolicy.background(unrelated) { error("Unrelated apps must not read system configuration") })
        }
    }

    @Test fun systemModeChangesAffectTheNextBuildWithoutStaleState() {
        assertEquals(0xff121212.toInt(), SystemSplashPolicy.background("com.baidu.tieba") { 0x22 })
        assertNull(SystemSplashPolicy.background("com.baidu.tieba") { 0x12 })
        assertEquals(0xff121212.toInt(), SystemSplashPolicy.background("com.baidu.tieba") { 0x22 })
    }
}
