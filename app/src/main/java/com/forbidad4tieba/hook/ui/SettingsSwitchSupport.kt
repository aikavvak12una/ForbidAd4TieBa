package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus

internal data class SettingsSwitchSupport(
    val supported: Boolean,
    val partial: Boolean,
    val note: String?,
    val unknown: Boolean = false,
)

internal object SettingsSwitchSupportResolver {
    fun resolve(
        prefKey: String,
        supported: Boolean,
        featureStatusMap: Map<String, HookFeatureStatus>,
    ): SettingsSwitchSupport {
        if (!supported) {
            return SettingsSwitchSupport(
                supported = false,
                partial = false,
                note = null,
            )
        }
        val featureKey = ConfigManager.scanFeatureKeyForPrefKeyOrNull(prefKey)
            ?: return SettingsSwitchSupport(
                supported = true,
                partial = false,
                note = null,
            )
        val availabilityState = ConfigManager.getScanFeatureAvailabilityState(prefKey)
        val status = featureStatusMap[featureKey]
            ?: return SettingsSwitchSupport(
                supported = availabilityState == ConfigManager.ScanFeatureAvailabilityState.AVAILABLE ||
                    availabilityState == ConfigManager.ScanFeatureAvailabilityState.PARTIAL,
                partial = availabilityState == ConfigManager.ScanFeatureAvailabilityState.PARTIAL,
                note = when (availabilityState) {
                    ConfigManager.ScanFeatureAvailabilityState.UNKNOWN -> UiText.Settings.SCAN_UNKNOWN_NOTE
                    ConfigManager.ScanFeatureAvailabilityState.DISABLED -> UiText.Settings.SCAN_DISABLED_PREFIX + "-"
                    ConfigManager.ScanFeatureAvailabilityState.PARTIAL -> UiText.Settings.SCAN_PARTIAL_PREFIX + "-"
                    ConfigManager.ScanFeatureAvailabilityState.AVAILABLE -> null
                },
                unknown = availabilityState == ConfigManager.ScanFeatureAvailabilityState.UNKNOWN,
            )
        return when (status.state) {
            HookFeatureState.DISABLED -> {
                val critical = if (status.missingCritical.isEmpty()) "-" else status.missingCritical.joinToString(", ")
                SettingsSwitchSupport(
                    supported = false,
                    partial = false,
                    note = UiText.Settings.SCAN_DISABLED_PREFIX + critical,
                )
            }
            HookFeatureState.PARTIAL -> {
                val miss = status.missingCritical + status.missingOptional
                val detail = if (miss.isEmpty()) "-" else miss.joinToString(", ")
                SettingsSwitchSupport(
                    supported = true,
                    partial = true,
                    note = UiText.Settings.SCAN_PARTIAL_PREFIX + detail,
                )
            }
            else -> SettingsSwitchSupport(
                supported = true,
                partial = false,
                note = null,
            )
        }
    }

    fun mergeDescription(base: String?, note: String?): String? {
        if (base.isNullOrBlank()) return note
        if (note.isNullOrBlank()) return base
        return "$base\n$note"
    }
}
