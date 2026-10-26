package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.utils.toLowerHex

import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

object TabPreferences {
    const val KEY_ENABLE_TAB_CUSTOMIZATION = "enable_tab_customization"

    const val KEY_SIMPLIFY_HOME_TABS = "simplify_home_tabs"

    const val KEY_CUSTOM_HOME_TOP_TABS = KEY_SIMPLIFY_HOME_TABS

    const val KEY_HOME_TOP_TAB_MATERIAL = "home_top_tab_material"

    const val KEY_HOME_TOP_TAB_RECOMMEND = "home_top_tab_recommend"

    const val KEY_HOME_TOP_TAB_LIVE = "home_top_tab_live"

    const val KEY_HOME_TOP_TAB_FOLLOWED = "home_top_tab_followed"

    const val KEY_HOME_TOP_TAB_DISABLED_KEYS = "home_top_tab_disabled_keys"

    const val KEY_HOME_TOP_TAB_CATALOG_JSON = "home_top_tab_catalog_json"

    const val KEY_HOME_TOP_TAB_FIXED_ENABLED = "home_top_tab_fixed_enabled"

    const val KEY_HOME_TOP_TAB_FIXED_SELECTION_JSON = "home_top_tab_fixed_selection_json"

    const val KEY_AUTO_HIDE_HOME_TAB = "auto_hide_home_tab"

    const val KEY_HIDE_HOME_TAB_RED_DOT = "hide_home_tab_red_dot"

    const val KEY_SIMPLIFY_BOTTOM_TABS = "simplify_bottom_tabs"

    const val KEY_CUSTOM_BOTTOM_TABS = KEY_SIMPLIFY_BOTTOM_TABS

    const val KEY_BOTTOM_TAB_HOME = "bottom_tab_home"

    const val KEY_BOTTOM_TAB_ENTER_FORUM = "bottom_tab_enter_forum"

    const val KEY_BOTTOM_TAB_RETAIL_STORE = "bottom_tab_retail_store"

    const val KEY_BOTTOM_TAB_MESSAGE = "bottom_tab_message"

    const val KEY_BOTTOM_TAB_MINE = "bottom_tab_mine"

    const val KEY_BOTTOM_TAB_LIQUID_GLASS = "bottom_tab_liquid_glass"

    @Volatile private var homeTopTabCatalogJsonCache: String? = null

    const val HOME_TOP_TAB_SOURCE_HOST = "host"

    const val HOME_TOP_TAB_SOURCE_MODULE = "module"

    const val HOME_TOP_TAB_KEY_MATERIAL = "code:material"

    const val HOME_TOP_TAB_KEY_RECOMMEND = "code:recommend"

    const val HOME_TOP_TAB_KEY_LIVE = "code:live"

    const val HOME_TOP_TAB_KEY_FOLLOWED = "module:followed"

    const val HOME_TOP_TAB_CODE_MATERIAL = "material"

    const val HOME_TOP_TAB_CODE_RECOMMEND = "recommend"

    const val HOME_TOP_TAB_CODE_LIVE = "live"

    const val HOME_TOP_TAB_CODE_FOLLOWED = "my_followed"

    const val HOME_TOP_TAB_CODE_FOLLOWED_ALT = "myfollowed"

    const val HOME_TOP_TAB_CODE_FOLLOWED_ALT_2 = "followed"

    const val HOME_TOP_TAB_URL_FOLLOWED_PATH = "hybrid-usergrow-base/myfollowed/hybrid"

    private const val HOME_TOP_TAB_KEY_PREFIX_CODE = "code:"

    private const val HOME_TOP_TAB_KEY_PREFIX_URL = "url:"

    private const val HOME_TOP_TAB_KEY_PREFIX_TYPE = "type:"

    private const val HOME_TOP_TAB_TYPE_MATERIAL = 9

    private const val HOME_TOP_TAB_TYPE_RECOMMEND = 1

