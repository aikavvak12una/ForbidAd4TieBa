package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.contracts.MemberAccess
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.symbol.model.CollectionSearchSymbols
import com.forbidad4tieba.hook.symbol.model.CollectionNetworkTargets
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.NavBarSearchButton
import com.forbidad4tieba.hook.ui.CollectionSearchDialog
import com.forbidad4tieba.hook.ui.UiText
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * Adds local search to the collection page.
 *
 * The hook injects a separate top-right search button, filters the loaded adapter list locally, and
 * keeps the complete model list unchanged so click positions can be remapped after filtering.
 */
object CollectionSearchHook {
    private const val PAGE_SIZE = 20
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private data class ActivityState(
        var buttonView: View? = null,
    )

    private data class DiskRestoreResult(
        val items: List<Any>,
        val trustedFull: Boolean,
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

    private val sSessions = CollectionSearchSessions()
    private val sActivityStates = Collections.synchronizedMap(WeakHashMap<Activity, ActivityState>())
    private val sHookedPresenterClasses =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()))
    private val memoryCache = CollectionSearchMemoryCache()
    private val sMarkTextCache = Collections.synchronizedMap(WeakHashMap<Any, String>())
    private val sHookedAdapterClasses =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()))
    private val sDiskIoExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tbhook-collect-cache-io").apply { isDaemon = true }
    }
    private val sNetExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tbhook-collect-search-net").apply { isDaemon = true }
    }

    private inline fun dbg(message: () -> String) {
        if (ConfigManager.snapshot().isDetailedLoggingEnabled) {
            XposedCompat.logD("[CollectionSearchHook][dbg] ${message()}")
        }
    }

    private fun dbg(message: String) {
        if (ConfigManager.snapshot().isDetailedLoggingEnabled) {
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
        MemberAccess.findMethodOrNull(activityClass, "onCreate", Bundle::class.java)?.let { method ->
            RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installActivityHooks:method").intercept { chain ->
                val result = chain.proceed()
                ensureSearchButton(chain.thisObject as? Activity)
                result
            }
        }

        MemberAccess.findMethodOrNull(activityClass, "onResume")?.let { method ->
            RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installActivityHooks:method:2").intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                ensureSearchButton(activity)
                updateSearchButtonVisual(activity)
                result
            }
        }

        MemberAccess.findMethodOrNull(activityClass, "onDestroy")?.let { method ->
            RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installActivityHooks:method:3").intercept { chain ->
                val activity = chain.thisObject as? Activity
                val result = chain.proceed()
                if (activity != null) sActivityStates.remove(activity)
                result
            }
        }
    }

    private fun installFragmentHooks(mod: io.github.libxposed.api.XposedModule, fragmentClass: Class<*>) {
        MemberAccess.findMethodOrNull(
            fragmentClass,
            "onCreateView",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Bundle::class.java,
        )?.let { method ->
            RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installFragmentHooks:method").intercept { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject ?: return@intercept result
                bindFragmentPresenter(fragment)
                ensureFragmentState(fragment)
                updateSearchButtonVisual(findHostActivity(fragment))
                result
            }
        }

        MemberAccess.findMethodOrNull(fragmentClass, "onResume")?.let { method ->
            RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installFragmentHooks:method:2").intercept { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject ?: return@intercept result
                val state = ensureFragmentState(fragment)
                dbg {
                    "onResume fullReady=${state.fullDataReady} fetchingAll=${state.fetchingAll} " +
                        "requested=${state.fullLoadRequested} active=${state.active} query='${state.query}'"
                }
                if (!state.fullDataReady && state.shouldRestoreFullDataOnResume()) {
                    val restoredFull = restoreFullDataFromCache(fragment, state)
                    if (!restoredFull) {
                        restoreFullDataFromDiskAsync(
                            fragment = fragment,
                            state = state,
                            fetchOnMiss = state.active || state.query.isNotBlank(),
                            userVisible = false,
                        )
                    }
                }
                result
            }
        }

        MemberAccess.findMethodOrNull(
            fragmentClass,
            "onActivityResult",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Intent::class.java,
        )?.let { method ->
            RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installFragmentHooks:method:3").intercept { chain ->
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

        MemberAccess.findMethodOrNull(fragmentClass, "onDestroy")?.let { method ->
            RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installFragmentHooks:method:4").intercept { chain ->
                val fragment = chain.thisObject
                val result = chain.proceed()
                if (fragment != null) clearFragmentState(fragment)
                result
            }
        }

        MemberAccess.findMethodOrNull(
            fragmentClass,
            "onItemClick",
            android.widget.AdapterView::class.java,
            View::class.java,
            Int::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
        )?.let { method ->
            RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installFragmentHooks:method:5").intercept { chain ->
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
                val args = chain.args.toTypedArray()
                args[2] = mapped
                chain.proceed(args)
            }
        }

        MemberAccess.findMethodOrNull(fragmentClass, "onClick", View::class.java)?.let { method ->
            RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installFragmentHooks:method:6").intercept { chain ->
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
        sSessions.bindPresenter(presenter, fragment)
        resolvePresenterAdapter(presenter)?.let { adapter ->
            sSessions.bindAdapter(adapter, fragment)
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
        RuntimeHooks.builder(mod, method, "CollectionSearchHook", "installPresenterUpdateHooks:method").intercept { chain ->
            val result = chain.proceed()
            val presenter = chain.thisObject
            val fragment = if (presenter != null) sSessions.presenterOwner(presenter) else null
            if (fragment != null) {
                val adapter = resolvePresenterAdapter(presenter)
                if (adapter != null) {
                    sSessions.bindAdapter(adapter, fragment)
                    installAdapterFooterHook(adapter.javaClass)
                }
                reapplyFilterIfNeeded(fragment)
            }
            result
        }
    }

    private fun ensureFragmentState(
        fragment: Any,
        sourceAccount: String? = resolveCurrentAccount(fragment.javaClass.classLoader),
    ): CollectionSearchPageSession = sSessions.ensure(fragment, sourceAccount)

    private fun clearFragmentState(fragment: Any) {
        sSessions.clear(fragment)
        updateSearchButtonVisual(findHostActivity(fragment))
    }

    private fun isSessionCurrent(fragment: Any, state: CollectionSearchPageSession): Boolean =
        sSessions.isCurrent(fragment, state, resolveCurrentAccount(fragment.javaClass.classLoader))

    private fun isFilterActive(fragment: Any): Boolean = sSessions[fragment]?.active == true

    private fun remapIndex(fragment: Any, index: Int): Int {
        val state = sSessions[fragment] ?: return index
        if (!state.active || index < 0) return index
        return if (index < state.indexMap.size) state.indexMap[index] else index
    }

    private fun reapplyFilterIfNeeded(fragment: Any) {
        val state = sSessions[fragment] ?: return
        if (!state.active || state.applying) return
        applyFilter(fragment, state.query, fromUser = false)
    }

    private fun isMemoryCacheTrustedFull(cache: CollectionSearchMemoryCache.Snapshot): Boolean {
        return cache.fullReady
    }

    private fun getTrustedFullCacheItems(fragment: Any, state: CollectionSearchPageSession): List<Any>? {
        if (!isSessionCurrent(fragment, state)) return null
        val cache = getFullDataCache(state.sourceAccount ?: return null) ?: return null
        if (!isMemoryCacheTrustedFull(cache)) return null
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
        val state = sSessions[fragment] ?: return false
        if (!state.active || !state.syncFooterVisible || index < 0) return false
        val isFooter = index >= state.indexMap.size
        if (isFooter) {
            dbg { "isSyncFooterClick=true index=$index filteredSize=${state.indexMap.size}" }
        }
        return isFooter
    }

    private fun syncFirstPageEveryEntry(fragment: Any, force: Boolean = false) {
        val userId = readCurrentAccount(fragment.javaClass.classLoader)
        val accountKey = CollectionSearchPageSession.accountKey(userId)
        val state = ensureFragmentState(fragment, accountKey)
        val request = state.beginFirstPage(userId, force) ?: return
        dbg { "syncFirstPageEveryEntry start force=$force fetchingAll=${state.fetchingAll}" }
        sNetExecutor.execute {
            val result = fetchFirstPage(fragment, request)
            val host = findHostActivity(fragment)
            host?.runOnUiThread {
                val current = sSessions.current(fragment, request) ?: return@runOnUiThread
                if (!current.finishFirstPage(request, resolveCurrentAccount(fragment.javaClass.classLoader))) {
                    return@runOnUiThread
                }
                val firstPage = result?.items.orEmpty()
                dbg { "syncFirstPageEveryEntry done size=${firstPage.size} rawLen=${result?.rawPage?.length ?: 0}" }
                if (result != null && accountKey != null && result.rawPage.isNotBlank()) {
                    if (!mergeFirstPageIntoCache(fragment, current, firstPage)) return@runOnUiThread
                    persistFirstPageSnapshot(fragment, accountKey, result.rawPage)
                    if (current.active) {
                        applyFilter(fragment, current.query, fromUser = false)
                    } else if (current.fullDataReady) {
                        applyAdapterList(fragment, getFullModelList(fragment))
                    }
                }
            }
            if (host == null) {
                sSessions.current(fragment, request)?.finishFirstPage(request, resolveCurrentAccount(fragment.javaClass.classLoader))
            }
        }
    }

    private fun fetchFirstPage(
        fragment: Any,
        request: CollectionSearchRequest,
    ): FirstPageSyncResult? {
        if (!isRequestCurrent(fragment, request)) return null
        val userId = request.userId
        val model = resolveModel(fragment) ?: return null
        val parseMethod = resolveModelParseMethod(model.javaClass) ?: return null
        val bridge = resolveNetworkBridge(model.javaClass.classLoader) ?: return null

        val server = runCatching { bridge.serverAddressField.get(null)?.toString().orEmpty() }.getOrDefault("")
        val path = runCatching { bridge.markGetStoreField.get(null)?.toString().orEmpty() }.getOrDefault("")
        if (server.isBlank() || path.isBlank() || userId.isNullOrBlank()) {
            dbg("fetchFirstPage abort invalid params server/path/userId")
            return null
        }
        if (!isRequestCurrent(fragment, request)) return null

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

    private fun mergeFirstPageIntoCache(
        fragment: Any,
        state: CollectionSearchPageSession,
        firstPage: List<Any>,
    ): Boolean {
        if (!isSessionCurrent(fragment, state)) return false
        val accountKey = state.sourceAccount ?: return false
        state.invalidateDiskRestore()
        if (!state.fullDataReady) {
            replaceModelDataset(fragment, firstPage)
            val fullReadyNow = firstPage.isEmpty()
            putFullDataCache(accountKey, firstPage, fullReady = fullReadyNow)
            state.fullDataReady = fullReadyNow
            return true
        }

        if (firstPage.isEmpty()) {
            dbg { "mergeFirstPageIntoCache clear all by empty first page" }
            replaceModelDataset(fragment, emptyList())
            putFullDataCache(accountKey, emptyList(), fullReady = true)
            state.fullDataReady = true
            return true
        }

        val trustedCache = getFullDataCache(accountKey)?.takeIf(::isMemoryCacheTrustedFull)?.items
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
            putFullDataCache(accountKey, firstPage, fullReady = state.fullDataReady)
            state.fullDataReady = true
            return true
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
        putFullDataCache(accountKey, merged, fullReady = state.fullDataReady)
        state.fullDataReady = true
        return true
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
        val userId = readCurrentAccount(fragment.javaClass.classLoader)
        val accountKey = CollectionSearchPageSession.accountKey(userId)
        val state = ensureFragmentState(fragment, accountKey)
        val request = state.beginFullLoad(userId) ?: return
        val token = request.token
        val cacheContext = resolveAppContext(fragment)
        dbg { "startFetchAllCollections token=$token userVisible=$userVisible" }

        sNetExecutor.execute {
            val allResult = loadAllCollections(fragment, request)
            // A completed final page may still be cached for its source account after leaving.
            persistFullSnapshot(cacheContext, request, allResult)
            val host = findHostActivity(fragment)
            if (host == null) {
                sSessions.current(fragment, request)?.finishFullLoad(
                    request, resolveCurrentAccount(fragment.javaClass.classLoader),
                )
                return@execute
            }
            host.runOnUiThread {
                val current = sSessions.current(fragment, request) ?: return@runOnUiThread
                if (!current.finishFullLoad(request, resolveCurrentAccount(fragment.javaClass.classLoader))) {
                    return@runOnUiThread
                }
                val allItems = allResult?.items.orEmpty()
                dbg {
                    "startFetchAllCollections finish token=$token items=${allItems.size} " +
                        "complete=${allResult?.complete} pages=${allResult?.rawPages?.size ?: 0}"
                }
                if (allResult != null && accountKey != null) {
                    val complete = allResult.complete
                    current.fullDataReady = complete
                    replaceModelDataset(fragment, allItems)
                    putFullDataCache(accountKey, allItems, fullReady = complete)
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

    private fun loadAllCollections(
        fragment: Any,
        request: CollectionSearchRequest,
    ): LoadAllResult? {
        if (!isRequestCurrent(fragment, request)) return null
        val userId = request.userId
        val model = resolveModel(fragment) ?: return null
        val parseMethod = resolveModelParseMethod(model.javaClass) ?: return null
        val bridge = resolveNetworkBridge(model.javaClass.classLoader) ?: return null

        val server = runCatching { bridge.serverAddressField.get(null)?.toString().orEmpty() }.getOrDefault("")
        val path = runCatching { bridge.markGetStoreField.get(null)?.toString().orEmpty() }.getOrDefault("")
        if (server.isBlank() || path.isBlank() || userId.isNullOrBlank()) {
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
            if (!isRequestCurrent(fragment, request)) return null
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

    private fun isRequestCurrent(fragment: Any, request: CollectionSearchRequest): Boolean {
        return sSessions.accepts(fragment, request, resolveCurrentAccount(fragment.javaClass.classLoader))
    }

    private fun postCollectionPage(
        bridge: CollectionNetworkTargets,
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
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun restoreFullDataFromCache(fragment: Any, state: CollectionSearchPageSession): Boolean {
        if (!isSessionCurrent(fragment, state)) return false
        val cached = getFullDataCache(state.sourceAccount ?: return false) ?: return false
        val trustedFull = isMemoryCacheTrustedFull(cached)
        dbg {
            "restoreFullDataFromCache size=${cached.items.size} fullReady=${cached.fullReady} " +
                "trusted=$trustedFull"
        }
        replaceModelDataset(fragment, cached.items)
        state.fullDataReady = trustedFull
        if (state.fullDataReady) {
            suppressLoadingFooter(fragment)
        }
        return trustedFull
    }

    private fun restoreFullDataFromDiskAsync(
        fragment: Any,
        state: CollectionSearchPageSession,
        fetchOnMiss: Boolean,
        userVisible: Boolean,
    ): Boolean {
        if (!isSessionCurrent(fragment, state)) return false
        if (state.joinDiskRestore(fetchOnMiss, userVisible)) return true
        if (state.diskRestoreTried) return false

        val host = findHostActivity(fragment) ?: return false
        val context = host.applicationContext ?: host
        val accountKey = state.sourceAccount ?: return false
        val model = resolveModel(fragment) ?: return false
        val parseMethod = resolveModelParseMethod(model.javaClass) ?: return false
        val request = state.beginDiskRestore(fetchOnMiss, userVisible) ?: return false

        sDiskIoExecutor.execute {
            val restored = readFullDataFromDisk(context, accountKey, model, parseMethod)
            host.runOnUiThread {
                completeDiskRestore(fragment, request, restored)
            }
        }
        return true
    }

    private fun readFullDataFromDisk(
        context: Context,
        accountKey: String,
        model: Any,
        parseMethod: Method,
    ): DiskRestoreResult? {
        val snapshot = CollectionSearchCacheStore.read(context, accountKey) ?: return null
        if (!snapshot.fullReady || snapshot.rawPages.isEmpty()) {
            dbg {
                "restoreFullDataFromDisk skip fullReady=${snapshot.fullReady} " +
                    "pages=${snapshot.rawPages.size}"
            }
            return null
        }

        val dedupe = LinkedHashMap<String, Any>(1024)
        snapshot.rawPages.forEach { raw ->
            val page = parseCollectionPage(model, parseMethod, raw)
            page.forEach { item ->
                dedupe[resolveMarkStableKey(item)] = item
            }
        }
        val trustedFull = isTrustedFullSnapshot(snapshot)
        val restored = dedupe.values.toList()
        if (restored.isEmpty() && !trustedFull) return null
        dbg {
            "restoreFullDataFromDisk restored=${restored.size} pages=${snapshot.rawPages.size} " +
                "trustedFull=$trustedFull"
        }
        return DiskRestoreResult(restored, trustedFull)
    }

    private fun completeDiskRestore(
        fragment: Any,
        request: CollectionSearchRequest,
        restored: DiskRestoreResult?,
    ) {
        val current = sSessions.current(fragment, request) ?: return
        val intent = current.finishDiskRestore(request, resolveCurrentAccount(fragment.javaClass.classLoader)) ?: return
        val accountKey = request.sourceAccount ?: return
        val fetchOnMiss = intent.fetchOnMiss
        val userVisible = intent.userVisible

        if (restored != null) {
            replaceModelDataset(fragment, restored.items)
            putFullDataCache(accountKey, restored.items, fullReady = restored.trustedFull)
            current.fullDataReady = restored.trustedFull
            if (restored.trustedFull) {
                suppressLoadingFooter(fragment)
            } else {
                startFetchAfterDiskRestoreMiss(fragment, current, fetchOnMiss, userVisible)
            }
            if (current.active) {
                applyFilter(fragment, current.query, fromUser = false)
            }
            return
        }

        startFetchAfterDiskRestoreMiss(fragment, current, fetchOnMiss, userVisible)
    }

    private fun startFetchAfterDiskRestoreMiss(
        fragment: Any,
        state: CollectionSearchPageSession,
        fetchOnMiss: Boolean,
        userVisible: Boolean,
    ) {
        if (
            !isSessionCurrent(fragment, state) ||
            !fetchOnMiss ||
            state.fullDataReady ||
            state.fetchingAll ||
            state.fullLoadRequested
        ) {
            return
        }
        state.fullLoadRequested = true
        startFetchAllCollections(fragment, userVisible = userVisible)
        if (userVisible) {
            showToast(findHostActivity(fragment), UiText.CollectionSearch.TOAST_NO_CACHE)
        }
    }

    private fun getFullDataCache(accountKey: String): CollectionSearchMemoryCache.Snapshot? = memoryCache.get(accountKey)

    private fun putFullDataCache(accountKey: String, items: List<Any>, fullReady: Boolean) {
        memoryCache.put(accountKey, items, fullReady)
    }

    private fun persistFullSnapshot(context: Context?, request: CollectionSearchRequest, result: LoadAllResult?) {
        val data = result ?: return
        if (!data.complete || data.rawPages.isEmpty()) {
            dbg {
                "persistFullSnapshot skip complete=${data.complete} " +
                    "items=${data.items.size} pages=${data.rawPages.size}"
            }
            return
        }
        context ?: return
        val accountKey = request.completeCacheAccount(data.complete, data.rawPages.isNotEmpty()) ?: return
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

    private fun persistFirstPageSnapshot(fragment: Any, accountKey: String, rawPage: String?) {
        if (rawPage.isNullOrBlank()) return
        val context = resolveAppContext(fragment) ?: return
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
        return CollectionSearchPageSession.accountKey(readCurrentAccount(cl))
    }

    private fun readCurrentAccount(cl: ClassLoader?): String? {
        if (cl == null) return null
        val method = sRuntimeTargets?.currentAccountMethod ?: return null
        return runCatching { method.invoke(null)?.toString().orEmpty() }.getOrDefault("")
    }

    private fun installAdapterFooterHook(adapterClass: Class<*>) {
        if (!sHookedAdapterClasses.add(adapterClass)) return
        val mod = XposedCompat.module ?: return
        dbg { "installAdapterFooterHook class=${adapterClass.name}" }

        val targets = sRuntimeTargets?.adapter ?: return
        val getCount = targets.countMethod
        if (getCount != null) {
            getCount.isAccessible = true
            RuntimeHooks.builder(mod, getCount, "CollectionSearchHook", "installAdapterFooterHook:getCount").intercept { chain ->
                val result = chain.proceed()
                val rawCount = (result as? Int) ?: return@intercept result
                val adapter = chain.thisObject ?: return@intercept rawCount
                val fragment = sSessions.adapterOwner(adapter) ?: return@intercept rawCount
                val state = sSessions[fragment] ?: return@intercept rawCount
                if (!state.active || !state.syncFooterVisible) return@intercept rawCount
                if (rawCount > 0) return@intercept rawCount
                dbg { "force getCount=1 for empty filtered result" }
                1
            }
        }

        val isEnabled = targets.enabledMethod
        if (isEnabled != null) {
            isEnabled.isAccessible = true
            RuntimeHooks.builder(mod, isEnabled, "CollectionSearchHook", "installAdapterFooterHook:isEnabled").intercept { chain ->
                val result = chain.proceed()
                val enabled = (result as? Boolean) ?: return@intercept result
                val adapter = chain.thisObject ?: return@intercept enabled
                val fragment = sSessions.adapterOwner(adapter) ?: return@intercept enabled
                val state = sSessions[fragment] ?: return@intercept enabled
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

        val getView = targets.viewMethod ?: return

        getView.isAccessible = true
        RuntimeHooks.builder(mod, getView, "CollectionSearchHook", "installAdapterFooterHook:getView").intercept { chain ->
            val result = chain.proceed()
            val adapter = chain.thisObject ?: return@intercept result
            val fragment = sSessions.adapterOwner(adapter) ?: return@intercept result
            val state = sSessions[fragment] ?: return@intercept result
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
        sSessions.bindAdapter(adapter, fragment)
        installAdapterFooterHook(adapter.javaClass)
        val footerAccess = sRuntimeTargets?.adapter ?: return
        dbg {
            "updateSyncActionFooter show=$show showFooter=${methodName(footerAccess.showFooterMethod)} " +
                "showArg=true/false " +
                "loading=${methodName(footerAccess.loadingMethod)} hasMore=${methodName(footerAccess.hasMoreMethod)}"
        }
        runCatching { footerAccess.loadingMethod?.invoke(adapter, false) }
        runCatching { footerAccess.hasMoreMethod?.invoke(adapter, show) }
        runCatching { footerAccess.showFooterMethod?.invoke(adapter, show) }
        runCatching { footerAccess.notifyMethod?.invoke(adapter) }
    }

    private fun suppressLoadingFooter(fragment: Any) {
        val presenter = resolvePresenter(fragment) ?: return
        val adapter = resolvePresenterAdapter(presenter) ?: return
        sSessions.bindAdapter(adapter, fragment)
        installAdapterFooterHook(adapter.javaClass)
        ensureFragmentState(fragment).syncFooterVisible = false
        val footerAccess = sRuntimeTargets?.adapter ?: return
        dbg { "suppressLoadingFooter showFooter=${methodName(footerAccess.showFooterMethod)}" }
        runCatching { footerAccess.loadingMethod?.invoke(adapter, false) }
        runCatching { footerAccess.hasMoreMethod?.invoke(adapter, false) }
        runCatching { footerAccess.showFooterMethod?.invoke(adapter, false) }
        runCatching { footerAccess.notifyMethod?.invoke(adapter) }
    }

    private fun resolvePresenterAdapter(presenter: Any): Any? {
        val fieldName = sRuntimeTargets?.presenterAdapterField ?: return null
        return readFieldValue(presenter, fieldName)
    }

    private fun resolveNetworkBridge(cl: ClassLoader?): CollectionNetworkTargets? =
        if (cl == null) null else sRuntimeTargets?.network

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
            XposedCompat.logD { "[CollectionSearchHook] add search button failed: ${t.message}" }
        }
    }

    private fun resolveNavigationBar(activity: Activity): Any? {
        val targets = sRuntimeTargets ?: return null
        val navigationBar = if (targets.activityNavControllerField != null) {
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
        CollectionSearchDialog.show(
            activity = activity,
            currentQuery = state.query,
            onSearch = { query ->
                if (isSessionCurrent(fragment, state)) applyFilter(fragment, query, fromUser = true)
            },
            onClearQuery = {
                if (isSessionCurrent(fragment, state)) clearFilter(fragment, fromUser = true)
            },
            onRefresh = {
                if (isSessionCurrent(fragment, state)) triggerManualFullSync(fragment, userVisible = true)
            },
        )
    }

    private fun applyFilter(fragment: Any, query: String, fromUser: Boolean) {
        val state = ensureFragmentState(fragment)
        dbg {
            "applyFilter query='${query.trim()}' fromUser=$fromUser " +
                "fullReady=${state.fullDataReady} fetchingAll=${state.fetchingAll} requested=${state.fullLoadRequested}"
        }
        if (!state.fullDataReady) {
            val restoredFull = restoreFullDataFromCache(fragment, state)
            val waitingForDisk = if (restoredFull) {
                false
            } else {
                restoreFullDataFromDiskAsync(
                    fragment = fragment,
                    state = state,
                    fetchOnMiss = true,
                    userVisible = fromUser,
                )
            }
            if (
                !state.fullDataReady &&
                !waitingForDisk &&
                !state.fetchingAll &&
                !state.fullLoadRequested
            ) {
                state.fullLoadRequested = true
                startFetchAllCollections(fragment, userVisible = fromUser)
                if (fromUser) {
                    showToast(findHostActivity(fragment), UiText.CollectionSearch.TOAST_NO_CACHE)
                }
            }
        }

        val trustedCacheItems = if (state.fullDataReady) getTrustedFullCacheItems(fragment, state) else null
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
        val matches = CollectionSearchIndex.filter(fullList, tokens, ::buildMarkSearchText)
        val filtered = matches.items

        state.query = query.trim()
        state.active = true
        state.indexMap = matches.positions
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
            XposedCompat.logD { "[CollectionSearchHook] apply adapter list failed: ${t.message}" }
        } finally {
            state.applying = false
        }
    }

    private fun resolvePresenterListSetter(clazz: Class<*>): Method? =
        sRuntimeTargets?.presenterListSetterMethod?.takeIf { it.declaringClass.isAssignableFrom(clazz) }

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

    private fun resolveModelListGetter(clazz: Class<*>): Method? =
        sRuntimeTargets?.modelListGetterMethod?.takeIf { it.declaringClass.isAssignableFrom(clazz) }

    private fun resolveModelParseMethod(clazz: Class<*>): Method? =
        sRuntimeTargets?.modelParseMethod?.takeIf { it.declaringClass.isAssignableFrom(clazz) }

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

    private fun readFieldValue(instance: Any, field: Field): Any? =
        if (field.declaringClass.isInstance(instance)) runCatching { field.get(instance) }.getOrNull() else null

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

    private fun resolveModelListField(model: Any): Field? =
        sRuntimeTargets?.modelListField?.takeIf { it.declaringClass.isInstance(model) }

    private fun replaceFragmentDisplayList(fragment: Any, allItems: List<Any>) {
        val field = resolveFragmentDisplayListField(fragment) ?: return
        runCatching {
            field.set(fragment, ArrayList(allItems))
        }
    }

    private fun resolveFragmentDisplayListField(fragment: Any): Field? =
        sRuntimeTargets?.fragmentDisplayListField?.takeIf { it.declaringClass.isInstance(fragment) }

    private fun buildMarkSearchText(mark: Any): String {
        sMarkTextCache[mark]?.let { return it }
        val accessor = sRuntimeTargets?.mark ?: return ""
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

    private fun readString(target: Any, method: Method?): String? {
        if (method == null) return null
        return try {
            method.invoke(target)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveMarkStableKey(mark: Any): String {
        val accessor = sRuntimeTargets?.mark ?: return "obj:${System.identityHashCode(mark)}"
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
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private fun updateSearchButtonVisual(activity: Activity?) {
        val host = activity ?: return
        val button = sActivityStates[host]?.buttonView ?: return
        val fragment = findActiveThreadFragment(host)
        val isFiltered = fragment != null && isFilterActive(fragment)
        button.alpha = if (isFiltered) 1.0f else 0.85f
    }

    private fun isEditMode(activity: Activity): Boolean =
        invokeNoArg(activity, sRuntimeTargets?.editModeMethod) == true

    private fun findActiveThreadFragment(activity: Activity): Any? {
        val supportManager = invokeNoArg(activity, sRuntimeTargets?.page?.supportManager) ?: return null
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
            val childManager = invokeNoArg(fragment, sRuntimeTargets?.page?.childManager)
            if (childManager != null) {
                collectFragments(childManager, queue)
            }
        }
        return null
    }

    private fun collectFragments(manager: Any, queue: ArrayDeque<Any>) {
        val raw = invokeNoArg(manager, sRuntimeTargets?.page?.fragments) as? List<*> ?: return
        for (fragment in raw) if (fragment != null) queue.addLast(fragment)
    }

    private fun isFragmentAdded(fragment: Any): Boolean {
        val result = invokeNoArg(fragment, sRuntimeTargets?.page?.isAdded) as? Boolean
        return result ?: true
    }

    private fun findHostActivity(fragment: Any): Activity? {
        return invokeNoArg(fragment, sRuntimeTargets?.page?.activity) as? Activity
    }

    private fun invokeNoArg(target: Any, method: Method?): Any? =
        if (method != null && method.declaringClass.isInstance(target)) runCatching { method.invoke(target) }.getOrNull() else null

    private fun showToast(activity: Activity?, message: String) {
        val host = activity ?: return
        if (!isActivityAlive(host)) return
        host.runOnUiThread {
            Toast.makeText(host, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isActivityAlive(activity: Activity): Boolean {
        if (activity.isFinishing) return false
        return !activity.isDestroyed
    }
}
