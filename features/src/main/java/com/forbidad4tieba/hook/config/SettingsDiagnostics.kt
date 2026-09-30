package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.PerformancePreferences.KEY_FORCE_LOW_END_DEVICE_CONFIG
import com.forbidad4tieba.hook.config.PerformancePreferences.KEY_ENABLE_PERFORMANCE_OPTIMIZATION

object SettingsDiagnostics {
    fun formatPerformanceStatusLines(settings: SettingsSnapshot = ConfigManager.snapshot()): List<String> {
        val currentPrefs = ConfigManager.currentValues()

        fun configured(setting: BooleanPreference, absentDefault: Boolean = setting.defaultValue): Boolean {
            val p = currentPrefs ?: return absentDefault
            return if (p.contains(setting.key)) setting.read(p) else absentDefault
        }

        fun onOff(value: Boolean): String = if (value) "ON" else "OFF"

        fun scanState(key: String): String = if (SettingsCatalog.capabilityFor(key) == null) {
            "NOT_REQUIRED"
        } else {
            ConfigManager.getScanFeatureAvailabilityState(key).toString()
        }

        fun masterReason(configured: Boolean, active: Boolean): String {
            return when {
                active -> "active"
                !settings.areRestrictedFeaturesUnlocked -> "restricted_locked"
                !configured -> "config_off"
                !ConfigManager.isScanFeatureAvailable(KEY_ENABLE_PERFORMANCE_OPTIMIZATION) -> "scan_unavailable"
                else -> "inactive"
            }
        }

        fun childReason(key: String, configured: Boolean, active: Boolean): String {
            return when {
                active -> "active"
                !settings.areRestrictedFeaturesUnlocked -> "restricted_locked"
                !settings.isPerformanceOptimizationEnabled -> "master_off"
                !ConfigManager.isScanFeatureAvailable(key) -> "scan_unavailable"
                !configured -> "config_off"
                else -> "inactive"
            }
        }

        fun childLine(setting: BooleanPreference, active: Boolean, absentDefault: Boolean = setting.defaultValue): String {
            val key = setting.key
            val configured = configured(setting, absentDefault)
            return "PerformanceFeature[$key] config=${onOff(configured)} " +
                "master=${onOff(settings.isPerformanceOptimizationEnabled)} " +
                "active=${onOff(active)} scan=${scanState(key)} " +
                "reason=${childReason(key, configured, active)}"
        }

        val masterConfigured = configured(PerformancePreferences.ENABLE_PERFORMANCE_OPTIMIZATION)

        return listOf(
            "PerformanceFeature[$KEY_ENABLE_PERFORMANCE_OPTIMIZATION] " +
                "config=${onOff(masterConfigured)} active=${onOff(settings.isPerformanceOptimizationEnabled)} " +
                "scan=${scanState(KEY_ENABLE_PERFORMANCE_OPTIMIZATION)} " +
                "restricted=${onOff(settings.areRestrictedFeaturesUnlocked)} " +
                "reason=${masterReason(masterConfigured, settings.isPerformanceOptimizationEnabled)}",
            childLine(PerformancePreferences.FORCE_HOST_PERFORMANCE_FLAGS, settings.isHostPerformanceFlagsForced),
            childLine(PerformancePreferences.FORCE_LOW_END_DEVICE_CONFIG, settings.isLowEndDeviceConfigForced, ConfigManager.isScanFeatureAvailable(KEY_FORCE_LOW_END_DEVICE_CONFIG)),
            childLine(PerformancePreferences.DISABLE_APSARAS_SCHEDULE, settings.isApsarasScheduleDisabled),
            childLine(PerformancePreferences.ENABLE_PB_PERFORMANCE_MODE, settings.isPbPerformanceModeEnabled),
            childLine(PerformancePreferences.FORCE_PB_PRELOAD, settings.isPbPreloadForced),
            childLine(PerformancePreferences.DISABLE_HOST_SLIDE_ANIMATION, settings.isHostSlideAnimationDisabled),
            childLine(PerformancePreferences.ENABLE_PB_SCROLL_COALESCE, settings.isPbScrollCoalesceEnabled),
            childLine(PerformancePreferences.DISABLE_AD_SDK_COMPONENTS, settings.isAdSdkComponentsDisabled),
            childLine(PerformancePreferences.DISABLE_FLUTTER_PREINIT, settings.isFlutterPreinitDisabled),
            childLine(PerformancePreferences.FORCE_HOST_FEED_COLD_OPT, settings.isHostFeedColdOptEnabled),
            childLine(PerformancePreferences.BLOCK_TITAN_PATCH, settings.isTitanPatchBlockEnabled),
            childLine(PerformancePreferences.DISABLE_AI_COMPONENTS, settings.isAiComponentsDisabled),
            childLine(PerformancePreferences.DISABLE_VIDEO_COMPONENTS, settings.isVideoComponentsDisabled),
            childLine(PerformancePreferences.DISABLE_MONITOR_SYNC_COMPONENTS, settings.isMonitorSyncComponentsDisabled),
        )
    }
}
