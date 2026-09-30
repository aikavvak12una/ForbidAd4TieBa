package com.forbidad4tieba.hook.config

import android.content.SharedPreferences

/** Explicitly distinguishes user controls from upgrade inputs and module state. */
internal enum class PreferenceUse { SWITCH, FORM, AUTOMATIC, LEGACY, MODULE_STATE }

internal data class SwitchPresentation(
    val label: String,
    val description: String,
)

/** Storage declarations own defaults; effective policy owns dependencies between choices. */
internal sealed class Preference<T>(
    val key: String,
    val defaultValue: T,
    val use: PreferenceUse,
    val capabilityKey: String?,
) {
    abstract fun read(values: SettingsValues): T
    fun read(prefs: SharedPreferences): T = read(prefs.readOnlyValues())
}

internal class BooleanPreference(
    key: String,
    defaultValue: Boolean,
    use: PreferenceUse,
    capabilityKey: String?,
    val presentation: SwitchPresentation? = null,
) : Preference<Boolean>(key, defaultValue, use, capabilityKey) {
    override fun read(values: SettingsValues): Boolean = values.getBoolean(key, defaultValue)
}

internal class IntPreference(
    key: String, defaultValue: Int, use: PreferenceUse,
) : Preference<Int>(key, defaultValue, use, null) {
    override fun read(values: SettingsValues): Int = values.getInt(key, defaultValue)
}

internal class StringPreference(
    key: String, defaultValue: String?, use: PreferenceUse,
) : Preference<String?>(key, defaultValue, use, null) {
    override fun read(values: SettingsValues): String? = values.getString(key, defaultValue)
}
