package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.TabPreferences
import android.content.Context
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.HomeTabItemTargets
import com.forbidad4tieba.hook.symbol.model.HomeTabResolvedSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale

object HomeTabHook {
    @Volatile private var sLastTabItemClass: Class<*>? = null

    private const val TAB_TYPE_RECOMMEND = 1
    private const val TAB_TYPE_LIVE = 6
    private const val TAB_TYPE_MATERIAL = 9
    private const val TAB_TYPE_WEB_ACTIVITY = 202

    private const val TAB_CODE_RECOMMEND = TabPreferences.HOME_TOP_TAB_CODE_RECOMMEND
    private const val TAB_CODE_LIVE = TabPreferences.HOME_TOP_TAB_CODE_LIVE
    private const val TAB_CODE_MATERIAL = TabPreferences.HOME_TOP_TAB_CODE_MATERIAL
    private const val TAB_CODE_FOLLOWED = TabPreferences.HOME_TOP_TAB_CODE_FOLLOWED
    private const val TAB_CODE_FOLLOWED_ALT = TabPreferences.HOME_TOP_TAB_CODE_FOLLOWED_ALT
    private const val TAB_CODE_FOLLOWED_ALT_2 = TabPreferences.HOME_TOP_TAB_CODE_FOLLOWED_ALT_2

    private val TAB_NAME_FOLLOWED = UiText.Settings.HOME_TOP_TAB_FOLLOWED_LABEL
    private const val TAB_URL_FOLLOWED =
        "https://tieba.baidu.com/mo/q/hybrid-usergrow-base/myFollowed/hybrid" +
            "?customfullscreen=1&nonavigationbar=1&loadingSignal=1&nohead=1&skin=default" +
            "&tbhook_from_home_top_tab=1"
    private const val TAB_URL_FOLLOWED_PATH = TabPreferences.HOME_TOP_TAB_URL_FOLLOWED_PATH

    private data class FollowedTemplateContext(
        val itemClass: Class<*>?,
        val template: Any?,
    )

    private data class FollowedSyncResult(
        val added: Boolean,
        val removed: Int,
        val failed: Boolean,
    ) {
        val changed: Boolean
            get() = added || removed > 0
    }

    @Volatile private var sRuntimeTargets: HomeTabResolvedSymbols? = null
    @Volatile private var lastFixedFailure: HomeTabFixedProjection.Failure? = null
    @Volatile private var followedFailureLogged = false
    private val catalogSnapshot = HomeTabCatalogSnapshot()

    internal fun hook(symbols: HomeTabResolvedSymbols, api: XposedInterface? = XposedCompat.module) {
        val mod = api ?: return
        sRuntimeTargets = symbols
        catalogSnapshot.clear()
        lastFixedFailure = null
        followedFailureLogged = false
        val fixedParserInstalled = hookFixedParser(symbols, mod)
        try {
            RuntimeHooks.builder(mod, symbols.rebuildMethod, "HomeTabHook", "hook:symbols.rebuildMethod").intercept { chain ->
                val result = chain.proceed()
                val settings = ConfigManager.snapshot()
                // The parser owns both filtering and the raw catalog in fixed mode.
                if (fixedParserInstalled && settings.isHomeTopTabFixedEnabled) return@intercept result
                @Suppress("UNCHECKED_CAST")
                val list = resolveMutableListField(chain.thisObject) as? MutableList<Any?>
                if (list != null) {
                    captureCatalog(list)
                    if (!settings.isHomeTopTabsCustomEnabled) return@intercept result
                    val currentSelection = TabPreferences.resolveHomeTopTabSelection(settings, allowFixed = false)
                    val templateContext = resolveFollowedTemplateContext(list)
                    val sizeBefore = list.size
                    val filteredCount = filterTabsInPlace(list, currentSelection)
                    val followedSync = syncFollowedTabState(list, currentSelection, templateContext)
                    if (filteredCount > 0 || followedSync.changed || followedSync.failed) {
                        XposedCompat.logD {
                            "[HomeTabHook] > top tabs rebuilt: $sizeBefore -> ${list.size}, " +
                                "removed=$filteredCount, followedAdded=${followedSync.added}, " +
                                "followedRemoved=${followedSync.removed}, followedFailed=${followedSync.failed}, " +
                                "disabledKeys=${currentSelection.disabledKeys.size}, " +
                                "followed=${currentSelection.followedEnabled}"
                        }
                    }
                }
                result
            }
            XposedCompat.log("[HomeTabHook] hook INSTALLED: ${symbols.hostClass.name}.${symbols.rebuildMethod.name}")
        } catch (t: Throwable) {
            XposedCompat.log(
                "[HomeTabHook] FAILED (${symbols.hostClass.name}.${symbols.rebuildMethod.name}): ${t.message}",
            )
            XposedCompat.log(t)
        }
    }

