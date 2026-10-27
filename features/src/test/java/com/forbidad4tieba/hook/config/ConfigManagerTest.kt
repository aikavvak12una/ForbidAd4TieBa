package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigManagerTest {
    private val performanceCapabilities = listOf(
        HookFeatureKey.COOKIE_SYNC_DEDUP,
        HookFeatureKey.DISABLE_AD_SDK_COMPONENTS,
        HookFeatureKey.DISABLE_VIDEO_COMPONENTS,
    )

    @Test
    fun nonScanPreferencesAreAvailableWithoutScanState() {
        assertNull(SettingsCatalog.capabilityFor(PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION))
        assertNull(SettingsCatalog.capabilityFor(TabPreferences.KEY_HIDE_HOME_TAB_RED_DOT))
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ConfigManager.getScanFeatureAvailabilityState(PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION),
        )
        assertTrue(ConfigManager.isScanFeatureAvailable(TabPreferences.KEY_HIDE_HOME_TAB_RED_DOT))
    }

    @Test
    fun detailedLoggingStableHostPathIsAvailableBeforeSymbolScan() {
        assertEquals(
            HookFeatureKey.DETAILED_LOGGING,
            SettingsCatalog.capabilityFor(AccountPreferences.KEY_ENABLE_DETAILED_LOGGING),
        )
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ConfigManager.getScanFeatureAvailabilityState(AccountPreferences.KEY_ENABLE_DETAILED_LOGGING),
        )
        assertTrue(ConfigManager.isScanFeatureAvailable(AccountPreferences.KEY_ENABLE_DETAILED_LOGGING))
    }

    @Test
    fun autoSignInRemainsUnknownUntilScanStateIsApplied() {
        assertEquals(
            AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN,
            SettingsCatalog.capabilityFor(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN),
        )
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.UNKNOWN,
            ConfigManager.getScanFeatureAvailabilityState(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN),
        )
        assertFalse(ConfigManager.isScanFeatureAvailable(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN))
    }

    @Test
    fun autoSignInAvailabilityFollowsScanStatus() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.AUTO_SIGN_IN to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            assertEquals(
                ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
                ConfigManager.getScanFeatureAvailabilityState(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN),
            )
            assertTrue(ConfigManager.isScanFeatureAvailable(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN))
        }

        withScanAvailability(
            mapOf(
                HookFeatureKey.AUTO_SIGN_IN to ConfigManager.ScanFeatureAvailabilityState.PARTIAL,
            ),
        ) {
            assertEquals(
                ConfigManager.ScanFeatureAvailabilityState.PARTIAL,
                ConfigManager.getScanFeatureAvailabilityState(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN),
            )
            assertTrue(ConfigManager.isScanFeatureAvailable(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN))
        }

        withScanAvailability(
            mapOf(
                HookFeatureKey.AUTO_SIGN_IN to ConfigManager.ScanFeatureAvailabilityState.DISABLED,
            ),
        ) {
            assertEquals(
                ConfigManager.ScanFeatureAvailabilityState.DISABLED,
                ConfigManager.getScanFeatureAvailabilityState(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN),
            )
            assertFalse(ConfigManager.isScanFeatureAvailable(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN))
        }
    }

    @Test
    fun scanBackedPreferencesRemainUnknownUntilScanStateIsApplied() {
        assertEquals(
            HomeGlassPreferences.KEY_ENABLE_HOME_NATIVE_GLASS,
            SettingsCatalog.capabilityFor(HomeGlassPreferences.KEY_ENABLE_HOME_NATIVE_GLASS),
        )
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.UNKNOWN,
            ConfigManager.getScanFeatureAvailabilityState(HomeGlassPreferences.KEY_ENABLE_HOME_NATIVE_GLASS),
        )
    }

    @Test
    fun adChildPreferencesMapToIndependentFeatureKeys() {
        assertEquals(
            HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT,
            SettingsCatalog.capabilityFor(AdPreferences.KEY_BLOCK_AD_SEARCH_BOX_TEXT),
        )
        assertEquals(
            HookFeatureKey.BLOCK_AD_FEED,
            SettingsCatalog.capabilityFor(AdPreferences.KEY_BLOCK_AD_FEED),
        )
        assertEquals(
            HookFeatureKey.BLOCK_AD_HOME_BOTTOM_EASTER_EGG,
            SettingsCatalog.capabilityFor(
                AdPreferences.KEY_BLOCK_AD_HOME_BOTTOM_EASTER_EGG,
            ),
        )
    }

    @Test
    fun formerlyDescribedPreferencesUseCanonicalFeatureKeys() {
        assertEquals(
            HookFeatureKey.AUTO_LOAD_MORE,
            SettingsCatalog.capabilityFor(ExtensionPreferences.KEY_ENABLE_AUTO_LOAD_MORE),
        )
        assertEquals(
            HookFeatureKey.DISABLE_AUTO_REFRESH,
            SettingsCatalog.capabilityFor(ExtensionPreferences.KEY_DISABLE_AUTO_REFRESH),
        )
        assertEquals(
            HookFeatureKey.DEFAULT_ORIGINAL_IMAGE,
            SettingsCatalog.capabilityFor(SimpleToggle.DEFAULT_ORIGINAL_IMAGE.prefKey),
        )
        assertEquals(
            HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER,
            SettingsCatalog.capabilityFor(ExtensionPreferences.KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER),
        )
        assertEquals(
            HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY,
            SettingsCatalog.capabilityFor(ReplyPreferences.KEY_ENABLE_PB_LIKE_AUTO_REPLY),
        )
    }

    @Test
    fun customPostFilterChildSettingsDoNotBecomeRuntimeActiveWhenParentIsOff() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.ENABLE_CUSTOM_POST_FILTER to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val snapshot = buildSnapshot(
                mapOf(
                    PostFilterPreferences.KEY_ENABLE_CUSTOM_POST_FILTER to false,
                    PostFilterPreferences.KEY_FILTER_POST_VOTE to true,
                    PostFilterPreferences.KEY_FILTER_POST_LOTTERY to true,
                    PostFilterPreferences.KEY_FILTER_POST_FORUM_KEYWORD to true,
                    PostFilterPreferences.KEY_FILTER_POST_FORUM_KEYWORD_LIST to "alpha,beta",
                    AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE to true,
                ),
            )

            assertFalse(snapshot.isCustomPostFilterEnabled)
            assertFalse(snapshot.isPostVoteFilterEnabled)
            assertFalse(snapshot.isPostLotteryFilterEnabled)
            assertFalse(snapshot.isPostForumKeywordFilterEnabled)
            assertTrue(snapshot.postForumKeywordList.isEmpty())
            assertFalse(snapshot.isPostModelScoreFilterEnabled)
        }
    }

    @Test
    fun customPostFilterChildSettingsBecomeRuntimeActiveOnlyWhenParentIsAvailableAndOn() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.ENABLE_CUSTOM_POST_FILTER to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val snapshot = buildSnapshot(
                mapOf(
                    PostFilterPreferences.KEY_ENABLE_CUSTOM_POST_FILTER to true,
                    PostFilterPreferences.KEY_FILTER_POST_VOTE to true,
                    PostFilterPreferences.KEY_FILTER_POST_LOTTERY to true,
                    PostFilterPreferences.KEY_FILTER_POST_FORUM_KEYWORD to true,
                    PostFilterPreferences.KEY_FILTER_POST_FORUM_KEYWORD_LIST to "alpha,beta",
                    AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE to true,
                ),
            )

            assertTrue(snapshot.isCustomPostFilterEnabled)
            assertTrue(snapshot.isPostVoteFilterEnabled)
            assertTrue(snapshot.isPostLotteryFilterEnabled)
            assertTrue(snapshot.isPostForumKeywordFilterEnabled)
            assertEquals(listOf("alpha", "beta"), snapshot.postForumKeywordList)
            assertTrue(snapshot.isPostModelScoreFilterEnabled)
        }
    }

    @Test
    fun topAndBottomTabChildrenDoNotBecomeRuntimeActiveWhenParentIsOff() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.SIMPLIFY_HOME_TOP_TABS to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
                HookFeatureKey.SIMPLIFY_BOTTOM_TABS to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val snapshot = buildSnapshot(
                mapOf(
                    TabPreferences.KEY_CUSTOM_HOME_TOP_TABS to false,
                    TabPreferences.KEY_HOME_TOP_TAB_MATERIAL to true,
                    TabPreferences.KEY_HOME_TOP_TAB_RECOMMEND to true,
                    TabPreferences.KEY_HOME_TOP_TAB_LIVE to true,
                    TabPreferences.KEY_HOME_TOP_TAB_FOLLOWED to true,
                    TabPreferences.KEY_CUSTOM_BOTTOM_TABS to false,
                    TabPreferences.KEY_BOTTOM_TAB_HOME to true,
                    TabPreferences.KEY_BOTTOM_TAB_ENTER_FORUM to true,
                    TabPreferences.KEY_BOTTOM_TAB_RETAIL_STORE to true,
                    TabPreferences.KEY_BOTTOM_TAB_MESSAGE to true,
                    TabPreferences.KEY_BOTTOM_TAB_MINE to true,
                ),
            )

            assertFalse(snapshot.isHomeTopTabsCustomEnabled)
            assertFalse(snapshot.isHomeTopTabMaterialEnabled)
            assertFalse(snapshot.isHomeTopTabRecommendEnabled)
            assertFalse(snapshot.isHomeTopTabLiveEnabled)
            assertFalse(snapshot.isHomeTopTabFollowedEnabled)
            assertTrue(snapshot.homeTopTabDisabledKeys.isEmpty())
            assertFalse(snapshot.isBottomTabsCustomEnabled)
            assertFalse(snapshot.isBottomTabHomeEnabled)
            assertFalse(snapshot.isBottomTabEnterForumEnabled)
            assertFalse(snapshot.isBottomTabRetailStoreEnabled)
            assertFalse(snapshot.isBottomTabMessageEnabled)
            assertFalse(snapshot.isBottomTabMineEnabled)
        }
    }

    @Test
    fun topAndBottomTabChildrenBecomeRuntimeActiveOnlyWhenParentIsAvailableAndOn() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.SIMPLIFY_HOME_TOP_TABS to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
                HookFeatureKey.SIMPLIFY_BOTTOM_TABS to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val snapshot = buildSnapshot(
                mapOf(
                    TabPreferences.KEY_CUSTOM_HOME_TOP_TABS to true,
                    TabPreferences.KEY_HOME_TOP_TAB_MATERIAL to true,
                    TabPreferences.KEY_HOME_TOP_TAB_RECOMMEND to false,
                    TabPreferences.KEY_HOME_TOP_TAB_LIVE to true,
                    TabPreferences.KEY_HOME_TOP_TAB_FOLLOWED to false,
                    TabPreferences.KEY_CUSTOM_BOTTOM_TABS to true,
                    TabPreferences.KEY_BOTTOM_TAB_HOME to true,
                    TabPreferences.KEY_BOTTOM_TAB_ENTER_FORUM to false,
                    TabPreferences.KEY_BOTTOM_TAB_RETAIL_STORE to true,
                    TabPreferences.KEY_BOTTOM_TAB_MESSAGE to false,
                    TabPreferences.KEY_BOTTOM_TAB_MINE to true,
                ),
            )

            assertTrue(snapshot.isHomeTopTabsCustomEnabled)
            assertTrue(snapshot.isHomeTopTabMaterialEnabled)
            assertFalse(snapshot.isHomeTopTabRecommendEnabled)
            assertTrue(snapshot.isHomeTopTabLiveEnabled)
            assertFalse(snapshot.isHomeTopTabFollowedEnabled)
            assertTrue(TabPreferences.HOME_TOP_TAB_KEY_RECOMMEND in snapshot.homeTopTabDisabledKeys)
            assertTrue(TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED in snapshot.homeTopTabDisabledKeys)
            assertTrue(snapshot.isBottomTabsCustomEnabled)
            assertTrue(snapshot.isBottomTabHomeEnabled)
            assertFalse(snapshot.isBottomTabEnterForumEnabled)
            assertTrue(snapshot.isBottomTabRetailStoreEnabled)
            assertFalse(snapshot.isBottomTabMessageEnabled)
            assertTrue(snapshot.isBottomTabMineEnabled)
        }
    }

    @Test
    fun homeTabRedDotBlockIsScanIndependentAndDefaultsOff() {
        val defaultSnapshot = buildSnapshot(emptyMap())
        val enabledSnapshot = buildSnapshot(
            mapOf(
                TabPreferences.KEY_HIDE_HOME_TAB_RED_DOT to true,
            ),
        )

        assertFalse(defaultSnapshot.isHomeTabRedDotHidden)
        assertTrue(enabledSnapshot.isHomeTabRedDotHidden)
    }

    @Test
    fun inputMemeBarBlockDefaultsOffAndFollowsScanAvailability() {
        assertEquals(
            HookFeatureKey.HIDE_INPUT_MEME_BAR,
            SettingsCatalog.capabilityFor(ExtensionPreferences.KEY_HIDE_INPUT_MEME_BAR),
        )
        withScanAvailability(
            mapOf(
                HookFeatureKey.HIDE_INPUT_MEME_BAR to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            assertFalse(buildSnapshot(emptyMap()).isInputMemeBarHidden)
            assertTrue(
                buildSnapshot(
                    mapOf(ExtensionPreferences.KEY_HIDE_INPUT_MEME_BAR to true),
                ).isInputMemeBarHidden,
            )
        }
        withScanAvailability(
            mapOf(
                HookFeatureKey.HIDE_INPUT_MEME_BAR to
                    ConfigManager.ScanFeatureAvailabilityState.DISABLED,
            ),
        ) {
            assertFalse(
                buildSnapshot(
                    mapOf(ExtensionPreferences.KEY_HIDE_INPUT_MEME_BAR to true),
                ).isInputMemeBarHidden,
            )
        }
    }

    @Test
    fun dynamicHomeTopTabDisabledKeysDriveRuntimeSelection() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.SIMPLIFY_HOME_TOP_TABS to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val disabled = setOf(
                TabPreferences.HOME_TOP_TAB_KEY_RECOMMEND,
                TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED,
                "code:custom",
            )
            val expandedDisabled = TabPreferences.expandHomeTopTabDisabledKeys(disabled)
            val snapshot = buildSnapshot(
                mapOf(
                    TabPreferences.KEY_CUSTOM_HOME_TOP_TABS to true,
                    TabPreferences.KEY_HOME_TOP_TAB_MATERIAL to true,
                    TabPreferences.KEY_HOME_TOP_TAB_RECOMMEND to true,
                    TabPreferences.KEY_HOME_TOP_TAB_LIVE to true,
                    TabPreferences.KEY_HOME_TOP_TAB_FOLLOWED to true,
                    TabPreferences.KEY_HOME_TOP_TAB_DISABLED_KEYS to
                        TabPreferences.serializeHomeTopTabDisabledKeys(disabled),
                ),
            )

            assertTrue(snapshot.isHomeTopTabsCustomEnabled)
            assertTrue(snapshot.isHomeTopTabMaterialEnabled)
            assertFalse(snapshot.isHomeTopTabRecommendEnabled)
            assertTrue(snapshot.isHomeTopTabLiveEnabled)
            assertFalse(snapshot.isHomeTopTabFollowedEnabled)
            assertEquals(expandedDisabled, snapshot.homeTopTabDisabledKeys)
        }
    }

    @Test
    fun inactiveChildConfigurationDataIsNotExposedInRuntimeSnapshot() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
                HookFeatureKey.ENABLE_CUSTOM_POST_FILTER to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val snapshot = buildSnapshot(
                mapOf(
                    AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    ReplyPreferences.KEY_ENABLE_PB_LIKE_AUTO_REPLY to false,
                    ReplyPreferences.KEY_PB_LIKE_AUTO_REPLY_TEXT to "hello",
                    PostFilterPreferences.KEY_ENABLE_CUSTOM_POST_FILTER to true,
                    PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE to false,
                    PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS to "quality=0.5",
                    PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES to "quality=10",
                ),
            )

            assertFalse(snapshot.isPbLikeAutoReplyEnabled)
            assertEquals("", snapshot.pbLikeAutoReplyText)
            assertFalse(snapshot.isPostModelScoreFilterEnabled)
            assertTrue(snapshot.postModelScoreThresholds.isEmpty())
            assertTrue(snapshot.postModelScoreAutoPercentiles.isEmpty())
        }
    }

    @Test
    fun performanceChildrenDoNotBecomeRuntimeActiveWhenMasterIsOff() {
        withScanAvailability(performanceCapabilities.associateWith { ConfigManager.ScanFeatureAvailabilityState.AVAILABLE }) {
            val snapshot = buildSnapshot(mapOf(
                AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to false,
                PerformancePreferences.KEY_COOKIE_SYNC_DEDUP to true,
                PerformancePreferences.KEY_DISABLE_AD_SDK_COMPONENTS to true,
                PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS to true,
            ))
            assertFalse(snapshot.isPerformanceOptimizationEnabled)
            assertFalse(snapshot.isCookieSyncDedupEnabled)
            assertFalse(snapshot.isAdSdkComponentsDisabled)
            assertFalse(snapshot.isVideoPreloadDisabled)
        }
    }

    @Test
    fun performanceChildrenBecomeRuntimeActiveWhenMasterIsOn() {
        withScanAvailability(performanceCapabilities.associateWith { ConfigManager.ScanFeatureAvailabilityState.AVAILABLE }) {
            val snapshot = buildSnapshot(mapOf(
                AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to true,
                PerformancePreferences.KEY_COOKIE_SYNC_DEDUP to true,
                PerformancePreferences.KEY_DISABLE_AD_SDK_COMPONENTS to true,
                PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS to true,
            ))
            assertTrue(snapshot.isPerformanceOptimizationEnabled)
            assertTrue(snapshot.isCookieSyncDedupEnabled)
            assertTrue(snapshot.isAdSdkComponentsDisabled)
            assertTrue(snapshot.isVideoPreloadDisabled)
        }
    }

    @Test
    fun performanceChildrenUseEnabledDefaultsOnlyWhenMasterIsOn() {
        withScanAvailability(performanceCapabilities.associateWith { ConfigManager.ScanFeatureAvailabilityState.AVAILABLE }) {
            for (master in listOf(false, true)) {
                val snapshot = buildSnapshot(mapOf(
                    AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to master,
                ))
                assertEquals(master, snapshot.isPerformanceOptimizationEnabled)
                assertEquals(master, snapshot.isCookieSyncDedupEnabled)
                assertEquals(master, snapshot.isAdSdkComponentsDisabled)
                assertEquals(master, snapshot.isVideoPreloadDisabled)
            }
        }
    }

    @Test
    fun performanceConfigurationRequiresItsOwnResolvedSymbolsWithoutChangingSavedPreference() {
        val targets = listOf(
            Triple(PerformancePreferences.KEY_COOKIE_SYNC_DEDUP, HookFeatureKey.COOKIE_SYNC_DEDUP,
                { s: SettingsSnapshot -> s.isCookieSyncDedupEnabled }),
            Triple(PerformancePreferences.KEY_DISABLE_AD_SDK_COMPONENTS, HookFeatureKey.DISABLE_AD_SDK_COMPONENTS,
                { s: SettingsSnapshot -> s.isAdSdkComponentsDisabled }),
            Triple(PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS, HookFeatureKey.DISABLE_VIDEO_COMPONENTS,
                { s: SettingsSnapshot -> s.isVideoPreloadDisabled }),
        )
        val saved = targets.associate { it.first to true } + mapOf(
            AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
            PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to true,
        )
        for ((key, capability, read) in targets) for (state in ConfigManager.ScanFeatureAvailabilityState.entries) {
            val capabilities = performanceCapabilities.associateWith { ConfigManager.ScanFeatureAvailabilityState.AVAILABLE } +
                (capability to state)
            withScanAvailability(capabilities) {
                val snapshot = buildSnapshot(saved)
                assertEquals("$key/$state", state == ConfigManager.ScanFeatureAvailabilityState.AVAILABLE ||
                    state == ConfigManager.ScanFeatureAvailabilityState.PARTIAL, read(snapshot))
                targets.filter { it.first != key }.forEach { (_, _, readOther) -> assertTrue(readOther(snapshot)) }
                assertTrue(saved.getValue(key))
            }
        }
    }

    @Test
    fun performanceDiagnosticsReportMissingCapabilitiesForEachRetainedControl() {
        val keys = listOf(PerformancePreferences.KEY_COOKIE_SYNC_DEDUP,
            PerformancePreferences.KEY_DISABLE_AD_SDK_COMPONENTS, PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS)
        for (index in keys.indices) for (state in listOf(
            ConfigManager.ScanFeatureAvailabilityState.UNKNOWN, ConfigManager.ScanFeatureAvailabilityState.DISABLED,
        )) {
            val capabilities = performanceCapabilities.associateWith { ConfigManager.ScanFeatureAvailabilityState.AVAILABLE } +
                (performanceCapabilities[index] to state)
            withScanAvailability(capabilities) {
                val snapshot = buildSnapshot(keys.associateWith { true } + mapOf(
                    AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to true,
                ))
                val lines = SettingsDiagnostics.formatPerformanceStatusLines(snapshot)
                assertEquals(6, lines.size)
                val status = lines.single { it.startsWith("PerformanceFeature[${keys[index]}]") }
                assertTrue(status.contains("active=OFF scan=$state reason=scan_unavailable"))
            }
        }
    }

    @Test
    fun homeNativeGlassEnabledWithBackgroundImage() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.HOME_NATIVE_GLASS to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val snapshot = buildSnapshot(
                mapOf(
                    HomeGlassPreferences.KEY_ENABLE_HOME_NATIVE_GLASS to true,
                    HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT to "/tmp/bg.png",
                ),
            )

            assertTrue(snapshot.isHomeNativeGlassEnabled)
        }
    }

    @Test fun liquidGlassSnapshotUsesCustomSettingsOnlyWhenEnabled() {
        val values = mapOf<String, Any?>(
            TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS to true,
            BottomTabLiquidGlassPreferences.POSITION_ADJUSTMENT to 40,
            BottomTabLiquidGlassPreferences.LIQUID_GLASS to false,
        )
        assertEquals(BottomTabLiquidGlassConfig(positionAdjustment = 40, liquidGlassEnabled = false),
            buildSnapshot(values).bottomTabLiquidGlass)
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT,
            buildSnapshot(values + (TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS to false)).bottomTabLiquidGlass)
    }


    @Test
    fun tabCustomizationUpgradePreservesEachPreviouslyEnabledFeature() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.SIMPLIFY_HOME_TOP_TABS to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
                HookFeatureKey.SIMPLIFY_BOTTOM_TABS to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val keys = listOf(
                TabPreferences.KEY_CUSTOM_HOME_TOP_TABS,
                TabPreferences.KEY_CUSTOM_BOTTOM_TABS,
                TabPreferences.KEY_AUTO_HIDE_HOME_TAB,
                TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS,
            )
            keys.forEachIndexed { index, key ->
                val snapshot = buildSnapshot(mapOf(key to true))
                assertEquals(
                    keys.indices.map { it == index },
                    listOf(snapshot.isHomeTopTabsCustomEnabled, snapshot.isBottomTabsCustomEnabled,
                        snapshot.isHomeTabAutoHideEnabled, snapshot.isFloatingBottomBarEnabled),
                )
            }
        }
    }

    @Test
    fun tabCustomizationMasterOffSuppressesAllFourChildrenAndTheirConfiguration() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.SIMPLIFY_HOME_TOP_TABS to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
                HookFeatureKey.SIMPLIFY_BOTTOM_TABS to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val values = mapOf<String, Any?>(
                TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to false,
                TabPreferences.KEY_CUSTOM_HOME_TOP_TABS to true,
                TabPreferences.KEY_CUSTOM_BOTTOM_TABS to true,
                TabPreferences.KEY_AUTO_HIDE_HOME_TAB to true,
                TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS to true,
                TabPreferences.KEY_HOME_TOP_TAB_DISABLED_KEYS to "code:material",
                TabPreferences.KEY_BOTTOM_TAB_RETAIL_STORE to false,
                "bottom_tab_liquid_glass_bottom_gap_dp" to 24,
                TabPreferences.KEY_HIDE_HOME_TAB_RED_DOT to true,
            )
            val disabled = buildSnapshot(values)
            assertFalse(disabled.isHomeTopTabsCustomEnabled)
            assertFalse(disabled.isBottomTabsCustomEnabled)
            assertFalse(disabled.isHomeTabAutoHideEnabled)
            assertFalse(disabled.isFloatingBottomBarEnabled)
            assertTrue(disabled.homeTopTabDisabledKeys.isEmpty())
            assertFalse(disabled.isBottomTabHomeEnabled)
            assertEquals(BottomTabLiquidGlassConfig.DEFAULT, disabled.bottomTabLiquidGlass)
            assertTrue(disabled.isHomeTabRedDotHidden)

            val restored = buildSnapshot(values + (TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to true))
            assertTrue(restored.isHomeTopTabsCustomEnabled)
            assertTrue(restored.isBottomTabsCustomEnabled)
            assertTrue(restored.isHomeTabAutoHideEnabled)
            assertTrue(restored.isFloatingBottomBarEnabled)
            assertFalse(restored.isHomeTopTabMaterialEnabled)
            assertTrue(restored.isBottomTabHomeEnabled)
            assertFalse(restored.isBottomTabRetailStoreEnabled)
            assertEquals(0, restored.bottomTabLiquidGlass.positionAdjustment)
        }
    }

    @Test
    fun tabCustomizationMasterDoesNotEnableUnselectedChildren() {
        val snapshot = buildSnapshot(mapOf(TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to true))
        assertFalse(snapshot.isHomeTopTabsCustomEnabled)
        assertFalse(snapshot.isBottomTabsCustomEnabled)
        assertFalse(snapshot.isHomeTabAutoHideEnabled)
        assertFalse(snapshot.isFloatingBottomBarEnabled)
    }

    @Test
    fun tabCustomizationMasterDoesNotBypassUnavailableChildSymbols() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.SIMPLIFY_HOME_TOP_TABS to ConfigManager.ScanFeatureAvailabilityState.DISABLED,
                HookFeatureKey.SIMPLIFY_BOTTOM_TABS to ConfigManager.ScanFeatureAvailabilityState.DISABLED,
            ),
        ) {
            val snapshot = buildSnapshot(
                mapOf(
                    TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to true,
                    TabPreferences.KEY_CUSTOM_HOME_TOP_TABS to true,
                    TabPreferences.KEY_CUSTOM_BOTTOM_TABS to true,
                    TabPreferences.KEY_AUTO_HIDE_HOME_TAB to true,
                    TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS to true,
                ),
            )
            assertFalse(snapshot.isHomeTopTabsCustomEnabled)
            assertFalse(snapshot.isBottomTabsCustomEnabled)
            assertTrue(snapshot.isHomeTabAutoHideEnabled)
            assertTrue(snapshot.isFloatingBottomBarEnabled)
        }
    }

    private fun buildSnapshot(values: Map<String, Any?>): SettingsSnapshot {
        val method = ConfigManager::class.java.getDeclaredMethod(
            "buildSettingsSnapshot",
            SharedPreferences::class.java,
        )
        method.isAccessible = true
        return method.invoke(ConfigManager, sharedPreferences(values)) as SettingsSnapshot
    }

    private fun <T> withScanAvailability(
        value: Map<String, ConfigManager.ScanFeatureAvailabilityState>,
        block: () -> T,
    ): T {
        val field = ConfigManager::class.java.getDeclaredField("scanFeatureAvailability")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val previous = field.get(ConfigManager) as Map<String, ConfigManager.ScanFeatureAvailabilityState>
        field.set(ConfigManager, value)
        return try {
            block()
        } finally {
            field.set(ConfigManager, previous)
        }
    }

    private fun sharedPreferences(values: Map<String, Any?>): SharedPreferences = TestPreferences(
        values,
        readMethods = setOf("getBoolean", "getInt", "getString", "getAll",
            "registerOnSharedPreferenceChangeListener", "unregisterOnSharedPreferenceChangeListener"),
        typedDefaults = true,
    ).prefs
}