    private const val HOME_TOP_TAB_TYPE_LIVE = 6

    private val HOME_TOP_TAB_LEGACY_MATERIAL_KEYS = setOf(
        HOME_TOP_TAB_KEY_MATERIAL,
        "$HOME_TOP_TAB_KEY_PREFIX_TYPE$HOME_TOP_TAB_TYPE_MATERIAL",
    )

    internal val HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS = setOf(
        HOME_TOP_TAB_KEY_RECOMMEND,
        "$HOME_TOP_TAB_KEY_PREFIX_TYPE$HOME_TOP_TAB_TYPE_RECOMMEND",
    )

    private val HOME_TOP_TAB_LEGACY_LIVE_KEYS = setOf(
        HOME_TOP_TAB_KEY_LIVE,
        "$HOME_TOP_TAB_KEY_PREFIX_TYPE$HOME_TOP_TAB_TYPE_LIVE",
    )

    private val HOME_TOP_TAB_LEGACY_FOLLOWED_KEYS = setOf(HOME_TOP_TAB_KEY_FOLLOWED)

    data class HomeTopTabCatalogEntry(
        val key: String,
        val label: String,
        val type: Int? = null,
        val code: String? = null,
        val url: String? = null,
        val source: String = HOME_TOP_TAB_SOURCE_HOST,
        val order: Int = 0,
    )

    data class HomeTopTabSelection(
        val materialEnabled: Boolean,
        val recommendEnabled: Boolean,
        val liveEnabled: Boolean,
        val followedEnabled: Boolean,
        val disabledKeys: Set<String> = emptySet(),
        val fixedEnabled: Boolean = false,
        val fixedKeys: Set<String> = emptySet(),
    ) {
        fun hasEnabledTab(): Boolean = if (fixedEnabled) fixedKeys.isNotEmpty()
            else materialEnabled || recommendEnabled || liveEnabled || followedEnabled

        fun isTabKeyEnabled(key: String?): Boolean {
            if (fixedEnabled) return canonicalHomeTopTabKey(key)?.let { it in fixedKeys } == true
            if (key.isNullOrBlank()) return true
            return key !in disabledKeys
        }
    }

    fun normalizeHomeTopTabSelection(selection: HomeTopTabSelection): HomeTopTabSelection {
        if (selection.fixedEnabled || selection.disabledKeys.isNotEmpty() || selection.hasEnabledTab()) return selection
        // Keep a valid fallback when external preferences become inconsistent.
        return selection.copy(
            materialEnabled = false,
            recommendEnabled = true,
            liveEnabled = false,
            followedEnabled = false,
            disabledKeys = selection.disabledKeys - HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS,
        )
    }

    fun resolveHomeTopTabSelection(
        settings: SettingsSnapshot = ConfigManager.snapshot(),
        allowFixed: Boolean = true,
    ): HomeTopTabSelection {
        if (!settings.isHomeTopTabsCustomEnabled) {
            return HomeTopTabSelection(
                materialEnabled = false,
                recommendEnabled = false,
                liveEnabled = false,
                followedEnabled = false,
                disabledKeys = emptySet(),
            )
        }
        if (settings.isHomeTopTabFixedEnabled && !allowFixed) {
            return legacyHomeTopTabSelection(settings.homeTopTabDisabledKeys)
        }
        val current = HomeTopTabSelection(
            materialEnabled = settings.isHomeTopTabMaterialEnabled,
            recommendEnabled = settings.isHomeTopTabRecommendEnabled,
            liveEnabled = settings.isHomeTopTabLiveEnabled,
            followedEnabled = settings.isHomeTopTabFollowedEnabled,
            disabledKeys = settings.homeTopTabDisabledKeys,
            fixedEnabled = settings.isHomeTopTabFixedEnabled,
            fixedKeys = settings.homeTopTabFixedKeys,
        )
        return normalizeHomeTopTabSelection(current)
    }

