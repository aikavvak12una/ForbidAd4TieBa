package com.forbidad4tieba.hook.config

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
            childLine(PerformancePreferences.COOKIE_SYNC_DEDUP, settings.isCookieSyncDedupEnabled),
            childLine(PerformancePreferences.DISABLE_AD_SDK_COMPONENTS, settings.isAdSdkComponentsDisabled),
            childLine(PerformancePreferences.DISABLE_VIDEO_COMPONENTS, settings.isVideoComponentsDisabled),
        )
    }
}
