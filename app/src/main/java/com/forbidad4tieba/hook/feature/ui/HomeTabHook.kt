package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object HomeTabHook {
    /**
     * 集中定义 tab item 字段解析规则。
     * 第一次遇到某个类时一次性解析所有字段，
     * 把缓存未命中次数从 4 次降到 1 次。
     */
    private data class TabItemFieldSchema(
        val typeField: Field? = null,
        val codeField: Field? = null,
        val nameField: Field? = null,
        val urlField: Field? = null,
    )

    private val sListFieldCache = ConcurrentHashMap<Class<*>, Field>()
    private val sSchemaCache = ConcurrentHashMap<Class<*>, TabItemFieldSchema>()
    private val sMainSetterMethodCache = ConcurrentHashMap<Class<*>, Method>()
    private val sMainSetterMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val sMainIntFieldCache = ConcurrentHashMap<Class<*>, Field>()
    private val sMainIntFieldMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val sMainBooleanFieldCache = ConcurrentHashMap<Class<*>, Field>()
    private val sMainBooleanFieldMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val sNoArgCtorCache = ConcurrentHashMap<Class<*>, Constructor<*>>()
    private val sNoArgCtorMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    private val sInstanceFieldsCache = ConcurrentHashMap<Class<*>, Array<Field>>()
    private val sInvalidSelectionLogged = AtomicBoolean(false)
    @Volatile private var sLastTabItemClass: Class<*>? = null

    private const val TAB_TYPE_RECOMMEND = 1
    private const val TAB_TYPE_LIVE = 6
    private const val TAB_TYPE_MATERIAL = 9
    private const val TAB_TYPE_WEB_ACTIVITY = 202

    private const val TAB_CODE_RECOMMEND = "recommend"
    private const val TAB_CODE_LIVE = "live"
    private const val TAB_CODE_MATERIAL = "material"
    private const val TAB_CODE_FOLLOWED = "my_followed"
    private const val TAB_CODE_FOLLOWED_ALT = "myfollowed"
    private const val TAB_CODE_FOLLOWED_ALT_2 = "followed"

    private val TAB_NAME_FOLLOWED = UiText.Settings.HOME_TOP_TAB_FOLLOWED_LABEL
    private val TAB_NAME_MATERIAL = UiText.Settings.HOME_TOP_TAB_MATERIAL_LABEL
    private val TAB_NAME_RECOMMEND = UiText.Settings.HOME_TOP_TAB_RECOMMEND_LABEL
    private val TAB_NAME_LIVE = UiText.Settings.HOME_TOP_TAB_LIVE_LABEL
    private const val TAB_URL_FOLLOWED =
        "https://tieba.baidu.com/mo/q/hybrid-usergrow-base/myFollowed/hybrid" +
            "?customfullscreen=1&nonavigationbar=1&loadingSignal=1&nohead=1&skin=default" +
            "&tbhook_from_home_top_tab=1"
    private const val TAB_URL_FOLLOWED_PATH = "hybrid-usergrow-base/myfollowed/hybrid"

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

    private data class RuntimeTargets(
        val hostClass: String,
        val rebuildMethod: String,
        val listField: String,
        val itemTypeField: String,
        val itemCodeField: String,
        val itemNameField: String,
        val itemUrlField: String,
        val itemMainSetterMethod: String?,
        val itemMainIntField: String?,
        val itemMainBooleanField: String?,
    )

    @Volatile private var sRuntimeTargets: RuntimeTargets? = null

    internal fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val mod = XposedCompat.module ?: return
        val targets = resolveTargets(symbols)
        if (targets == null) {
            XposedCompat.log(
                "[HomeTabHook] SKIP - missing symbols: " +
                    "(homeTabClass/homeTabRebuildMethod/homeTabListField/" +
                    "homeTabItemTypeField/homeTabItemCodeField/homeTabItemNameField/homeTabItemUrlField)"
            )
            return
        }
        sRuntimeTargets = targets
        sSchemaCache.clear()
        sMainSetterMethodCache.clear()
        sMainSetterMethodMissCache.clear()
        sMainIntFieldCache.clear()
        sMainIntFieldMissCache.clear()
        sMainBooleanFieldCache.clear()
        sMainBooleanFieldMissCache.clear()
        try {
            val hostClass = XposedCompat.findClassOrNull(targets.hostClass, cl)
            if (hostClass == null) {
                XposedCompat.log("[HomeTabHook] class NOT FOUND: ${targets.hostClass}")
                return
            }
            precacheMutableListField(hostClass, targets.listField)
            val method = XposedCompat.findMethodOrNull(hostClass, targets.rebuildMethod)
            if (method == null) {
                XposedCompat.log("[HomeTabHook] method NOT FOUND: ${targets.hostClass}.${targets.rebuildMethod}")
                return
            }
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.isHomeTopTabsCustomEnabled) {
                    val selection = resolveCurrentSelection()
                    @Suppress("UNCHECKED_CAST")
                    val list = resolveMutableListField(chain.thisObject, targets.listField) as? MutableList<Any?>
                    if (list != null) {
                        val templateContext = resolveFollowedTemplateContext(list)
                        val sizeBefore = list.size
                        val filteredCount = filterTabsInPlace(list, selection)
                        val followedSync = syncFollowedTabState(list, selection, templateContext)
                        if (filteredCount > 0 || followedSync.changed || followedSync.failed) {
                            XposedCompat.logD {
                                "[HomeTabHook] > top tabs rebuilt: $sizeBefore -> ${list.size}, " +
                                    "removed=$filteredCount, followedAdded=${followedSync.added}, " +
                                    "followedRemoved=${followedSync.removed}, followedFailed=${followedSync.failed}, " +
                                    "selection=(material=${selection.materialEnabled}, " +
                                    "recommend=${selection.recommendEnabled}, live=${selection.liveEnabled}, " +
                                    "followed=${selection.followedEnabled})"
                            }
                        }
                    }
                }
                result
            }
            XposedCompat.log("[HomeTabHook] hook INSTALLED: ${targets.hostClass}.${targets.rebuildMethod}")
        } catch (t: Throwable) {
            XposedCompat.log("[HomeTabHook] FAILED (${targets.hostClass}.${targets.rebuildMethod}): ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolveTargets(symbols: HookSymbols): RuntimeTargets? {
        val hostClass = symbols.homeTabClass?.takeIf { it.isNotBlank() } ?: return null
        val rebuildMethod = symbols.homeTabRebuildMethod?.takeIf { it.isNotBlank() } ?: return null
        val listField = symbols.homeTabListField?.takeIf { it.isNotBlank() } ?: return null
        val itemTypeField = symbols.homeTabItemTypeField?.takeIf { it.isNotBlank() } ?: return null
        val itemCodeField = symbols.homeTabItemCodeField?.takeIf { it.isNotBlank() } ?: return null
        val itemNameField = symbols.homeTabItemNameField?.takeIf { it.isNotBlank() } ?: return null
        val itemUrlField = symbols.homeTabItemUrlField?.takeIf { it.isNotBlank() } ?: return null
        val itemMainSetterMethod = symbols.homeTabItemMainSetterMethod?.takeIf { it.isNotBlank() }
        val itemMainIntField = symbols.homeTabItemMainIntField?.takeIf { it.isNotBlank() }
        val itemMainBooleanField = symbols.homeTabItemMainBooleanField?.takeIf { it.isNotBlank() }
        return RuntimeTargets(
            hostClass = hostClass,
            rebuildMethod = rebuildMethod,
            listField = listField,
            itemTypeField = itemTypeField,
            itemCodeField = itemCodeField,
            itemNameField = itemNameField,
            itemUrlField = itemUrlField,
            itemMainSetterMethod = itemMainSetterMethod,
            itemMainIntField = itemMainIntField,
            itemMainBooleanField = itemMainBooleanField,
        )
    }

    private fun precacheMutableListField(cls: Class<*>, preferredFieldName: String) {
        val field = runCatching { cls.getDeclaredField(preferredFieldName) }.getOrNull()
        if (field != null && List::class.java.isAssignableFrom(field.type)) {
            runCatching {
                field.isAccessible = true
                sListFieldCache[cls] = field
            }
        }
    }

    private fun resolveCurrentSelection(): ConfigManager.HomeTopTabSelection {
        val raw = ConfigManager.HomeTopTabSelection(
            materialEnabled = ConfigManager.isHomeTopTabMaterialEnabled,
            recommendEnabled = ConfigManager.isHomeTopTabRecommendEnabled,
            liveEnabled = ConfigManager.isHomeTopTabLiveEnabled,
            followedEnabled = ConfigManager.isHomeTopTabFollowedEnabled,
        )
        val normalized = ConfigManager.normalizeHomeTopTabSelection(raw)
        if (raw != normalized) {
            if (sInvalidSelectionLogged.compareAndSet(false, true)) {
                XposedCompat.log(
                    "[HomeTabHook] invalid home-top-tab selection detected; " +
                        "fallback to recommend-only"
                )
            }
        } else {
            sInvalidSelectionLogged.set(false)
        }
        return normalized
    }

    private fun filterTabsInPlace(
        list: MutableList<Any?>,
        selection: ConfigManager.HomeTopTabSelection,
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
        selection: ConfigManager.HomeTopTabSelection,
    ): Boolean {
        return when (resolveTargetTab(tabType, tabCode, tabUrl)) {
            HomeTopTargetTab.MATERIAL -> !selection.materialEnabled
            HomeTopTargetTab.RECOMMEND -> !selection.recommendEnabled
            HomeTopTargetTab.LIVE -> !selection.liveEnabled
            HomeTopTargetTab.FOLLOWED -> !selection.followedEnabled
            HomeTopTargetTab.OTHER -> false
        }
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
        selection: ConfigManager.HomeTopTabSelection,
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
            return FollowedSyncResult(
                added = false,
                removed = removed,
                failed = false,
            )
        }

        val newFollowedTab = createFollowedTabItem(templateContext)
        if (newFollowedTab == null) {
            XposedCompat.logW("[HomeTabHook] followed tab inject failed: no writable tab template/class found")
            return FollowedSyncResult(
                added = false,
                removed = removed,
                failed = true,
            )
        }

        val insertIndex = resolveFollowedInsertIndex(list).coerceIn(0, list.size)
        list.add(insertIndex, newFollowedTab)
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
        val cached = sNoArgCtorCache[cls]
        if (cached != null) {
            return newInstance(cached)
        }
        if (sNoArgCtorMissCache.contains(cls)) return null

        val ctor = try {
            cls.getDeclaredConstructor().apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
        if (ctor == null) {
            sNoArgCtorMissCache.add(cls)
            return null
        }
        sNoArgCtorCache[cls] = ctor
        return newInstance(ctor)
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
        copyInstanceFields(template, clone)
        return clone
    }

    private fun copyInstanceFields(source: Any, target: Any) {
        for (field in resolveInstanceFields(source.javaClass)) {
            try {
                field.set(target, field.get(source))
            } catch (_: Throwable) {
                // 跳过不可变或类型不兼容的字段。
            }
        }
    }

    private fun resolveInstanceFields(cls: Class<*>): Array<Field> {
        return sInstanceFieldsCache.getOrPut(cls) {
            val fields = ArrayList<Field>(16)
            var current: Class<*>? = cls
            while (current != null && current != Any::class.java) {
                for (field in current.declaredFields) {
                    if (Modifier.isStatic(field.modifiers)) continue
                    try {
                        field.isAccessible = true
                        fields.add(field)
                    } catch (_: Throwable) {
                        // 跳过不可访问的字段。
                    }
                }
                current = current.superclass
            }
            fields.toTypedArray()
        }
    }

    private fun applyFollowedTabIdentity(tabItem: Any): Boolean {
        val typeApplied = setTabType(tabItem, TAB_TYPE_WEB_ACTIVITY)
        val codeApplied = setTabCode(tabItem, TAB_CODE_FOLLOWED)
        val nameApplied = setTabName(tabItem, TAB_NAME_FOLLOWED)
        val urlApplied = setTabUrl(tabItem, TAB_URL_FOLLOWED)
        setTabMain(tabItem, true)

        if (!typeApplied || !codeApplied || !nameApplied || !urlApplied) {
            XposedCompat.logW(
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
                // 跳过并走兜底路径。
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

    private fun resolveMutableListField(owner: Any?, preferredFieldName: String): Any? {
        if (owner == null) return null
        val cls = owner.javaClass
        val cached = sListFieldCache[cls]
        if (cached != null) {
            return try {
                cached.get(owner)
            } catch (_: Throwable) {
                null
            }
        }

        try {
            val field = cls.getDeclaredField(preferredFieldName)
            if (!List::class.java.isAssignableFrom(field.type)) return null
            field.isAccessible = true
            sListFieldCache[cls] = field
            return field.get(owner)
        } catch (t: Throwable) {
            XposedCompat.logD { "HomeTabHook: ${t.message}" }
            return null
        }
    }

    private fun resolveSchema(cls: Class<*>): TabItemFieldSchema {
        sSchemaCache[cls]?.let { return it }
        val targets = sRuntimeTargets
        val typeField = resolveFieldByName(
            cls = cls,
            fieldName = targets?.itemTypeField,
        ) { it == Int::class.javaPrimitiveType || it == Int::class.javaObjectType }
        val codeField = resolveFieldByName(
            cls = cls,
            fieldName = targets?.itemCodeField,
        ) { it == String::class.java }
        val nameField = resolveFieldByName(
            cls = cls,
            fieldName = targets?.itemNameField,
        ) { it == String::class.java }
        val urlField = resolveFieldByName(
            cls = cls,
            fieldName = targets?.itemUrlField,
        ) { it == String::class.java }
        val schema = TabItemFieldSchema(
            typeField = typeField,
            codeField = codeField,
            nameField = nameField,
            urlField = urlField,
        )
        sSchemaCache[cls] = schema
        return schema
    }

    private fun resolveFieldByName(
        cls: Class<*>,
        fieldName: String?,
        typeCheck: (Class<*>) -> Boolean,
    ): Field? {
        if (fieldName.isNullOrBlank()) return null
        val field = runCatching { cls.getDeclaredField(fieldName) }.getOrNull() ?: return null
        if (!typeCheck(field.type)) return null
        field.isAccessible = true
        return field
    }

    private fun resolveTypeField(cls: Class<*>): Field? {
        return resolveSchema(cls).typeField
    }

    private fun resolveCodeField(cls: Class<*>): Field? {
        return resolveSchema(cls).codeField
    }

    private fun resolveNameField(cls: Class<*>): Field? {
        return resolveSchema(cls).nameField
    }

    private fun resolveUrlField(cls: Class<*>): Field? {
        return resolveSchema(cls).urlField
    }

    private fun resolveMainSetterMethod(cls: Class<*>): Method? {
        val cached = sMainSetterMethodCache[cls]
        if (cached != null) return cached
        if (sMainSetterMethodMissCache.contains(cls)) return null

        val methodName = sRuntimeTargets?.itemMainSetterMethod
        if (!methodName.isNullOrBlank()) {
            val method = cls.declaredMethods.firstOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.size == 1 &&
                    (candidate.parameterTypes[0] == Boolean::class.javaPrimitiveType ||
                        candidate.parameterTypes[0] == Boolean::class.java)
            }
            if (method != null) {
                method.isAccessible = true
                sMainSetterMethodCache[cls] = method
                return method
            }
        }

        sMainSetterMethodMissCache.add(cls)
        return null
    }

    private fun resolveMainIntField(cls: Class<*>): Field? {
        val cached = sMainIntFieldCache[cls]
        if (cached != null) return cached
        if (sMainIntFieldMissCache.contains(cls)) return null

        val fieldName = sRuntimeTargets?.itemMainIntField
        if (!fieldName.isNullOrBlank()) {
            val field = runCatching { cls.getDeclaredField(fieldName) }.getOrNull()
            if (field != null && (field.type == Int::class.javaPrimitiveType || field.type == Int::class.javaObjectType)) {
                field.isAccessible = true
                sMainIntFieldCache[cls] = field
                return field
            }
        }

        sMainIntFieldMissCache.add(cls)
        return null
    }

    private fun resolveMainBooleanField(cls: Class<*>): Field? {
        val cached = sMainBooleanFieldCache[cls]
        if (cached != null) return cached
        if (sMainBooleanFieldMissCache.contains(cls)) return null

        val fieldName = sRuntimeTargets?.itemMainBooleanField
        if (!fieldName.isNullOrBlank()) {
            val field = runCatching { cls.getDeclaredField(fieldName) }.getOrNull()
            if (field != null &&
                (field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.javaObjectType)
            ) {
                field.isAccessible = true
                sMainBooleanFieldCache[cls] = field
                return field
            }
        }

        sMainBooleanFieldMissCache.add(cls)
        return null
    }

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


