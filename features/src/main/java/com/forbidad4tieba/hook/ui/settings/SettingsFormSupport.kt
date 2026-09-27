package com.forbidad4tieba.hook.ui.settings

import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.ui.SettingsSwitchSupport
import com.forbidad4tieba.hook.ui.SettingsSwitchSupportResolver
import com.forbidad4tieba.hook.ui.SwitchItem
import com.forbidad4tieba.hook.ui.UiText

internal object SettingsFormSupport {
    fun labelWithScanSupport(label: String, support: SettingsSwitchSupport): String {
        return when {
            support.unknown -> UiText.Settings.withScanUnknownSuffix(label)
            !support.supported -> UiText.Settings.withUnsupportedSuffix(label)
            support.partial -> UiText.Settings.withPartialSuffix(label)
            else -> label
        }
    }

    fun descriptionWithScanSupport(description: String?, support: SettingsSwitchSupport): String? {
        return SettingsSwitchSupportResolver.mergeDescription(description, support.note)
    }

    fun resolveSwitchSupport(
        item: SwitchItem,
        featureStatusMap: Map<String, HookFeatureStatus>,
    ): SettingsSwitchSupport {
        return SettingsSwitchSupportResolver.resolve(
            prefKey = item.prefKey,
            supported = item.supported,
            featureStatusMap = featureStatusMap,
        )
    }
}
