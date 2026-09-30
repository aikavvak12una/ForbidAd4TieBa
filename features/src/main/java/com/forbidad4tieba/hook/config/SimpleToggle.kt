package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.ui.UiText

/** Independent extension switches. Complex parent/child and restricted policies stay with their owners. */
enum class SimpleToggle(
    val prefKey: String,
    val capabilityKey: String,
    val defaultValue: Boolean,
    val label: String,
    val description: String,
) {
    DEFAULT_ORIGINAL_IMAGE(
        "enable_default_original_image", HookFeatureKey.DEFAULT_ORIGINAL_IMAGE, false,
        UiText.Settings.DEFAULT_ORIGINAL_IMAGE_LABEL, UiText.Settings.DEFAULT_ORIGINAL_IMAGE_DESC,
    ),
    DEFAULT_LZL_EARLIEST(
        "default_lzl_earliest", HookFeatureKey.DEFAULT_LZL_EARLIEST, false,
        UiText.Settings.DEFAULT_LZL_EARLIEST_LABEL, UiText.Settings.DEFAULT_LZL_EARLIEST_DESC,
    );

    internal val preference = BooleanPreference(
        prefKey, defaultValue, PreferenceUse.SWITCH, capabilityKey,
        SwitchPresentation(label, description),
    )

    companion object {
        internal fun forPrefKey(key: String): SimpleToggle? = entries.find { it.prefKey == key }

        internal fun enabledBy(user: UserSettings, host: HostCapabilities): Set<SimpleToggle> =
            entries.filterTo(linkedSetOf()) {
                it.preference.read(user) && host.isAvailable(it.capabilityKey)
            }
    }
}
