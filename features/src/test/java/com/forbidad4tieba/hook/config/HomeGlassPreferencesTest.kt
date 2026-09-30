package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class HomeGlassPreferencesTest {
    @Test fun legacyStyleOnlySeedsLightModeAndAnyModeValueStopsFallback() {
        val values = mutableMapOf<String, Any>(
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH to " legacy.jpg ",
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP to 30,
        )
        val prefs = preferences(values)
        val light = readLight(prefs)
        val dark = HomeGlassPreferences.readHomeNativeGlassStyle(prefs, HomeGlassPreferences.HOME_NATIVE_GLASS_DARK_STYLE_KEYS)
        assertEquals("legacy.jpg", light.backgroundImagePath)
        assertEquals(30, light.cardRadiusDp)
        assertEquals("", dark.backgroundImagePath)
        assertEquals(24, dark.cardRadiusDp)

        values[HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_LIGHT] = 7
        val selected = readLight(prefs)
        assertEquals("", selected.backgroundImagePath)
        assertEquals(7, selected.cardRadiusDp)
    }

    @Test fun legacyTintOffsetIsPersistedExactlyOnceAtTheExistingReadBoundary() {
        val values = mutableMapOf<String, Any>(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT to 90)
        var writes = 0
        val prefs = preferences(values) { writes++ }
        assertEquals(40, readLight(prefs).tintAlphaPercent)
        assertEquals(40, values[HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT])
        assertEquals(true, values[HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED])
        assertEquals(40, readLight(prefs).tintAlphaPercent)
        assertEquals(1, writes)
    }

    @Test fun typedModeValuesKeepBoundsAndShadowMigrationPrecedence() {
        val values = mutableMapOf<String, Any>(
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_LIGHT to 500,
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_LIGHT to -10,
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_LIGHT to -500,
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED_LIGHT to false,
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_STROKE_ENABLED_LIGHT to false,
        )
        val prefs = preferences(values)
        val style = readLight(prefs)
        assertEquals(32, style.cardRadiusDp)
        assertEquals(10, style.cardBlurPercent)
        assertEquals(-100, style.tintAlphaPercent)
        assertEquals(0, style.shadowStrengthPercent)
        assertFalse(style.strokeEnabled)
        values[HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_LIGHT] = 65
        assertEquals(65, readLight(prefs).shadowStrengthPercent)
    }

    @Test fun bothModeBundlesAreCompleteAndUseRegisteredDeclarations() {
        val modes = listOf(HomeGlassPreferences.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS, HomeGlassPreferences.HOME_NATIVE_GLASS_DARK_STYLE_KEYS)
        assertEquals(22, modes.flatMap { it.all().toList() }.distinct().size)
        modes.forEach { mode ->
            mode.javaClass.declaredFields.filter { Preference::class.java.isAssignableFrom(it.type) }.forEach { field ->
                field.isAccessible = true
                assertTrue(field.get(mode) in SettingsCatalog.preferences)
            }
        }
    }

    private fun readLight(prefs: SharedPreferences) =
        HomeGlassPreferences.readHomeNativeGlassStyle(prefs, HomeGlassPreferences.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS)

    private fun preferences(values: MutableMap<String, Any>, applied: () -> Unit = {}): SharedPreferences {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putInt", "putBoolean" -> { values[args!![0] as String] = args[1]; proxy }
                "apply" -> { applied(); null }
                else -> error("Unexpected editor method: ${method.name}")
            }
        }
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "contains" -> values.containsKey(args!![0] as String)
                "getString", "getInt", "getBoolean" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preferences method: ${method.name}")
            }
        } as SharedPreferences
    }
}
