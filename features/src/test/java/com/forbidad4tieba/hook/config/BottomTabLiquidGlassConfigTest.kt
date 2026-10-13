package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomTabLiquidGlassConfigTest {
    @Test fun defaultsShareOnlyMaterialAndBottomClearance() {
        assertEquals(BottomTabLiquidGlassConfig(-1, true, true),
            BottomTabLiquidGlassPreferences.read(preferences(mutableMapOf())))
    }

    @Test fun outOfRangeGapIsBoundedWithoutChangingEffectChoices() {
        val low = BottomTabLiquidGlassConfig(-20, liquidGlassEnabled = false).normalized()
        assertEquals(BottomTabLiquidGlassConfig(-1, liquidGlassEnabled = false), low)
        val high = BottomTabLiquidGlassConfig(Int.MAX_VALUE).normalized()
        assertEquals(BottomTabLiquidGlassConfig(120), high)
    }

    @Test fun styleDraftRoundTripsWithoutOverwritingTheSharedEnableSwitch() {
        val values = mutableMapOf<String, Any>("unrelated" to true,
            BottomTabLiquidGlassPreferences.ENABLED to false)
        val prefs = preferences(values)
        val config = BottomTabLiquidGlassConfig(42, false, false)
        BottomTabLiquidGlassPreferences.save(prefs, config)
        assertEquals(config, BottomTabLiquidGlassPreferences.read(prefs))
        assertEquals(true, values["unrelated"])
        BottomTabLiquidGlassPreferences.save(prefs, BottomTabLiquidGlassConfig.DEFAULT)
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT,
            BottomTabLiquidGlassPreferences.read(prefs))
        assertEquals(false, values[BottomTabLiquidGlassPreferences.ENABLED])
    }

    @Test fun allStyleChoicesRoundTripWithoutChangingEnablement() {
        val prefs = preferences(mutableMapOf())
        for (blur in listOf(false, true)) for (glass in listOf(false, true)) {
            for (width in listOf(100, 115, 200)) for (amount in listOf(0, 25, 100)) {
                val choice = BottomTabLiquidGlassConfig(24, blur, glass, width, amount)
                BottomTabLiquidGlassPreferences.save(prefs, choice)
                assertEquals(choice, BottomTabLiquidGlassPreferences.read(prefs))
                assertEquals(false, BottomTabLiquidGlassPreferences.isEnabled(prefs.readOnlyValues()))
            }
        }
    }

    @Test fun sliderValuesAreBoundedOnReadAndSave() {
        val values = mutableMapOf<String, Any>(
            BottomTabLiquidGlassPreferences.DROPLET_WIDTH to Int.MIN_VALUE,
            BottomTabLiquidGlassPreferences.BLUR_PERCENT to Int.MAX_VALUE,
        )
        val prefs = preferences(values)
        assertEquals(BottomTabLiquidGlassConfig(dropletWidthPercent = 100, blurPercent = 100),
            BottomTabLiquidGlassPreferences.read(prefs))
        BottomTabLiquidGlassPreferences.save(prefs,
            BottomTabLiquidGlassConfig(dropletWidthPercent = Int.MAX_VALUE, blurPercent = -1))
        assertEquals(200, values[BottomTabLiquidGlassPreferences.DROPLET_WIDTH])
        assertEquals(0, values[BottomTabLiquidGlassPreferences.BLUR_PERCENT])
    }

    @Test fun migrationOptsInOnlyWhenEitherOldBarWasEffective() {
        for (tab in listOf(false, true)) for (reply in listOf(false, true)) {
            for (parent in listOf(null, false, true)) for (floating in listOf(null, false, true)) {
                val values = mutableMapOf<String, Any>(
                    TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS to tab,
                    BottomTabLiquidGlassPreferences.POST_REPLY_BAR to reply,
                    "unrelated" to 42,
                )
                parent?.let { values[TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION] = it }
                floating?.let { values[BottomTabLiquidGlassPreferences.FLOATING] = it }
                val before = values.toMap()
                val expected = (tab || reply) && parent != false && floating != false
                val prefs = preferences(values)
                BottomTabLiquidGlassPreferences.ensureInitialized(prefs)
                assertEquals(before + (BottomTabLiquidGlassPreferences.ENABLED to expected), values)
                TabCustomizationPreferences.ensureInitialized(prefs)
                assertEquals(parent ?: false, TabCustomizationPreferences.isEnabled(prefs))
                assertEquals(expected, BottomTabLiquidGlassPreferences.isEnabled(prefs.readOnlyValues()))
            }
        }
    }

    @Test fun initializedChoiceIsIndependentOfEveryLegacySwitch() {
        for (enabled in listOf(false, true)) {
            val values = mutableMapOf<String, Any>(
                BottomTabLiquidGlassPreferences.ENABLED to enabled,
                TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to !enabled,
                TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS to !enabled,
                BottomTabLiquidGlassPreferences.POST_REPLY_BAR to !enabled,
                BottomTabLiquidGlassPreferences.FLOATING to !enabled,
            )
            val before = values.toMap()
            val prefs = preferences(values)
            BottomTabLiquidGlassPreferences.ensureInitialized(prefs)
            assertEquals(before, values)
            assertEquals(enabled, BottomTabLiquidGlassPreferences.isEnabled(prefs.readOnlyValues()))
        }
    }

    @Test fun retiredOptionsCannotOverrideCurrentMaterialAndLayout() {
        val prefs = preferences(mutableMapOf(
            "bottom_tab_liquid_glass_height_dp" to -50,
            "bottom_tab_liquid_glass_width_percent" to 900,
            "bottom_tab_liquid_glass_shadow" to true,
            "bottom_tab_liquid_glass_refraction" to false,
            "bottom_tab_liquid_glass_highlight" to false,
            "bottom_tab_liquid_glass_tilt" to false,
            "bottom_tab_liquid_glass_press_effect" to false,
            BottomTabLiquidGlassPreferences.BOTTOM_GAP to 500,
        ))
        assertEquals(BottomTabLiquidGlassConfig(120), BottomTabLiquidGlassPreferences.read(prefs))
        for (height in listOf(48, 64, 120)) for (width in listOf(0, 60, 100)) {
            assertEquals(BottomTabLiquidGlassConfig.DEFAULT, BottomTabLiquidGlassPreferences.read(preferences(mutableMapOf(
                "bottom_tab_liquid_glass_height_dp" to height, "bottom_tab_liquid_glass_width_percent" to width))))
        }
        val saved = mutableMapOf<String, Any>()
        BottomTabLiquidGlassPreferences.save(preferences(saved), BottomTabLiquidGlassConfig.DEFAULT)
        org.junit.Assert.assertFalse(saved.containsKey("bottom_tab_liquid_glass_height_dp"))
        org.junit.Assert.assertFalse(saved.containsKey("bottom_tab_liquid_glass_width_percent"))
        org.junit.Assert.assertFalse(saved.containsKey("bottom_tab_liquid_glass_shadow"))
        assertEquals(setOf(BottomTabLiquidGlassPreferences.BOTTOM_GAP, BottomTabLiquidGlassPreferences.BLUR,
            BottomTabLiquidGlassPreferences.LIQUID_GLASS, BottomTabLiquidGlassPreferences.DROPLET_WIDTH,
            BottomTabLiquidGlassPreferences.BLUR_PERCENT), saved.keys)
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
                "contains" -> values.containsKey(args!![0])
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
