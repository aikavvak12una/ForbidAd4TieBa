package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.ConfigManager.ScanFeatureAvailabilityState
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectiveSettingsPolicyTest {
    @Test
    fun parentMastersGateOnlyTheirOwnChildrenAndSavedChoicesStayIntact() {
        val values = mutableMapOf<String, Any>(
            AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
            PostFilterPreferences.KEY_ENABLE_CUSTOM_POST_FILTER to true,
            PostFilterPreferences.KEY_FILTER_POST_VOTE to true,
            PostFilterPreferences.KEY_FILTER_POST_LOTTERY to true,
            PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to true,
            PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS to true,
            TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to true,
            TabPreferences.KEY_CUSTOM_BOTTOM_TABS to true,
            TabPreferences.KEY_BOTTOM_TAB_MESSAGE to false,
        )
        val capabilities = HostCapabilities(mapOf(
            HookFeatureKey.ENABLE_CUSTOM_POST_FILTER to ScanFeatureAvailabilityState.AVAILABLE,
            HookFeatureKey.DISABLE_VIDEO_COMPONENTS to ScanFeatureAvailabilityState.AVAILABLE,
            HookFeatureKey.SIMPLIFY_BOTTOM_TABS to ScanFeatureAvailabilityState.AVAILABLE,
        ))
        val enabled = EffectiveSettingsPolicy.derive(
            UserSettings(values),
            capabilities,
            RemoteSettingsPolicy(false),
        ).snapshot
        assertTrue(enabled.isCustomPostFilterEnabled)
        assertTrue(enabled.isPostVoteFilterEnabled)
        assertTrue(enabled.isPostLotteryFilterEnabled)
        assertTrue(enabled.isPerformanceOptimizationEnabled)
        assertTrue(enabled.isVideoPreloadDisabled)
        assertTrue(enabled.isBottomTabsCustomEnabled)
        assertFalse(enabled.isBottomTabMessageEnabled)

        val disabled = EffectiveSettingsPolicy.derive(
            UserSettings(
                values + mapOf(
                    PostFilterPreferences.KEY_ENABLE_CUSTOM_POST_FILTER to false,
                    PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to false,
                    TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to false,
                ),
            ),
            capabilities,
            RemoteSettingsPolicy(false),
        ).snapshot
        assertFalse(disabled.isCustomPostFilterEnabled)
        assertFalse(disabled.isPostVoteFilterEnabled)
        assertFalse(disabled.isPerformanceOptimizationEnabled)
        assertFalse(disabled.isVideoPreloadDisabled)
        assertFalse(disabled.isBottomTabsCustomEnabled)
        assertEquals(true, values[PostFilterPreferences.KEY_FILTER_POST_VOTE])
        assertEquals(true, values[PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS])
        assertEquals(false, values[TabPreferences.KEY_BOTTOM_TAB_MESSAGE])
    }

    @Test
    fun unknownFailsClosedWhilePartialAndRemoteLocksRemainIndependent() {
        val user = UserSettings(mapOf(
            AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN to true,
            AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
            AccountPreferences.KEY_ENABLE_DETAILED_LOGGING to true,
        ))
        assertFalse(
            EffectiveSettingsPolicy.derive(user, HostCapabilities(emptyMap()), RemoteSettingsPolicy(false))
                .snapshot.isAutoSignInEnabled,
        )
        val partial = HostCapabilities(mapOf(HookFeatureKey.AUTO_SIGN_IN to ScanFeatureAvailabilityState.PARTIAL))
        assertTrue(
            EffectiveSettingsPolicy.derive(user, partial, RemoteSettingsPolicy(false))
                .snapshot.isAutoSignInEnabled,
        )
        assertFalse(
            EffectiveSettingsPolicy.derive(user, partial, RemoteSettingsPolicy(true))
                .snapshot.isDetailedLoggingEnabled,
        )
        assertTrue(user.getBoolean(AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED, false))
    }

    @Test
    fun independentControlsDoNotSilentlyEnableTheirLegacyMasters() {
        val host = HostCapabilities(mapOf(
            HookFeatureKey.DEFAULT_LZL_EARLIEST to ScanFeatureAvailabilityState.AVAILABLE,
            HookFeatureKey.COMMENT_LEVEL_FILTER to ScanFeatureAvailabilityState.AVAILABLE,
            HookFeatureKey.COMMENT_SHORTCUT to ScanFeatureAvailabilityState.AVAILABLE,
        ))
        val snapshot = EffectiveSettingsPolicy.derive(
            UserSettings(mapOf(
                SimpleToggle.DEFAULT_LZL_EARLIEST.prefKey to true,
                CommentFilterPreferences.KEY_ENABLE to false,
                CommentFilterPreferences.KEY_SHORTCUT to true,
                CommentFilterPreferences.KEY_MINIMUM_LEVEL to 8,
                TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to false,
                TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS to false,
                BottomTabLiquidGlassPreferences.POST_REPLY_BAR to false,
                BottomTabLiquidGlassPreferences.ENABLED to true,
                BottomTabLiquidGlassPreferences.POSITION_ADJUSTMENT to 24,
            )),
            host,
            RemoteSettingsPolicy(true),
        ).snapshot
        assertTrue(snapshot[SimpleToggle.DEFAULT_LZL_EARLIEST])
        assertFalse(snapshot.commentLevelFilter.enabled)
        assertEquals(8, snapshot.commentLevelFilter.minimumLevel)
        assertTrue(snapshot.isCommentShortcutEnabled)
        assertFalse(snapshot.isBottomTabsCustomEnabled)
        assertTrue(snapshot.isFloatingBottomBarEnabled)
        assertEquals(24, snapshot.bottomTabLiquidGlass.positionAdjustment)
    }

    @Test
    fun policyInputsAreStableAndBottomNormalizationIsAnEffect() {
        val values = mutableMapOf<String, Any>(
            TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to true,
            TabPreferences.KEY_CUSTOM_BOTTOM_TABS to true,
            TabPreferences.KEY_BOTTOM_TAB_HOME to false,
            TabPreferences.KEY_BOTTOM_TAB_ENTER_FORUM to false,
            TabPreferences.KEY_BOTTOM_TAB_RETAIL_STORE to false,
            TabPreferences.KEY_BOTTOM_TAB_MESSAGE to false,
            TabPreferences.KEY_BOTTOM_TAB_MINE to false,
        )
        val user = UserSettings(values)
        val host = HostCapabilities(mapOf(HookFeatureKey.SIMPLIFY_BOTTOM_TABS to ScanFeatureAvailabilityState.AVAILABLE))
        values.clear()
        val evaluation = EffectiveSettingsPolicy.derive(user, host, RemoteSettingsPolicy(false))
        assertTrue(evaluation.snapshot.isBottomTabHomeEnabled)
        assertNotNull(evaluation.normalizedBottomTabs)
        assertNull(
            EffectiveSettingsPolicy.derive(
                UserSettings(mapOf(TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to false)),
                host,
                RemoteSettingsPolicy(false),
            ).normalizedBottomTabs,
        )
    }

    @Test
    fun cloudDialogModeRequiresMasterAndOwnCapabilityIndependentOfTheWhitelistToggle() {
        val values = mapOf<String, Any?>(
            AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
            AdPreferences.KEY_BLOCK_AD to true,
        )
        val cloudOnly = HostCapabilities(mapOf(
            HookFeatureKey.BLOCK_AD to ScanFeatureAvailabilityState.DISABLED,
            HookFeatureKey.BLOCK_AD_CLOUD_DIALOGS to ScanFeatureAvailabilityState.AVAILABLE,
        ))

        val blacklist = EffectiveSettingsPolicy.derive(
            UserSettings(values),
            cloudOnly,
            RemoteSettingsPolicy(false),
        ).snapshot
        assertFalse(blacklist.isAdBlockEnabled)
        assertTrue(blacklist.isCloudDialogAdBlockEnabled)
        assertFalse(blacklist.isCloudDialogWhitelistEnabled)

        val whitelist = EffectiveSettingsPolicy.derive(
            UserSettings(values + (AdPreferences.KEY_BLOCK_AD_CLOUD_DIALOGS to true)),
            cloudOnly,
            RemoteSettingsPolicy(false),
        ).snapshot
        assertTrue(whitelist.isCloudDialogAdBlockEnabled)
        assertTrue(whitelist.isCloudDialogWhitelistEnabled)

        val masterOff = EffectiveSettingsPolicy.derive(
            UserSettings(values + (AdPreferences.KEY_BLOCK_AD to false)),
            cloudOnly,
            RemoteSettingsPolicy(false),
        ).snapshot
        assertFalse(masterOff.isCloudDialogAdBlockEnabled)
        assertFalse(masterOff.isCloudDialogWhitelistEnabled)

        val cloudUnavailable = EffectiveSettingsPolicy.derive(
            UserSettings(values + (AdPreferences.KEY_BLOCK_AD_CLOUD_DIALOGS to true)),
            HostCapabilities(mapOf(
                HookFeatureKey.BLOCK_AD to ScanFeatureAvailabilityState.AVAILABLE,
                HookFeatureKey.BLOCK_AD_CLOUD_DIALOGS to ScanFeatureAvailabilityState.DISABLED,
            )),
            RemoteSettingsPolicy(false),
        ).snapshot
        assertFalse(cloudUnavailable.isCloudDialogAdBlockEnabled)
        assertFalse(cloudUnavailable.isCloudDialogWhitelistEnabled)

        val cloudPartial = EffectiveSettingsPolicy.derive(
            UserSettings(values),
            HostCapabilities(mapOf(
                HookFeatureKey.BLOCK_AD_CLOUD_DIALOGS to ScanFeatureAvailabilityState.PARTIAL,
            )),
            RemoteSettingsPolicy(false),
        ).snapshot
        assertTrue(cloudPartial.isCloudDialogAdBlockEnabled)
        assertFalse(cloudPartial.isCloudDialogWhitelistEnabled)

        val remoteLocked = EffectiveSettingsPolicy.derive(
            UserSettings(values + (AdPreferences.KEY_BLOCK_AD_CLOUD_DIALOGS to true)),
            cloudOnly,
            RemoteSettingsPolicy(true),
        ).snapshot
        assertFalse(remoteLocked.isCloudDialogAdBlockEnabled)
        assertFalse(remoteLocked.isCloudDialogWhitelistEnabled)

        val bootstrap = SettingsSnapshot.bootstrap()
        assertFalse(bootstrap.isCloudDialogAdBlockEnabled)
        assertFalse(bootstrap.isCloudDialogWhitelistEnabled)
    }
}
