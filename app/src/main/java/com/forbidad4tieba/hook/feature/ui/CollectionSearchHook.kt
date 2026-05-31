package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.symbol.model.CollectionSearchSymbols
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.NavBarSearchButton
import com.forbidad4tieba.hook.utils.ReflectionUtils
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.utils.ClearableInputRow
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * 鏀惰棌椤垫悳绱細
 * - 娣诲姞鐙珛鐨勫彸涓婅鎼滅储鎸夐挳銆? * - 鍦ㄦ湰鍦拌繃婊?adapter 鍒楄〃銆? * - 淇濇寔瀹屾暣 model 鏁版嵁涓嶅彉锛岃繃婊ゅ悗閲嶆槧灏勭偣鍑讳笅鏍囥€? * - 娣锋穯鎴愬憳鐢辨壂鎻忕鍙疯В鏋愪竴娆★紝杩愯鏈熶綔涓哄浐瀹氱洰鏍囦娇鐢ㄣ€? */
object CollectionSearchHook {
    private const val FULL_CACHE_MAX_ACCOUNTS = 3
    private const val PAGE_SIZE = 20

    private data class FilterState(
        var query: String = "",
        var active: Boolean = false,
        var indexMap: IntArray = IntArray(0),
        var applying: Boolean = false,
        var fetchingAll: Boolean = false,
        var syncingFirstPage: Boolean = false,
        var fetchToken: Int = 0,
        var fullDataReady: Boolean = false,
        var diskRestoreTried: Boolean = false,
        var fullLoadRequested: Boolean = false,
        var syncFooterVisible: Boolean = false,
    ) {
        fun shouldRestoreFullDataOnResume(): Boolean {
            return active || query.isNotBlank() || fullLoadRequested || syncFooterVisible
        }
    }

    private data class ActivityState(
        var buttonView: View? = null,
    )

    private data class FullDataCache(
        val updatedAtMs: Long,
        val items: ArrayList<Any>,
        val fullReady: Boolean,
    )

    private data class LoadAllResult(
        val items: List<Any>,
        val rawPages: List<String>,
        val complete: Boolean,
    )

    private data class FirstPageSyncResult(
        val items: List<Any>,
        val rawPage: String,
    )

    private data class MarkAccessor(
        val titleMethod: Method?,
        val authorMethod: Method?,
        val forumMethod: Method?,
        val threadIdMethod: Method?,
        val postIdMethod: Method?,
        val idMethod: Method?,
    )

    private data class AdapterFooterAccess(
        val showFooterMethod: Method?,
        val showFooterShowArg: Boolean,
        val showFooterHideArg: Boolean,
        val loadingMethod: Method?,
        val hasMoreMethod: Method?,
    )

    private data class NetworkBridge(
        val netCtor: java.lang.reflect.Constructor<*>,
        val addPostDataMethod: Method,
        val postNetDataMethod: Method,
        val serverAddressField: Field,
        val markGetStoreField: Field,
        val getCurrentAccountMethod: Method,
    )

