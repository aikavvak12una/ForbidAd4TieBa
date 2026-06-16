package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.config.ConfigManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSwitchSupportResolverTest {
    @Test
    fun nonScanPreferenceDoesNotShowUnknownScanState() {
        val support = SettingsSwitchSupportResolver.resolve(
            prefKey = ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION,
            supported = true,
            featureStatusMap = emptyMap(),
        )

        assertTrue(support.supported)
        assertFalse(support.partial)
        assertFalse(support.unknown)
        assertNull(support.note)
    }

    @Test
    fun scanBackedPreferenceStillShowsUnknownWhenStatusIsUnavailable() {
        val support = SettingsSwitchSupportResolver.resolve(
            prefKey = ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS,
            supported = true,
            featureStatusMap = emptyMap(),
        )

        assertFalse(support.supported)
        assertFalse(support.partial)
        assertTrue(support.unknown)
    }
}
