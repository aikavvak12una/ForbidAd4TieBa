package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.ConfigManager.ScanFeatureAvailabilityState
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.*
import org.junit.Test

class EffectiveSettingsPolicyTest {
    @Test fun lzlEarliestDefaultsOffAndRequiresItsOwnCapabilityWithoutRestrictedUnlock() {
        val host = HostCapabilities(mapOf(HookFeatureKey.DEFAULT_LZL_EARLIEST to ScanFeatureAvailabilityState.AVAILABLE))
        val choice = UserSettings(mapOf(SimpleToggle.DEFAULT_LZL_EARLIEST.prefKey to true))
        val remote = RemoteSettingsPolicy(true)
        assertFalse(EffectiveSettingsPolicy.derive(UserSettings(emptyMap<String, Any>()), host, remote)
            .snapshot[SimpleToggle.DEFAULT_LZL_EARLIEST])
        assertTrue(EffectiveSettingsPolicy.derive(choice, host, remote).snapshot[SimpleToggle.DEFAULT_LZL_EARLIEST])
        assertFalse(EffectiveSettingsPolicy.derive(choice, HostCapabilities(emptyMap()), remote)
            .snapshot[SimpleToggle.DEFAULT_LZL_EARLIEST])
        assertTrue(choice.getBoolean(SimpleToggle.DEFAULT_LZL_EARLIEST.prefKey, false))
    }

    @Test fun policyInputsStayStableWhenTheirSourcesChange() {
        val values = mutableMapOf<String, Any>(
            AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN to true,
            AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
        )
        val availability = mutableMapOf(HookFeatureKey.AUTO_SIGN_IN to ScanFeatureAvailabilityState.PARTIAL)
        val user = UserSettings(values)
        val host = HostCapabilities(availability)
        values.clear()
        availability.clear()
        assertTrue(EffectiveSettingsPolicy.derive(user, host, RemoteSettingsPolicy(false)).snapshot.isAutoSignInEnabled)
    }

    @Test fun remoteLockSuppressesRestrictedEffectsWithoutEditingTheSavedChoice() {
        val user = UserSettings(mapOf(
            AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
            AccountPreferences.KEY_ENABLE_DETAILED_LOGGING to true,
        ))
        val host = HostCapabilities(emptyMap())
        assertFalse(EffectiveSettingsPolicy.derive(user, host, RemoteSettingsPolicy(true)).snapshot.isDetailedLoggingEnabled)
        assertTrue(user.getBoolean(AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED, false))
        assertTrue(EffectiveSettingsPolicy.derive(user, host, RemoteSettingsPolicy(false)).snapshot.isDetailedLoggingEnabled)
    }

    @Test fun unknownCapabilitiesFailClosedAndPartialCapabilitiesRemainUsable() {
        val user = UserSettings(mapOf(
            AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN to true,
            AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
        ))
        assertFalse(EffectiveSettingsPolicy.derive(user, HostCapabilities(emptyMap()), RemoteSettingsPolicy(false))
            .snapshot.isAutoSignInEnabled)
        val partial = HostCapabilities(mapOf(HookFeatureKey.AUTO_SIGN_IN to ScanFeatureAvailabilityState.PARTIAL))
        assertTrue(EffectiveSettingsPolicy.derive(user, partial, RemoteSettingsPolicy(false)).snapshot.isAutoSignInEnabled)
    }

    @Test fun bottomTabNormalizationIsAnEffectOnlyWhileCustomizationIsEffective() {
        val choices = mapOf(
            TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to true,
            TabPreferences.KEY_CUSTOM_BOTTOM_TABS to true,
            TabPreferences.KEY_BOTTOM_TAB_HOME to false,
            TabPreferences.KEY_BOTTOM_TAB_ENTER_FORUM to false,
            TabPreferences.KEY_BOTTOM_TAB_RETAIL_STORE to false,
            TabPreferences.KEY_BOTTOM_TAB_MESSAGE to false,
            TabPreferences.KEY_BOTTOM_TAB_MINE to false,
        )
        val user = UserSettings(choices)
        val host = HostCapabilities(mapOf(HookFeatureKey.SIMPLIFY_BOTTOM_TABS to ScanFeatureAvailabilityState.AVAILABLE))
        val enabled = EffectiveSettingsPolicy.derive(user, host, RemoteSettingsPolicy(false))
        assertTrue(enabled.snapshot.isBottomTabHomeEnabled)
        assertNotNull(enabled.normalizedBottomTabs)
        assertFalse(user.getBoolean(TabPreferences.KEY_BOTTOM_TAB_HOME, true))

        val disabled = UserSettings(choices + (TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to false))
        assertNull(EffectiveSettingsPolicy.derive(disabled, host, RemoteSettingsPolicy(false)).normalizedBottomTabs)
        assertNull(EffectiveSettingsPolicy.derive(user, HostCapabilities(emptyMap()), RemoteSettingsPolicy(false)).normalizedBottomTabs)
    }

    @Test fun derivingSettingsDoesNotPublishRuntimeState() {
        val version = ConfigManager.snapshotVersion()
        val published = ConfigManager.snapshot()
        EffectiveSettingsPolicy.derive(UserSettings(emptyMap<String, Any>()), HostCapabilities(emptyMap()), RemoteSettingsPolicy(false))
        assertEquals(version, ConfigManager.snapshotVersion())
        assertSame(published, ConfigManager.snapshot())
    }
}
