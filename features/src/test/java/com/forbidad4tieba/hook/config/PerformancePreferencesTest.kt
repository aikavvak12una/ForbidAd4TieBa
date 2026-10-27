package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.*
import org.junit.Test

class PerformancePreferencesTest {
    // Frozen list of user-authorized retirements, independent of the production cleanup list.
    private val retiredKeys = listOf(
        "force_pb_preload", "disable_ai_components", "disable_monitor_sync_components",
        "enable_pb_performance_mode", "enable_pb_scroll_coalesce", "disable_host_slide_animation",
        "force_host_feed_cold_opt", "disable_apsaras_schedule", "disable_flutter_preinit",
        "force_low_end_device_config", "block_titan_patch",
    )
    private val capabilities = listOf(HookFeatureKey.COOKIE_SYNC_DEDUP,
        HookFeatureKey.DISABLE_AD_SDK_COMPONENTS, HookFeatureKey.DISABLE_VIDEO_COMPONENTS,
        HookFeatureKey.DISABLE_YY_BACKGROUND, HookFeatureKey.DISABLE_SWAN_PRELOAD)

    @Test fun allPerformanceChildrenDefaultOnAndTheMasterDefaultIsPreserved() {
        assertEquals("force_host_performance_flags", PerformancePreferences.KEY_COOKIE_SYNC_DEDUP)
        assertEquals(listOf(
            "enable_performance_optimization", "force_host_performance_flags",
            "disable_ad_sdk_components", "disable_video_components",
            "disable_yy_background", "disable_swan_preload",
        ), PerformancePreferences.preferences.map { it.key })
        assertEquals(listOf(false, true, true, true, true, true), PerformancePreferences.preferences.map { it.defaultValue })
        assertEquals(listOf(null) + capabilities, PerformancePreferences.preferences.map { it.capabilityKey })
        retiredKeys.forEach { key -> assertFalse(key, SettingsCatalog.preferences.any { it.key == key }) }
        val fields = SettingsSnapshot::class.java.declaredFields.map { it.name }.toSet()
        for (field in listOf("isPbPreloadForced", "isAiComponentsDisabled", "isMonitorSyncComponentsDisabled",
            "isPbPerformanceModeEnabled", "isPbScrollCoalesceEnabled", "isHostSlideAnimationDisabled",
            "isHostFeedColdOptEnabled", "isApsarasScheduleDisabled", "isFlutterPreinitDisabled",
            "isLowEndDeviceConfigForced", "isTitanPatchBlockEnabled", "isHostPerformanceFlagsForced",
            "isVideoComponentsDisabled")) {
            assertFalse("Obsolete runtime field: $field", field in fields)
        }
    }

    @Test fun upgradeRemovesOnlyRetiredValuesInOneWriteAndIsIdempotent() {
        val preserved = mapOf<String, Any>(
            "force_host_performance_flags" to false,
            "disable_ad_sdk_components" to true,
            "disable_video_components" to false,
            "disable_yy_background" to false,
            "disable_swan_preload" to false,
            "enable_performance_optimization" to true,
            "unrelated" to 3,
        )
        for (oldValue in listOf(true, false, "obsolete", 7)) {
            val cases = listOf(emptyMap(), retiredKeys.associateWith { oldValue }) +
                retiredKeys.map { mapOf(it to oldValue) }
            for (retired in cases) {
                val prefs = TestPreferences((preserved + retired).toMutableMap(),
                    editorMethods = setOf("remove", "apply"))
                repeat(2) { PerformancePreferences.removeRetiredSettings(prefs.prefs) }
                assertEquals(preserved, prefs.values)
                assertEquals(if (retired.isEmpty()) 0 else 1, prefs.edits)
                assertEquals(if (retired.isEmpty()) 0 else 1, prefs.writes)
            }
        }
    }

    @Test fun dirtyRetiredValuesCannotChangePublishedPolicyInAnyCapabilityOrLockState() {
        for (master in listOf(false, true)) for (state in ConfigManager.ScanFeatureAvailabilityState.entries) {
            for (locked in listOf(false, true)) {
                val values = mapOf<String, Any>(
                    "enable_performance_optimization" to master, "restricted_features_unlocked" to true,
                    "force_host_performance_flags" to true, "disable_ad_sdk_components" to false,
                    "disable_video_components" to true,
                )
                val host = HostCapabilities(capabilities.associateWith { state })
                val remote = RemoteSettingsPolicy(locked)
                val expected = EffectiveSettingsPolicy.derive(UserSettings(values), host, remote)
                for (oldValue in listOf(true, false, "obsolete", 7)) {
                    val dirtyCases = retiredKeys.map { mapOf(it to oldValue) } +
                        listOf(retiredKeys.associateWith { oldValue })
                    for (dirty in dirtyCases) {
                        assertEquals("$master/$state/$locked/$dirty", expected,
                            EffectiveSettingsPolicy.derive(UserSettings(values + dirty), host, remote))
                    }
                }
            }
        }
    }

    @Test fun storedCookieChoiceSurvivesNarrowingAndDoesNotAffectOtherControls() {
        val host = HostCapabilities(capabilities.associateWith { ConfigManager.ScanFeatureAvailabilityState.AVAILABLE })
        for (chosen in listOf(false, true)) {
            val snapshot = EffectiveSettingsPolicy.derive(UserSettings(mapOf(
                "enable_performance_optimization" to true, "restricted_features_unlocked" to true,
                "force_host_performance_flags" to chosen,
            )), host, RemoteSettingsPolicy(false)).snapshot
            assertEquals(chosen, snapshot.isCookieSyncDedupEnabled)
            assertTrue(snapshot.isAdSdkComponentsDisabled)
            assertTrue(snapshot.isVideoPreloadDisabled)
            assertTrue(snapshot.isYyBackgroundDisabled)
            assertTrue(snapshot.isSwanPreloadDisabled)
        }
    }

}
