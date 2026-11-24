package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.config.AdPreferences
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.HomeGlassPreferences
import com.forbidad4tieba.hook.config.PerformancePreferences
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.ui.settings.SettingsSliderChangeListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsControlPolicyTest {
    @Test
    fun touchGesturePreviewsEveryValueButCommitsOnlyTheRelease() {
        val previews = mutableListOf<Int>()
        val saved = mutableListOf<Int>()
        val listener = SettingsSliderChangeListener({ previews += it }, { saved += it })

        listener.onStartTrackingTouch(null)
        listener.onProgressChanged(null, 10, true)
        listener.onProgressChanged(null, 20, true)
        assertEquals(listOf(10, 20), previews)
        assertTrue(saved.isEmpty())

        listener.onStopTrackingTouch(null)
        assertEquals(listOf(20), saved)
        listener.onStopTrackingTouch(null)
        assertEquals(listOf(20), saved)
    }

    @Test
    fun keyboardAndAccessibilityChangesCommitWithoutATouchRelease() {
        val saved = mutableListOf<Int>()
        val listener = SettingsSliderChangeListener({}, { saved += it })

        listener.onProgressChanged(null, 4, true)
        listener.onProgressChanged(null, 5, true)
        assertEquals(listOf(4, 5), saved)
    }

    @Test
    fun loadingAndModeSwitchesPreviewWithoutCommittingAHiddenChange() {
        val previews = mutableListOf<Int>()
        val saved = mutableListOf<Int>()
        val listener = SettingsSliderChangeListener({ previews += it }, { saved += it })

        listener.onProgressChanged(null, 18, false)
        listener.onProgressChanged(null, 42, false)
        listener.onStartTrackingTouch(null)
        listener.onStopTrackingTouch(null)
        assertEquals(listOf(18, 42), previews)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun switchSupportUsesItsOwnCapabilityAndFailsClosedWhenStatusIsUnknown() {
        val nonScan = SettingsSwitchSupportResolver.resolve(
            PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION,
            supported = true,
            featureStatusMap = emptyMap(),
        )
        assertTrue(nonScan.supported)
        assertFalse(nonScan.partial)
        assertFalse(nonScan.unknown)
        assertNull(nonScan.note)

        withScanAvailability(emptyMap()) {
            val unknown = SettingsSwitchSupportResolver.resolve(
                HomeGlassPreferences.KEY_ENABLE_HOME_NATIVE_GLASS,
                supported = true,
                featureStatusMap = emptyMap(),
            )
            assertFalse(unknown.supported)
            assertTrue(unknown.unknown)
        }

        val disabledChild = SettingsSwitchSupportResolver.resolve(
            AdPreferences.KEY_BLOCK_AD_SEARCH_BOX_TEXT,
            supported = true,
            featureStatusMap = mapOf(
                HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT to HookFeatureStatus(
                    state = HookFeatureState.DISABLED,
                    missingCritical = listOf("searchBoxViewClass"),
                ),
                HookFeatureKey.BLOCK_AD to HookFeatureStatus(state = HookFeatureState.PARTIAL),
            ),
        )
        assertFalse(disabledChild.supported)
        assertFalse(disabledChild.partial)
        assertFalse(disabledChild.unknown)
    }

    private fun withScanAvailability(
        value: Map<String, ConfigManager.ScanFeatureAvailabilityState>,
        block: () -> Unit,
    ) {
        val field = ConfigManager::class.java.getDeclaredField("scanFeatureAvailability").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val previous = field.get(ConfigManager) as Map<String, ConfigManager.ScanFeatureAvailabilityState>
        field.set(ConfigManager, value)
        try {
            block()
        } finally {
            field.set(ConfigManager, previous)
        }
    }
}