    internal fun legacyHomeTopTabSelection(disabledKeys: Set<String>) = HomeTopTabSelection(
        materialEnabled = isLegacyHomeTopTabEnabled(disabledKeys, HOME_TOP_TAB_KEY_MATERIAL),
        recommendEnabled = isLegacyHomeTopTabEnabled(disabledKeys, HOME_TOP_TAB_KEY_RECOMMEND),
        liveEnabled = isLegacyHomeTopTabEnabled(disabledKeys, HOME_TOP_TAB_KEY_LIVE),
        followedEnabled = isLegacyHomeTopTabEnabled(disabledKeys, HOME_TOP_TAB_KEY_FOLLOWED),
        disabledKeys = disabledKeys,
    )

    internal fun fixedHomeTopTabSelection(keys: Set<String>, disabledKeys: Set<String>) = HomeTopTabSelection(
        materialEnabled = HOME_TOP_TAB_KEY_MATERIAL in keys,
        recommendEnabled = HOME_TOP_TAB_KEY_RECOMMEND in keys,
        liveEnabled = HOME_TOP_TAB_KEY_LIVE in keys,
        followedEnabled = HOME_TOP_TAB_KEY_FOLLOWED in keys,
        disabledKeys = disabledKeys,
        fixedEnabled = true,
        fixedKeys = keys,
    )

    data class FixedHomeTopTab(val key: String, val label: String)

    /** Names are only for unavailable rows; identity never depends on display text. */
    internal fun readFixedHomeTopTabs(p: SettingsValues): List<FixedHomeTopTab> =
        parseFixedHomeTopTabs(HOME_TOP_TAB_FIXED_SELECTION_JSON.read(p))

    internal fun readFixedHomeTopTabs(p: SharedPreferences): List<FixedHomeTopTab> =
        readFixedHomeTopTabs(p.readOnlyValues())

    internal fun fixedHomeTopTabKeys(entries: List<FixedHomeTopTab>): Set<String> =
        Collections.unmodifiableSet(entries.mapNotNullTo(LinkedHashSet()) { canonicalHomeTopTabKey(it.key) })

