package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
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

    @Test
    fun adChildPreferenceUsesItsOwnScanStatus() {
        val support = SettingsSwitchSupportResolver.resolve(
            prefKey = ConfigManager.KEY_BLOCK_AD_SEARCH_BOX_TEXT,
            supported = true,
            featureStatusMap = mapOf(
                HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT to HookFeatureStatus(
                    state = HookFeatureState.DISABLED,
                    missingCritical = listOf("searchBoxViewClass"),
                ),
                HookFeatureKey.BLOCK_AD to HookFeatureStatus(state = HookFeatureState.PARTIAL),
            ),
        )

        assertFalse(support.supported)
        assertFalse(support.partial)
        assertFalse(support.unknown)
    }
}
