package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import java.lang.reflect.Proxy
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
        HookFeatureKey.DISABLE_AD_SDK_COMPONENTS, HookFeatureKey.DISABLE_VIDEO_COMPONENTS)

    @Test fun declarationKeepsOnlyTheMasterAndThreeVerifiedControlsWithOriginalChoices() {
        assertEquals("force_host_performance_flags", PerformancePreferences.KEY_COOKIE_SYNC_DEDUP)
        assertEquals(listOf(
            "enable_performance_optimization", "force_host_performance_flags",
            "disable_ad_sdk_components", "disable_video_components",
        ), PerformancePreferences.preferences.map { it.key })
        assertEquals(listOf(false, true, true, true), PerformancePreferences.preferences.map { it.defaultValue })
        assertEquals(listOf(null) + capabilities, PerformancePreferences.preferences.map { it.capabilityKey })
        retiredKeys.forEach { key -> assertFalse(key, SettingsCatalog.preferences.any { it.key == key }) }
        val fields = SettingsSnapshot::class.java.declaredFields.map { it.name }.toSet()
        for (field in listOf("isPbPreloadForced", "isAiComponentsDisabled", "isMonitorSyncComponentsDisabled",
            "isPbPerformanceModeEnabled", "isPbScrollCoalesceEnabled", "isHostSlideAnimationDisabled",
            "isHostFeedColdOptEnabled", "isApsarasScheduleDisabled", "isFlutterPreinitDisabled",
            "isLowEndDeviceConfigForced", "isTitanPatchBlockEnabled", "isHostPerformanceFlagsForced",
            "isVideoPreloadDisabled")) {
            assertFalse("Obsolete runtime field: $field", field in fields)
        }
    }

    @Test fun upgradeRemovesOnlyRetiredValuesInOneWriteAndIsIdempotent() {
        val preserved = mapOf<String, Any>(
            "force_host_performance_flags" to false,
            "disable_ad_sdk_components" to true,
            "disable_video_components" to false,
            "enable_performance_optimization" to true,
            "unrelated" to 3,
        )
        for (oldValue in listOf(true, false, "obsolete", 7)) {
            val cases = listOf(emptyMap(), retiredKeys.associateWith { oldValue }) +
                retiredKeys.map { mapOf(it to oldValue) }
            for (retired in cases) {
                val prefs = MigrationPreferences((preserved + retired).toMutableMap())
                repeat(2) { PerformancePreferences.removeRetiredSettings(prefs.instance) }
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
            assertTrue(snapshot.isVideoComponentsDisabled)
        }
    }

    private class MigrationPreferences(val values: MutableMap<String, Any>) {
        var edits = 0
        var writes = 0
        private val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "remove" -> { values.remove(args!![0]); proxy }
                "apply" -> { writes++; null }
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        val instance = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "contains" -> values.containsKey(args!![0])
                "edit" -> { edits++; editor }
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
