package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class BottomTabLiquidGlassConfigTest {
    @Test fun defaultSlidersAreCentredForBothBars() {
        val config = BottomTabLiquidGlassPreferences.read(preferences(mutableMapOf()))
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT, config)
        assertEquals(listOf(0, 0, 0, 0), listOf(config.homeWidthAdjustment, config.replyWidthAdjustment,
            config.heightAdjustment, config.positionAdjustment))
    }

    @Test fun onlyNewFieldsControlGeometryAndMissingFieldsNeverRestoreLegacyValues() {
        val oldValues = mutableMapOf<String, Any>(
            "bottom_tab_liquid_glass_bottom_gap_dp" to 120,
            "bottom_tab_liquid_glass_droplet_width_percent" to 200,
            "bottom_tab_liquid_glass_height_dp" to 999,
            "bottom_tab_liquid_glass_width_percent" to 5,
        )
        val prefs = preferences(oldValues)
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT, BottomTabLiquidGlassPreferences.read(prefs))
        oldValues[BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT] = -40
        oldValues[BottomTabLiquidGlassPreferences.HEIGHT_ADJUSTMENT] = 25
        oldValues[BottomTabLiquidGlassPreferences.POSITION_ADJUSTMENT] = -100
        assertEquals(BottomTabLiquidGlassConfig(homeWidthAdjustment = -40, replyWidthAdjustment = -40,
            heightAdjustment = 25, positionAdjustment = -100),
            BottomTabLiquidGlassPreferences.read(prefs))
        prefs.edit().clear().commit()
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT, BottomTabLiquidGlassPreferences.read(prefs))
        oldValues["bottom_tab_liquid_glass_bottom_gap_dp"] = 80
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT, BottomTabLiquidGlassPreferences.read(prefs))
    }

    @Test fun allAdjustmentsAndMaterialsRoundTripWithoutChangingEnablement() {
        val values = mutableMapOf<String, Any>("unrelated" to true, BottomTabLiquidGlassPreferences.ENABLED to false)
        val prefs = preferences(values)
        for (width in listOf(-100, 0, 100)) for (replyWidth in listOf(-100, 0, 100)) for (height in listOf(-100, 0, 100)) {
            for (position in listOf(-100, 0, 100)) for (blur in listOf(false, true)) for (glass in listOf(false, true)) {
                for (amount in listOf(0, 25, 100)) {
                    val choice = BottomTabLiquidGlassConfig(width, height, position, blur, glass, amount, replyWidth)
                    BottomTabLiquidGlassPreferences.save(prefs, choice)
                    assertEquals(choice, BottomTabLiquidGlassPreferences.read(prefs))
                    assertFalse(BottomTabLiquidGlassPreferences.isEnabled(prefs.readOnlyValues()))
                    assertEquals(true, values["unrelated"])
                }
            }
        }
        BottomTabLiquidGlassPreferences.save(prefs, BottomTabLiquidGlassConfig.DEFAULT)
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT, BottomTabLiquidGlassPreferences.read(prefs))
    }

    @Test fun adjustmentBoundsApplyOnReadAndSave() {
        val values = mutableMapOf<String, Any>(
            BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT to Int.MIN_VALUE,
            BottomTabLiquidGlassPreferences.REPLY_WIDTH_ADJUSTMENT to Int.MAX_VALUE,
            BottomTabLiquidGlassPreferences.HEIGHT_ADJUSTMENT to Int.MAX_VALUE,
            BottomTabLiquidGlassPreferences.POSITION_ADJUSTMENT to Int.MIN_VALUE,
            BottomTabLiquidGlassPreferences.BLUR_PERCENT to Int.MAX_VALUE,
        )
        val prefs = preferences(values)
        assertEquals(BottomTabLiquidGlassConfig(-100, 100, -100, blurPercent = 100, replyWidthAdjustment = 100),
            BottomTabLiquidGlassPreferences.read(prefs))
        BottomTabLiquidGlassPreferences.save(prefs,
            BottomTabLiquidGlassConfig(Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, blurPercent = -1,
                replyWidthAdjustment = Int.MIN_VALUE))
        assertEquals(BottomTabLiquidGlassConfig(100, -100, 100, blurPercent = 0, replyWidthAdjustment = -100),
            BottomTabLiquidGlassPreferences.read(prefs))
    }

    @Test fun saveWritesOnlyCurrentSettings() {
        val values = mutableMapOf<String, Any>()
        BottomTabLiquidGlassPreferences.save(preferences(values), BottomTabLiquidGlassConfig.DEFAULT)
        assertEquals(setOf(
            BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT,
            BottomTabLiquidGlassPreferences.REPLY_WIDTH_ADJUSTMENT,
            BottomTabLiquidGlassPreferences.HEIGHT_ADJUSTMENT,
            BottomTabLiquidGlassPreferences.POSITION_ADJUSTMENT,
            BottomTabLiquidGlassPreferences.BLUR,
            BottomTabLiquidGlassPreferences.LIQUID_GLASS,
            BottomTabLiquidGlassPreferences.BLUR_PERCENT,
        ), values.keys)
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

    @Test fun sharedWidthMigratesOnceIncludingAlreadyInitializedOrDisabledBars() {
        for (enabled in listOf(false, true)) for (width in listOf(-100, 43, 100, Int.MAX_VALUE)) {
            val values = mutableMapOf<String, Any>(BottomTabLiquidGlassPreferences.ENABLED to enabled,
                BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT to width, "unrelated" to 7)
            val prefs = preferences(values)
            val oldStyle = BottomTabLiquidGlassPreferences.read(prefs)
            BottomTabLiquidGlassPreferences.ensureInitialized(prefs)
            assertEquals(oldStyle, BottomTabLiquidGlassPreferences.read(prefs))
            assertEquals(width.coerceIn(-100, 100), values[BottomTabLiquidGlassPreferences.REPLY_WIDTH_ADJUSTMENT])
            values[BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT] = -37
            BottomTabLiquidGlassPreferences.ensureInitialized(prefs)
            assertEquals(width.coerceIn(-100, 100), BottomTabLiquidGlassPreferences.read(prefs).replyWidthAdjustment)
            assertEquals(enabled, values[BottomTabLiquidGlassPreferences.ENABLED])
            assertEquals(7, values["unrelated"])
        }
    }

    @Test fun firstHomeEditPreservesOldReplyWidthAndLaterEditsAreIndependent() {
        val values = mutableMapOf<String, Any>(BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT to -70)
        val prefs = preferences(values)
        val old = BottomTabLiquidGlassPreferences.read(prefs)
        BottomTabLiquidGlassPreferences.save(prefs, old.copy(homeWidthAdjustment = 60))
        val changedHome = BottomTabLiquidGlassPreferences.read(prefs)
        assertEquals(60, changedHome.homeWidthAdjustment)
        assertEquals(-70, changedHome.replyWidthAdjustment)
        BottomTabLiquidGlassPreferences.save(prefs, changedHome.copy(replyWidthAdjustment = 0))
        BottomTabLiquidGlassPreferences.ensureInitialized(prefs)
        val changedReply = BottomTabLiquidGlassPreferences.read(prefs)
        assertEquals(60, changedReply.homeWidthAdjustment)
        assertEquals(0, changedReply.replyWidthAdjustment)
        BottomTabLiquidGlassPreferences.save(prefs, BottomTabLiquidGlassConfig.DEFAULT)
        assertEquals(BottomTabLiquidGlassConfig.DEFAULT, BottomTabLiquidGlassPreferences.read(prefs))
    }

    private fun preferences(values: MutableMap<String, Any>): SharedPreferences {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putInt", "putBoolean" -> { values[args!![0] as String] = args[1]; proxy }
                "clear" -> { values.clear(); proxy }
                "commit" -> true
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
