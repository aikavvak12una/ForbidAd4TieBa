package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.TabPreferences.FixedHomeTopTab
import com.forbidad4tieba.hook.config.TabPreferences.HomeTopTabCatalogEntry
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {
    @Test
    fun tabCustomizationMigrationRunsOnceAndNeverDerivesTheMasterFromNewGlassChildren() {
        for (key in listOf(
            TabPreferences.KEY_CUSTOM_HOME_TOP_TABS,
            TabPreferences.KEY_CUSTOM_BOTTOM_TABS,
            TabPreferences.KEY_AUTO_HIDE_HOME_TAB,
        )) {
            val values = mutableMapOf<String, Any>(key to true, "unrelated" to false)
            val store = preferences(values)
            TabCustomizationPreferences.ensureInitialized(store.prefs)
            assertEquals(true, values[TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION])
            val migrated = values.toMap()
            TabCustomizationPreferences.ensureInitialized(store.prefs)
            assertEquals(migrated, values)
            assertEquals(1, store.applies)
        }

        val values = mutableMapOf<String, Any>()
        val store = preferences(values)
        TabCustomizationPreferences.ensureInitialized(store.prefs)
        assertEquals(false, values[TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION])
        values[BottomTabLiquidGlassPreferences.POST_REPLY_BAR] = true
        TabCustomizationPreferences.ensureInitialized(store.prefs)
        assertFalse(TabCustomizationPreferences.isEnabled(store.prefs))
        assertEquals(1, store.applies)
    }

    @Test
    fun floatingBarMigrationPreservesEitherLegacyChoiceAndCopiesTheOldWidthOnce() {
        for (legacy in listOf(
            TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS,
            BottomTabLiquidGlassPreferences.POST_REPLY_BAR,
        )) {
            val values = mutableMapOf<String, Any>(legacy to true, "unrelated" to 42)
            val store = preferences(values)
            BottomTabLiquidGlassPreferences.ensureInitialized(store.prefs)
            assertEquals(true, values[BottomTabLiquidGlassPreferences.ENABLED])
            assertEquals(42, values["unrelated"])
            BottomTabLiquidGlassPreferences.ensureInitialized(store.prefs)
            assertEquals(1, store.applies)
        }

        val explicitlyDisabled = mutableMapOf<String, Any>(
            BottomTabLiquidGlassPreferences.ENABLED to false,
            TabPreferences.KEY_BOTTOM_TAB_LIQUID_GLASS to true,
            BottomTabLiquidGlassPreferences.POST_REPLY_BAR to true,
        )
        val disabledStore = preferences(explicitlyDisabled)
        BottomTabLiquidGlassPreferences.ensureInitialized(disabledStore.prefs)
        assertFalse(BottomTabLiquidGlassPreferences.isEnabled(disabledStore.prefs.readOnlyValues()))
        assertEquals(0, disabledStore.writes)

        val widths = mutableMapOf<String, Any>(
            BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT to -70,
            "unrelated" to 9,
        )
        val widthStore = preferences(widths)
        BottomTabLiquidGlassPreferences.ensureInitialized(widthStore.prefs)
        assertEquals(-70, widths[BottomTabLiquidGlassPreferences.REPLY_WIDTH_ADJUSTMENT])
        widths[BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT] = 60
        BottomTabLiquidGlassPreferences.ensureInitialized(widthStore.prefs)
        assertEquals(-70, BottomTabLiquidGlassPreferences.read(widthStore.prefs).replyWidthAdjustment)
        assertEquals(9, widths["unrelated"])
    }

    @Test
    fun homeGlassLegacyStyleSeedsLightOnlyAndTintOffsetMigratesExactlyOnce() {
        val styleValues = mutableMapOf<String, Any>(
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH to " legacy.jpg ",
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP to 30,
        )
        val styleStore = glassPreferences(styleValues)
        val light = HomeGlassPreferences.readHomeNativeGlassStyle(
            styleStore.prefs,
            HomeGlassPreferences.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS,
        )
        val dark = HomeGlassPreferences.readHomeNativeGlassStyle(
            styleStore.prefs,
            HomeGlassPreferences.HOME_NATIVE_GLASS_DARK_STYLE_KEYS,
        )
        assertEquals("legacy.jpg", light.backgroundImagePath)
        assertEquals(30, light.cardRadiusDp)
        assertEquals("", dark.backgroundImagePath)
        assertEquals(24, dark.cardRadiusDp)

        val tintValues = mutableMapOf<String, Any>(
            HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT to 90,
        )
        val tintStore = glassPreferences(tintValues)
        val migrated = HomeGlassPreferences.readHomeNativeGlassStyle(
            tintStore.prefs, HomeGlassPreferences.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS,
        ).tintAlphaPercent
        assertEquals(40, migrated)
        assertEquals(40, tintValues[HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT])
        assertEquals(true, tintValues[HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED])
        assertEquals(migrated, HomeGlassPreferences.readHomeNativeGlassStyle(
            tintStore.prefs, HomeGlassPreferences.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS,
        ).tintAlphaPercent)
        assertEquals(1, tintStore.applies)
    }

    @Test
    fun retiredPerformanceKeysAreRemovedInOneWriteWhileRetainedChoicesSurvive() {
        val retired = listOf(
            "force_pb_preload",
            "disable_ai_components",
            "disable_monitor_sync_components",
            "enable_pb_performance_mode",
            "enable_pb_scroll_coalesce",
            "disable_host_slide_animation",
            "force_host_feed_cold_opt",
            "disable_apsaras_schedule",
            "disable_flutter_preinit",
            "force_low_end_device_config",
            "block_titan_patch",
        )
        val retained = mutableMapOf<String, Any>(
            PerformancePreferences.KEY_COOKIE_SYNC_DEDUP to false,
            PerformancePreferences.KEY_DISABLE_AD_SDK_COMPONENTS to true,
            PerformancePreferences.KEY_DISABLE_VIDEO_COMPONENTS to false,
            PerformancePreferences.KEY_DISABLE_YY_BACKGROUND to true,
            PerformancePreferences.KEY_DISABLE_SWAN_PRELOAD to true,
            "unrelated" to 3,
        )
        val stale = mutableMapOf<String, Any>().apply {
            putAll(retained)
            retired.forEach { put(it, true) }
        }
        val store = TestPreferences(
            stale,
            editorMethods = setOf("remove", "apply"),
        )
        PerformancePreferences.removeRetiredSettings(store.prefs)
        PerformancePreferences.removeRetiredSettings(store.prefs)
        assertEquals(retained, store.values)
        assertEquals(1, store.edits)
        assertEquals(1, store.applies)
    }

    @Test
    fun fixedTabSaveIsOneAtomicChoiceAndDisablingItRetainsKnownSelections() {
        val choices = mutableMapOf<String, Any>(
            TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to true,
            TabPreferences.KEY_CUSTOM_HOME_TOP_TABS to true,
            TabPreferences.KEY_HOME_TOP_TAB_FIXED_ENABLED to true,
        )
        val store = preferences(choices, readString = true)
        val catalog = listOf(
            HomeTopTabCatalogEntry("type:1", "推荐"),
            HomeTopTabCatalogEntry("code:live", "直播"),
            HomeTopTabCatalogEntry("code:new", "新项"),
            TabPreferences.followedHomeTopTabCatalogEntry(),
        )
        val selected = listOf(
            FixedHomeTopTab(TabPreferences.HOME_TOP_TAB_KEY_RECOMMEND, "推荐"),
            FixedHomeTopTab(TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED, "关注"),
        )

        TabPreferences.saveHomeTopTabSelection(store.prefs, catalog, selected, true)
        assertEquals(1, store.edits)
        assertEquals(1, store.applies)
        assertEquals(
            setOf("code:live", "type:6", "code:new"),
            TabPreferences.readHomeTopTabDisabledKeys(store.prefs),
        )
        val available = HostCapabilities(mapOf(
            HookFeatureKey.SIMPLIFY_HOME_TOP_TABS to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            HookFeatureKey.FIXED_HOME_TOP_TABS to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
        ))
        assertTrue(EffectiveSettingsPolicy.derive(UserSettings(store.values), available,
            RemoteSettingsPolicy(false)).snapshot.isHomeTopTabFixedEnabled)

        TabPreferences.saveHomeTopTabSelection(store.prefs, catalog, selected, false)
        val state = EffectiveSettingsPolicy.derive(UserSettings(store.values), available,
            RemoteSettingsPolicy(false)).snapshot
        assertFalse(state.isHomeTopTabFixedEnabled)
        val runtime = TabPreferences.resolveHomeTopTabSelection(state)
        assertTrue(runtime.isTabKeyEnabled("type:1"))
        assertTrue(runtime.isTabKeyEnabled("code:future"))
        assertEquals(selected, TabPreferences.readFixedHomeTopTabs(store.prefs))
    }

    private fun preferences(
        values: MutableMap<String, Any>,
        readString: Boolean = false,
    ): TestPreferences<Any> = TestPreferences(
        values,
        readMethods = if (readString) setOf("getBoolean", "getInt", "getString")
        else setOf("getBoolean", "getInt"),
        editorMethods = setOf("putBoolean", "putInt", "putString", "apply"),
    )

    private fun glassPreferences(values: MutableMap<String, Any>): TestPreferences<Any> = TestPreferences(
        values,
        readMethods = setOf("getString", "getInt", "getBoolean"),
        editorMethods = setOf("putInt", "putBoolean", "apply"),
    )
}
