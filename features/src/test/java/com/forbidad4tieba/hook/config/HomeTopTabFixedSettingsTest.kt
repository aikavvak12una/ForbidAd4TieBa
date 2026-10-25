package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import com.forbidad4tieba.hook.config.TabPreferences.FixedHomeTopTab
import com.forbidad4tieba.hook.config.TabPreferences.HomeTopTabCatalogEntry
import com.forbidad4tieba.hook.config.TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED
import com.forbidad4tieba.hook.config.TabPreferences.HOME_TOP_TAB_KEY_RECOMMEND
import com.forbidad4tieba.hook.config.TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION
import com.forbidad4tieba.hook.config.TabPreferences.KEY_CUSTOM_HOME_TOP_TABS
import com.forbidad4tieba.hook.config.TabPreferences.KEY_HOME_TOP_TAB_FIXED_ENABLED
import com.forbidad4tieba.hook.config.TabPreferences.KEY_HOME_TOP_TAB_FIXED_SELECTION_JSON
import com.forbidad4tieba.hook.config.TabPreferences.KEY_HOME_TOP_TAB_RECOMMEND
import com.forbidad4tieba.hook.config.TabPreferences.KEY_HOME_TOP_TAB_LIVE
import com.forbidad4tieba.hook.config.TabPreferences.KEY_HOME_TOP_TAB_FOLLOWED
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class HomeTopTabFixedSettingsTest {
    private val choices = mapOf<String, Any>(
        KEY_ENABLE_TAB_CUSTOMIZATION to true,
        KEY_CUSTOM_HOME_TOP_TABS to true,
        KEY_HOME_TOP_TAB_FIXED_ENABLED to true,
        KEY_HOME_TOP_TAB_FIXED_SELECTION_JSON to "[{\"key\":\"code:recommend\",\"label\":\"推荐\"}]",
    )
    private val available = HostCapabilities(mapOf(
        HookFeatureKey.SIMPLIFY_HOME_TOP_TABS to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
        HookFeatureKey.FIXED_HOME_TOP_TABS to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
    ))

    @Test fun persistedSelectionIsAvailableBeforeAnyCatalogOrSettingsPageRead() {
        val snapshot = derive(choices)
        val selection = TabPreferences.resolveHomeTopTabSelection(snapshot)
        assertTrue(snapshot.isHomeTopTabFixedEnabled)
        assertEquals(setOf(HOME_TOP_TAB_KEY_RECOMMEND), snapshot.homeTopTabFixedKeys)
        assertTrue(selection.isTabKeyEnabled("type:1"))
        assertFalse(selection.isTabKeyEnabled("code:server-added"))
        assertFalse(selection.isTabKeyEnabled(null))
        assertFalse(selection.isTabKeyEnabled(""))
        assertFalse(snapshot.isHomeTopTabFollowedEnabled)
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.homeTopTabFixedKeys as MutableSet<String>).add("code:injected")
        }
    }

    @Test fun eitherParentOrTheFixedCapabilityDisablesOnlyEffectiveFixedSettings() {
        for (key in listOf(KEY_ENABLE_TAB_CUSTOMIZATION, KEY_CUSTOM_HOME_TOP_TABS)) {
            val snapshot = derive(choices + (key to false))
            assertFalse(snapshot.isHomeTopTabFixedEnabled)
            assertTrue(snapshot.homeTopTabFixedKeys.isEmpty())
            assertTrue(derive(choices).isHomeTopTabFixedEnabled)
        }
        val ordinaryOnly = HostCapabilities(mapOf(
            HookFeatureKey.SIMPLIFY_HOME_TOP_TABS to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
        ))
        val snapshot = derive(choices, ordinaryOnly)
        assertTrue(snapshot.isHomeTopTabsCustomEnabled)
        assertFalse(snapshot.isHomeTopTabFixedEnabled)
        assertTrue(snapshot.isHomeTopTabFollowedEnabled)
        assertTrue(TabPreferences.resolveHomeTopTabSelection(snapshot).isTabKeyEnabled("code:new"))
    }

    @Test fun aliasesRoundTripAndLabelsNeverParticipateInMatching() {
        val saved = listOf(
            FixedHomeTopTab(" TYPE:1 ", "旧标题"), FixedHomeTopTab("code:recommend", "另一个标题"),
            FixedHomeTopTab("code:myfollowed", "ignored"), FixedHomeTopTab("code:custom", "推荐"),
        )
        val decoded = TabPreferences.parseFixedHomeTopTabs(TabPreferences.serializeFixedHomeTopTabs(saved))
        assertEquals(listOf(HOME_TOP_TAB_KEY_RECOMMEND, HOME_TOP_TAB_KEY_FOLLOWED, "code:custom"), decoded.map { it.key })
        assertEquals("旧标题", decoded.first().label)
        val snapshot = derive(choices + (KEY_HOME_TOP_TAB_FIXED_SELECTION_JSON to TabPreferences.serializeFixedHomeTopTabs(decoded)))
        assertTrue(snapshot.isHomeTopTabFollowedEnabled)
        assertFalse(snapshot.isHomeTopTabMaterialEnabled)
        assertTrue(TabPreferences.resolveHomeTopTabSelection(snapshot).hasEnabledTab())
    }

    @Test fun malformedOrEmptyFixedSettingsStayEmptyForTheRuntimeNonemptyGuard() {
        for (raw in listOf("", "{", "[]", "[null]", "[\"code:recommend\"]", "[{\"key\":\"garbage\"}]")) {
            val state = derive(choices + (KEY_HOME_TOP_TAB_FIXED_SELECTION_JSON to raw))
            assertTrue(state.isHomeTopTabFixedEnabled)
            assertTrue("raw=$raw", state.homeTopTabFixedKeys.isEmpty())
            assertFalse(state.isHomeTopTabRecommendEnabled)
            assertFalse(state.isHomeTopTabFollowedEnabled)
        }
    }

    @Test fun aCustomHostTabAloneIsAValidSelectionAndUrlChangesDoNotMatchByTitle() {
        val key = checkNotNull(TabPreferences.homeTopTabKeyFor(202, null, "https://example.com/tab/one"))
        val selected = listOf(FixedHomeTopTab(key, "推荐"))
        val state = derive(choices + (KEY_HOME_TOP_TAB_FIXED_SELECTION_JSON to TabPreferences.serializeFixedHomeTopTabs(selected)))
        val selection = TabPreferences.resolveHomeTopTabSelection(state)
        assertTrue(selection.hasEnabledTab())
        assertTrue(selection.isTabKeyEnabled(key))
        assertFalse(selection.isTabKeyEnabled(TabPreferences.homeTopTabKeyFor(202, null, "https://example.com/tab/two")))
        assertFalse(selection.isTabKeyEnabled("code:recommend"))
        assertEquals("code:custom", TabPreferences.homeTopTabKeyFor(1, " CUSTOM ", "https://example.com/"))
    }

    @Test fun missingSelectionsRemainEditableAndReturningAliasesBecomeAvailable() {
        val catalog = listOf(HomeTopTabCatalogEntry("type:1", "新标题"), TabPreferences.followedHomeTopTabCatalogEntry())
        val selected = listOf(FixedHomeTopTab("code:recommend", "旧标题"), FixedHomeTopTab("code:season", "季节"))
        assertEquals(listOf(HomeTopTabCatalogEntry("code:season", "季节")), TabPreferences.unavailableFixedHomeTopTabs(catalog, selected))
        assertTrue(TabPreferences.unavailableFixedHomeTopTabs(catalog + HomeTopTabCatalogEntry("code:season", "归来"), selected).isEmpty())
    }

    @Test fun modeAndSelectionSaveTogetherAndDisablingFixedKeepsKnownChoices() {
        val store = PreferenceStore(choices.toMutableMap())
        val catalog = listOf(
            HomeTopTabCatalogEntry("type:1", "推荐"), HomeTopTabCatalogEntry("code:live", "直播"),
            HomeTopTabCatalogEntry("code:new", "新项"), TabPreferences.followedHomeTopTabCatalogEntry(),
        )
        val selected = listOf(FixedHomeTopTab("code:recommend", "推荐"), FixedHomeTopTab(HOME_TOP_TAB_KEY_FOLLOWED, "关注"))
        TabPreferences.saveHomeTopTabSelection(store.prefs, catalog, selected, true)
        assertEquals(1, store.applies)
        assertEquals(1, store.edits)
        assertEquals(setOf("code:live", "type:6", "code:new"), TabPreferences.readHomeTopTabDisabledKeys(store.prefs))
        assertEquals(true, store.values[KEY_HOME_TOP_TAB_RECOMMEND])
        assertEquals(false, store.values[KEY_HOME_TOP_TAB_LIVE])
        assertEquals(true, store.values[KEY_HOME_TOP_TAB_FOLLOWED])
        TabPreferences.saveHomeTopTabSelection(store.prefs, catalog, selected, false)
        val state = derive(store.values)
        assertFalse(state.isHomeTopTabFixedEnabled)
        val selection = TabPreferences.resolveHomeTopTabSelection(state)
        assertTrue(selection.isTabKeyEnabled("type:1"))
        assertTrue(selection.followedEnabled)
        assertFalse(selection.isTabKeyEnabled("code:new"))
        assertTrue(selection.isTabKeyEnabled("code:future"))
        assertEquals(selected, TabPreferences.readFixedHomeTopTabs(store.prefs))
    }

    private fun derive(values: Map<String, Any>, capabilities: HostCapabilities = available) =
        EffectiveSettingsPolicy.derive(UserSettings(values), capabilities, RemoteSettingsPolicy(false)).snapshot

    private class PreferenceStore(val values: MutableMap<String, Any>) {
        var applies = 0
        var edits = 0
        private val pending = mutableMapOf<String, Any>()
        private val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putBoolean", "putString" -> { pending[args!![0] as String] = args[1]; proxy }
                "apply" -> { values.putAll(pending); pending.clear(); applies++; null }
                else -> error("Unexpected edit: ${method.name}")
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "contains" -> values.containsKey(args!![0])
                "getBoolean", "getString" -> values[args!![0]] ?: args[1]
                "edit" -> { edits++; editor }
                else -> error("Unexpected read: ${method.name}")
            }
        } as SharedPreferences
    }
}