    private fun hookFixedParser(symbols: HomeTabResolvedSymbols, mod: XposedInterface): Boolean {
        val parser = symbols.parseMethod ?: return false
        return try {
            RuntimeHooks.builder(mod, parser, "HomeTabHook.Fixed", "hook:symbols.parseMethod").intercept { chain ->
                val raw = chain.proceed()
                val settings = ConfigManager.snapshot()
                if (!settings.isHomeTopTabsCustomEnabled || !settings.isHomeTopTabFixedEnabled) return@intercept raw
                @Suppress("UNCHECKED_CAST")
                val list = raw as? List<Any?> ?: return@intercept raw
                captureCatalog(list)
                val selection = TabPreferences.resolveHomeTopTabSelection(settings)
                val projected = HomeTabFixedProjection.project(list, selection, { item ->
                    item?.let {
                        TabPreferences.homeTopTabKeyFor(resolveTabType(it).takeIf { type -> type >= 0 }, resolveTabCode(it), resolveTabUrl(it))
                    }
                }) { selected ->
                    if (selection.followedEnabled) {
                        syncFollowedTabState(selected, selection, resolveFollowedTemplateContext(list))
                    }
                }
                if (projected.failure != lastFixedFailure) {
                    lastFixedFailure = projected.failure
                    projected.failure?.let { XposedCompat.logW("[HomeTabHook.Fixed] retaining host tabs: $it") }
                }
                projected.tabs
            }
            XposedCompat.log("[HomeTabHook.Fixed] hook INSTALLED: ${symbols.hostClass.name}.${parser.name}")
            true
        } catch (failure: Throwable) {
            XposedCompat.log("[HomeTabHook.Fixed] install FAILED: ${failure.message}")
            false
        }
    }

    private fun captureCatalog(list: List<Any?>) {
        catalogSnapshot.capture(list.mapNotNull { item ->
            item?.let {
                HomeTabCatalogSnapshot.Item(
                    type = resolveTabType(it).takeIf { type -> type >= 0 },
                    code = resolveTabCode(it), label = resolveTabName(it), url = resolveTabUrl(it),
                )
            }
        })
    }

    internal fun refreshTopTabCatalog(context: Context) {
        val list = catalogSnapshot.current()
        if (list.isEmpty()) return
        val entries = ArrayList<TabPreferences.HomeTopTabCatalogEntry>(list.size)
        for (index in list.indices) {
            val item = list[index]
            TabPreferences.buildHomeTopTabCatalogEntry(
                type = item.type,
                code = item.code,
                label = item.label,
                url = item.url,
                order = index,
            )?.takeUnless { it.key == TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED }?.let(entries::add)
        }
        if (entries.isEmpty()) return
        runCatching {
            TabPreferences.updateHomeTopTabCatalog(context, entries)
        }.onFailure { t ->
            XposedCompat.logD { "[HomeTabHook] update top tab catalog ignored: ${t.message}" }
        }
    }

    private fun filterTabsInPlace(
        list: MutableList<Any?>,
        selection: TabPreferences.HomeTopTabSelection,
    ): Int {
        if (list.isEmpty()) return 0
        var removedCount = 0
        val it = list.iterator()
        while (it.hasNext()) {
            val tabItem = it.next() ?: continue
            val tabType = resolveTabType(tabItem)
            val tabCode = resolveTabCode(tabItem)
            val tabUrl = resolveTabUrl(tabItem)
            if (shouldFilterOut(tabType, tabCode, tabUrl, selection)) {
                it.remove()
                removedCount++
            }
        }
        return removedCount
    }

    private fun shouldFilterOut(
        tabType: Int,
        tabCode: String?,
        tabUrl: String?,
        selection: TabPreferences.HomeTopTabSelection,
    ): Boolean {
        val key = TabPreferences.homeTopTabKeyFor(tabType.takeIf { it >= 0 }, tabCode, tabUrl)
        return !selection.isTabKeyEnabled(key)
    }

