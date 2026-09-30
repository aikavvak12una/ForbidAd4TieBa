package com.forbidad4tieba.hook.config

import android.content.SharedPreferences

internal object TabCustomizationPreferences {
    fun isEnabled(prefs: SharedPreferences): Boolean = isEnabled(prefs.readOnlyValues())

    fun isEnabled(prefs: SettingsValues): Boolean {
        if (prefs.contains(TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION)) {
            return TabPreferences.ENABLE_TAB_CUSTOMIZATION.read(prefs)
        }
        return TabPreferences.CUSTOM_HOME_TOP_TABS.read(prefs) ||
            TabPreferences.CUSTOM_BOTTOM_TABS.read(prefs) ||
            TabPreferences.AUTO_HIDE_HOME_TAB.read(prefs) ||
            TabPreferences.BOTTOM_TAB_LIQUID_GLASS.read(prefs)
    }

    fun ensureInitialized(prefs: SharedPreferences) {
        if (prefs.contains(TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION)) return
        // Preserve the old switches on upgrade, then keep the master independent of its children.
        prefs.edit()
            .putBoolean(TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION, isEnabled(prefs))
            .apply()
    }
}
