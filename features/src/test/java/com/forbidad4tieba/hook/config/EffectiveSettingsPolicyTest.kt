package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.ConfigManager.ScanFeatureAvailabilityState
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.*
import org.junit.Test

class EffectiveSettingsPolicyTest {
    @Test fun titanEarlyAndPublishedPoliciesAgreeForAllInputs() {
        // The pre-attach path cannot consume scan availability. Adding such a dependency must
        // revisit the bootstrap contract instead of silently making the two paths disagree.
        assertNull(PerformancePreferences.ENABLE_PERFORMANCE_OPTIMIZATION.capabilityKey)
        assertNull(PerformancePreferences.BLOCK_TITAN_PATCH.capabilityKey)
        for (mask in 0 until 16) {
            val unlocked = mask and 1 != 0
            val performance = mask and 2 != 0
            val titan = mask and 4 != 0
            val locked = mask and 8 != 0
            val values = UserSettings(mapOf(
                AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to unlocked,
                PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to performance,
                PerformancePreferences.KEY_BLOCK_TITAN_PATCH to titan,
            ))
            val expected = unlocked && performance && titan && !locked
            assertEquals("early mask=$mask", expected,
                EffectiveSettingsPolicy.isTitanPatchBlockEnabled(values, locked))
            assertEquals("published mask=$mask", expected,
                EffectiveSettingsPolicy.derive(values, HostCapabilities(emptyMap()),
                    RemoteSettingsPolicy(locked)).snapshot.isTitanPatchBlockEnabled)
        }
    }

    @Test fun slidersReachThePublishedFloatingStyleWithoutEnablingIt() {
        for (enabled in listOf(false, true)) {
            val values = mapOf<String, Any>(
                BottomTabLiquidGlassPreferences.ENABLED to enabled,
                BottomTabLiquidGlassPreferences.WIDTH_ADJUSTMENT to 100,
                BottomTabLiquidGlassPreferences.HEIGHT_ADJUSTMENT to -50,
                BottomTabLiquidGlassPreferences.POSITION_ADJUSTMENT to 35,
                BottomTabLiquidGlassPreferences.BLUR_PERCENT to 0,
            )
            val state = EffectiveSettingsPolicy.derive(UserSettings(values),
                HostCapabilities(emptyMap()), RemoteSettingsPolicy(false)).snapshot
            assertEquals(enabled, state.isFloatingBottomBarEnabled)
            assertEquals(if (enabled) BottomTabLiquidGlassConfig(100, -50, 35, blurPercent = 0)
                else BottomTabLiquidGlassConfig.DEFAULT, state.bottomTabLiquidGlass)
        }
    }

    @Test fun floatingIsIndependentOfTabCustomizationAndLegacySwitchesInEveryMaterialMode() {
        for (master in listOf(false, true)) for (tab in listOf(false, true)) for (reply in listOf(false, true)) {
            for (floating in listOf(false, true)) for (blur in listOf(false, true)) for (glass in listOf(false, true)) {
                val values = mapOf<String, Any>(
                    TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to master,
                    TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS to tab,
                    BottomTabLiquidGlassPreferences.POST_REPLY_BAR to reply,
                    BottomTabLiquidGlassPreferences.FLOATING to !floating,
                    BottomTabLiquidGlassPreferences.ENABLED to floating,
                    BottomTabLiquidGlassPreferences.BLUR to blur,
                    BottomTabLiquidGlassPreferences.LIQUID_GLASS to glass,
                    BottomTabLiquidGlassPreferences.POSITION_ADJUSTMENT to 24,
                )
                val user = UserSettings(values)
                val state = EffectiveSettingsPolicy.derive(user, HostCapabilities(emptyMap()), RemoteSettingsPolicy(false)).snapshot
                assertEquals(floating, state.isFloatingBottomBarEnabled)
                if (floating) {
                    assertEquals(BottomTabLiquidGlassConfig(positionAdjustment = 24, blurEnabled = blur,
                        liquidGlassEnabled = glass), state.bottomTabLiquidGlass)
                } else assertEquals(BottomTabLiquidGlassConfig.DEFAULT, state.bottomTabLiquidGlass)
                assertEquals(reply, user.getBoolean(BottomTabLiquidGlassPreferences.POST_REPLY_BAR, false))
                assertEquals(glass, user.getBoolean(BottomTabLiquidGlassPreferences.LIQUID_GLASS, false))
            }
        }
    }

    @Test fun eitherPreviouslyEnabledBarMigratesToTheSharedFloatingFeature() {
        val choices = mapOf<String, Any>(
            TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to true,
            TabPreferences.BOTTOM_TAB_LIQUID_GLASS.key to false,
            BottomTabLiquidGlassPreferences.POST_REPLY_BAR to true,
            "bottom_tab_liquid_glass_bottom_gap_dp" to 20,
            BottomTabLiquidGlassPreferences.BLUR to false,
        )
        fun evaluate(values: Map<String, Any>) = EffectiveSettingsPolicy.derive(UserSettings(values),
            HostCapabilities(emptyMap()), RemoteSettingsPolicy(false)).snapshot
        val replyOnly = evaluate(choices)
        assertTrue(replyOnly.isFloatingBottomBarEnabled)
        assertEquals(BottomTabLiquidGlassConfig(blurEnabled = false), replyOnly.bottomTabLiquidGlass)
        val both = evaluate(choices + (TabPreferences.BOTTOM_TAB_LIQUID_GLASS.key to true))
        assertTrue(both.isFloatingBottomBarEnabled)
        assertEquals(replyOnly.bottomTabLiquidGlass, both.bottomTabLiquidGlass)
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT,
            evaluate(choices + (TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to false)).bottomTabLiquidGlass)
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT,
            evaluate(choices + (BottomTabLiquidGlassPreferences.POST_REPLY_BAR to false)).bottomTabLiquidGlass)
        assertEquals(replyOnly.bottomTabLiquidGlass,
            evaluate(choices + (BottomTabLiquidGlassPreferences.ENABLED to true) +
                (TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to false)).bottomTabLiquidGlass)
    }

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
