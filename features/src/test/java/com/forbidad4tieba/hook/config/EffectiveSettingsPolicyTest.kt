package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.ConfigManager.ScanFeatureAvailabilityState
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.*
import org.junit.Test

class EffectiveSettingsPolicyTest {
    @Test fun performanceControlsRequireUnlockMasterChoiceAndTheirOwnCapability() {
        val targets = listOf(
            Triple(PerformancePreferences.KEY_COOKIE_SYNC_DEDUP, HookFeatureKey.COOKIE_SYNC_DEDUP,
                { s: SettingsSnapshot -> s.isCookieSyncDedupEnabled }),
            Triple(PerformancePreferences.KEY_DISABLE_AD_SDK_COMPONENTS, HookFeatureKey.DISABLE_AD_SDK_COMPONENTS,
                { s: SettingsSnapshot -> s.isAdSdkComponentsDisabled }),
            Triple(PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS, HookFeatureKey.DISABLE_VIDEO_COMPONENTS,
                { s: SettingsSnapshot -> s.isVideoPreloadDisabled }),
            Triple(PerformancePreferences.KEY_DISABLE_YY_BACKGROUND, HookFeatureKey.DISABLE_YY_BACKGROUND,
                { s: SettingsSnapshot -> s.isYyBackgroundDisabled }),
            Triple(PerformancePreferences.KEY_DISABLE_SWAN_PRELOAD, HookFeatureKey.DISABLE_SWAN_PRELOAD,
                { s: SettingsSnapshot -> s.isSwanPreloadDisabled }),
        )
        for ((key, capability, read) in targets) for (mask in 0 until 16) {
            for (availability in ScanFeatureAvailabilityState.entries) {
                val unlocked = mask and 1 != 0
                val master = mask and 2 != 0
                val chosen = mask and 4 != 0
                val locked = mask and 8 != 0
                val values = targets.associate { it.first to true } + mapOf(
                    AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to unlocked,
                    PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to master,
                    key to chosen,
                )
                val capabilities = targets.associate { it.second to ScanFeatureAvailabilityState.AVAILABLE } +
                    (capability to availability)
                val snapshot = EffectiveSettingsPolicy.derive(UserSettings(values), HostCapabilities(capabilities),
                    RemoteSettingsPolicy(locked)).snapshot
                val activeMaster = unlocked && master && !locked
                val targetAvailable = availability == ScanFeatureAvailabilityState.AVAILABLE ||
                    availability == ScanFeatureAvailabilityState.PARTIAL
                assertEquals("$key/$mask/$availability", activeMaster && chosen && targetAvailable, read(snapshot))
                assertEquals(activeMaster, snapshot.isPerformanceOptimizationEnabled)
                targets.filter { it.first != key }.forEach { (_, _, readOther) ->
                    assertEquals("Independent control: $key/$mask/$availability", activeMaster, readOther(snapshot))
                }
                assertEquals(chosen, values.getValue(key))
            }
        }
    }

    @Test fun togglingVideoPreservesTheSavedAndPublishedAdSdkChoice() {
        val host = HostCapabilities(mapOf(
            HookFeatureKey.DISABLE_VIDEO_COMPONENTS to ScanFeatureAvailabilityState.AVAILABLE,
            HookFeatureKey.DISABLE_AD_SDK_COMPONENTS to ScanFeatureAvailabilityState.AVAILABLE,
        ))
        for (master in listOf(false, true)) for (adChoice in listOf(false, true)) {
            val saved = mutableMapOf(
                AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to master,
                PerformancePreferences.KEY_DISABLE_AD_SDK_COMPONENTS to adChoice,
            )
            // Additional channel blocking belongs to the installed video group, not this setting.
            for (videoChoice in listOf(false, true, false)) {
                saved[PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS] = videoChoice
                val before = saved.toMap()
                val values = UserSettings(saved)
                val snapshot = EffectiveSettingsPolicy.derive(values, host, RemoteSettingsPolicy(false)).snapshot
                assertEquals(master && videoChoice, snapshot.isVideoPreloadDisabled)
                assertEquals(master && adChoice, snapshot.isAdSdkComponentsDisabled)
                assertEquals(adChoice, values.getBoolean(PerformancePreferences.KEY_DISABLE_AD_SDK_COMPONENTS, !adChoice))
                assertEquals(before, saved)
            }
        }
    }

    @Test fun videoComponentControlAndVideoPostFilterRemainIndependent() {
        for (mask in 0 until 16) for (videoAvailable in listOf(false, true)) {
            for (filterAvailable in listOf(false, true)) {
                val performance = mask and 1 != 0
                val videoDisabled = mask and 2 != 0
                val filter = mask and 4 != 0
                val videoPostsFiltered = mask and 8 != 0
                val values = UserSettings(mapOf(
                    AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to performance,
                    PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS to videoDisabled,
                    PostFilterPreferences.KEY_ENABLE_CUSTOM_POST_FILTER to filter,
                    PostFilterPreferences.KEY_FILTER_POST_VIDEO to videoPostsFiltered,
                ))
                fun state(available: Boolean) = if (available) ScanFeatureAvailabilityState.AVAILABLE
                    else ScanFeatureAvailabilityState.DISABLED
                val host = HostCapabilities(mapOf(
                    HookFeatureKey.DISABLE_VIDEO_COMPONENTS to state(videoAvailable),
                    HookFeatureKey.ENABLE_CUSTOM_POST_FILTER to state(filterAvailable),
                ))
                val snapshot = EffectiveSettingsPolicy.derive(values, host, RemoteSettingsPolicy(false)).snapshot
                assertEquals("video control: $mask/$videoAvailable/$filterAvailable",
                    performance && videoDisabled && videoAvailable, snapshot.isVideoPreloadDisabled)
                assertEquals("video post filter: $mask/$videoAvailable/$filterAvailable",
                    filter && videoPostsFiltered && filterAvailable, snapshot.isPostVideoFilterEnabled)
            }
        }
    }

    @Test fun slidersReachThePublishedFloatingStyleWithoutEnablingIt() {
        for (enabled in listOf(false, true)) {
            val values = mapOf<String, Any>(
                BottomTabLiquidGlassPreferences.ENABLED to enabled,
                BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT to 100,
                BottomTabLiquidGlassPreferences.REPLY_WIDTH_ADJUSTMENT to -100,
                BottomTabLiquidGlassPreferences.HEIGHT_ADJUSTMENT to -50,
                BottomTabLiquidGlassPreferences.POSITION_ADJUSTMENT to 35,
                BottomTabLiquidGlassPreferences.BLUR_PERCENT to 0,
            )
            val state = EffectiveSettingsPolicy.derive(UserSettings(values),
                HostCapabilities(emptyMap()), RemoteSettingsPolicy(false)).snapshot
            assertEquals(enabled, state.isFloatingBottomBarEnabled)
            assertEquals(if (enabled) BottomTabLiquidGlassConfig(100, -50, 35, blurPercent = 0, replyWidthAdjustment = -100)
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
