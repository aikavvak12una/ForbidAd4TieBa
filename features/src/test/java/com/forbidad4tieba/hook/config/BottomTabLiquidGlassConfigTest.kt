package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomTabLiquidGlassConfigTest {
    @Test fun defaultsKeepTheExistingAppearance() {
        assertEquals(BottomTabLiquidGlassConfig(64, 0, -1, true, true, true, true, true, true),
            BottomTabLiquidGlassPreferences.read(preferences(mutableMapOf())))
    }

    @Test fun outOfRangeDimensionsAreBoundedWithoutChangingEffectChoices() {
        val low = BottomTabLiquidGlassConfig(0, 1, -20, pressEffectEnabled = false).normalized()
        assertEquals(BottomTabLiquidGlassConfig(48, 60, -1, pressEffectEnabled = false), low)
        val high = BottomTabLiquidGlassConfig(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE).normalized()
        assertEquals(BottomTabLiquidGlassConfig(120, 100, 120), high)
        assertEquals(0, BottomTabLiquidGlassConfig(widthPercent = 0).normalized().widthPercent)
    }

    @Test fun saveRoundTripsAllFieldsAndKeepsUnrelatedSettings() {
        val values = mutableMapOf<String, Any>("unrelated" to true)
        val prefs = preferences(values)
        val config = BottomTabLiquidGlassConfig(80, 72, 42, false, true, false, true, false, false)
        BottomTabLiquidGlassPreferences.save(prefs, config)
        assertEquals(config, BottomTabLiquidGlassPreferences.read(prefs))
        assertEquals(true, values["unrelated"])
        BottomTabLiquidGlassPreferences.save(prefs, BottomTabLiquidGlassConfig.DEFAULT)
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT, BottomTabLiquidGlassPreferences.read(prefs))
    }

    @Test fun persistedDimensionsAreValidatedAtTheSnapshotBoundary() {
        val prefs = preferences(mutableMapOf(
            BottomTabLiquidGlassPreferences.HEIGHT to -50,
            BottomTabLiquidGlassPreferences.WIDTH to 900,
            BottomTabLiquidGlassPreferences.BOTTOM_GAP to 500,
        ))
        assertEquals(BottomTabLiquidGlassConfig(48, 100, 120), BottomTabLiquidGlassPreferences.read(prefs))
    }

    private fun preferences(values: MutableMap<String, Any>): SharedPreferences {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putInt", "putBoolean" -> { values[args!![0] as String] = args[1]; proxy }
                "apply" -> null
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getInt", "getBoolean" -> values[args!![0]] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
