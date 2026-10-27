package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PerformancePreferencesTest {
    @Test fun upgradeRemovesOnlyTheRetiredChoiceAndDoesNotWriteAgain() {
        for (oldValue in listOf(true, false, "obsolete")) {
            val values = mutableMapOf<String, Any>("force_pb_preload" to oldValue,
                PerformancePreferences.KEY_ENABLE_PB_PERFORMANCE_MODE to true, "unrelated" to 3)
            val expected = values - "force_pb_preload"
            var writes = 0
            val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
                when (method.name) {
                    "remove" -> { values.remove(args!![0]); proxy }
                    "apply" -> { writes++; null }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
            val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java)) { _, method, args ->
                when (method.name) {
                    "contains" -> values.containsKey(args!![0])
                    "edit" -> editor
                    else -> error("Unexpected preferences call: ${method.name}")
                }
            } as SharedPreferences
            repeat(2) { PerformancePreferences.removeRetiredSettings(prefs) }
            assertEquals(expected, values)
            assertEquals(1, writes)
        }
    }

    @Test fun retiredValuesCannotReenableASettingOrChangePublishedPolicy() {
        assertFalse(SettingsCatalog.preferences.any { it.key == "force_pb_preload" })
        assertFalse(SettingsSnapshot::class.java.declaredFields.any { it.name == "isPbPreloadForced" })
        val values = mapOf<String, Any>("enable_performance_optimization" to true, "restricted_features_unlocked" to true)
        for (state in ConfigManager.ScanFeatureAvailabilityState.entries) for (locked in listOf(false, true)) {
            val host = HostCapabilities(HookFeatureKey.orderedKeys.associateWith { state })
            val remote = RemoteSettingsPolicy(locked)
            val expected = EffectiveSettingsPolicy.derive(UserSettings(values), host, remote)
            for (oldValue in listOf(true, false, "obsolete")) {
                assertEquals(expected, EffectiveSettingsPolicy.derive(UserSettings(values + ("force_pb_preload" to oldValue)), host, remote))
            }
        }
    }
}