    internal fun parseFixedHomeTopTabs(raw: String?): List<FixedHomeTopTab> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val entries = LinkedHashMap<String, FixedHomeTopTab>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: return emptyList()
                val key = canonicalHomeTopTabKey(item.optString("key")) ?: return emptyList()
                entries.putIfAbsent(key, FixedHomeTopTab(key, item.optString("label").trim()))
            }
            entries.values.toList()
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }

    internal fun serializeFixedHomeTopTabs(entries: List<FixedHomeTopTab>): String = JSONArray().apply {
        entries.distinctBy { canonicalHomeTopTabKey(it.key) }.forEach { entry ->
            canonicalHomeTopTabKey(entry.key)?.let { key ->
                put(JSONObject().put("key", key).put("label", entry.label.trim()))
            }
        }
    }.toString()

    fun canonicalHomeTopTabKey(key: String?): String? {
        val value = key?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when {
            value in HOME_TOP_TAB_LEGACY_MATERIAL_KEYS -> HOME_TOP_TAB_KEY_MATERIAL
            value in HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS -> HOME_TOP_TAB_KEY_RECOMMEND
            value in HOME_TOP_TAB_LEGACY_LIVE_KEYS -> HOME_TOP_TAB_KEY_LIVE
            value == HOME_TOP_TAB_KEY_FOLLOWED || value == "code:$HOME_TOP_TAB_CODE_FOLLOWED" ||
                value == "code:$HOME_TOP_TAB_CODE_FOLLOWED_ALT" || value == "code:$HOME_TOP_TAB_CODE_FOLLOWED_ALT_2" -> HOME_TOP_TAB_KEY_FOLLOWED
            value.startsWith(HOME_TOP_TAB_KEY_PREFIX_CODE) && value.length > HOME_TOP_TAB_KEY_PREFIX_CODE.length -> value
            value.startsWith(HOME_TOP_TAB_KEY_PREFIX_URL) && value.substringAfter(':').let { hash ->
                hash.length == 16 && hash.all { it in '0'..'9' || it in 'a'..'f' }
            } -> value
            value.startsWith(HOME_TOP_TAB_KEY_PREFIX_TYPE) &&
                value.substringAfter(':').toIntOrNull()?.let { it >= 0 } == true -> value
            else -> null
        }
    }

    internal fun unavailableFixedHomeTopTabs(
        catalog: List<HomeTopTabCatalogEntry>,
        selected: List<FixedHomeTopTab>,
    ): List<HomeTopTabCatalogEntry> {
        val available = catalog.mapNotNullTo(HashSet()) { canonicalHomeTopTabKey(it.key) }
        return selected.filter { it.key !in available }.map { HomeTopTabCatalogEntry(it.key, it.label) }
    }

    internal fun saveHomeTopTabSelection(
        prefs: SharedPreferences,
        entries: List<HomeTopTabCatalogEntry>,
        selected: List<FixedHomeTopTab>,
        fixedEnabled: Boolean,
    ) {
        val selectedKeys = fixedHomeTopTabKeys(selected)
        val knownKeys = expandHomeTopTabDisabledKeys(entries.mapTo(LinkedHashSet()) { it.key })
        val disabledKeys = expandHomeTopTabDisabledKeys(
            (readHomeTopTabDisabledKeys(prefs) - knownKeys) + entries.filter {
                canonicalHomeTopTabKey(it.key) !in selectedKeys
            }.map { it.key },
        )
        val legacy = legacyHomeTopTabSelection(disabledKeys)
        prefs.edit()
            .putBoolean(KEY_HOME_TOP_TAB_FIXED_ENABLED, fixedEnabled)
            .putString(KEY_HOME_TOP_TAB_FIXED_SELECTION_JSON, serializeFixedHomeTopTabs(selected))
            .putString(KEY_HOME_TOP_TAB_DISABLED_KEYS, serializeHomeTopTabDisabledKeys(disabledKeys))
            .putBoolean(KEY_HOME_TOP_TAB_MATERIAL, legacy.materialEnabled)
            .putBoolean(KEY_HOME_TOP_TAB_RECOMMEND, legacy.recommendEnabled)
            .putBoolean(KEY_HOME_TOP_TAB_LIVE, legacy.liveEnabled)
            .putBoolean(KEY_HOME_TOP_TAB_FOLLOWED, legacy.followedEnabled)
            .apply()
    }

    fun buildHomeTopTabCatalogEntry(
        type: Int?,
        code: String?,
        label: String?,
        url: String?,
        order: Int,
        source: String = HOME_TOP_TAB_SOURCE_HOST,
    ): HomeTopTabCatalogEntry? {
        val key = homeTopTabKeyFor(type, code, url) ?: return null
        return HomeTopTabCatalogEntry(
            key = key,
            label = label?.trim().orEmpty(),
            type = type?.takeIf { it >= 0 },
            code = normalizeHomeTopTabCode(code),
            url = normalizeNonBlank(url),
            source = source,
            order = order,
        )
    }

    fun followedHomeTopTabCatalogEntry(order: Int = Int.MAX_VALUE): HomeTopTabCatalogEntry {
        return HomeTopTabCatalogEntry(
            key = HOME_TOP_TAB_KEY_FOLLOWED,
            label = "",
            type = null,
            code = HOME_TOP_TAB_CODE_FOLLOWED,
            url = null,
            source = HOME_TOP_TAB_SOURCE_MODULE,
            order = order,
        )
    }

    fun readHomeTopTabCatalog(context: Context): List<HomeTopTabCatalogEntry> {
        val hostEntries = readHomeTopTabHostCatalog(context)
        return hostEntries + followedHomeTopTabCatalogEntry(order = hostEntries.size)
    }

    fun readHomeTopTabHostCatalog(context: Context): List<HomeTopTabCatalogEntry> {
        val raw = HOME_TOP_TAB_CATALOG_JSON.read(ConfigManager.getModuleStatePrefs(context))
        homeTopTabCatalogJsonCache = raw
        return parseHomeTopTabCatalog(raw)
    }

    fun updateHomeTopTabCatalog(context: Context, entries: List<HomeTopTabCatalogEntry>): Boolean {
        val normalized = normalizeHomeTopTabCatalog(entries)
        val serialized = serializeHomeTopTabCatalog(normalized)
        val statePrefs = ConfigManager.getModuleStatePrefs(context)
        val cached = homeTopTabCatalogJsonCache ?: TabPreferences.HOME_TOP_TAB_CATALOG_JSON.read(statePrefs)
        if (cached == serialized) return false
        statePrefs.edit().putString(KEY_HOME_TOP_TAB_CATALOG_JSON, serialized).apply()
        homeTopTabCatalogJsonCache = serialized
        return true
    }

    fun readHomeTopTabDisabledKeys(p: SharedPreferences): Set<String> = readHomeTopTabDisabledKeys(p.readOnlyValues())

    internal fun readHomeTopTabDisabledKeys(p: SettingsValues): Set<String> {
        if (p.contains(KEY_HOME_TOP_TAB_DISABLED_KEYS)) {
            return expandHomeTopTabDisabledKeys(
                parseHomeTopTabKeySet(TabPreferences.HOME_TOP_TAB_DISABLED_KEYS.read(p)),
            )
        }
        val disabled = LinkedHashSet<String>()
        if (!TabPreferences.HOME_TOP_TAB_MATERIAL.read(p)) disabled += HOME_TOP_TAB_LEGACY_MATERIAL_KEYS
        if (!TabPreferences.HOME_TOP_TAB_RECOMMEND.read(p)) disabled += HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS
        if (!TabPreferences.HOME_TOP_TAB_LIVE.read(p)) disabled += HOME_TOP_TAB_LEGACY_LIVE_KEYS
        if (!TabPreferences.HOME_TOP_TAB_FOLLOWED.read(p)) disabled += HOME_TOP_TAB_LEGACY_FOLLOWED_KEYS
        return disabled
    }

    fun serializeHomeTopTabDisabledKeys(keys: Set<String>): String = serializeHomeTopTabKeySet(keys)

    fun expandHomeTopTabDisabledKeys(keys: Set<String>): Set<String> {
        if (keys.isEmpty()) return emptySet()
        val out = LinkedHashSet<String>()
        out += keys.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (out.any { it in HOME_TOP_TAB_LEGACY_MATERIAL_KEYS }) out += HOME_TOP_TAB_LEGACY_MATERIAL_KEYS
        if (out.any { it in HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS }) out += HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS
        if (out.any { it in HOME_TOP_TAB_LEGACY_LIVE_KEYS }) out += HOME_TOP_TAB_LEGACY_LIVE_KEYS
        if (out.any { it in HOME_TOP_TAB_LEGACY_FOLLOWED_KEYS }) out += HOME_TOP_TAB_LEGACY_FOLLOWED_KEYS
        return out
    }

    fun isLegacyHomeTopTabEnabled(disabledKeys: Set<String>, key: String): Boolean {
        val aliases = when (key) {
            HOME_TOP_TAB_KEY_MATERIAL -> HOME_TOP_TAB_LEGACY_MATERIAL_KEYS
            HOME_TOP_TAB_KEY_RECOMMEND -> HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS
            HOME_TOP_TAB_KEY_LIVE -> HOME_TOP_TAB_LEGACY_LIVE_KEYS
            HOME_TOP_TAB_KEY_FOLLOWED -> HOME_TOP_TAB_LEGACY_FOLLOWED_KEYS
            else -> setOf(key)
        }
        return aliases.none { it in disabledKeys }
    }

    private fun normalizeHomeTopTabCatalog(entries: List<HomeTopTabCatalogEntry>): List<HomeTopTabCatalogEntry> {
        val out = LinkedHashMap<String, HomeTopTabCatalogEntry>()
        entries.sortedBy { it.order }.forEachIndexed { index, entry ->
            if (entry.key.isBlank() || entry.source != HOME_TOP_TAB_SOURCE_HOST) return@forEachIndexed
            out.putIfAbsent(
                entry.key,
                entry.copy(
                    label = entry.label.trim(),
                    code = normalizeHomeTopTabCode(entry.code),
                    url = normalizeNonBlank(entry.url),
                    order = index,
                    source = HOME_TOP_TAB_SOURCE_HOST,
                ),
            )
        }
        return out.values.toList()
    }

    private fun serializeHomeTopTabCatalog(entries: List<HomeTopTabCatalogEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("label", entry.label)
                    .put("type", entry.type ?: JSONObject.NULL)
                    .put("code", entry.code ?: JSONObject.NULL)
                    .put("url", entry.url ?: JSONObject.NULL)
                    .put("source", entry.source)
                    .put("order", entry.order),
            )
        }
        return array.toString()
    }

    private fun parseHomeTopTabCatalog(raw: String?): List<HomeTopTabCatalogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val entries = ArrayList<HomeTopTabCatalogEntry>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val key = item.optString("key").takeIf { it.isNotBlank() } ?: continue
                entries += HomeTopTabCatalogEntry(
                    key = key,
                    label = item.optString("label").trim(),
                    type = item.optIntOrNull("type"),
                    code = item.optStringOrNull("code")?.let(::normalizeHomeTopTabCode),
                    url = item.optStringOrNull("url")?.let(::normalizeNonBlank),
                    source = item.optString("source", HOME_TOP_TAB_SOURCE_HOST),
                    order = item.optInt("order", i),
                )
            }
            normalizeHomeTopTabCatalog(entries)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseHomeTopTabKeySet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            val array = JSONArray(raw)
            val out = LinkedHashSet<String>()
            for (i in 0 until array.length()) {
                val key = array.optString(i).trim()
                if (key.isNotEmpty()) out += key
            }
            out
        } catch (_: Throwable) {
            raw.split('\n', ',', ';')
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toCollection(LinkedHashSet())
        }
    }

    private fun serializeHomeTopTabKeySet(keys: Set<String>): String {
        val array = JSONArray()
        keys.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .forEach(array::put)
        return array.toString()
    }

    private fun isFollowedHomeTopTab(code: String?, url: String?): Boolean {
        val normalizedCode = normalizeHomeTopTabCode(code)
        if (normalizedCode == HOME_TOP_TAB_CODE_FOLLOWED ||
            normalizedCode == HOME_TOP_TAB_CODE_FOLLOWED_ALT ||
            normalizedCode == HOME_TOP_TAB_CODE_FOLLOWED_ALT_2
        ) {
            return true
        }
        return normalizeHomeTopTabUrl(url)?.contains(HOME_TOP_TAB_URL_FOLLOWED_PATH) == true
    }

    private fun normalizeHomeTopTabCode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().lowercase(Locale.ROOT)
    }

    private fun normalizeHomeTopTabUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().lowercase(Locale.ROOT)
    }

    private fun normalizeNonBlank(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim()
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.toLowerHex()
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name)
    }

    data class BottomTabSelection(
        val homeEnabled: Boolean,
        val enterForumEnabled: Boolean,
        val retailStoreEnabled: Boolean,
        val messageEnabled: Boolean,
        val mineEnabled: Boolean,
    ) {
        fun hasEnabledTab(): Boolean {
            return homeEnabled || enterForumEnabled || retailStoreEnabled || messageEnabled || mineEnabled
        }
    }

    fun normalizeBottomTabSelection(selection: BottomTabSelection): BottomTabSelection {
        if (selection.hasEnabledTab()) return selection
        // Keep a valid fallback when external preferences become inconsistent.
        return selection.copy(
            homeEnabled = true,
            enterForumEnabled = false,
            retailStoreEnabled = false,
            messageEnabled = false,
            mineEnabled = false,
        )
    }

    fun resolveBottomTabSelection(): BottomTabSelection {
        return ConfigManager.snapshot().bottomTabSelection ?: BottomTabSelection(
                homeEnabled = false,
                enterForumEnabled = false,
                retailStoreEnabled = false,
                messageEnabled = false,
                mineEnabled = false,
        )
    }

    fun homeTopTabKeyFor(type: Int?, code: String?, url: String?): String? {
        if (isFollowedHomeTopTab(code, url)) return HOME_TOP_TAB_KEY_FOLLOWED
        normalizeHomeTopTabCode(code)?.let { return "$HOME_TOP_TAB_KEY_PREFIX_CODE$it" }
        normalizeHomeTopTabUrl(url)?.let { return "$HOME_TOP_TAB_KEY_PREFIX_URL${sha256Hex(it).take(16)}" }
        val tabType = type?.takeIf { it >= 0 } ?: return null
        return "$HOME_TOP_TAB_KEY_PREFIX_TYPE$tabType"
    }

    internal val ENABLE_TAB_CUSTOMIZATION = BooleanPreference(
        KEY_ENABLE_TAB_CUSTOMIZATION, false, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.TAB_CUSTOMIZATION_LABEL, UiText.Settings.TAB_CUSTOMIZATION_DESC),
    )
    internal val CUSTOM_HOME_TOP_TABS = BooleanPreference(
        KEY_CUSTOM_HOME_TOP_TABS, false, PreferenceUse.SWITCH, HookFeatureKey.SIMPLIFY_HOME_TOP_TABS,
        SwitchPresentation(UiText.Settings.SIMPLIFY_HOME_TAB_LABEL, UiText.Settings.SIMPLIFY_HOME_TAB_DESC),
    )
    internal val HOME_TOP_TAB_MATERIAL = BooleanPreference(
        KEY_HOME_TOP_TAB_MATERIAL, true, PreferenceUse.LEGACY, null,
    )
    internal val HOME_TOP_TAB_RECOMMEND = BooleanPreference(
        KEY_HOME_TOP_TAB_RECOMMEND, true, PreferenceUse.LEGACY, null,
    )
    internal val HOME_TOP_TAB_LIVE = BooleanPreference(
        KEY_HOME_TOP_TAB_LIVE, true, PreferenceUse.LEGACY, null,
    )
    internal val HOME_TOP_TAB_FOLLOWED = BooleanPreference(
        KEY_HOME_TOP_TAB_FOLLOWED, true, PreferenceUse.LEGACY, null,
    )
    internal val HOME_TOP_TAB_DISABLED_KEYS = StringPreference(
        KEY_HOME_TOP_TAB_DISABLED_KEYS, null, PreferenceUse.FORM,
    )
    internal val HOME_TOP_TAB_CATALOG_JSON = StringPreference(
        KEY_HOME_TOP_TAB_CATALOG_JSON, null, PreferenceUse.MODULE_STATE,
    )
    internal val HOME_TOP_TAB_FIXED_ENABLED = BooleanPreference(
        KEY_HOME_TOP_TAB_FIXED_ENABLED, false, PreferenceUse.FORM, HookFeatureKey.FIXED_HOME_TOP_TABS,
        SwitchPresentation(UiText.Settings.HOME_TOP_TAB_FIXED_LABEL, UiText.Settings.HOME_TOP_TAB_FIXED_DESC),
    )
    internal val HOME_TOP_TAB_FIXED_SELECTION_JSON = StringPreference(
        KEY_HOME_TOP_TAB_FIXED_SELECTION_JSON, null, PreferenceUse.FORM,
    )
    internal val AUTO_HIDE_HOME_TAB = BooleanPreference(
        KEY_AUTO_HIDE_HOME_TAB, false, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.AUTO_HIDE_HOME_TAB_LABEL, UiText.Settings.AUTO_HIDE_HOME_TAB_DESC),
    )
    internal val HIDE_HOME_TAB_RED_DOT = BooleanPreference(
        KEY_HIDE_HOME_TAB_RED_DOT, false, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.HIDE_HOME_TAB_RED_DOT_LABEL, UiText.Settings.HIDE_HOME_TAB_RED_DOT_DESC),
    )
    internal val CUSTOM_BOTTOM_TABS = BooleanPreference(
        KEY_CUSTOM_BOTTOM_TABS, false, PreferenceUse.SWITCH, HookFeatureKey.SIMPLIFY_BOTTOM_TABS,
        SwitchPresentation(UiText.Settings.SIMPLIFY_BOTTOM_TAB_LABEL, UiText.Settings.SIMPLIFY_BOTTOM_TAB_DESC),
    )
    internal val BOTTOM_TAB_HOME = BooleanPreference(
        KEY_BOTTOM_TAB_HOME, true, PreferenceUse.FORM, null,
    )
    internal val BOTTOM_TAB_ENTER_FORUM = BooleanPreference(
        KEY_BOTTOM_TAB_ENTER_FORUM, true, PreferenceUse.FORM, null,
    )
    internal val BOTTOM_TAB_RETAIL_STORE = BooleanPreference(
        KEY_BOTTOM_TAB_RETAIL_STORE, true, PreferenceUse.FORM, null,
    )
    internal val BOTTOM_TAB_MESSAGE = BooleanPreference(
        KEY_BOTTOM_TAB_MESSAGE, true, PreferenceUse.FORM, null,
    )
    internal val BOTTOM_TAB_MINE = BooleanPreference(
        KEY_BOTTOM_TAB_MINE, true, PreferenceUse.FORM, null,
    )
    internal val BOTTOM_TAB_LIQUID_GLASS = BooleanPreference(
        KEY_BOTTOM_TAB_LIQUID_GLASS, false, PreferenceUse.LEGACY, null,
    )

    internal val preferences: List<Preference<*>> = listOf(
        ENABLE_TAB_CUSTOMIZATION,
        CUSTOM_HOME_TOP_TABS,
        HOME_TOP_TAB_MATERIAL,
        HOME_TOP_TAB_RECOMMEND,
        HOME_TOP_TAB_LIVE,
        HOME_TOP_TAB_FOLLOWED,
        HOME_TOP_TAB_DISABLED_KEYS,
        HOME_TOP_TAB_CATALOG_JSON,
        HOME_TOP_TAB_FIXED_ENABLED,
        HOME_TOP_TAB_FIXED_SELECTION_JSON,
        AUTO_HIDE_HOME_TAB,
        HIDE_HOME_TAB_RED_DOT,
        CUSTOM_BOTTOM_TABS,
        BOTTOM_TAB_HOME,
        BOTTOM_TAB_ENTER_FORUM,
        BOTTOM_TAB_RETAIL_STORE,
        BOTTOM_TAB_MESSAGE,
        BOTTOM_TAB_MINE,
        BOTTOM_TAB_LIQUID_GLASS,
    )

    internal fun saveBottomTabSelection(p: SharedPreferences, selection: BottomTabSelection) {
        p.edit()
            .putBoolean(KEY_BOTTOM_TAB_HOME, selection.homeEnabled)
            .putBoolean(KEY_BOTTOM_TAB_ENTER_FORUM, selection.enterForumEnabled)
            .putBoolean(KEY_BOTTOM_TAB_RETAIL_STORE, selection.retailStoreEnabled)
            .putBoolean(KEY_BOTTOM_TAB_MESSAGE, selection.messageEnabled)
            .putBoolean(KEY_BOTTOM_TAB_MINE, selection.mineEnabled)
            .apply()
    }

}
