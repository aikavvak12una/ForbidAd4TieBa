package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.NavBarSearchButton
import com.forbidad4tieba.hook.utils.ReflectionUtils
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.utils.ClearableInputRow
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

/**
 * 历史页 PbHistoryActivity 的本地搜索。
 *
 * 这个 hook 只过滤已经加载的本地历史列表。
 * 不触发网络请求。
 */
object HistorySearchHook {
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private data class RuntimeTargets(
        val adapterField: String,
        val adapterSetListMethod: String,
        val listField: String,
        val activityListUpdateMethod: String?,
        val activityNavBarField: String,
        val threadNameMethod: String,
        val forumNameMethod: String,
        val userNameMethod: String,
        val descriptionMethod: String,
        val threadIdMethod: String,
        val postIdMethod: String,
        val liveIdMethod: String,
    )

    private data class ActivityState(
        var buttonView: View? = null,
        var allItems: ArrayList<Any> = ArrayList(),
        var query: String = "",
        var active: Boolean = false,
        var applying: Boolean = false,
    )

    private data class HistoryAccessor(
        val threadName: Method?,
        val forumName: Method?,
        val userName: Method?,
        val description: Method?,
        val threadId: Method?,
        val postId: Method?,
        val liveId: Method?,
    )

    private val sStates = Collections.synchronizedMap(WeakHashMap<Activity, ActivityState>())
    private val sAdapterSetterCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Method>())
    private val sHistoryAccessorCache = Collections.synchronizedMap(WeakHashMap<Class<*>, HistoryAccessor>())
    private val sItemSearchTextCache = Collections.synchronizedMap(WeakHashMap<Any, String>())
    private val sFieldLookupCache = Collections.synchronizedMap(WeakHashMap<Class<*>, MutableMap<String, Field?>>())
    @Volatile
    private var sRuntimeTargets: RuntimeTargets? = null

    private fun dbg(msg: String) {
        XposedCompat.logD("[HistorySearchHook][dbg] $msg")
    }

    fun hook(cl: ClassLoader, symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols()) {
        val mod = XposedCompat.module ?: return
        val targets = resolveTargets(symbols)
        if (targets == null) {
            XposedCompat.log(
                "[HistorySearchHook] skipped: scan symbols missing " +
                    "(historyAdapterField/historyAdapterSetListMethod/historyListField/" +
                    "historyActivityNavBarField/" +
                    "historyThreadNameMethod/historyForumNameMethod/historyUserNameMethod/" +
                    "historyDescriptionMethod/historyThreadIdMethod/historyPostIdMethod/historyLiveIdMethod)",
            )
            return
        }
        sRuntimeTargets = targets
        sHistoryAccessorCache.clear()
        val activityClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_HISTORY_ACTIVITY_CLASS, cl)
        if (activityClass == null) {
            XposedCompat.log("[HistorySearchHook] class NOT FOUND: ${StableTiebaHookPoints.PB_HISTORY_ACTIVITY_CLASS}")
            return
        }

        try {
            installLifecycleHooks(mod, activityClass)
            installListUpdateHooks(mod, activityClass)
            XposedCompat.log("[HistorySearchHook] hook INSTALLED")
        } catch (t: Throwable) {
            XposedCompat.log("[HistorySearchHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolveTargets(symbols: HookSymbols?): RuntimeTargets? {
        val scanSymbols = symbols ?: return null
        val adapterField = scanSymbols.historyAdapterField?.takeIf { it.isNotBlank() } ?: return null
        val adapterSetListMethod = scanSymbols.historyAdapterSetListMethod?.takeIf { it.isNotBlank() } ?: return null
        val listField = scanSymbols.historyListField?.takeIf { it.isNotBlank() } ?: return null
        val activityListUpdateMethod = scanSymbols.historyActivityListUpdateMethod?.takeIf { it.isNotBlank() }
        val activityNavBarField = scanSymbols.historyActivityNavBarField?.takeIf { it.isNotBlank() } ?: return null
        val threadNameMethod = scanSymbols.historyThreadNameMethod?.takeIf { it.isNotBlank() } ?: return null
        val forumNameMethod = scanSymbols.historyForumNameMethod?.takeIf { it.isNotBlank() } ?: return null
        val userNameMethod = scanSymbols.historyUserNameMethod?.takeIf { it.isNotBlank() } ?: return null
        val descriptionMethod = scanSymbols.historyDescriptionMethod?.takeIf { it.isNotBlank() } ?: return null
        val threadIdMethod = scanSymbols.historyThreadIdMethod?.takeIf { it.isNotBlank() } ?: return null
        val postIdMethod = scanSymbols.historyPostIdMethod?.takeIf { it.isNotBlank() } ?: return null
        val liveIdMethod = scanSymbols.historyLiveIdMethod?.takeIf { it.isNotBlank() } ?: return null
        return RuntimeTargets(
            adapterField = adapterField,
            adapterSetListMethod = adapterSetListMethod,
            listField = listField,
            activityListUpdateMethod = activityListUpdateMethod,
            activityNavBarField = activityNavBarField,
            threadNameMethod = threadNameMethod,
            forumNameMethod = forumNameMethod,
            userNameMethod = userNameMethod,
            descriptionMethod = descriptionMethod,
            threadIdMethod = threadIdMethod,
            postIdMethod = postIdMethod,
            liveIdMethod = liveIdMethod,
        )
    }

    private fun installLifecycleHooks(mod: io.github.libxposed.api.XposedModule, activityClass: Class<*>) {
        XposedCompat.findMethodOrNull(activityClass, "onCreate", Bundle::class.java)?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    ensureSearchButton(activity)
                    refreshAllItemsFromField(activity)
                }
                result
            }
        }

        XposedCompat.findMethodOrNull(activityClass, "onResume")?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    ensureSearchButton(activity)
                    refreshAllItemsFromField(activity)
                    val state = ensureState(activity)
                    if (state.active && state.query.isNotBlank()) {
                        applyFilter(activity, state.query, fromUser = false)
                    } else {
                        updateSearchButtonVisual(activity)
                    }
                }
                result
            }
        }

        XposedCompat.findMethodOrNull(activityClass, "onDestroy")?.let { method ->
            mod.hook(method).intercept { chain ->
                val activity = chain.thisObject as? Activity
                val result = chain.proceed()
                if (activity != null) {
                    sStates.remove(activity)
                }
                result
            }
        }
    }

    private fun installListUpdateHooks(mod: io.github.libxposed.api.XposedModule, activityClass: Class<*>) {
        val methodName = sRuntimeTargets?.activityListUpdateMethod ?: return
        val method = findMethodInHierarchy(activityClass, methodName) { target ->
            !Modifier.isStatic(target.modifiers) &&
                target.returnType == Void.TYPE &&
                target.parameterTypes.size == 1 &&
                (List::class.java.isAssignableFrom(target.parameterTypes[0]) ||
                    ArrayList::class.java.isAssignableFrom(target.parameterTypes[0]))
        } ?: return
        method.isAccessible = true
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity ?: return@intercept result
            val incoming = (chain.args.getOrNull(0) as? List<*>)?.filterNotNull()?.toList().orEmpty()
            updateAllItems(activity, incoming)
            result
        }
    }

    private fun ensureState(activity: Activity): ActivityState {
        synchronized(sStates) {
            return sStates[activity] ?: ActivityState().also { sStates[activity] = it }
        }
    }

    private fun refreshAllItemsFromField(activity: Activity) {
        val listField = sRuntimeTargets?.listField
        val list = readHistoryListFromField(activity)
        if (list.isNotEmpty() || (listField != null && readFieldValue(activity, listField) is List<*>)) {
            updateAllItems(activity, list)
        }
    }

    private fun updateAllItems(activity: Activity, incoming: List<Any>) {
        val state = ensureState(activity)
        state.allItems = ArrayList(incoming)
        dbg("updateAllItems size=${state.allItems.size} active=${state.active} query='${state.query}'")
        if (state.active && state.query.isNotBlank()) {
            applyFilter(activity, state.query, fromUser = false)
        } else if (!state.active) {
            updateSearchButtonVisual(activity)
        }
    }

    private fun ensureSearchButton(activity: Activity) {
        if (!isActivityAlive(activity)) return
        val state = ensureState(activity)
        if (state.buttonView?.parent != null) return

        val navigationBar = resolveNavigationBar(activity) ?: return
        val alignRight = NavBarSearchButton.resolveNavigationRightAlign(navigationBar.javaClass.classLoader) ?: return
        val addMethod = NavBarSearchButton.resolveAddCustomViewMethod(navigationBar.javaClass) ?: return
        val iconDrawable = NavBarSearchButton.resolveSearchIconDrawable(activity, navigationBar)
        val button = NavBarSearchButton.buildSearchButton(
            activity = activity,
            iconDrawable = iconDrawable,
            contentDesc = UiText.HistorySearch.BUTTON_CONTENT_DESC,
        ) { onSearchButtonClick(it) }

        try {
            addMethod.invoke(navigationBar, alignRight, button, null)
            state.buttonView = button
            updateSearchButtonVisual(activity)
            NavBarSearchButton.scheduleReposition(button) {
                runCatching { placeSearchButtonLeftOfClearButton(button, navigationBar) }
            }
        } catch (t: Throwable) {
            XposedCompat.logD("[HistorySearchHook] add search button failed: ${t.message}")
        }
    }

    private fun resolveNavigationBar(activity: Activity): Any? {
        val navBarField = sRuntimeTargets?.activityNavBarField ?: return null
        val navigationBar = readFieldValue(activity, navBarField) ?: return null
        if (NavBarSearchButton.resolveAddCustomViewMethod(navigationBar.javaClass) == null) return null
        return navigationBar
    }

    private fun placeSearchButtonLeftOfClearButton(button: View, navigationBar: Any) {
        val navRoot = NavBarSearchButton.extractNavigationRootView(navigationBar)
        val parent = (button.parent as? ViewGroup)
            ?: navRoot?.let { NavBarSearchButton.findParentOfView(it, button) }
            ?: return
        val buttonIndex = parent.indexOfChild(button)
        if (buttonIndex < 0) return

        val clearContainer = findClearTextButtonContainer(parent, button)
            ?: navRoot?.let { findClearTextButtonContainer(it, button) }
            ?: return
        val targetParent = clearContainer.parent as? ViewGroup ?: return
        if (targetParent !== parent) return

        val targetIndex = parent.indexOfChild(clearContainer)
        if (targetIndex < 0) return
        val desired = if (buttonIndex < targetIndex) targetIndex - 1 else targetIndex
        if (desired == buttonIndex) return

        val lp = button.layoutParams
        parent.removeViewAt(buttonIndex)
        val safeIndex = desired.coerceIn(0, parent.childCount)
        parent.addView(button, safeIndex, lp)
        dbg("reposition search button parent=${parent.javaClass.simpleName} button=$buttonIndex->$safeIndex clear=$targetIndex")
    }

    private fun findClearTextButtonContainer(root: View, searchButton: View): View? {
        if (root === searchButton) return null
        if (root is TextView && root.text?.isNotBlank() == true) return root
        if (root !is ViewGroup) return null

        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child === searchButton) continue
            if (containsTextButton(child)) return child
        }
        return null
    }

    private fun containsTextButton(root: View): Boolean {
        if (root is TextView && root.text?.isNotBlank() == true) return true
        if (root !is ViewGroup) return false
        for (i in 0 until root.childCount) {
            if (containsTextButton(root.getChildAt(i))) return true
        }
        return false
    }

    private fun onSearchButtonClick(activity: Activity) {
        if (!isActivityAlive(activity)) return
        val state = ensureState(activity)
        showSearchDialog(
            activity = activity,
            currentQuery = state.query,
            onSearch = { query ->
                if (query.isBlank()) {
                    clearFilter(activity, fromUser = true)
                } else {
                    applyFilter(activity, query, fromUser = true)
                }
            },
            onClear = {
                clearFilter(activity, fromUser = true)
            },
        )
    }

    private fun showSearchDialog(
        activity: Activity,
        currentQuery: String,
        onSearch: (String) -> Unit,
        onClear: () -> Unit,
    ) {
        val density = activity.resources.displayMetrics.density
        val hPad = (18 * density).toInt()
        val vPad = (10 * density).toInt()
        val input = EditText(activity).apply {
            hint = UiText.HistorySearch.DIALOG_HINT
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
            clearSymbol = UiText.HistorySearch.INPUT_CLEAR_SYMBOL,
            verticalPadding = vPad,
            onClear = onClear
        )
        val underline = View(activity).apply {
            setBackgroundColor(0xFF2F7DFF.toInt())
            alpha = 0.72f
        }
        input.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            underline.alpha = if (hasFocus) 1.0f else 0.72f
        }
        val inputContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, (8 * density).toInt(), hPad, (4 * density).toInt())
            addView(
                inputRow,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            addView(
                underline,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (2.0f * density).toInt().coerceAtLeast(1)
                ).apply { topMargin = (2 * density).toInt() }
            )
        }

        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(UiText.HistorySearch.DIALOG_TITLE)
            .setView(inputContainer)
            .setPositiveButton(UiText.HistorySearch.DIALOG_ACTION_SEARCH) { _, _ ->
                onSearch(input.text?.toString().orEmpty().trim())
            }
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.let { window -> applySearchDialogCardStyle(window, density) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                isAllCaps = false
                setTextColor(0xFF4C87F7.toInt())
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                isAllCaps = false
                setTextColor(0xFF6B7280.toInt())
            }
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

    private fun applyFilter(activity: Activity, query: String, fromUser: Boolean) {
        val state = ensureState(activity)
        val fullList = if (state.allItems.isNotEmpty()) {
            state.allItems
        } else {
            ArrayList(readHistoryListFromField(activity))
        }
        val normalized = normalizeQuery(query)
        val tokens = normalized.split(' ').filter { it.isNotBlank() }

        val filtered = ArrayList<Any>(fullList.size)
        fullList.forEach { item ->
            val matched = if (tokens.isEmpty()) {
                true
            } else {
                val text = buildHistorySearchText(item)
                tokens.all { token -> text.contains(token) }
            }
            if (matched) filtered.add(item)
        }

        state.query = query.trim()
        state.active = true
        applyAdapterList(activity, filtered)
        updateSearchButtonVisual(activity)
        dbg("applyFilter query='${state.query}' matched=${filtered.size} full=${fullList.size} tokens=${tokens.size}")
        if (fromUser) {
            showToast(activity, UiText.HistorySearch.toastMatched(filtered.size, fullList.size))
        }
    }

    private fun clearFilter(activity: Activity, fromUser: Boolean) {
        val state = ensureState(activity)
        val had = state.active || state.query.isNotBlank()
        state.query = ""
        state.active = false
        val fullList = if (state.allItems.isNotEmpty()) state.allItems else ArrayList(readHistoryListFromField(activity))
        applyAdapterList(activity, fullList)
        updateSearchButtonVisual(activity)
        if (fromUser && had) {
            showToast(activity, UiText.HistorySearch.TOAST_FILTER_CLEARED)
        }
    }

    private fun applyAdapterList(activity: Activity, list: List<Any>) {
        val state = ensureState(activity)
        if (state.applying) return
        val adapter = resolveHistoryAdapter(activity) ?: return
        val setter = resolveAdapterSetListMethod(adapter.javaClass) ?: return
        state.applying = true
        try {
            setter.invoke(adapter, ArrayList(list))
        } catch (t: Throwable) {
            XposedCompat.logD("[HistorySearchHook] apply adapter list failed: ${t.message}")
        } finally {
            state.applying = false
        }
    }

    private fun resolveHistoryAdapter(activity: Activity): Any? {
        val fieldName = sRuntimeTargets?.adapterField ?: return null
        val adapter = readFieldValue(activity, fieldName) ?: return null
        if (resolveAdapterSetListMethod(adapter.javaClass) == null) return null
        return adapter
    }

    private fun resolveAdapterSetListMethod(clazz: Class<*>): Method? {
        sAdapterSetterCache[clazz]?.let { return it }
        val methodName = sRuntimeTargets?.adapterSetListMethod ?: return null
        val resolved = findMethodInHierarchy(clazz, methodName) { method ->
            method.parameterTypes.size == 1 &&
                method.returnType == Void.TYPE &&
                (List::class.java.isAssignableFrom(method.parameterTypes[0]) ||
                    ArrayList::class.java.isAssignableFrom(method.parameterTypes[0]))
        }?.apply { isAccessible = true }
        if (resolved != null) sAdapterSetterCache[clazz] = resolved
        return resolved
    }

    private fun readHistoryListFromField(activity: Activity): List<Any> {
        val fieldName = sRuntimeTargets?.listField ?: return emptyList()
        val direct = readFieldValue(activity, fieldName) as? List<*>
        if (direct != null) {
            return direct.filterNotNull()
        }
        return emptyList()
    }

    private fun buildHistorySearchText(item: Any): String {
        sItemSearchTextCache[item]?.let { return it }
        val accessor = resolveHistoryAccessor(item.javaClass)
        val parts = ArrayList<String>(7)
        readString(item, accessor.threadName)?.let(parts::add)
        readString(item, accessor.forumName)?.let(parts::add)
        readString(item, accessor.userName)?.let(parts::add)
        readString(item, accessor.description)?.let(parts::add)
        readString(item, accessor.threadId)?.let(parts::add)
        readString(item, accessor.postId)?.let(parts::add)
        readString(item, accessor.liveId)?.let(parts::add)
        return normalizeQuery(parts.joinToString(" ")).also { sItemSearchTextCache[item] = it }
    }

    private fun resolveHistoryAccessor(clazz: Class<*>): HistoryAccessor {
        sHistoryAccessorCache[clazz]?.let { return it }
        val targets = sRuntimeTargets
        val accessor = HistoryAccessor(
            threadName = findGetter(clazz, targets?.threadNameMethod),
            forumName = findGetter(clazz, targets?.forumNameMethod),
            userName = findGetter(clazz, targets?.userNameMethod),
            description = findGetter(clazz, targets?.descriptionMethod),
            threadId = findGetter(clazz, targets?.threadIdMethod),
            postId = findGetter(clazz, targets?.postIdMethod),
            liveId = findGetter(clazz, targets?.liveIdMethod),
        )
        sHistoryAccessorCache[clazz] = accessor
        return accessor
    }

    private fun findGetter(clazz: Class<*>, name: String?): Method? {
        if (name.isNullOrBlank()) return null
        return findMethodInHierarchy(clazz, name) { it.parameterTypes.isEmpty() }
    }

    private fun readString(target: Any, method: Method?): String? {
        if (method == null) return null
        return runCatching {
            method.invoke(target)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun updateSearchButtonVisual(activity: Activity?) {
        val host = activity ?: return
        val button = sStates[host]?.buttonView ?: return
        val active = sStates[host]?.active == true && sStates[host]?.query?.isNotBlank() == true
        button.alpha = if (active) 1.0f else 0.85f
    }

    private fun normalizeQuery(input: String): String {
        return input.lowercase(Locale.ROOT).replace(MULTI_SPACE_REGEX, " ").trim()
    }

    private fun readFieldValue(instance: Any, fieldName: String): Any? {
        return try {
            val field = findFieldInHierarchy(instance.javaClass, fieldName) ?: return null
            field.get(instance)
        } catch (_: Throwable) {
            null
        }
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

    private fun isActivityAlive(activity: Activity): Boolean {
        if (activity.isFinishing) return false
        return !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)
    }

    private fun showToast(activity: Activity?, message: String) {
        val host = activity ?: return
        if (!isActivityAlive(host)) return
        host.runOnUiThread { Toast.makeText(host, message, Toast.LENGTH_SHORT).show() }
    }
}