    private fun resolveTargetTab(tabType: Int, tabCode: String?, tabUrl: String?): HomeTopTargetTab {
        if (isFollowedCode(tabCode) || isFollowedUrl(tabUrl)) {
            return HomeTopTargetTab.FOLLOWED
        }
        when (tabCode) {
            TAB_CODE_MATERIAL -> return HomeTopTargetTab.MATERIAL
            TAB_CODE_RECOMMEND -> return HomeTopTargetTab.RECOMMEND
            TAB_CODE_LIVE -> return HomeTopTargetTab.LIVE
        }
        return when (tabType) {
            TAB_TYPE_MATERIAL -> HomeTopTargetTab.MATERIAL
            TAB_TYPE_RECOMMEND -> HomeTopTargetTab.RECOMMEND
            TAB_TYPE_LIVE -> HomeTopTargetTab.LIVE
            else -> HomeTopTargetTab.OTHER
        }
    }

    private fun resolveFollowedTemplateContext(list: List<Any?>): FollowedTemplateContext {
        var itemClass: Class<*>? = null
        var materialTemplate: Any? = null
        var webTemplate: Any? = null
        var anyTemplate: Any? = null
        for (entry in list) {
            val tabItem = entry ?: continue
            if (itemClass == null) itemClass = tabItem.javaClass
            if (anyTemplate == null) anyTemplate = tabItem

            val tabType = resolveTabType(tabItem)
            val tabCode = resolveTabCode(tabItem)
            val tabUrl = resolveTabUrl(tabItem)
            if (materialTemplate == null &&
                resolveTargetTab(tabType, tabCode, tabUrl) == HomeTopTargetTab.MATERIAL
            ) {
                materialTemplate = tabItem
            }
            if (webTemplate == null && isWebTabCandidate(tabType, tabUrl)) {
                webTemplate = tabItem
            }
        }
        val finalClass = itemClass ?: sLastTabItemClass
        if (finalClass != null) {
            sLastTabItemClass = finalClass
        }
        return FollowedTemplateContext(
            itemClass = finalClass,
            template = materialTemplate ?: webTemplate ?: anyTemplate,
        )
    }

    private fun isWebTabCandidate(tabType: Int, tabUrl: String?): Boolean {
        if (tabType == TAB_TYPE_MATERIAL || tabType == TAB_TYPE_WEB_ACTIVITY) return true
        return isUrlLike(tabUrl.orEmpty())
    }

    private fun syncFollowedTabState(
        list: MutableList<Any?>,
        selection: TabPreferences.HomeTopTabSelection,
        templateContext: FollowedTemplateContext,
    ): FollowedSyncResult {
        if (!selection.followedEnabled) {
            val removed = removeFollowedTabs(list)
            return FollowedSyncResult(
                added = false,
                removed = removed,
                failed = false,
            )
        }
        return ensureFollowedTabEnabled(list, templateContext)
    }

    private fun ensureFollowedTabEnabled(
        list: MutableList<Any?>,
        templateContext: FollowedTemplateContext,
    ): FollowedSyncResult {
        val followedIndexes = ArrayList<Int>(2)
        for (index in list.indices) {
            val tabItem = list[index] ?: continue
            if (isFollowedTab(tabItem)) {
                followedIndexes.add(index)
            }
        }

        var removed = 0
        if (followedIndexes.size > 1) {
            for (i in followedIndexes.size - 1 downTo 1) {
                list.removeAt(followedIndexes[i])
                removed++
            }
        }
        if (followedIndexes.isNotEmpty()) {
            followedFailureLogged = false
            return FollowedSyncResult(
                added = false,
                removed = removed,
                failed = false,
            )
        }

        val newFollowedTab = createFollowedTabItem(templateContext)
        if (newFollowedTab == null) {
            if (!followedFailureLogged) {
                XposedCompat.logW("[HomeTabHook] followed tab inject failed: no writable tab template/class found")
                followedFailureLogged = true
            }
            return FollowedSyncResult(
                added = false,
                removed = removed,
                failed = true,
            )
        }

        val insertIndex = resolveFollowedInsertIndex(list).coerceIn(0, list.size)
        list.add(insertIndex, newFollowedTab)
        followedFailureLogged = false
        return FollowedSyncResult(
            added = true,
            removed = removed,
            failed = false,
        )
    }

    private fun removeFollowedTabs(list: MutableList<Any?>): Int {
        if (list.isEmpty()) return 0
        var removed = 0
        val it = list.iterator()
        while (it.hasNext()) {
            val tabItem = it.next() ?: continue
            if (isFollowedTab(tabItem)) {
                it.remove()
                removed++
            }
        }
        return removed
    }

    private fun isFollowedTab(tabItem: Any): Boolean {
        return resolveTargetTab(
            tabType = resolveTabType(tabItem),
            tabCode = resolveTabCode(tabItem),
            tabUrl = resolveTabUrl(tabItem),
        ) == HomeTopTargetTab.FOLLOWED
    }

