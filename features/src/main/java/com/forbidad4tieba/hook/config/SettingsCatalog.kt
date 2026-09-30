package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

/** Aggregates domain declarations. No reflection, preference IO or callback use. */
internal object SettingsCatalog {
    val preferences: List<Preference<*>> = listOf(
        RemoteEnvironmentState.preferences,
        BottomTabLiquidGlassPreferences.preferences,
        AdPreferences.preferences,
        TabPreferences.preferences,
        ExtensionPreferences.preferences,
        HomeGlassPreferences.preferences,
        FreeCopyPreferences.preferences,
        ReplyPreferences.preferences,
        AccountPreferences.preferences,
        PerformancePreferences.preferences,
        PostFilterPreferences.preferences,
    ).flatten() + SimpleToggle.entries.map { it.preference }
    private val byKey = preferences.associateBy { it.key }

    fun capabilityFor(key: String): String? {
        val setting = byKey[key]
        if (setting != null) return setting.capabilityKey
        // Callers can also ask about host capabilities that have no persisted preference.
        return key.takeIf { it in HookFeatureKey.orderedKeys }
    }
}