    private val sFragmentStates = Collections.synchronizedMap(WeakHashMap<Any, FilterState>())
    private val sActivityStates = Collections.synchronizedMap(WeakHashMap<Activity, ActivityState>())
    private val sPresenterOwners = Collections.synchronizedMap(WeakHashMap<Any, Any>())
    private val sHookedPresenterClasses =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()))
    private val sPresenterListSetterCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Method>())
    private val sModelListGetterCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Method>())
    private val sModelListFieldCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Field>())
    private val sModelParseMethodCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Method>())
    private val sFragmentDisplayListFieldCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Field>())
    private val sCurrentAccountMethodCache = Collections.synchronizedMap(WeakHashMap<ClassLoader, Method>())
    private val sAdapterFooterAccessCache = Collections.synchronizedMap(WeakHashMap<Class<*>, AdapterFooterAccess>())
    private val sNetworkBridgeCache = Collections.synchronizedMap(WeakHashMap<ClassLoader, NetworkBridge>())
    private val sFullDataCacheByAccount = Collections.synchronizedMap(LinkedHashMap<String, FullDataCache>())
    private val sMarkAccessorCache = Collections.synchronizedMap(WeakHashMap<Class<*>, MarkAccessor>())
    private val sMarkTextCache = Collections.synchronizedMap(WeakHashMap<Any, String>())
    private val sFieldLookupCache = Collections.synchronizedMap(WeakHashMap<Class<*>, MutableMap<String, Field?>>())
    private val sAdapterOwners = Collections.synchronizedMap(WeakHashMap<Any, Any>())
    private val sHookedAdapterClasses =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()))
    private val sEditModeMethodCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Method>())
    private val sDiskIoExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tbhook-collect-cache-io").apply { isDaemon = true }
    }
    private val sNetExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tbhook-collect-search-net").apply { isDaemon = true }
    }

    private inline fun dbg(message: () -> String) {
        if (ConfigManager.shouldOutputDetailedLogs()) {
            XposedCompat.logD("[CollectionSearchHook][dbg] ${message()}")
        }
    }

    private fun dbg(message: String) {
        if (ConfigManager.shouldOutputDetailedLogs()) {
            XposedCompat.logD("[CollectionSearchHook][dbg] $message")
        }
    }

    private fun methodName(method: Method?): String {
        if (method == null) return "null"
        return "${method.name}(${method.parameterTypes.joinToString(",") { it.simpleName }})"
    }

    @Volatile
    private var sRuntimeTargets: CollectionSearchSymbols? = null

    internal fun hook(symbols: CollectionSearchSymbols) {
        val mod = XposedCompat.module ?: return
        sRuntimeTargets = symbols

        try {
            installActivityHooks(mod, symbols.activityClass)
            installFragmentHooks(mod, symbols.fragmentClass)
            XposedCompat.log("[CollectionSearchHook] hook INSTALLED")
        } catch (t: Throwable) {
            XposedCompat.log("[CollectionSearchHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installActivityHooks(mod: io.github.libxposed.api.XposedModule, activityClass: Class<*>) {
        XposedCompat.findMethodOrNull(activityClass, "onCreate", Bundle::class.java)?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                ensureSearchButton(chain.thisObject as? Activity)
                result
            }
        }

        XposedCompat.findMethodOrNull(activityClass, "onResume")?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                ensureSearchButton(activity)
                updateSearchButtonVisual(activity)
                result
            }
        }

        XposedCompat.findMethodOrNull(activityClass, "onDestroy")?.let { method ->
            mod.hook(method).intercept { chain ->
                val activity = chain.thisObject as? Activity
                val result = chain.proceed()
                if (activity != null) sActivityStates.remove(activity)
                result
            }
        }
    }

    private fun installFragmentHooks(mod: io.github.libxposed.api.XposedModule, fragmentClass: Class<*>) {
        XposedCompat.findMethodOrNull(
            fragmentClass,
            "onCreateView",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Bundle::class.java,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject ?: return@intercept result
                bindFragmentPresenter(fragment)
                ensureFragmentState(fragment)
                updateSearchButtonVisual(findHostActivity(fragment))
                result
            }
        }

        XposedCompat.findMethodOrNull(fragmentClass, "onResume")?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject ?: return@intercept result
                val state = ensureFragmentState(fragment)
                dbg {
                    "onResume fullReady=${state.fullDataReady} fetchingAll=${state.fetchingAll} " +
                        "requested=${state.fullLoadRequested} active=${state.active} query='${state.query}'"
                }
                if (!state.fullDataReady && state.shouldRestoreFullDataOnResume()) {
                    val hitMemory = restoreFullDataFromCache(fragment, state)
                    if (!hitMemory && !state.diskRestoreTried) {
                        state.diskRestoreTried = true
                        restoreFullDataFromDisk(fragment, state)
                    }
                }
                result
            }
        }

        XposedCompat.findMethodOrNull(
            fragmentClass,
            "onActivityResult",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Intent::class.java,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                val fragment = chain.thisObject
                val requestCode = chain.args.getOrNull(0) as? Int
                val resultCode = chain.args.getOrNull(1) as? Int
                val result = chain.proceed()
                if (fragment != null && requestCode == 17001 && (resultCode == 1 || resultCode == Activity.RESULT_OK)) {
                    syncFirstPageEveryEntry(fragment, force = true)
                }
                result
            }
        }

        XposedCompat.findMethodOrNull(fragmentClass, "onDestroy")?.let { method ->
            mod.hook(method).intercept { chain ->
                val fragment = chain.thisObject
                val result = chain.proceed()
                if (fragment != null) clearFragmentState(fragment)
                result
            }
        }

        XposedCompat.findMethodOrNull(
            fragmentClass,
            "onItemClick",
            android.widget.AdapterView::class.java,
            View::class.java,
            Int::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                val fragment = chain.thisObject
                val index = (chain.args.getOrNull(2) as? Int) ?: return@intercept chain.proceed()
                if (fragment != null && isSyncFooterClick(fragment, index)) {
                    dbg { "onItemClick sync footer clicked index=$index" }
                    triggerManualFullSync(fragment, userVisible = true)
                    return@intercept null
                }
                if (fragment == null || !isFilterActive(fragment)) return@intercept chain.proceed()
                val mapped = remapIndex(fragment, index)
                if (mapped == index) return@intercept chain.proceed()
                val args = chain.args.toMutableList()
                args[2] = mapped
                chain.proceed(args.toTypedArray())
            }
        }

        XposedCompat.findMethodOrNull(fragmentClass, "onClick", View::class.java)?.let { method ->
            mod.hook(method).intercept { chain ->
                val fragment = chain.thisObject
                if (fragment == null || !isFilterActive(fragment)) return@intercept chain.proceed()
                val view = chain.args.getOrNull(0) as? View ?: return@intercept chain.proceed()
                val rawTag = view.tag as? Int ?: return@intercept chain.proceed()
                val mapped = remapIndex(fragment, rawTag)
                if (mapped == rawTag) return@intercept chain.proceed()
                view.tag = mapped
                try {
                    chain.proceed()
                } finally {
                    view.tag = rawTag
                }
            }
        }
    }

    private fun bindFragmentPresenter(fragment: Any) {
        val presenter = resolvePresenter(fragment) ?: return
        sPresenterOwners[presenter] = fragment
        resolvePresenterAdapter(presenter)?.let { adapter ->
            sAdapterOwners[adapter] = fragment
            installAdapterFooterHook(adapter.javaClass)
        }
        installPresenterUpdateHooks(presenter.javaClass)
    }

    private fun installPresenterUpdateHooks(presenterClass: Class<*>) {
        if (!sHookedPresenterClasses.add(presenterClass)) return
        val mod = XposedCompat.module ?: return
        val method = resolvePresenterListSetter(presenterClass)
        if (method == null) {
            XposedCompat.logW("[CollectionSearchHook] presenter update method NOT FOUND: ${presenterClass.name}")
            return
        }
        method.isAccessible = true
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            val presenter = chain.thisObject
            val fragment = if (presenter != null) sPresenterOwners[presenter] else null
            if (fragment != null) {
                val adapter = resolvePresenterAdapter(presenter)
                if (adapter != null) {
                    sAdapterOwners[adapter] = fragment
                    installAdapterFooterHook(adapter.javaClass)
                }
                reapplyFilterIfNeeded(fragment)
            }
            result
        }
    }

    private fun ensureFragmentState(fragment: Any): FilterState {
        synchronized(sFragmentStates) {
            return sFragmentStates[fragment] ?: FilterState().also { sFragmentStates[fragment] = it }
        }
    }

    private fun clearFragmentState(fragment: Any) {
        sFragmentStates[fragment]?.fetchToken = (sFragmentStates[fragment]?.fetchToken ?: 0) + 1
        sFragmentStates.remove(fragment)
        synchronized(sPresenterOwners) {
            val iterator = sPresenterOwners.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value == fragment) iterator.remove()
            }
        }
        updateSearchButtonVisual(findHostActivity(fragment))
    }

    private fun isFilterActive(fragment: Any): Boolean = sFragmentStates[fragment]?.active == true

    private fun remapIndex(fragment: Any, index: Int): Int {
        val state = sFragmentStates[fragment] ?: return index
        if (!state.active || index < 0) return index
        return if (index < state.indexMap.size) state.indexMap[index] else index
    }

    private fun reapplyFilterIfNeeded(fragment: Any) {
        val state = sFragmentStates[fragment] ?: return
        if (!state.active || state.applying) return
        applyFilter(fragment, state.query, fromUser = false)
    }

    private fun hasFullCache(fragment: Any): Boolean {
        getFullDataCache(fragment)?.let { cache ->
            val trusted = isMemoryCacheTrustedFull(fragment, cache)
            dbg {
                "hasFullCache memory fullReady=${cache.fullReady} size=${cache.items.size} " +
                    "trusted=$trusted"
            }
            if (trusted) return true
        }
        val context = resolveAppContext(fragment) ?: return false
        val accountKey = resolveCurrentAccount(fragment.javaClass.classLoader) ?: return false
        val snapshot = CollectionSearchCacheStore.read(context, accountKey) ?: return false
        val trusted = isTrustedFullSnapshot(snapshot)
        dbg {
            "hasFullCache disk fullReady=${snapshot.fullReady} pages=${snapshot.rawPages.size} " +
                "trusted=$trusted"
        }
        return trusted
    }

    private fun isMemoryCacheTrustedFull(fragment: Any, cache: FullDataCache): Boolean {
        if (!cache.fullReady) return false
        if (cache.items.size > PAGE_SIZE) return true
        val context = resolveAppContext(fragment) ?: return false
        val accountKey = resolveCurrentAccount(fragment.javaClass.classLoader) ?: return false
        val snapshot = CollectionSearchCacheStore.read(context, accountKey) ?: return false
        if (!isTrustedFullSnapshot(snapshot)) return false
        return if (cache.items.isEmpty()) {
            estimateSnapshotUniqueCount(snapshot.rawPages) == 0
        } else {
            true
        }
    }

    private fun getTrustedFullCacheItems(fragment: Any): List<Any>? {
        val cache = getFullDataCache(fragment) ?: return null
        if (!isMemoryCacheTrustedFull(fragment, cache)) return null
        return cache.items
    }

    private fun isTrustedFullSnapshot(snapshot: CollectionSearchCacheStore.Snapshot): Boolean {
        if (snapshot.dirty) return false
        if (!snapshot.fullReady) return false
        if (snapshot.rawPages.isEmpty()) return false
        val pageSizes = snapshot.rawPages.map(::extractStoreThreadSize).filter { it >= 0 }
        if (pageSizes.isEmpty()) return false
        val firstSize = pageSizes.firstOrNull() ?: return false
        if (firstSize == 0) {
            val allEmpty = pageSizes.all { it == 0 }
            return allEmpty && estimateSnapshotUniqueCount(snapshot.rawPages) == 0
        }
        if (pageSizes.any { it in 1 until PAGE_SIZE }) return true
        val uniqueEstimate = estimateSnapshotUniqueCount(snapshot.rawPages)
        return firstSize >= PAGE_SIZE && uniqueEstimate > PAGE_SIZE
    }

    private fun extractStoreThreadSize(raw: String): Int {
        return runCatching {
            JSONObject(raw).optJSONArray("store_thread")?.length() ?: -1
        }.getOrDefault(-1)
    }

    private fun estimateSnapshotUniqueCount(rawPages: List<String>): Int {
        val keys = HashSet<String>(256)
        rawPages.forEach { raw ->
            runCatching {
                val arr = JSONObject(raw).optJSONArray("store_thread") ?: return@runCatching
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val tid = item.optString("thread_id")
                    val pid = item.optString("post_id")
                    val key = when {
                        id.isNotBlank() -> "id:$id"
                        tid.isNotBlank() || pid.isNotBlank() -> "tp:${tid}_${pid}"
                        else -> ""
                    }
                    if (key.isNotBlank()) keys.add(key)
                }
            }
        }
        return keys.size
    }

    private fun isSyncFooterClick(fragment: Any, index: Int): Boolean {
        val state = sFragmentStates[fragment] ?: return false
        if (!state.active || !state.syncFooterVisible || index < 0) return false
        val isFooter = index >= state.indexMap.size
        if (isFooter) {
            dbg { "isSyncFooterClick=true index=$index filteredSize=${state.indexMap.size}" }
        }
        return isFooter
    }

    private fun syncFirstPageEveryEntry(fragment: Any, force: Boolean = false) {
        val state = ensureFragmentState(fragment)
        if (state.syncingFirstPage && !force) return
        if (!force && state.fetchingAll) return
        state.syncingFirstPage = true
        dbg { "syncFirstPageEveryEntry start force=$force fetchingAll=${state.fetchingAll}" }
        sNetExecutor.execute {
            val result = fetchFirstPage(fragment)
            val host = findHostActivity(fragment)
            host?.runOnUiThread {
                val current = sFragmentStates[fragment] ?: return@runOnUiThread
                current.syncingFirstPage = false
                val firstPage = result?.items.orEmpty()
                dbg { "syncFirstPageEveryEntry done size=${firstPage.size} rawLen=${result?.rawPage?.length ?: 0}" }
                if (result != null && result.rawPage.isNotBlank()) {
                    mergeFirstPageIntoCache(fragment, firstPage)
                    persistFirstPageSnapshot(fragment, result.rawPage)
                    if (current.active) {
                        applyFilter(fragment, current.query, fromUser = false)
                    } else if (current.fullDataReady) {
                        applyAdapterList(fragment, getFullModelList(fragment))
                    }
                }
            }
            if (host == null) {
                sFragmentStates[fragment]?.syncingFirstPage = false
            }
        }
    }

    private fun fetchFirstPage(fragment: Any): FirstPageSyncResult? {
        val model = resolveModel(fragment) ?: return null
        val parseMethod = resolveModelParseMethod(model.javaClass) ?: return null
        val bridge = resolveNetworkBridge(model.javaClass.classLoader) ?: return null

        val server = runCatching { bridge.serverAddressField.get(null)?.toString().orEmpty() }.getOrDefault("")
        val path = runCatching { bridge.markGetStoreField.get(null)?.toString().orEmpty() }.getOrDefault("")
        val userId = runCatching { bridge.getCurrentAccountMethod.invoke(null)?.toString().orEmpty() }.getOrDefault("")
        if (server.isBlank() || path.isBlank() || userId.isBlank()) {
            dbg("fetchFirstPage abort invalid params server/path/userId")
            return null
        }

        val raw = postCollectionPage(
            bridge = bridge,
            url = server + path,
            userId = userId,
            offset = 0,
            rn = PAGE_SIZE,
        ) ?: return null
        val page = parseCollectionPage(model, parseMethod, raw)
        if (page.isEmpty()) {
            val size = extractStoreThreadSize(raw)
            if (size == 0) {
                dbg("fetchFirstPage parsed empty but trusted by store_thread=0")
                return FirstPageSyncResult(items = emptyList(), rawPage = raw)
            }
            dbg("fetchFirstPage parsed empty page")
            return null
        }
        return FirstPageSyncResult(items = page, rawPage = raw)
    }

    private fun mergeFirstPageIntoCache(fragment: Any, firstPage: List<Any>) {
        val state = ensureFragmentState(fragment)
        if (!state.fullDataReady) {
            replaceModelDataset(fragment, firstPage)
            val fullReadyNow = firstPage.isEmpty()
            putFullDataCache(fragment, firstPage, fullReady = fullReadyNow)
            state.fullDataReady = fullReadyNow
            return
        }

        if (firstPage.isEmpty()) {
            dbg { "mergeFirstPageIntoCache clear all by empty first page" }
            replaceModelDataset(fragment, emptyList())
            putFullDataCache(fragment, emptyList(), fullReady = true)
            state.fullDataReady = true
            return
        }

        val trustedCache = getTrustedFullCacheItems(fragment)
        val fullList = if (trustedCache != null && trustedCache.size >= firstPage.size) {
            trustedCache
        } else {
            getFullModelList(fragment)
        }
        dbg {
            "mergeFirstPageIntoCache first=${firstPage.size} base=${fullList.size} " +
                "trustedCache=${trustedCache?.size ?: 0} fullReady=${state.fullDataReady}"
        }
        if (fullList.isEmpty()) {
            replaceModelDataset(fragment, firstPage)
            putFullDataCache(fragment, firstPage, fullReady = state.fullDataReady)
            state.fullDataReady = true
            return
        }

        val firstPageKeys = HashSet<String>(firstPage.size)
        firstPage.forEach { firstPageKeys.add(resolveMarkStableKey(it)) }
        val oldTopKeys = HashSet<String>(PAGE_SIZE)
        fullList.take(PAGE_SIZE).forEach { oldTopKeys.add(resolveMarkStableKey(it)) }
        val removedTopKeys = HashSet<String>(oldTopKeys)
        removedTopKeys.removeAll(firstPageKeys)
        val canConfirmRemoval = firstPage.size < PAGE_SIZE

        val merged = ArrayList<Any>(fullList.size + PAGE_SIZE)
        merged.addAll(firstPage)
        fullList.forEach { item ->
            val key = resolveMarkStableKey(item)
            if (firstPageKeys.contains(key)) return@forEach
            if (canConfirmRemoval && removedTopKeys.contains(key)) return@forEach
            merged.add(item)
        }

        replaceModelDataset(fragment, merged)
        putFullDataCache(fragment, merged, fullReady = state.fullDataReady)
        state.fullDataReady = true
    }

    private fun triggerManualFullSync(fragment: Any, userVisible: Boolean) {
        val state = ensureFragmentState(fragment)
        if (state.fetchingAll) {
            if (userVisible) showToast(findHostActivity(fragment), UiText.CollectionSearch.TOAST_SYNC_IN_PROGRESS)
            dbg { "triggerManualFullSync ignored: fetchingAll=true" }
            return
        }
        state.fullLoadRequested = true
        dbg { "triggerManualFullSync start userVisible=$userVisible" }
        startFetchAllCollections(fragment, userVisible = userVisible)
    }

    private fun startFetchAllCollections(fragment: Any, userVisible: Boolean = true) {
        val state = ensureFragmentState(fragment)
        if (state.fetchingAll) return
        state.fetchingAll = true
        val token = state.fetchToken + 1
        state.fetchToken = token
        dbg { "startFetchAllCollections token=$token userVisible=$userVisible" }

        sNetExecutor.execute {
            val allResult = loadAllCollections(fragment, token)
            persistFullSnapshot(fragment, allResult)
            val host = findHostActivity(fragment)
            if (host == null) {
                val current = sFragmentStates[fragment] ?: return@execute
                if (current.fetchToken != token) return@execute
                current.fetchingAll = false
                return@execute
            }
            host.runOnUiThread {
                val current = sFragmentStates[fragment] ?: return@runOnUiThread
                if (current.fetchToken != token) return@runOnUiThread
                current.fetchingAll = false
                val allItems = allResult?.items.orEmpty()
                dbg {
                    "startFetchAllCollections finish token=$token items=${allItems.size} " +
                        "complete=${allResult?.complete} pages=${allResult?.rawPages?.size ?: 0}"
                }
                if (allResult != null) {
                    val complete = allResult.complete
                    current.fullDataReady = complete
                    replaceModelDataset(fragment, allItems)
                    putFullDataCache(fragment, allItems, fullReady = complete)
                    if (current.active) {
                        updateSyncActionFooter(fragment, true)
                    } else {
                        suppressLoadingFooter(fragment)
                    }
                    if (current.active) {
                        applyFilter(fragment, current.query, fromUser = false)
                    }
                    if (userVisible) {
                        if (complete) {
                            showToast(host, UiText.CollectionSearch.toastLoadedAllFavorites(allItems.size))
                        } else {
                            showToast(host, UiText.CollectionSearch.TOAST_SYNC_PARTIAL)
                        }
                    }
                } else {
                    current.fullLoadRequested = false
                    if (userVisible) showToast(host, UiText.CollectionSearch.TOAST_SYNC_FAILED)
                }
            }
        }
    }

    private fun loadAllCollections(fragment: Any, token: Int): LoadAllResult? {
        val model = resolveModel(fragment) ?: return null
        val parseMethod = resolveModelParseMethod(model.javaClass) ?: return null
        val bridge = resolveNetworkBridge(model.javaClass.classLoader) ?: return null

        val server = runCatching { bridge.serverAddressField.get(null)?.toString().orEmpty() }.getOrDefault("")
        val path = runCatching { bridge.markGetStoreField.get(null)?.toString().orEmpty() }.getOrDefault("")
        val userId = runCatching { bridge.getCurrentAccountMethod.invoke(null)?.toString().orEmpty() }.getOrDefault("")
        if (server.isBlank() || path.isBlank() || userId.isBlank()) {
            dbg { "loadAllCollections abort invalid params server/path/userId" }
            return null
        }

        val url = server + path
        val rn = PAGE_SIZE
        val maxPages = 500
        var offset = 0
        val dedupe = LinkedHashMap<String, Any>(1024)
        val rawPages = ArrayList<String>(64)
        dbg { "loadAllCollections begin rn=$rn maxPages=$maxPages" }

        repeat(maxPages) { pageIndex ->
            if (!isFetchTokenValid(fragment, token)) return null
            val raw = postCollectionPage(bridge, url, userId, offset, rn)
                ?: run {
                    dbg { "loadAllCollections stop: network null at page=$pageIndex offset=$offset" }
                    return dedupe.values.takeIf { it.isNotEmpty() }?.let { LoadAllResult(ArrayList(it), rawPages, false) }
                }
            if (raw.isNotBlank()) rawPages.add(raw)
            val page = parseCollectionPage(model, parseMethod, raw)
            if (page.isEmpty()) {
                val storeSize = extractStoreThreadSize(raw)
                dbg { "loadAllCollections stop: empty page at page=$pageIndex offset=$offset storeSize=$storeSize" }
                if (storeSize == 0) {
                    return LoadAllResult(ArrayList(dedupe.values), rawPages, true)
                }
                return dedupe.values.takeIf { it.isNotEmpty() }?.let { LoadAllResult(ArrayList(it), rawPages, false) }
            }
            val beforeSize = dedupe.size
            page.forEach { item ->
                dedupe[resolveMarkStableKey(item)] = item
            }
            val appended = dedupe.size - beforeSize
            dbg {
                "loadAllCollections page=$pageIndex offset=$offset size=${page.size} " +
                    "appended=$appended total=${dedupe.size}"
            }
            if (offset > 0 && page.size >= rn && appended <= 0) {
                dbg { "loadAllCollections stop: repeated page detected at page=$pageIndex" }
                return dedupe.values.takeIf { it.isNotEmpty() }?.let { LoadAllResult(ArrayList(it), rawPages, false) }
            }
            offset += page.size
            if (page.size < rn) {
                dbg { "loadAllCollections stop: last page size=${page.size} < rn=$rn" }
                return LoadAllResult(ArrayList(dedupe.values), rawPages, true)
            }
        }
        dbg { "loadAllCollections stop: reached maxPages with total=${dedupe.size}" }
        return dedupe.values.takeIf { it.isNotEmpty() }?.let { LoadAllResult(ArrayList(it), rawPages, false) }
    }

    private fun isFetchTokenValid(fragment: Any, token: Int): Boolean {
        return sFragmentStates[fragment]?.fetchToken == token
    }

    private fun postCollectionPage(
        bridge: NetworkBridge,
        url: String,
        userId: String,
        offset: Int,
        rn: Int,
    ): String? {
        return try {
            val net = bridge.netCtor.newInstance(url)
            bridge.addPostDataMethod.invoke(net, "user_id", userId)
            bridge.addPostDataMethod.invoke(net, "offset", offset.toString())
            bridge.addPostDataMethod.invoke(net, "rn", rn.toString())
            bridge.postNetDataMethod.invoke(net) as? String
        } catch (t: Throwable) {
            dbg { "postCollectionPage failed offset=$offset rn=$rn err=${t.message}" }
            null
        }
    }

    private fun parseCollectionPage(model: Any, parseMethod: Method, raw: String): List<Any> {
        return try {
            val result = parseMethod.invoke(model, raw)
            when (result) {
                is List<*> -> result.filterNotNull()
                is Array<*> -> result.filterNotNull()
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun restoreFullDataFromCache(fragment: Any, state: FilterState): Boolean {
        val cached = getFullDataCache(fragment) ?: return false
        val trustedFull = isMemoryCacheTrustedFull(fragment, cached)
        dbg {
            "restoreFullDataFromCache size=${cached.items.size} fullReady=${cached.fullReady} " +
                "trusted=$trustedFull"
        }
        replaceModelDataset(fragment, cached.items)
        state.fullDataReady = trustedFull
        if (state.fullDataReady) {
            suppressLoadingFooter(fragment)
        }
        return true
    }

    private fun restoreFullDataFromDisk(fragment: Any, state: FilterState): Boolean {
        val context = resolveAppContext(fragment) ?: return false
        val accountKey = resolveCurrentAccount(fragment.javaClass.classLoader) ?: return false
        val snapshot = CollectionSearchCacheStore.read(context, accountKey) ?: return false
        if (!snapshot.fullReady || snapshot.rawPages.isEmpty()) {
            dbg {
                "restoreFullDataFromDisk skip fullReady=${snapshot.fullReady} " +
                    "pages=${snapshot.rawPages.size}"
            }
            return false
        }

        val model = resolveModel(fragment) ?: return false
        val parseMethod = resolveModelParseMethod(model.javaClass) ?: return false
        val dedupe = LinkedHashMap<String, Any>(1024)
        snapshot.rawPages.forEach { raw ->
            val page = parseCollectionPage(model, parseMethod, raw)
            page.forEach { item ->
                dedupe[resolveMarkStableKey(item)] = item
            }
        }
        val trustedFull = isTrustedFullSnapshot(snapshot)
        val restored = dedupe.values.toList()
        if (restored.isEmpty() && !trustedFull) return false
        dbg {
            "restoreFullDataFromDisk restored=${restored.size} pages=${snapshot.rawPages.size} " +
                "trustedFull=$trustedFull"
        }
        replaceModelDataset(fragment, restored)
        putFullDataCache(fragment, restored, fullReady = trustedFull)
        state.fullDataReady = trustedFull
        if (trustedFull) {
            suppressLoadingFooter(fragment)
        }
        return true
    }

    private fun getFullDataCache(fragment: Any): FullDataCache? {
        val accountKey = resolveCurrentAccount(fragment.javaClass.classLoader) ?: return null
        synchronized(sFullDataCacheByAccount) {
            val cache = sFullDataCacheByAccount[accountKey] ?: return null
            if (cache.items.isEmpty() && !cache.fullReady) {
                sFullDataCacheByAccount.remove(accountKey)
                return null
            }
            return FullDataCache(
                updatedAtMs = cache.updatedAtMs,
                items = ArrayList(cache.items),
                fullReady = cache.fullReady,
            )
        }
    }

    private fun putFullDataCache(fragment: Any, items: List<Any>, fullReady: Boolean = true) {
        if (items.isEmpty() && !fullReady) return
        val accountKey = resolveCurrentAccount(fragment.javaClass.classLoader) ?: return
        synchronized(sFullDataCacheByAccount) {
            sFullDataCacheByAccount[accountKey] = FullDataCache(
                updatedAtMs = System.currentTimeMillis(),
                items = ArrayList(items),
                fullReady = fullReady,
            )
            trimFullDataCacheLocked()
        }
    }

    private fun trimFullDataCacheLocked() {
        while (sFullDataCacheByAccount.size > FULL_CACHE_MAX_ACCOUNTS) {
            val iterator = sFullDataCacheByAccount.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }

    private fun persistFullSnapshot(fragment: Any, result: LoadAllResult?) {
        val data = result ?: return
        if (!data.complete || data.rawPages.isEmpty()) {
            dbg {
                "persistFullSnapshot skip complete=${data.complete} " +
                    "items=${data.items.size} pages=${data.rawPages.size}"
            }
            return
        }
        val context = resolveAppContext(fragment) ?: return
        val accountKey = resolveCurrentAccount(fragment.javaClass.classLoader) ?: return
        sDiskIoExecutor.execute {
            dbg { "persistFullSnapshot write pages=${data.rawPages.size} account=$accountKey" }
            CollectionSearchCacheStore.write(
                context = context,
                accountKey = accountKey,
                rawPages = data.rawPages,
                dirty = false,
                fullReady = data.complete,
            )
        }
    }

    private fun persistFirstPageSnapshot(fragment: Any, rawPage: String?) {
        if (rawPage.isNullOrBlank()) return
        val context = resolveAppContext(fragment) ?: return
        val accountKey = resolveCurrentAccount(fragment.javaClass.classLoader) ?: return
        sDiskIoExecutor.execute {
            dbg { "persistFirstPageSnapshot write rawLen=${rawPage.length} account=$accountKey" }
            CollectionSearchCacheStore.updateFirstPage(
                context = context,
                accountKey = accountKey,
                firstPageRaw = rawPage,
            )
        }
    }

    private fun resolveAppContext(fragment: Any): Context? {
        val activity = findHostActivity(fragment) ?: return null
        return activity.applicationContext ?: activity
    }

    private fun resolveCurrentAccount(cl: ClassLoader?): String? {
        val loader = cl ?: return null
        val method = synchronized(sCurrentAccountMethodCache) {
            sCurrentAccountMethodCache[loader] ?: runCatching {
                val coreAppClass = Class.forName(StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS, false, loader)
                coreAppClass.getDeclaredMethod(StableTiebaHookPoints.METHOD_GET_CURRENT_ACCOUNT)
                    .apply { isAccessible = true }
            }.getOrNull()?.also { sCurrentAccountMethodCache[loader] = it }
        } ?: return null
        val account = runCatching {
            method.invoke(null)?.toString()?.trim().orEmpty()
        }.getOrDefault("")
        return account.ifBlank { "__default__" }
    }

    private fun installAdapterFooterHook(adapterClass: Class<*>) {
        if (!sHookedAdapterClasses.add(adapterClass)) return
        val mod = XposedCompat.module ?: return
        dbg { "installAdapterFooterHook class=${adapterClass.name}" }

        val getCount = findMethodInHierarchy(adapterClass, "getCount") { method ->
            method.parameterTypes.isEmpty() &&
                (method.returnType == Int::class.javaPrimitiveType || method.returnType == Int::class.java)
        }
        if (getCount != null) {
            getCount.isAccessible = true
            mod.hook(getCount).intercept { chain ->
                val result = chain.proceed()
                val rawCount = (result as? Int) ?: return@intercept result
                val adapter = chain.thisObject ?: return@intercept rawCount
                val fragment = sAdapterOwners[adapter] ?: return@intercept rawCount
                val state = sFragmentStates[fragment] ?: return@intercept rawCount
                if (!state.active || !state.syncFooterVisible) return@intercept rawCount
                if (rawCount > 0) return@intercept rawCount
                dbg { "force getCount=1 for empty filtered result" }
                1
            }
        }

        val isEnabled = findMethodInHierarchy(adapterClass, "isEnabled") { method ->
            method.parameterTypes.size == 1 &&
                (method.parameterTypes[0] == Int::class.javaPrimitiveType || method.parameterTypes[0] == Int::class.java) &&
                (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.java)
        }
        if (isEnabled != null) {
            isEnabled.isAccessible = true
            mod.hook(isEnabled).intercept { chain ->
                val result = chain.proceed()
                val enabled = (result as? Boolean) ?: return@intercept result
                val adapter = chain.thisObject ?: return@intercept enabled
                val fragment = sAdapterOwners[adapter] ?: return@intercept enabled
                val state = sFragmentStates[fragment] ?: return@intercept enabled
                if (!state.active || !state.syncFooterVisible) return@intercept enabled
                val index = (chain.args.getOrNull(0) as? Int) ?: return@intercept enabled
                if (index < 0) return@intercept enabled
                if (index >= state.indexMap.size) {
                    dbg { "force isEnabled=true for sync footer index=$index filteredSize=${state.indexMap.size}" }
                    return@intercept true
                }
                enabled
            }
        }

        val getView = adapterClass.declaredMethods.firstOrNull { method ->
            method.name == "getView" &&
                method.parameterTypes.size == 3 &&
                (method.parameterTypes[0] == Int::class.javaPrimitiveType || method.parameterTypes[0] == Int::class.java) &&
                View::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                ViewGroup::class.java.isAssignableFrom(method.parameterTypes[2]) &&
                View::class.java.isAssignableFrom(method.returnType)
        } ?: return

        getView.isAccessible = true
        mod.hook(getView).intercept { chain ->
            val result = chain.proceed()
            val adapter = chain.thisObject ?: return@intercept result
            val fragment = sAdapterOwners[adapter] ?: return@intercept result
            val state = sFragmentStates[fragment] ?: return@intercept result
            if (!state.active || !state.syncFooterVisible) return@intercept result
            val index = (chain.args.getOrNull(0) as? Int) ?: return@intercept result
            if (index < state.indexMap.size) return@intercept result

            val root = result as? View ?: return@intercept result
            findFooterTextView(root)?.let { tv ->
                tv.text = UiText.CollectionSearch.SYNC_ACTION_FOOTER
                tv.setTextColor(0xFF2F7DFF.toInt())
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            hideFooterProgress(root)
            dbg { "footer getView patched index=$index filteredSize=${state.indexMap.size}" }
            result
        }
    }

    private fun findFooterTextView(root: View): TextView? {
        if (root is TextView) return root
        if (root !is ViewGroup) return null
        val queue = ArrayDeque<View>()
        for (i in 0 until root.childCount) {
            queue.addLast(root.getChildAt(i))
        }
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view is TextView) return view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    queue.addLast(view.getChildAt(i))
                }
            }
        }
        return null
    }

    private fun hideFooterProgress(root: View) {
        if (root is ProgressBar) {
            root.visibility = View.GONE
            return
        }
        if (root !is ViewGroup) return
        val queue = ArrayDeque<View>()
        for (i in 0 until root.childCount) {
            queue.addLast(root.getChildAt(i))
        }
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view is ProgressBar) {
                view.visibility = View.GONE
            } else if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    queue.addLast(view.getChildAt(i))
                }
            }
        }
    }

    private fun updateSyncActionFooter(fragment: Any, show: Boolean) {
        val state = ensureFragmentState(fragment)
        state.syncFooterVisible = show
        val presenter = resolvePresenter(fragment) ?: return
        val adapter = resolvePresenterAdapter(presenter) ?: return
        sAdapterOwners[adapter] = fragment
        installAdapterFooterHook(adapter.javaClass)
        val footerAccess = resolveAdapterFooterAccess(adapter)
        dbg {
            "updateSyncActionFooter show=$show showFooter=${methodName(footerAccess.showFooterMethod)} " +
                "showArg=${footerAccess.showFooterShowArg}/${footerAccess.showFooterHideArg} " +
                "loading=${methodName(footerAccess.loadingMethod)} hasMore=${methodName(footerAccess.hasMoreMethod)}"
        }
        if (show) {
            runCatching { footerAccess.loadingMethod?.invoke(adapter, false) }
            runCatching { footerAccess.hasMoreMethod?.invoke(adapter, true) }
            runCatching { footerAccess.showFooterMethod?.invoke(adapter, footerAccess.showFooterShowArg) }
        } else {
            runCatching { footerAccess.loadingMethod?.invoke(adapter, false) }
            runCatching { footerAccess.hasMoreMethod?.invoke(adapter, false) }
            runCatching { footerAccess.showFooterMethod?.invoke(adapter, footerAccess.showFooterHideArg) }
        }
        runCatching { callNoArgMethod(adapter, "notifyDataSetChanged") }
    }

    private fun suppressLoadingFooter(fragment: Any) {
        val presenter = resolvePresenter(fragment) ?: return
        val adapter = resolvePresenterAdapter(presenter) ?: return
        sAdapterOwners[adapter] = fragment
        installAdapterFooterHook(adapter.javaClass)
        ensureFragmentState(fragment).syncFooterVisible = false
        val footerAccess = resolveAdapterFooterAccess(adapter)
        dbg { "suppressLoadingFooter showFooter=${methodName(footerAccess.showFooterMethod)}" }
        runCatching { footerAccess.loadingMethod?.invoke(adapter, false) }
        runCatching { footerAccess.hasMoreMethod?.invoke(adapter, false) }
        runCatching { footerAccess.showFooterMethod?.invoke(adapter, footerAccess.showFooterHideArg) }
        runCatching { callNoArgMethod(adapter, "notifyDataSetChanged") }
    }

    private fun resolvePresenterAdapter(presenter: Any): Any? {
        val fieldName = sRuntimeTargets?.presenterAdapterField ?: return null
        return readFieldValue(presenter, fieldName)
    }

    private fun resolveAdapterFooterAccess(adapter: Any): AdapterFooterAccess {
        val clazz = adapter.javaClass
        sAdapterFooterAccessCache[clazz]?.let { return it }
        val targets = sRuntimeTargets
        val showFooter = targets?.adapterShowFooterMethod?.let { findBooleanVoidMethod(clazz, it) }
        val loading = targets?.adapterLoadingMethod?.let { findBooleanVoidMethod(clazz, it) }
        val hasMore = targets?.adapterHasMoreMethod?.let { findBooleanVoidMethod(clazz, it) }
        dbg {
            "resolveAdapterFooterAccess class=${clazz.name} showFooter=${methodName(showFooter)} " +
                "showArg=true/false loading=${methodName(loading)} hasMore=${methodName(hasMore)}"
        }
        val access = AdapterFooterAccess(showFooter, showFooterShowArg = true, showFooterHideArg = false, loadingMethod = loading, hasMoreMethod = hasMore)
        sAdapterFooterAccessCache[clazz] = access
        return access
    }

    private fun findBooleanVoidMethod(clazz: Class<*>, methodName: String): Method? {
        return findMethodInHierarchy(clazz, methodName) { method ->
            method.parameterTypes.size == 1 &&
                method.returnType == Void.TYPE &&
                (method.parameterTypes[0] == Boolean::class.javaPrimitiveType ||
                    method.parameterTypes[0] == Boolean::class.java)
        }
    }

    private fun resolveNetworkBridge(cl: ClassLoader?): NetworkBridge? {
        val loader = cl ?: return null
        sNetworkBridgeCache[loader]?.let { return it }
        return try {
            val netClass = Class.forName(StableTiebaHookPoints.NETWORK_CLASS, false, loader)
            val tbConfigClass = Class.forName(StableTiebaHookPoints.TB_CONFIG_CLASS, false, loader)
            val coreAppClass = Class.forName(StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS, false, loader)

            val ctor = netClass.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
            val addPostData = netClass.getDeclaredMethod(
                StableTiebaHookPoints.METHOD_ADD_POST_DATA,
                String::class.java,
                String::class.java,
            ).apply { isAccessible = true }
            val postNetData = netClass.getDeclaredMethod(StableTiebaHookPoints.METHOD_POST_NET_DATA)
                .apply { isAccessible = true }
            val serverField = tbConfigClass.getDeclaredField(StableTiebaHookPoints.FIELD_SERVER_ADDRESS)
                .apply { isAccessible = true }
            val markField = tbConfigClass.getDeclaredField(StableTiebaHookPoints.FIELD_MARK_GET_STORE)
                .apply { isAccessible = true }
            val accountMethod = coreAppClass.getDeclaredMethod(StableTiebaHookPoints.METHOD_GET_CURRENT_ACCOUNT)
                .apply { isAccessible = true }

            NetworkBridge(
                netCtor = ctor,
                addPostDataMethod = addPostData,
                postNetDataMethod = postNetData,
                serverAddressField = serverField,
                markGetStoreField = markField,
                getCurrentAccountMethod = accountMethod,
            ).also { sNetworkBridgeCache[loader] = it }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureSearchButton(activity: Activity?) {
        val host = activity ?: return
        if (!isActivityAlive(host)) return

        val state = sActivityStates[host] ?: ActivityState().also { sActivityStates[host] = it }
        if (state.buttonView?.parent != null) return

        val navigationBar = resolveNavigationBar(host) ?: return
        val alignRight = NavBarSearchButton.resolveNavigationRightAlign(navigationBar.javaClass.classLoader) ?: return
        val addMethod = NavBarSearchButton.resolveAddCustomViewMethod(navigationBar.javaClass) ?: return
        val iconDrawable = NavBarSearchButton.resolveSearchIconDrawable(host, navigationBar)
        val button = NavBarSearchButton.buildSearchButton(
            activity = host,
            iconDrawable = iconDrawable,
            contentDesc = UiText.CollectionSearch.BUTTON_CONTENT_DESC,
        ) { onSearchButtonClick(it) }

        try {
            addMethod.invoke(navigationBar, alignRight, button, null)
            state.buttonView = button
            updateSearchButtonVisual(host)
            NavBarSearchButton.scheduleReposition(button) {
                runCatching { placeSearchButtonLeftOfTargetFrame(button, navigationBar) }
            }
        } catch (t: Throwable) {
            XposedCompat.logD("[CollectionSearchHook] add search button failed: ${t.message}")
        }
    }

    private fun resolveNavigationBar(activity: Activity): Any? {
        val targets = sRuntimeTargets ?: return null
        val navigationBar = if (!targets.activityNavControllerField.isNullOrBlank()) {
            val controllerField = targets.activityNavControllerField ?: return null
            val controller = readFieldValue(activity, controllerField) ?: return null
            readFieldValue(controller, targets.navBarField)
        } else {
            readFieldValue(activity, targets.navBarField)
        } ?: return null
        if (NavBarSearchButton.resolveAddCustomViewMethod(navigationBar.javaClass) == null) return null
        return navigationBar
    }

    private fun placeSearchButtonLeftOfTargetFrame(button: View, navigationBar: Any) {
        if (button.parent !is ViewGroup) return
        val navRoot = NavBarSearchButton.extractNavigationRootView(navigationBar)
        val parent = (button.parent as? ViewGroup)
            ?: navRoot?.let { NavBarSearchButton.findParentOfView(it, button) }
            ?: return

        val buttonIndex = parent.indexOfChild(button)
        if (buttonIndex < 0) return

        var targetIndex = -1
        var bestScore = Float.MAX_VALUE
        val buttonCenter = button.x + button.width / 2f
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child === button || child !is FrameLayout) continue
            if (child.visibility != View.VISIBLE) continue
            val childCenter = child.x + child.width / 2f
            var score = if (childCenter >= buttonCenter) childCenter - buttonCenter else 100000f + i
            if (child.hasOnClickListeners()) score -= 40f
            if (child.contentDescription?.isNotBlank() == true) score -= 12f
            if (hasImageDescendant(child)) score -= 20f
            if (score < bestScore) {
                bestScore = score
                targetIndex = i
            }
        }
        if (targetIndex < 0) return

        val desired = if (buttonIndex < targetIndex) targetIndex - 1 else targetIndex
        if (desired == buttonIndex) return

        val lp = button.layoutParams
        parent.removeViewAt(buttonIndex)
        val safeIndex = desired.coerceIn(0, parent.childCount)
        parent.addView(button, safeIndex, lp)
        dbg { "reposition search button parent=${parent.javaClass.simpleName} button=$buttonIndex->$safeIndex target=$targetIndex" }
    }

    private fun hasImageDescendant(root: View): Boolean {
        if (root is ImageView) return true
        if (root !is ViewGroup) return false
        for (i in 0 until root.childCount) {
            if (hasImageDescendant(root.getChildAt(i))) return true
        }
        return false
    }

    private fun onSearchButtonClick(activity: Activity) {
        if (!isActivityAlive(activity)) return
        if (isEditMode(activity)) {
            showToast(activity, UiText.CollectionSearch.TOAST_EXIT_EDIT_FIRST)
            return
        }
        val fragment = findActiveThreadFragment(activity)
        if (fragment == null) {
            showToast(activity, UiText.CollectionSearch.TOAST_PAGE_NOT_READY)
            return
        }
        val state = ensureFragmentState(fragment)
        showSearchDialog(
            activity = activity,
            currentQuery = state.query,
            onSearch = { query ->
                applyFilter(fragment, query, fromUser = true)
            },
            onClearQuery = {
                clearFilter(fragment, fromUser = true)
            },
            onRefresh = {
                triggerManualFullSync(fragment, userVisible = true)
            },
        )
    }

    private fun showSearchDialog(
        activity: Activity,
        currentQuery: String,
        onSearch: (String) -> Unit,
        onClearQuery: () -> Unit,
        onRefresh: () -> Unit,
    ) {
        val density = activity.resources.displayMetrics.density
        val hPad = (18 * density).toInt()
        val vPad = (10 * density).toInt()
        val input = EditText(activity).apply {
            hint = UiText.CollectionSearch.DIALOG_HINT
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(currentQuery)
            setSelection(text?.length ?: 0)
            setPadding(0, vPad, 0, vPad)
            textSize = 14.5f
            setTextColor(0xFF1F2937.toInt())
            setHintTextColor(0xFF9AA4B2.toInt())
            background = null
        }
        val inputRow = ClearableInputRow.create(
            activity = activity,
            input = input,
            initialText = currentQuery,
            clearSymbol = UiText.CollectionSearch.INPUT_CLEAR_SYMBOL,
            verticalPadding = vPad,
            onClear = onClearQuery
        )
        val underline = View(activity).apply {
            setBackgroundColor(0xFF2F7DFF.toInt())
            alpha = 0.64f
        }
        input.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            underline.alpha = if (hasFocus) 1.0f else 0.64f
        }
        val inputContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, (8 * density).toInt(), hPad, (4 * density).toInt())
            addView(
                inputRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                underline,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (2.0f * density).toInt().coerceAtLeast(1)
                ).apply {
                    topMargin = (2 * density).toInt()
                }
            )
        }

        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(UiText.CollectionSearch.DIALOG_TITLE)
            .setView(inputContainer)
            .setPositiveButton(UiText.CollectionSearch.DIALOG_ACTION_SEARCH) { _, _ ->
                onSearch(input.text?.toString().orEmpty().trim())
            }
            .setNeutralButton(UiText.CollectionSearch.DIALOG_ACTION_SYNC) { _, _ ->
                onRefresh()
            }
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.let { window -> applySearchDialogCardStyle(window, density) }
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positive?.isAllCaps = false
            neutral?.isAllCaps = false
            negative?.isAllCaps = false
            positive?.setTextColor(0xFF4C87F7.toInt())
            neutral?.setTextColor(0xFF4C87F7.toInt())
            negative?.setTextColor(0xFF6B7280.toInt())
        }
        dialog.show()
    }

    private fun applySearchDialogCardStyle(window: android.view.Window, density: Float) {
        val bg = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            cornerRadius = 18f * density
        }
        window.setBackgroundDrawable(InsetDrawable(bg, (10 * density).toInt()))
    }

    private fun applyFilter(fragment: Any, query: String, fromUser: Boolean) {
        val state = ensureFragmentState(fragment)
        dbg {
            "applyFilter query='${query.trim()}' fromUser=$fromUser " +
                "fullReady=${state.fullDataReady} fetchingAll=${state.fetchingAll} requested=${state.fullLoadRequested}"
        }
        if (!state.fullDataReady) {
            val hitMemory = restoreFullDataFromCache(fragment, state)
            if (!hitMemory && !state.diskRestoreTried) {
                state.diskRestoreTried = true
                restoreFullDataFromDisk(fragment, state)
            }
        }
        if (!state.fullDataReady && !state.fetchingAll && !state.fullLoadRequested && !hasFullCache(fragment)) {
            state.fullLoadRequested = true
            startFetchAllCollections(fragment, userVisible = fromUser)
            if (fromUser) {
                showToast(findHostActivity(fragment), UiText.CollectionSearch.TOAST_NO_CACHE)
            }
        }

        val trustedCacheItems = if (state.fullDataReady) getTrustedFullCacheItems(fragment) else null
        if (trustedCacheItems != null) {
            val modelNow = getFullModelList(fragment)
            if (modelNow.size < trustedCacheItems.size) {
                dbg { "applyFilter rehydrate model from cache model=${modelNow.size} cache=${trustedCacheItems.size}" }
                replaceModelDataset(fragment, trustedCacheItems)
            }
        }

        val fullList = trustedCacheItems ?: getFullModelList(fragment)
        if (!state.fullDataReady) {
            dbg { "applyFilter fallback partialListSize=${fullList.size}" }
            val identityMap = IntArray(fullList.size) { it }
            state.query = query.trim()
            state.active = true
            state.indexMap = identityMap
            applyAdapterList(fragment, fullList)
            updateSyncActionFooter(fragment, true)
            if (fromUser) {
                showToast(findHostActivity(fragment), UiText.CollectionSearch.TOAST_CACHE_SYNCING)
            }
            updateSearchButtonVisual(findHostActivity(fragment))
            return
        }

        if (fullList.isEmpty()) {
            state.query = query.trim()
            state.active = true
            state.indexMap = IntArray(0)
            applyAdapterList(fragment, emptyList())
            updateSyncActionFooter(fragment, true)
            if (fromUser) showToast(findHostActivity(fragment), UiText.CollectionSearch.TOAST_NO_ITEMS)
            updateSearchButtonVisual(findHostActivity(fragment))
            return
        }

        val normalized = normalizeQuery(query)
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        val filtered = ArrayList<Any>(fullList.size)
        val indexMap = ArrayList<Int>(fullList.size)
        fullList.forEachIndexed { index, item ->
            val matched = if (tokens.isEmpty()) {
                true
            } else {
                val text = buildMarkSearchText(item)
                tokens.all { token -> text.contains(token) }
            }
            if (matched) {
                filtered.add(item)
                indexMap.add(index)
            }
        }

        state.query = query.trim()
        state.active = true
        state.indexMap = indexMap.toIntArray()
        applyAdapterList(fragment, filtered)
        updateSyncActionFooter(fragment, true)
        dbg { "applyFilter matched=${filtered.size} fullSize=${fullList.size} tokens=${tokens.size}" }

        if (fromUser) {
            showToast(findHostActivity(fragment), UiText.CollectionSearch.toastMatched(filtered.size, fullList.size))
        }
        updateSearchButtonVisual(findHostActivity(fragment))
    }

    private fun clearFilter(fragment: Any, fromUser: Boolean) {
        val state = ensureFragmentState(fragment)
        val hadFilter = state.active || state.query.isNotBlank()
        state.query = ""
        state.active = false
        state.indexMap = IntArray(0)
        state.syncFooterVisible = false
        applyAdapterList(fragment, getFullModelList(fragment))
        updateSyncActionFooter(fragment, false)
        if (state.fullDataReady) {
            suppressLoadingFooter(fragment)
        }
        if (fromUser && hadFilter) {
            showToast(findHostActivity(fragment), UiText.CollectionSearch.TOAST_FILTER_CLEARED)
        }
        updateSearchButtonVisual(findHostActivity(fragment))
    }

    private fun applyAdapterList(fragment: Any, list: List<Any>) {
        val presenter = resolvePresenter(fragment) ?: return
        val setter = resolvePresenterListSetter(presenter.javaClass) ?: return
        val state = ensureFragmentState(fragment)
        if (state.applying) return
        state.applying = true
        try {
            setter.invoke(presenter, ArrayList(list))
        } catch (t: Throwable) {
            XposedCompat.logD("[CollectionSearchHook] apply adapter list failed: ${t.message}")
        } finally {
            state.applying = false
        }
    }

    private fun resolvePresenterListSetter(clazz: Class<*>): Method? {
        sPresenterListSetterCache[clazz]?.let { return it }
        val methodName = sRuntimeTargets?.presenterListSetterMethod ?: return null
        val resolved = findMethodInHierarchy(clazz, methodName) {
            it.parameterTypes.size == 1 &&
                (List::class.java.isAssignableFrom(it.parameterTypes[0]) ||
                    ArrayList::class.java.isAssignableFrom(it.parameterTypes[0])) &&
                it.returnType == Void.TYPE
        }
        resolved?.isAccessible = true
        if (resolved != null) sPresenterListSetterCache[clazz] = resolved
        return resolved
    }

    private fun getFullModelList(fragment: Any): List<Any> {
        val model = resolveModel(fragment) ?: return emptyList()
        val getter = resolveModelListGetter(model.javaClass) ?: return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            val raw = getter.invoke(model) as? List<*>
            raw?.filterNotNull() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun resolveModelListGetter(clazz: Class<*>): Method? {
        sModelListGetterCache[clazz]?.let { return it }
        val methodName = sRuntimeTargets?.modelListGetterMethod ?: return null
        val resolved = findMethodInHierarchy(clazz, methodName) {
            it.parameterTypes.isEmpty() &&
                (List::class.java.isAssignableFrom(it.returnType) ||
                    ArrayList::class.java.isAssignableFrom(it.returnType))
        }
        resolved?.isAccessible = true
        if (resolved != null) sModelListGetterCache[clazz] = resolved
        return resolved
    }

    private fun resolveModelParseMethod(clazz: Class<*>): Method? {
        sModelParseMethodCache[clazz]?.let { return it }
        val methodName = sRuntimeTargets?.modelParseMethod ?: return null
        val resolved = findMethodInHierarchy(clazz, methodName) {
            it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == String::class.java &&
                (List::class.java.isAssignableFrom(it.returnType) || ArrayList::class.java.isAssignableFrom(it.returnType))
        }
        resolved?.isAccessible = true
        if (resolved != null) sModelParseMethodCache[clazz] = resolved
        return resolved
    }

    private fun resolvePresenter(fragment: Any): Any? {
        val fieldName = sRuntimeTargets?.presenterField ?: return null
        val presenter = readFieldValue(fragment, fieldName) ?: return null
        if (resolvePresenterListSetter(presenter.javaClass) == null) return null
        return presenter
    }

    private fun resolveModel(fragment: Any): Any? {
        val fieldName = sRuntimeTargets?.modelField ?: return null
        val model = readFieldValue(fragment, fieldName) ?: return null
        if (resolveModelListGetter(model.javaClass) == null) return null
        return model
    }

    private fun readFieldValue(instance: Any, fieldName: String): Any? {
        return try {
            val field = findFieldInHierarchy(instance.javaClass, fieldName) ?: return null
            field.get(instance)
        } catch (_: Throwable) {
            null
        }
    }

    private fun replaceModelDataset(fragment: Any, allItems: List<Any>) {
        replaceModelList(resolveModel(fragment), allItems)
        replaceFragmentDisplayList(fragment, allItems)
    }

    private fun replaceModelList(model: Any?, allItems: List<Any>) {
        val target = model ?: return
        val field = resolveModelListField(target) ?: return
        runCatching {
            field.set(target, ArrayList(allItems))
        }
    }

    private fun resolveModelListField(model: Any): Field? {
        val clazz = model.javaClass
        sModelListFieldCache[clazz]?.let { return it }
        val fieldName = sRuntimeTargets?.modelListField ?: return null
        val field = findFieldInHierarchy(clazz, fieldName) ?: return null
        if (!List::class.java.isAssignableFrom(field.type) && !ArrayList::class.java.isAssignableFrom(field.type)) {
            return null
        }
        sModelListFieldCache[clazz] = field
        return field
    }

    private fun replaceFragmentDisplayList(fragment: Any, allItems: List<Any>) {
        val field = resolveFragmentDisplayListField(fragment) ?: return
        runCatching {
            field.set(fragment, ArrayList(allItems))
        }
    }

    private fun resolveFragmentDisplayListField(fragment: Any): Field? {
        val clazz = fragment.javaClass
        sFragmentDisplayListFieldCache[clazz]?.let { return it }
        val fieldName = sRuntimeTargets?.fragmentDisplayListField ?: return null
        val field = findFieldInHierarchy(clazz, fieldName) ?: return null
        if (!List::class.java.isAssignableFrom(field.type) && !ArrayList::class.java.isAssignableFrom(field.type)) {
            return null
        }
        sFragmentDisplayListFieldCache[clazz] = field
        return field
    }

    private fun findFieldValue(instance: Any, predicate: (Any) -> Boolean): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            for (field in current.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                runCatching {
                    field.isAccessible = true
                    val value = field.get(instance) ?: return@runCatching
                    if (predicate(value)) return value
                }
            }
            current = current.superclass
        }
        return null
    }

    private fun findFieldInHierarchy(clazz: Class<*>, name: String): Field? {
        synchronized(sFieldLookupCache) {
            val cached = sFieldLookupCache[clazz]
            if (cached != null && cached.containsKey(name)) return cached[name]
        }

        var current: Class<*>? = clazz
        var resolved: Field? = null
        while (current != null) {
            try {
                resolved = current.getDeclaredField(name).apply { isAccessible = true }
                break
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        synchronized(sFieldLookupCache) {
            val cached = sFieldLookupCache.getOrPut(clazz) { HashMap() }
            cached[name] = resolved
        }
        return resolved
    }

    private fun findMethodInHierarchy(clazz: Class<*>, name: String, predicate: (Method) -> Boolean): Method? {
        return ReflectionUtils.findMethodInHierarchy(clazz, name, predicate)
    }

    private fun buildMarkSearchText(mark: Any): String {
        sMarkTextCache[mark]?.let { return it }
        val accessor = resolveMarkAccessor(mark.javaClass)
        val parts = ArrayList<String>(5)
        readString(mark, accessor.titleMethod)?.let(parts::add)
        readString(mark, accessor.authorMethod)?.let(parts::add)
        readString(mark, accessor.forumMethod)?.let(parts::add)
        readString(mark, accessor.threadIdMethod)?.let(parts::add)
        readString(mark, accessor.idMethod)?.let(parts::add)
        val text = normalizeQuery(parts.joinToString(" "))
        sMarkTextCache[mark] = text
        return text
    }

    private fun resolveMarkAccessor(clazz: Class<*>): MarkAccessor {
        sMarkAccessorCache[clazz]?.let { return it }
        val accessor = MarkAccessor(
            titleMethod = findGetter(clazz, "getTitle"),
            authorMethod = findGetter(clazz, "getAuthorName", "getAuthor", "getUserName"),
            forumMethod = findGetter(clazz, "getForumName", "getForum", "getFname"),
            threadIdMethod = findGetter(clazz, "getThreadId"),
            postIdMethod = findGetter(clazz, "getPostId"),
            idMethod = findGetter(clazz, "getId"),
        )
        sMarkAccessorCache[clazz] = accessor
        return accessor
    }

    private fun findGetter(clazz: Class<*>, vararg names: String): Method? {
        names.forEach { name ->
            findMethodInHierarchy(clazz, name) { it.parameterTypes.isEmpty() }?.let {
                it.isAccessible = true
                return it
            }
        }
        return null
    }

    private fun readString(target: Any, method: Method?): String? {
        if (method == null) return null
        return try {
            method.invoke(target)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveMarkStableKey(mark: Any): String {
        val accessor = resolveMarkAccessor(mark.javaClass)
        val id = readString(mark, accessor.idMethod)
        if (!id.isNullOrBlank()) return "id:$id"
        val tid = readString(mark, accessor.threadIdMethod)
        val pid = readString(mark, accessor.postIdMethod)
        if (!tid.isNullOrBlank() || !pid.isNullOrBlank()) return "tp:${tid.orEmpty()}_${pid.orEmpty()}"
        return "obj:${System.identityHashCode(mark)}"
    }

    private fun normalizeQuery(input: String): String {
        return input
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun updateSearchButtonVisual(activity: Activity?) {
        val host = activity ?: return
        val button = sActivityStates[host]?.buttonView ?: return
        val fragment = findActiveThreadFragment(host)
        val isFiltered = fragment != null && isFilterActive(fragment)
        button.alpha = if (isFiltered) 1.0f else 0.85f
    }

    private fun isEditMode(activity: Activity): Boolean {
        val targetMethodName = sRuntimeTargets?.editModeMethod ?: return false
        val clazz = activity.javaClass
        sEditModeMethodCache[clazz]?.let { cached ->
            return runCatching { (cached.invoke(activity) as? Boolean) == true }.getOrDefault(false)
        }

        val method = findMethodInHierarchy(clazz, targetMethodName) {
            it.parameterTypes.isEmpty() && isBooleanType(it.returnType)
        } ?: return false
        sEditModeMethodCache[clazz] = method
        return runCatching { (method.invoke(activity) as? Boolean) == true }.getOrDefault(false)
    }

    private fun isBooleanType(type: Class<*>): Boolean {
        return type == Boolean::class.javaPrimitiveType || type == Boolean::class.java
    }

    private fun findActiveThreadFragment(activity: Activity): Any? {
        val supportManager = callNoArgMethod(activity, "getSupportFragmentManager") ?: return null
        val queue = ArrayDeque<Any>()
        collectFragments(supportManager, queue)
        while (queue.isNotEmpty()) {
            val fragment = queue.removeFirst()
            if (
                fragment.javaClass.name == StableTiebaHookPoints.COLLECTION_THREAD_FRAGMENT_CLASS &&
                isFragmentAdded(fragment)
            ) {
                return fragment
            }
            val childManager = callNoArgMethod(fragment, "getChildFragmentManager")
            if (childManager != null) {
                collectFragments(childManager, queue)
            }
        }
        return null
    }

    private fun collectFragments(manager: Any, queue: ArrayDeque<Any>) {
        val method = findMethodInHierarchy(manager.javaClass, "getFragments") { it.parameterTypes.isEmpty() } ?: return
        val raw = runCatching { method.invoke(manager) }.getOrNull()
        when (raw) {
            is List<*> -> raw.filterNotNull().forEach { queue.addLast(it) }
            is Array<*> -> raw.filterNotNull().forEach { queue.addLast(it) }
        }
    }

    private fun isFragmentAdded(fragment: Any): Boolean {
        val result = callNoArgMethod(fragment, "isAdded") as? Boolean
        return result ?: true
    }

    private fun findHostActivity(fragment: Any): Activity? {
        return callNoArgMethod(fragment, "getActivity") as? Activity
    }

    private fun callNoArgMethod(target: Any, methodName: String): Any? {
        val method = findMethodInHierarchy(target.javaClass, methodName) { it.parameterTypes.isEmpty() } ?: return null
        return runCatching { method.invoke(target) }.getOrNull()
    }

    private fun showToast(activity: Activity?, message: String) {
        val host = activity ?: return
        if (!isActivityAlive(host)) return
        host.runOnUiThread {
            Toast.makeText(host, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isActivityAlive(activity: Activity): Boolean {
        if (activity.isFinishing) return false
        return !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)
    }
}