    private fun resolveFollowedInsertIndex(list: List<Any?>): Int {
        var recommendIndex = -1
        var materialIndex = -1
        loop@ for (index in list.indices) {
            val tabItem = list[index] ?: continue
            val target = resolveTargetTab(
                tabType = resolveTabType(tabItem),
                tabCode = resolveTabCode(tabItem),
                tabUrl = resolveTabUrl(tabItem),
            )
            when (target) {
                HomeTopTargetTab.RECOMMEND -> {
                    recommendIndex = index
                    break@loop
                }
                HomeTopTargetTab.MATERIAL -> if (materialIndex < 0) {
                    materialIndex = index
                }
                else -> Unit
            }
        }
        if (recommendIndex >= 0) return recommendIndex + 1
        if (materialIndex >= 0) return materialIndex + 1
        return list.size
    }

    private fun createFollowedTabItem(templateContext: FollowedTemplateContext): Any? {
        val itemClass = templateContext.itemClass ?: sLastTabItemClass ?: return null
        val clonedTemplate = templateContext.template?.let { cloneTabItem(it) }
        val tabItem = clonedTemplate ?: instantiateTabItem(itemClass) ?: return null
        if (!applyFollowedTabIdentity(tabItem)) {
            return null
        }
        return tabItem
    }

    private fun instantiateTabItem(cls: Class<*>): Any? {
        val item = sRuntimeTargets?.item ?: return null
        if (cls != item.type) return null
        return item.constructor?.let(::newInstance)
    }

    private fun newInstance(ctor: Constructor<*>): Any? {
        return try {
            ctor.newInstance()
        } catch (_: Throwable) {
            null
        }
    }

    private fun cloneTabItem(template: Any): Any? {
        val clone = instantiateTabItem(template.javaClass) ?: return null
        copyCachedTabFields(template, clone)
        return clone
    }

    private fun copyCachedTabFields(source: Any, target: Any) {
        val cls = source.javaClass
        copyFieldValue(resolveTypeField(cls), source, target)
        copyFieldValue(resolveCodeField(cls), source, target)
        copyFieldValue(resolveNameField(cls), source, target)
        copyFieldValue(resolveUrlField(cls), source, target)
        copyFieldValue(resolveMainIntField(cls), source, target)
        copyFieldValue(resolveMainBooleanField(cls), source, target)
    }

    private fun copyFieldValue(field: Field?, source: Any, target: Any) {
        if (field == null) return
        try {
            field.set(target, field.get(source))
        } catch (_: Throwable) {
            // Template copy is best-effort; followed identity is applied afterward.
        }
    }

    private fun applyFollowedTabIdentity(tabItem: Any): Boolean {
        val typeApplied = setTabType(tabItem, TAB_TYPE_WEB_ACTIVITY)
        val codeApplied = setTabCode(tabItem, TAB_CODE_FOLLOWED)
        val nameApplied = setTabName(tabItem, TAB_NAME_FOLLOWED)
        val urlApplied = setTabUrl(tabItem, TAB_URL_FOLLOWED)
        setTabMain(tabItem, true)

        if (!typeApplied || !codeApplied || !nameApplied || !urlApplied) {
            if (!followedFailureLogged) XposedCompat.logW(
                "[HomeTabHook] followed tab build failed: " +
                    "type=$typeApplied, code=$codeApplied, name=$nameApplied, " +
                    "url=$urlApplied, class=${tabItem.javaClass.name}"
            )
            return false
        }
        return true
    }

    private fun setTabType(item: Any, type: Int): Boolean {
        val field = resolveTypeField(item.javaClass) ?: return false
        return writeIntField(field, item, type)
    }

