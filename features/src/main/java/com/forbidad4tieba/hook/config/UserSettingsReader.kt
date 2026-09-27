package com.forbidad4tieba.hook.config

import android.content.SharedPreferences

/** Storage migrations happen at the existing refresh boundary, before pure derivation. */
internal object UserSettingsReader {
    fun read(prefs: SharedPreferences): UserSettings {
        val light = ConfigManager.readHomeNativeGlassStyle(prefs, ConfigManager.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS)
        val dark = ConfigManager.readHomeNativeGlassStyle(prefs, ConfigManager.HOME_NATIVE_GLASS_DARK_STYLE_KEYS)
        return UserSettings(prefs.all, light, dark)
    }
}

internal fun SharedPreferences.readOnlyValues(): SettingsValues = object : SettingsValues {
    override fun contains(key: String): Boolean = this@readOnlyValues.contains(key)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = this@readOnlyValues.getBoolean(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int): Int = this@readOnlyValues.getInt(key, defaultValue)
    override fun getString(key: String, defaultValue: String?): String? = this@readOnlyValues.getString(key, defaultValue)
}
