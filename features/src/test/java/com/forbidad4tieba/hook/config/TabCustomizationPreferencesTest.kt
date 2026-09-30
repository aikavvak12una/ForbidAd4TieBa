package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TabCustomizationPreferencesTest {
    @Test
    fun migrationPreservesEachOldSwitchAndOnlyAddsTheMaster() {
        val keys = listOf(
            TabPreferences.KEY_CUSTOM_HOME_TOP_TABS,
            TabPreferences.KEY_CUSTOM_BOTTOM_TABS,
            TabPreferences.KEY_AUTO_HIDE_HOME_TAB,
            TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS,
        )
        for (key in keys) {
            val values = mutableMapOf<String, Any>(key to true, "unrelated" to false)
            val before = values.toMap()
            TabCustomizationPreferences.ensureInitialized(preferences(values))
            assertEquals(before + (TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to true), values)
        }
    }

    @Test
    fun preconfiguringChildrenOnAFreshInstallDoesNotSilentlyEnableTheMaster() {
        val values = mutableMapOf<String, Any>()
        val prefs = preferences(values)
        TabCustomizationPreferences.ensureInitialized(prefs)
        assertEquals(mapOf(TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to false), values)
        values[TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS] = true
        TabCustomizationPreferences.ensureInitialized(prefs)
        assertFalse(TabCustomizationPreferences.isEnabled(prefs))
        assertEquals(true, values[TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS])
    }

    @Test
    fun repeatedInitializationPreservesExplicitMasterChoices() {
        for (enabled in listOf(false, true)) {
            val values = mutableMapOf<String, Any>(
                TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to enabled,
                TabPreferences.KEY_CUSTOM_HOME_TOP_TABS to !enabled,
                TabPreferences.KEY_CUSTOM_BOTTOM_TABS to !enabled,
            )
            val before = values.toMap()
            TabCustomizationPreferences.ensureInitialized(preferences(values))
            assertEquals(before, values)
            assertEquals(enabled, TabCustomizationPreferences.isEnabled(preferences(values)))
        }
    }

    private fun preferences(values: MutableMap<String, Any>): SharedPreferences {
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putBoolean" -> { values[args!![0] as String] = args[1]; proxy }
                "apply" -> null
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> values[args!![0]] ?: args[1]
                "contains" -> values.containsKey(args!![0])
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
