package com.forbidad4tieba.hook.config

import android.content.SharedPreferences

internal object TabCustomizationPreferences {
    fun isEnabled(prefs: SharedPreferences): Boolean = isEnabled(prefs.readOnlyValues())

    fun isEnabled(prefs: SettingsValues): Boolean {
        if (prefs.contains(ConfigManager.KEY_ENABLE_TAB_CUSTOMIZATION)) {
            return prefs.getBoolean(ConfigManager.KEY_ENABLE_TAB_CUSTOMIZATION, false)
        }
        return prefs.getBoolean(ConfigManager.KEY_CUSTOM_HOME_TOP_TABS, false) ||
            prefs.getBoolean(ConfigManager.KEY_CUSTOM_BOTTOM_TABS, false) ||
            prefs.getBoolean(ConfigManager.KEY_AUTO_HIDE_HOME_TAB, false) ||
            prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_LIQUID_GLASS, false)
    }

    fun ensureInitialized(prefs: SharedPreferences) {
        if (prefs.contains(ConfigManager.KEY_ENABLE_TAB_CUSTOMIZATION)) return
        // Preserve the old switches on upgrade, then keep the master independent of its children.
        prefs.edit()
            .putBoolean(ConfigManager.KEY_ENABLE_TAB_CUSTOMIZATION, isEnabled(prefs))
            .apply()
    }
}
