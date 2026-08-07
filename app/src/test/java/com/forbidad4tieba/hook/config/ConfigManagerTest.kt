package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class ConfigManagerTest {
    @Test
    fun parseModelScoreThresholdsAcceptsSupportedSeparatorsAndKeepsLastDuplicateValue() {
        val thresholds = ConfigManager.parseModelScoreThresholds(
            """
            alpha=0.25
            beta:1.5; alpha = 0.75
            """.trimIndent(),
        )

        assertEquals(listOf("alpha", "beta"), thresholds.map { it.key })
        assertEquals(0.75, thresholds[0].threshold, 0.0)
        assertEquals(1.5, thresholds[1].threshold, 0.0)
    }

    @Test
    fun parseModelScoreThresholdsIgnoresInvalidValuesAndBlankKeys() {
        val thresholds = ConfigManager.parseModelScoreThresholds(
            """
            =0.1
            negative=-1
            nan=NaN
            infinite=Infinity
            bad=text
            ok=0
            spaced : 2.5
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ConfigManager.ModelScoreThreshold("ok", 0.0),
                ConfigManager.ModelScoreThreshold("spaced", 2.5),
            ),
            thresholds,
        )
    }

    @Test
    fun parseModelScoreThresholdsReturnsEmptyListForBlankInput() {
        assertTrue(ConfigManager.parseModelScoreThresholds(null).isEmpty())
        assertTrue(ConfigManager.parseModelScoreThresholds("   ").isEmpty())
    }

    @Test
    fun nonScanPreferencesAreAvailableWithoutScanState() {
        assertNull(ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION))
        assertNull(ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_FORCE_FEED_UI_OPT))
        assertNull(ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_HIDE_HOME_TAB_RED_DOT))
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION),
        )
        assertTrue(ConfigManager.isScanFeatureAvailable(ConfigManager.KEY_FORCE_FEED_UI_OPT))
        assertTrue(ConfigManager.isScanFeatureAvailable(ConfigManager.KEY_HIDE_HOME_TAB_RED_DOT))
    }

    @Test
    fun detailedLoggingStableHostPathIsAvailableBeforeSymbolScan() {
        assertEquals(
            HookFeatureKey.DETAILED_LOGGING,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_DETAILED_LOGGING),
        )
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_DETAILED_LOGGING),
        )
        assertTrue(ConfigManager.isScanFeatureAvailable(ConfigManager.KEY_ENABLE_DETAILED_LOGGING))
    }

    @Test
    fun autoSignInRemainsUnknownUntilScanStateIsApplied() {
        assertEquals(
            ConfigManager.KEY_ENABLE_AUTO_SIGN_IN,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN),
        )
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.UNKNOWN,
            ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN),
        )
        assertFalse(ConfigManager.isScanFeatureAvailable(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN))
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
                ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN),
            )
            assertTrue(ConfigManager.isScanFeatureAvailable(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN))
        }

        withScanAvailability(
            mapOf(
                HookFeatureKey.AUTO_SIGN_IN to ConfigManager.ScanFeatureAvailabilityState.PARTIAL,
            ),
        ) {
            assertEquals(
                ConfigManager.ScanFeatureAvailabilityState.PARTIAL,
                ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN),
            )
            assertTrue(ConfigManager.isScanFeatureAvailable(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN))
        }

        withScanAvailability(
            mapOf(
                HookFeatureKey.AUTO_SIGN_IN to ConfigManager.ScanFeatureAvailabilityState.DISABLED,
            ),
        ) {
            assertEquals(
                ConfigManager.ScanFeatureAvailabilityState.DISABLED,
                ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN),
            )
            assertFalse(ConfigManager.isScanFeatureAvailable(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN))
        }
    }

    @Test
    fun scanBackedPreferencesRemainUnknownUntilScanStateIsApplied() {
        assertEquals(
            ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS),
        )
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.UNKNOWN,
            ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS),
        )
    }

    @Test
    fun adChildPreferencesMapToIndependentFeatureKeys() {
        assertEquals(
            HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_BLOCK_AD_SEARCH_BOX_TEXT),
        )
        assertEquals(
            HookFeatureKey.BLOCK_AD_FEED,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_BLOCK_AD_FEED),
        )
    }

    @Test
    fun formerlyDescribedPreferencesUseCanonicalFeatureKeys() {
        assertEquals(
            HookFeatureKey.AUTO_LOAD_MORE,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_AUTO_LOAD_MORE),
        )
        assertEquals(
            HookFeatureKey.DISABLE_AUTO_REFRESH,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_DISABLE_AUTO_REFRESH),
        )
        assertEquals(
            HookFeatureKey.DEFAULT_ORIGINAL_IMAGE,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE),
        )
        assertEquals(
            HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER),
        )
        assertEquals(
            HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_PB_LIKE_AUTO_REPLY),
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
                    ConfigManager.KEY_ENABLE_CUSTOM_POST_FILTER to false,
                    ConfigManager.KEY_FILTER_POST_VOTE to true,
                    ConfigManager.KEY_FILTER_POST_LOTTERY to true,
                    ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD to true,
                    ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD_LIST to "alpha,beta",
                    ConfigManager.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    ConfigManager.KEY_FILTER_POST_MODEL_SCORE to true,
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
                    ConfigManager.KEY_ENABLE_CUSTOM_POST_FILTER to true,
                    ConfigManager.KEY_FILTER_POST_VOTE to true,
                    ConfigManager.KEY_FILTER_POST_LOTTERY to true,
                    ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD to true,
                    ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD_LIST to "alpha,beta",
                    ConfigManager.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    ConfigManager.KEY_FILTER_POST_MODEL_SCORE to true,
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
    fun legacyCompatSeedTurnsOnAdBlockMasterForLegacySchemaUsers() {
        val current = mutableSharedPreferences(
            mapOf("user_settings_version_code" to 32),
        )
        val legacy = mutableSharedPreferences(
            mapOf("user_settings_version_code" to 17),
        )

        invokeLegacyCompatSeed(current, legacy)

        assertTrue(current.getBoolean(ConfigManager.KEY_RESTRICTED_FEATURES_UNLOCKED, false))
        assertTrue(current.getBoolean(ConfigManager.KEY_BLOCK_AD, false))
        assertTrue(current.getBoolean(ConfigManager.KEY_BLOCK_AD_POST_PAGE, false))
    }

    @Test
    fun legacyCompatSeedBackfillsCustomPostFilterParentFromLegacyChildren() {
        val current = mutableSharedPreferences(
            mapOf("user_settings_version_code" to 32),
        )
        val legacy = mutableSharedPreferences(
            mapOf(
                "user_settings_version_code" to 17,
                ConfigManager.KEY_FILTER_POST_VIDEO to true,
            ),
        )

        invokeLegacyCompatSeed(current, legacy)

        assertTrue(current.getBoolean(ConfigManager.KEY_ENABLE_CUSTOM_POST_FILTER, false))
    }

    @Test
    fun legacyCompatSeedCopiesMissingUserSettingsFromLegacyPrefs() {
        val current = mutableSharedPreferences(
            mapOf("user_settings_version_code" to 32),
        )
        val legacy = mutableSharedPreferences(
            mapOf(
                "user_settings_version_code" to 17,
                ConfigManager.KEY_ENABLE_AUTO_LOAD_MORE to true,
                ConfigManager.KEY_FILTER_ENTER_FORUM_WEB to true,
            ),
        )

        invokeLegacyCompatSeed(current, legacy)

        assertTrue(current.getBoolean(ConfigManager.KEY_ENABLE_AUTO_LOAD_MORE, false))
        assertTrue(current.getBoolean(ConfigManager.KEY_FILTER_ENTER_FORUM_WEB, false))
    }

    @Test
    fun legacyCompatSeedSkipsInternalHookSymbolCacheKeys() {
        val current = mutableSharedPreferences(
            mapOf("user_settings_version_code" to 32),
        )
        val legacy = mutableSharedPreferences(
            mapOf(
                "user_settings_version_code" to 17,
                "hook_symbol_json_v19" to "{json}",
            ),
        )

        invokeLegacyCompatSeed(current, legacy)

        assertFalse(current.contains("hook_symbol_json_v19"))
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
                    ConfigManager.KEY_CUSTOM_HOME_TOP_TABS to false,
                    ConfigManager.KEY_HOME_TOP_TAB_MATERIAL to true,
                    ConfigManager.KEY_HOME_TOP_TAB_RECOMMEND to true,
                    ConfigManager.KEY_HOME_TOP_TAB_LIVE to true,
                    ConfigManager.KEY_HOME_TOP_TAB_FOLLOWED to true,
                    ConfigManager.KEY_CUSTOM_BOTTOM_TABS to false,
                    ConfigManager.KEY_BOTTOM_TAB_HOME to true,
                    ConfigManager.KEY_BOTTOM_TAB_ENTER_FORUM to true,
                    ConfigManager.KEY_BOTTOM_TAB_RETAIL_STORE to true,
                    ConfigManager.KEY_BOTTOM_TAB_MESSAGE to true,
                    ConfigManager.KEY_BOTTOM_TAB_MINE to true,
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
                    ConfigManager.KEY_CUSTOM_HOME_TOP_TABS to true,
                    ConfigManager.KEY_HOME_TOP_TAB_MATERIAL to true,
                    ConfigManager.KEY_HOME_TOP_TAB_RECOMMEND to false,
                    ConfigManager.KEY_HOME_TOP_TAB_LIVE to true,
                    ConfigManager.KEY_HOME_TOP_TAB_FOLLOWED to false,
                    ConfigManager.KEY_CUSTOM_BOTTOM_TABS to true,
                    ConfigManager.KEY_BOTTOM_TAB_HOME to true,
                    ConfigManager.KEY_BOTTOM_TAB_ENTER_FORUM to false,
                    ConfigManager.KEY_BOTTOM_TAB_RETAIL_STORE to true,
                    ConfigManager.KEY_BOTTOM_TAB_MESSAGE to false,
                    ConfigManager.KEY_BOTTOM_TAB_MINE to true,
                ),
            )

            assertTrue(snapshot.isHomeTopTabsCustomEnabled)
            assertTrue(snapshot.isHomeTopTabMaterialEnabled)
            assertFalse(snapshot.isHomeTopTabRecommendEnabled)
            assertTrue(snapshot.isHomeTopTabLiveEnabled)
            assertFalse(snapshot.isHomeTopTabFollowedEnabled)
            assertTrue(ConfigManager.HOME_TOP_TAB_KEY_RECOMMEND in snapshot.homeTopTabDisabledKeys)
            assertTrue(ConfigManager.HOME_TOP_TAB_KEY_FOLLOWED in snapshot.homeTopTabDisabledKeys)
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
                ConfigManager.KEY_HIDE_HOME_TAB_RED_DOT to true,
            ),
        )

        assertFalse(defaultSnapshot.isHomeTabRedDotHidden)
        assertTrue(enabledSnapshot.isHomeTabRedDotHidden)
    }

    @Test
    fun inputMemeBarBlockDefaultsOffAndFollowsScanAvailability() {
        assertEquals(
            HookFeatureKey.HIDE_INPUT_MEME_BAR,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_HIDE_INPUT_MEME_BAR),
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
                    mapOf(ConfigManager.KEY_HIDE_INPUT_MEME_BAR to true),
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
                    mapOf(ConfigManager.KEY_HIDE_INPUT_MEME_BAR to true),
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
                ConfigManager.HOME_TOP_TAB_KEY_RECOMMEND,
                ConfigManager.HOME_TOP_TAB_KEY_FOLLOWED,
                "code:custom",
            )
            val expandedDisabled = ConfigManager.expandHomeTopTabDisabledKeys(disabled)
            val snapshot = buildSnapshot(
                mapOf(
                    ConfigManager.KEY_CUSTOM_HOME_TOP_TABS to true,
                    ConfigManager.KEY_HOME_TOP_TAB_MATERIAL to true,
                    ConfigManager.KEY_HOME_TOP_TAB_RECOMMEND to true,
                    ConfigManager.KEY_HOME_TOP_TAB_LIVE to true,
                    ConfigManager.KEY_HOME_TOP_TAB_FOLLOWED to true,
                    ConfigManager.KEY_HOME_TOP_TAB_DISABLED_KEYS to
                        ConfigManager.serializeHomeTopTabDisabledKeys(disabled),
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
                    ConfigManager.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    ConfigManager.KEY_ENABLE_PB_LIKE_AUTO_REPLY to false,
                    ConfigManager.KEY_PB_LIKE_AUTO_REPLY_TEXT to "hello",
                    ConfigManager.KEY_ENABLE_CUSTOM_POST_FILTER to true,
                    ConfigManager.KEY_FILTER_POST_MODEL_SCORE to false,
                    ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS to "quality=0.5",
                    ConfigManager.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES to "quality=10",
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
        withScanAvailability(emptyMap()) {
            val snapshot = buildSnapshot(
                mapOf(
                    ConfigManager.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to false,
                    ConfigManager.KEY_DISABLE_MONITOR_SYNC_COMPONENTS to true,
                    ConfigManager.KEY_FORCE_HOST_PERFORMANCE_FLAGS to true,
                    ConfigManager.KEY_FORCE_FEED_UI_OPT to true,
                ),
            )

            assertFalse(snapshot.isPerformanceOptimizationEnabled)
            assertFalse(snapshot.isMonitorSyncComponentsDisabled)
            assertFalse(snapshot.isHostPerformanceFlagsForced)
            assertTrue(snapshot.isFeedUiOptForced)
            assertTrue(snapshot.isForceFeedUiOptRuntimeEnabled)
        }
    }

    @Test
    fun performanceChildrenBecomeRuntimeActiveWhenMasterIsOn() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.DISABLE_AI_COMPONENTS to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
                HookFeatureKey.ENABLE_PB_SCROLL_COALESCE to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val snapshot = buildSnapshot(
                mapOf(
                    ConfigManager.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to true,
                    ConfigManager.KEY_DISABLE_MONITOR_SYNC_COMPONENTS to true,
                    ConfigManager.KEY_FORCE_HOST_PERFORMANCE_FLAGS to true,
                    ConfigManager.KEY_DISABLE_AI_COMPONENTS to true,
                    ConfigManager.KEY_ENABLE_PB_SCROLL_COALESCE to true,
                ),
            )

            assertTrue(snapshot.isPerformanceOptimizationEnabled)
            assertTrue(snapshot.isMonitorSyncComponentsDisabled)
            assertTrue(snapshot.isHostPerformanceFlagsForced)
            assertTrue(snapshot.isAiComponentsDisabled)
            assertTrue(snapshot.isPbScrollCoalesceEnabled)
        }
    }

    @Test
    fun performanceChildrenUseEnabledDefaultsOnlyWhenMasterIsOn() {
        withScanAvailability(emptyMap()) {
            val snapshot = buildSnapshot(
                mapOf(
                    ConfigManager.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
                    ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION to true,
                ),
            )

            assertTrue(snapshot.isPerformanceOptimizationEnabled)
            assertTrue(snapshot.isHostPerformanceFlagsForced)
            assertTrue(snapshot.isLowEndDeviceConfigForced)
            assertTrue(snapshot.isApsarasScheduleDisabled)
            assertTrue(snapshot.isAdSdkComponentsDisabled)
            assertTrue(snapshot.isVideoComponentsDisabled)
            assertFalse(snapshot.isMonitorSyncComponentsDisabled)
            assertFalse(snapshot.isTitanPatchBlockEnabled)
        }
    }

    @Test
    fun homeNativeGlassDoesNotImplicitlyEnableForceFeedUiOpt() {
        withScanAvailability(
            mapOf(
                HookFeatureKey.HOME_NATIVE_GLASS to
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ),
        ) {
            val snapshot = buildSnapshot(
                mapOf(
                    ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS to true,
                    ConfigManager.KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT to "/tmp/bg.png",
                    ConfigManager.KEY_FORCE_FEED_UI_OPT to false,
                ),
            )

            assertTrue(snapshot.isHomeNativeGlassEnabled)
            assertFalse(snapshot.isFeedUiOptForced)
            assertFalse(snapshot.isForceFeedUiOptRuntimeEnabled)
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

    /**
     * 反射调用旧配置兼容补种逻辑，验证升级场景下的主开关迁移结果。
     *
     * 输入为当前配置和旧版混合配置；输出为无返回值。
     * 边界条件：测试只覆盖缺失键补种，不依赖 Android 真正的磁盘 SharedPreferences 实现。
     */
    private fun invokeLegacyCompatSeed(
        current: SharedPreferences,
        legacy: SharedPreferences,
    ) {
        val method = ConfigManager::class.java.getDeclaredMethod(
            "seedLegacyCompatPrefsIfNeeded",
            SharedPreferences::class.java,
            SharedPreferences::class.java,
        )
        method.isAccessible = true
        method.invoke(ConfigManager, current, legacy)
    }

    private fun sharedPreferences(values: Map<String, Any?>): SharedPreferences {
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            val key = args?.firstOrNull() as? String
            when (method.name) {
                "getBoolean" -> values[key] as? Boolean ?: args?.getOrNull(1) as Boolean
                "getInt" -> values[key] as? Int ?: args?.getOrNull(1) as Int
                "getString" -> values[key] as? String ?: args?.getOrNull(1) as? String
                "contains" -> values.containsKey(key)
                "getAll" -> values
                "registerOnSharedPreferenceChangeListener",
                "unregisterOnSharedPreferenceChangeListener",
                -> Unit
                "edit" -> error("edit() is not supported by test SharedPreferences")
                else -> error("Unexpected SharedPreferences call: ${method.name}")
            }
        } as SharedPreferences
    }

    /**
     * 构造一个可写的内存 SharedPreferences，用于测试迁移/补种这类会调用 `edit()` 的逻辑。
     *
     * 输入为初始键值；输出为可读写的 SharedPreferences 代理对象。
     * 边界条件：仅实现当前测试需要的读写能力，未覆盖的方法会直接抛错，避免静默掩盖测试缺口。
     */
    private fun mutableSharedPreferences(values: Map<String, Any?>): SharedPreferences {
        val store = LinkedHashMap(values)
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            val key = args?.firstOrNull() as? String
            when (method.name) {
                "getBoolean" -> store[key] as? Boolean ?: args?.getOrNull(1) as Boolean
                "getInt" -> store[key] as? Int ?: args?.getOrNull(1) as Int
                "getString" -> store[key] as? String ?: args?.getOrNull(1) as? String
                "contains" -> store.containsKey(key)
                "getAll" -> LinkedHashMap(store)
                "registerOnSharedPreferenceChangeListener",
                "unregisterOnSharedPreferenceChangeListener",
                -> Unit
                "edit" -> {
                    Proxy.newProxyInstance(
                        SharedPreferences.Editor::class.java.classLoader,
                        arrayOf(SharedPreferences.Editor::class.java),
                    ) { _, editorMethod, editorArgs ->
                        val editorKey = editorArgs?.firstOrNull() as? String
                        when (editorMethod.name) {
                            "putBoolean" -> {
                                store[editorKey!!] = editorArgs[1] as Boolean
                                null
                            }
                            "putInt" -> {
                                store[editorKey!!] = editorArgs[1] as Int
                                null
                            }
                            "putString" -> {
                                store[editorKey!!] = editorArgs[1] as String?
                                null
                            }
                            "remove" -> {
                                store.remove(editorKey)
                                null
                            }
                            "clear" -> {
                                store.clear()
                                null
                            }
                            "apply" -> null
                            "commit" -> true
                            else -> error("Unexpected SharedPreferences.Editor call: ${editorMethod.name}")
                        }
                    }
                }
                else -> error("Unexpected SharedPreferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