    private fun setTabCode(item: Any, code: String): Boolean {
        val cls = item.javaClass
        val field = resolveCodeField(cls) ?: return false
        return try {
            field.set(item, code)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun setTabName(item: Any, name: String): Boolean {
        val cls = item.javaClass
        val field = resolveNameField(cls) ?: return false
        return try {
            field.set(item, name)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun setTabUrl(item: Any, url: String): Boolean {
        val cls = item.javaClass
        val field = resolveUrlField(cls) ?: return false
        return try {
            field.set(item, url)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun setTabMain(item: Any, enabled: Boolean): Boolean {
        val cls = item.javaClass
        val mainSetter = resolveMainSetterMethod(cls)
        if (mainSetter != null) {
            try {
                mainSetter.invoke(item, enabled)
                return true
            } catch (_: Throwable) {
                // Skip and continue.
            }
        }

        val mainIntField = resolveMainIntField(cls)
        if (mainIntField != null && writeIntField(mainIntField, item, if (enabled) 1 else 0)) {
            return true
        }

        val mainBooleanField = resolveMainBooleanField(cls)
        if (mainBooleanField != null) {
            return try {
                if (mainBooleanField.type == Boolean::class.javaPrimitiveType) {
                    mainBooleanField.setBoolean(item, enabled)
                } else {
                    mainBooleanField.set(item, enabled)
                }
                true
            } catch (_: Throwable) {
                false
            }
        }

        return false
    }

    private fun writeIntField(field: Field, target: Any, value: Int): Boolean {
        return try {
            if (field.type == Int::class.javaPrimitiveType) {
                field.setInt(target, value)
            } else {
                field.set(target, value)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveTabType(item: Any): Int {
        val field = resolveTypeField(item.javaClass) ?: return -1
        return try {
            when (val value = field.get(item)) {
                is Number -> value.toInt()
                else -> -1
            }
        } catch (_: Throwable) {
            -1
        }
    }

    private fun resolveTabCode(item: Any): String? {
        val cls = item.javaClass
        val codeField = resolveCodeField(cls)
        if (codeField != null) {
            val code = try {
                normalizeCode(codeField.get(item) as? String)
            } catch (_: Throwable) {
                null
            }
            if (code != null) return code
        }
        return null
    }

    private fun resolveTabUrl(item: Any): String? {
        val cls = item.javaClass
        val urlField = resolveUrlField(cls)
        if (urlField != null) {
            val value = try {
                normalizeNonBlank(urlField.get(item) as? String)
            } catch (_: Throwable) {
                null
            }
            if (value != null) return value
        }
        return null
    }

    private fun resolveTabName(item: Any): String? {
        val cls = item.javaClass
        val nameField = resolveNameField(cls)
        if (nameField != null) {
            val value = try {
                normalizeNonBlank(nameField.get(item) as? String)
            } catch (_: Throwable) {
                null
            }
            if (value != null) return value
        }
        return null
    }

    private fun resolveMutableListField(owner: Any?): Any? {
        if (owner == null) return null
        val field = sRuntimeTargets?.listField ?: return null
        return try {
            field.get(owner)
        } catch (t: Throwable) {
            XposedCompat.logD { "HomeTabHook: ${t.message}" }
            null
        }
    }

    private fun itemTargets(cls: Class<*>): HomeTabItemTargets? =
        sRuntimeTargets?.item?.takeIf { it.type.isAssignableFrom(cls) }

    private fun resolveTypeField(cls: Class<*>): Field? = itemTargets(cls)?.typeField
    private fun resolveCodeField(cls: Class<*>): Field? = itemTargets(cls)?.codeField
    private fun resolveNameField(cls: Class<*>): Field? = itemTargets(cls)?.nameField
    private fun resolveUrlField(cls: Class<*>): Field? = itemTargets(cls)?.urlField
    private fun resolveMainSetterMethod(cls: Class<*>): Method? = itemTargets(cls)?.mainSetter
    private fun resolveMainIntField(cls: Class<*>): Field? = itemTargets(cls)?.mainIntField
    private fun resolveMainBooleanField(cls: Class<*>): Field? = itemTargets(cls)?.mainBooleanField

    private fun normalizeCode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().lowercase(Locale.ROOT)
    }

    private fun normalizeUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().lowercase(Locale.ROOT)
    }

    private fun normalizeNonBlank(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim()
    }

    private fun isFollowedCode(code: String?): Boolean {
        val normalized = normalizeCode(code) ?: return false
        return normalized == TAB_CODE_FOLLOWED ||
            normalized == TAB_CODE_FOLLOWED_ALT ||
            normalized == TAB_CODE_FOLLOWED_ALT_2
    }

    private fun isFollowedUrl(url: String?): Boolean {
        val normalized = normalizeUrl(url) ?: return false
        return normalized.contains(TAB_URL_FOLLOWED_PATH)
    }

    private fun isUrlLike(value: String): Boolean {
        val normalized = value.trim().lowercase(Locale.ROOT)
        return normalized.startsWith("http://") ||
            normalized.startsWith("https://") ||
            normalized.startsWith("tbopen://")
    }

    private enum class HomeTopTargetTab {
        MATERIAL,
        RECOMMEND,
        LIVE,
        FOLLOWED,
        OTHER,
    }
}
