package com.forbidad4tieba.hook.config

/** Read-only values let codecs serve saved preferences and an immutable policy input. */
internal interface SettingsValues {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getInt(key: String, defaultValue: Int): Int
    fun getString(key: String, defaultValue: String?): String?
}

/** Saved choices are independent of capability and remote-policy decisions. */
internal class UserSettings(
    values: Map<String, *>,
    val lightGlassStyle: ConfigManager.HomeNativeGlassStyleConfig = ConfigManager.HomeNativeGlassStyleConfig(),
    val darkGlassStyle: ConfigManager.HomeNativeGlassStyleConfig = ConfigManager.HomeNativeGlassStyleConfig(),
) : SettingsValues {
    private val values = values.mapValues { (_, value) ->
        if (value is Set<*>) value.toSet() else value
    }

    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as Boolean? ?: defaultValue
    override fun getInt(key: String, defaultValue: Int): Int = values[key] as Int? ?: defaultValue
    override fun getString(key: String, defaultValue: String?): String? = values[key] as String? ?: defaultValue
}

internal data class RemoteSettingsPolicy(val restrictedFeaturesLocked: Boolean)

internal data class SettingsEvaluation(
    val snapshot: SettingsSnapshot,
    val normalizedBottomTabs: ConfigManager.BottomTabSelection? = null,
)
