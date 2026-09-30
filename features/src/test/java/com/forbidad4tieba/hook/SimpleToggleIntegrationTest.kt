package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsCatalog
import com.forbidad4tieba.hook.config.ExtensionPreferences
import com.forbidad4tieba.hook.config.*
import com.forbidad4tieba.hook.config.ConfigManager.ScanFeatureAvailabilityState
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.ui.SettingsMenuGroupActions
import com.forbidad4tieba.hook.ui.SettingsMenuGroupBuilder
import com.forbidad4tieba.hook.ui.SettingsSwitchSupportResolver
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SimpleToggleIntegrationTest {
    @Test fun everyToggleHasOneMenuEntryOneInstallerAndAKnownCapability() {
        val declared = SimpleToggle.entries
        assertEquals(declared.size, declared.map { it.prefKey }.distinct().size)
        val bound = FeatureCatalog.definitions.mapNotNull { it.toggle }
        assertEquals(declared.toSet(), bound.toSet())
        assertEquals("Each independent toggle has one feature owner", declared.size, bound.size)
        for (unlocked in listOf(false, true)) {
            val menu = SettingsMenuGroupBuilder.build(unlocked, noActions).flatMap { it.items }
            declared.forEach { toggle ->
                val row = menu.single { it.prefKey == toggle.prefKey }
                assertEquals(toggle.label, row.label)
                assertEquals(toggle.description, row.description)
                assertEquals(toggle.defaultValue, row.defaultValue)
                assertTrue(row.supported)
                assertTrue(toggle.capabilityKey in HookFeatureKey.orderedKeys)
                assertEquals(toggle.capabilityKey, SettingsCatalog.capabilityFor(row.prefKey))
            }
        }
    }

    @Test fun savedKeysDefaultsAndMenuOrderRemainCompatible() {
        assertEquals(listOf("enable_default_original_image", "default_lzl_earliest"),
            SimpleToggle.entries.map { it.prefKey })
        assertTrue(SimpleToggle.entries.none { it.defaultValue })
        val keys = SettingsMenuGroupBuilder.build(false, noActions).flatMap { it.items }.map { it.prefKey }
        val start = keys.indexOf(ExtensionPreferences.KEY_DISABLE_PB_GESTURE_FONT_SCALE)
        assertEquals(listOf(ExtensionPreferences.KEY_DISABLE_PB_GESTURE_FONT_SCALE,
            "enable_default_original_image", "default_lzl_earliest", ExtensionPreferences.KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER),
            keys.subList(start, start + 4))
    }

    @Test fun everySavedChoiceAndCapabilityReachesTheSnapshotWithoutAffectingOtherToggles() {
        for (toggle in SimpleToggle.entries) {
            for (state in ScanFeatureAvailabilityState.entries) {
                for (choice in listOf(null, false, true)) {
                    for (locked in listOf(false, true)) {
                        val values = SimpleToggle.entries.associate { it.prefKey to true }.toMutableMap()
                        if (choice == null) values.remove(toggle.prefKey) else values[toggle.prefKey] = choice
                        val user = UserSettings(values)
                        val states = SimpleToggle.entries.associate { it.capabilityKey to ScanFeatureAvailabilityState.AVAILABLE } +
                            (toggle.capabilityKey to state)
                        val snapshot = EffectiveSettingsPolicy.derive(user, HostCapabilities(states), RemoteSettingsPolicy(locked)).snapshot
                        val expected = (choice ?: toggle.defaultValue) &&
                            state in listOf(ScanFeatureAvailabilityState.AVAILABLE, ScanFeatureAvailabilityState.PARTIAL)
                        assertEquals("$toggle/$state/$choice/$locked", expected, snapshot[toggle])
                        SimpleToggle.entries.filter { it != toggle }.forEach { assertTrue(snapshot[it]) }
                        assertEquals(choice ?: toggle.defaultValue, user.getBoolean(toggle.prefKey, toggle.defaultValue))
                    }
                }
            }
        }
    }

    @Test fun derivedSettingsAndRealCapabilitiesControlEachRegisteredInstallationPlan() {
        for (toggle in SimpleToggle.entries) {
            val definition = FeatureCatalog.definitions.single { it.toggle == toggle }
            val symbols = symbolsFor(toggle)
            val context = HookInstallContext("com.baidu.tieba", symbols)
            assertTrue("Fixture must supply $toggle capability", context.available(toggle.capabilityKey))
            val user = UserSettings(mapOf(toggle.prefKey to true))
            val snapshot = EffectiveSettingsPolicy.derive(user,
                HostCapabilities(mapOf(toggle.capabilityKey to ScanFeatureAvailabilityState.AVAILABLE)),
                RemoteSettingsPolicy(true)).snapshot
            assertEquals(listOf(definition.id), definition.entries(context, snapshot).map { it.id })
            assertTrue(definition.entries(context, SettingsSnapshot.bootstrap()).isEmpty())
            assertTrue(definition.entries(HookInstallContext("com.baidu.tieba", HookSymbols.unsupported()), snapshot).isEmpty())
            assertTrue(definition.entries(HookInstallContext("com.android.systemui", symbols), snapshot).isEmpty())
            val status = HookSymbolResolver.featureStatusMap(symbols)
            assertTrue(SettingsSwitchSupportResolver.resolve(toggle.prefKey, true, status).supported)
            assertFalse(SettingsSwitchSupportResolver.resolve(toggle.prefKey, true,
                HookSymbolResolver.featureStatusMap(HookSymbols.unsupported())).supported)
        }
    }

    // Exhaustive fixtures require a new toggle to demonstrate its own real symbol dependency.
    private fun symbolsFor(toggle: SimpleToggle): HookSymbols {
        val keys = when (toggle) {
            SimpleToggle.DEFAULT_LZL_EARLIEST -> listOf("lzlDefaultSortSetter")
            SimpleToggle.DEFAULT_ORIGINAL_IMAGE -> listOf(
                "origImageUrlDragImageViewClass", "origImageDataClass", "origImageTriggerMethod",
                "origImageAssistDataMethod", "origImageShowButtonField", "origImageBlockedField",
                "origImageOriginalProcessField", "origImageOriginalUrlField",
            )
        }
        val json = JSONObject()
        keys.forEach { json.put(it, "fixture") }
        return checkNotNull(HookSymbols.fromJson(json.toString()))
    }

    private val noActions = SettingsMenuGroupActions(
        onAdBlock = {}, onCustomPostFilter = {}, onCustomPostModelScore = {}, onCustomPostFilterKeyword = {},
        onPbLikeAutoReply = {}, onFreeCopy = {}, onPerformanceOptimization = {}, onAutoSignIn = {},
        onReplyVisibilityProbe = {}, onDetailedLogSave = {}, onTabCustomization = {}, onHomeTopTab = {},
        onHomeNativeGlass = {}, onBottomTab = {}, onBottomTabLiquidGlass = {},
    )
}
