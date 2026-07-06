package com.forbidad4tieba.hook.feature.ui

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Api102ModuleFacade
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

object HomeTabRedDotBlockHook {
    private const val METHOD_ON_BIND_VIEW_HOLDER = "onBindViewHolder"
    private const val METHOD_REFRESH = "refresh"
    private const val METHOD_REFRESH_FEED_UI_OPT = "refreshFeedUIOpt"
    private const val METHOD_GET_RED_NUM = "getRedNum"
    private const val MAX_DOT_SIZE_DP = 20f
    private const val MAX_TEXT_MARKER_WIDTH_DP = 56f
    private const val MAX_TEXT_MARKER_HEIGHT_DP = 24f
    private const val MAX_MSG_TAB_SCAN_DEPTH = 6
    private const val MAX_DOT_RATIO = 1.6f

    private val smallRedDotDrawableNames = arrayOf(
        "icon_news_red_dot",
        "icon_news_red_dot_stroke",
        "icon_official_bar_red_dot",
    )

    private val installed = AtomicBoolean(false)
    private val applyErrorLogged = AtomicBoolean(false)
    private val configuredMsgTabRoots = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val configuredMsgTabCandidates = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val scheduledMsgTabRoots = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val itemViewFieldCache = Collections.synchronizedMap(mutableMapOf<Class<*>, Field?>())
    private val applyingMsgTab = ThreadLocal<Boolean>()
    @Volatile
    private var messageRedDotGetRedNumMethod: Method? = null

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!ConfigManager.snapshot().isHomeTabRedDotHidden) {
            XposedCompat.logD("[HomeTabRedDotBlockHook] disabled by config")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        try {
            val smallRedDotResourceIds = resolveSmallRedDotResourceIds()
            val hookedPoints = listOf(
                StableTiebaHookPoints.HOME_SLIDING_INDEX_TAB_VIEW_CLASS,
                StableTiebaHookPoints.HOME_TAB_BAR_RIGHT_SLOT_CLASS,
            ).mapNotNull { className ->
                hookRedDotMethod(mod, cl, className)
            } + hookMsgTabSideNavigation(mod, cl) +
                hookMessageRedDotView(mod, cl) +
                hookSmallRedDotBackgroundResource(mod, cl, smallRedDotResourceIds)

            if (hookedPoints.isEmpty()) {
                installed.set(false)
                XposedCompat.log("[HomeTabRedDotBlockHook] hook point missing")
                return
            }

            XposedCompat.log(
                "[HomeTabRedDotBlockHook] hook INSTALLED: " +
                    hookedPoints.joinToString(),
            )
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[HomeTabRedDotBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookRedDotMethod(
        mod: Api102ModuleFacade,
        cl: ClassLoader,
        className: String,
    ): String? {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.logD("[HomeTabRedDotBlockHook] class NOT FOUND: $className")
            return null
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            StableTiebaHookPoints.METHOD_SET_RED_DOT_VISIBLE,
            java.lang.Boolean.TYPE,
        ) ?: run {
            XposedCompat.logD(
                "[HomeTabRedDotBlockHook] method NOT FOUND: " +
                    "${clazz.name}.${StableTiebaHookPoints.METHOD_SET_RED_DOT_VISIBLE}(boolean)",
            )
            return null
        }

        mod.hook(method).intercept { chain ->
            if (!ConfigManager.snapshot().isHomeTabRedDotHidden) {
                return@intercept chain.proceed()
            }
            val visible = chain.args.getOrNull(0) as? Boolean ?: return@intercept chain.proceed()
            if (!visible) {
                return@intercept chain.proceed()
            }
            chain.proceed(arrayOf(false))
        }
        return "${clazz.name}.${method.name}(boolean)"
    }

    private fun hookMsgTabSideNavigation(
        mod: Api102ModuleFacade,
        cl: ClassLoader,
    ): List<String> {
        val adapterClass = XposedCompat.findClassOrNull(
            StableTiebaHookPoints.MSG_TAB_SIDE_NAVIGATION_ADAPTER_CLASS,
            cl,
        ) ?: run {
            XposedCompat.logD(
                "[HomeTabRedDotBlockHook] class NOT FOUND: " +
                    StableTiebaHookPoints.MSG_TAB_SIDE_NAVIGATION_ADAPTER_CLASS,
            )
            return emptyList()
        }

        val hooked = ArrayList<String>(2)
        XposedCompat.findMethodOrNull(
            adapterClass,
            StableTiebaHookPoints.METHOD_ON_CREATE_VIEW_HOLDER,
            ViewGroup::class.java,
            java.lang.Integer.TYPE,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                afterMsgTabHolderChanged(result)
                result
            }
            hooked.add("${adapterClass.name}.${method.name}(ViewGroup,int)")
        } ?: XposedCompat.logD(
            "[HomeTabRedDotBlockHook] method NOT FOUND: " +
                "${adapterClass.name}.${StableTiebaHookPoints.METHOD_ON_CREATE_VIEW_HOLDER}(ViewGroup,int)",
        )

        val viewHolderClass = XposedCompat.findClassOrNull(
            "${StableTiebaHookPoints.RECYCLER_VIEW_CLASS}\$ViewHolder",
            cl,
        )
        if (viewHolderClass == null) {
            XposedCompat.logD("[HomeTabRedDotBlockHook] class NOT FOUND: RecyclerView.ViewHolder")
        } else {
            XposedCompat.findMethodOrNull(
                adapterClass,
                METHOD_ON_BIND_VIEW_HOLDER,
                viewHolderClass,
                java.lang.Integer.TYPE,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    afterMsgTabHolderChanged(chain.args.firstOrNull())
                    result
                }
                hooked.add("${adapterClass.name}.${method.name}(ViewHolder,int)")
            } ?: XposedCompat.logD(
                "[HomeTabRedDotBlockHook] method NOT FOUND: " +
                    "${adapterClass.name}.$METHOD_ON_BIND_VIEW_HOLDER(ViewHolder,int)",
            )
        }

        return hooked
    }

    private fun hookMessageRedDotView(
        mod: Api102ModuleFacade,
        cl: ClassLoader,
    ): List<String> {
        val clazz = XposedCompat.findClassOrNull(
            StableTiebaHookPoints.MESSAGE_RED_DOT_VIEW_CLASS,
            cl,
        ) ?: run {
            XposedCompat.logD(
                "[HomeTabRedDotBlockHook] class NOT FOUND: " +
                    StableTiebaHookPoints.MESSAGE_RED_DOT_VIEW_CLASS,
            )
            return emptyList()
        }

        messageRedDotGetRedNumMethod = XposedCompat.findMethodOrNull(clazz, METHOD_GET_RED_NUM)
        if (messageRedDotGetRedNumMethod == null) {
            XposedCompat.logD("[HomeTabRedDotBlockHook] method NOT FOUND: ${clazz.name}.$METHOD_GET_RED_NUM()")
        }

        val hooked = ArrayList<String>(3)
        listOf(METHOD_REFRESH, METHOD_REFRESH_FEED_UI_OPT).forEach { methodName ->
            XposedCompat.findMethodOrNull(clazz, methodName, java.lang.Integer.TYPE)?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val count = chain.args.firstOrNull() as? Int
                    if (isRuntimeEnabled() && count != null && count <= 0) {
                        suppressMessageRedDotViewLater(chain.thisObject as? View)
                    }
                    result
                }
                hooked.add("${clazz.name}.${method.name}(int)")
            } ?: XposedCompat.logD(
                "[HomeTabRedDotBlockHook] method NOT FOUND: ${clazz.name}.$methodName(int)",
            )
        }

        XposedCompat.findMethodOrNull(
            clazz,
            METHOD_REFRESH,
            String::class.java,
            java.lang.Boolean.TYPE,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val text = chain.args.getOrNull(0) as? String
                val threeDot = chain.args.getOrNull(1) as? Boolean == true
                if (isRuntimeEnabled() && (threeDot || !hasDigit(text))) {
                    suppressMessageRedDotViewLater(chain.thisObject as? View)
                }
                result
            }
            hooked.add("${clazz.name}.${method.name}(String,boolean)")
        } ?: XposedCompat.logD(
            "[HomeTabRedDotBlockHook] method NOT FOUND: ${clazz.name}.$METHOD_REFRESH(String,boolean)",
        )

        return hooked
    }

    private fun hookSmallRedDotBackgroundResource(
        mod: Api102ModuleFacade,
        cl: ClassLoader,
        smallRedDotResourceIds: Set<Int>,
    ): List<String> {
        if (smallRedDotResourceIds.isEmpty()) {
            XposedCompat.logD("[HomeTabRedDotBlockHook] resource hook skipped: drawable ids unavailable")
            return emptyList()
        }
        val skinManagerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.SKIN_MANAGER_CLASS, cl) ?: run {
            XposedCompat.logD(
                "[HomeTabRedDotBlockHook] class NOT FOUND: " +
                    StableTiebaHookPoints.SKIN_MANAGER_CLASS,
            )
            return emptyList()
        }

        val hooked = ArrayList<String>(2)
        listOf(
            arrayOf<Class<*>>(View::class.java, java.lang.Integer.TYPE),
            arrayOf<Class<*>>(View::class.java, java.lang.Integer.TYPE, java.lang.Integer.TYPE),
        ).forEach { parameterTypes ->
            XposedCompat.findMethodOrNull(
                skinManagerClass,
                StableTiebaHookPoints.METHOD_SET_BACKGROUND_RESOURCE,
                *parameterTypes,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val target = chain.args.firstOrNull() as? View
                    val resId = chain.args.getOrNull(1) as? Int
                    if (isRuntimeEnabled() && target != null && isSmallRedDotResource(resId, smallRedDotResourceIds)) {
                        suppressSmallRedDotResourceViewLater(target)
                    }
                    result
                }
                hooked.add("${skinManagerClass.name}.${method.name}(${parameterTypes.size})")
            } ?: XposedCompat.logD(
                "[HomeTabRedDotBlockHook] method NOT FOUND: " +
                    "${skinManagerClass.name}.${StableTiebaHookPoints.METHOD_SET_BACKGROUND_RESOURCE}" +
                    "(${parameterTypes.joinToString { it.simpleName }})",
            )
        }
        return hooked
    }

    private fun resolveSmallRedDotResourceIds(): Set<Int> {
        val context = ConfigManager.getAppContext() ?: run {
            XposedCompat.log("[HomeTabRedDotBlockHook] resource hook unavailable: app context missing")
            return emptySet()
        }
        val appContext = context.applicationContext ?: context
        val resources = appContext.resources
        val packageName = appContext.packageName

        val ids = LinkedHashSet<Int>(smallRedDotDrawableNames.size)
        val missing = ArrayList<String>()
        for (name in smallRedDotDrawableNames) {
            val id = try {
                resources.getIdentifier(name, "drawable", packageName)
                    .takeIf { it > 0 && resources.getResourceEntryName(it) == name }
            } catch (_: Throwable) {
                null
            }
            if (id == null) {
                missing += name
            } else {
                ids += id
            }
        }
        if (missing.isNotEmpty() || ids.size != smallRedDotDrawableNames.size) {
            XposedCompat.log(
                "[HomeTabRedDotBlockHook] resource hook unavailable: missing or duplicate drawables=" +
                    missing.joinToString(),
            )
            return emptySet()
        }
        return ids
    }

    private fun afterMsgTabHolderChanged(holder: Any?) {
        if (!isRuntimeEnabled()) return
        val root = holderItemView(holder) ?: return
        applyMsgTabSmallDotSuppressionSafely(root)
        configureMsgTabRootRefresh(root)
        scheduleMsgTabRootReapply(root)
    }

    private fun holderItemView(holder: Any?): View? {
        if (holder == null) return null
        return try {
            val field = findItemViewField(holder.javaClass) ?: return null
            field.get(holder) as? View
        } catch (_: Throwable) {
            null
        }
    }

    private fun findItemViewField(clazz: Class<*>): Field? {
        synchronized(itemViewFieldCache) {
            val cached = itemViewFieldCache[clazz]
            if (cached != null || itemViewFieldCache.containsKey(clazz)) return cached
        }
        val resolved = try {
            XposedCompat.findField(clazz, "itemView")
        } catch (_: Throwable) {
            null
        }
        synchronized(itemViewFieldCache) {
            itemViewFieldCache[clazz] = resolved
        }
        return resolved
    }

    private fun configureMsgTabRootRefresh(root: View) {
        val firstConfigure = synchronized(configuredMsgTabRoots) {
            if (configuredMsgTabRoots.containsKey(root)) {
                false
            } else {
                configuredMsgTabRoots[root] = true
                true
            }
        }
        if (!firstConfigure) return

        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                scheduleMsgTabRootReapply(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            applyMsgTabSmallDotSuppressionSafely(v)
        }
    }

    private fun scheduleMsgTabRootReapply(root: View) {
        if (!isRuntimeEnabled()) return
        synchronized(scheduledMsgTabRoots) {
            if (scheduledMsgTabRoots.containsKey(root)) return
            scheduledMsgTabRoots[root] = true
        }
        root.post {
            try {
                applyMsgTabSmallDotSuppressionSafely(root)
            } finally {
                synchronized(scheduledMsgTabRoots) {
                    scheduledMsgTabRoots.remove(root)
                }
            }
        }
    }

    private fun applyMsgTabSmallDotSuppressionSafely(root: View) {
        if (!isRuntimeEnabled() || isApplyingMsgTab()) return
        try {
            applyingMsgTab.set(true)
            applyMsgTabSmallDotSuppression(root, depth = 0)
        } catch (t: Throwable) {
            logApplyError("msg tab suppression failed", t)
        } finally {
            applyingMsgTab.remove()
        }
    }

    private fun applyMsgTabSmallDotSuppression(view: View, depth: Int) {
        if (isMsgTabSmallDotCandidate(view)) {
            configureMsgTabCandidateRefresh(view)
            suppressSmallDotView(view)
            return
        }
        val group = view as? ViewGroup ?: return
        if (depth >= MAX_MSG_TAB_SCAN_DEPTH) return
        for (index in 0 until group.childCount) {
            applyMsgTabSmallDotSuppression(group.getChildAt(index) ?: continue, depth + 1)
        }
    }

    private fun configureMsgTabCandidateRefresh(view: View) {
        val firstConfigure = synchronized(configuredMsgTabCandidates) {
            if (configuredMsgTabCandidates.containsKey(view)) {
                false
            } else {
                configuredMsgTabCandidates[view] = true
                true
            }
        }
        if (!firstConfigure) return

        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (isRuntimeEnabled() && isMsgTabSmallDotCandidate(v)) {
                suppressSmallDotView(v)
            }
        }
    }

    private fun suppressSmallDotView(view: View) {
        val suppressAlpha = !isMessageRedDotView(view)
        view.clearAnimation()
        if (view.visibility != View.GONE) {
            view.visibility = View.GONE
        }
        if (suppressAlpha && view.alpha != 0f) {
            view.alpha = 0f
        }
    }

    private fun isMsgTabSmallDotCandidate(view: View): Boolean {
        if (view is ViewGroup || view is ImageView) return false
        if (view.isClickable || view.isLongClickable || view.isFocusable || view.hasOnClickListeners()) {
            return false
        }

        val textView = view as? TextView
        if (textView != null) {
            return isSmallNonNumericTextMarker(textView)
        }
        if (isMessageRedDotView(view)) {
            return shouldSuppressMessageRedDotView(view)
        }
        return isSmallSquareView(view, MAX_DOT_SIZE_DP)
    }

    private fun isSmallNonNumericTextMarker(view: TextView): Boolean {
        val text = view.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return false
        if (text.any { it.isDigit() }) return false
        if (text.length > 2) return false

        val maxWidthPx = dp(view, MAX_TEXT_MARKER_WIDTH_DP)
        val maxHeightPx = dp(view, MAX_TEXT_MARKER_HEIGHT_DP)
        val width = positiveDimension(view, horizontal = true)
        val height = positiveDimension(view, horizontal = false)
        val widthFits = width == null || width <= maxWidthPx
        val heightFits = height == null || height <= maxHeightPx
        return widthFits && heightFits
    }

    private fun isSmallSquareView(view: View, maxSizeDp: Float): Boolean {
        val width = positiveDimension(view, horizontal = true) ?: return false
        val height = positiveDimension(view, horizontal = false) ?: return false
        val maxSizePx = dp(view, maxSizeDp)
        if (width > maxSizePx || height > maxSizePx) return false
        val ratio = maxOf(width, height).toFloat() / maxOf(1, minOf(width, height)).toFloat()
        return ratio <= MAX_DOT_RATIO
    }

    private fun positiveDimension(view: View, horizontal: Boolean): Int? {
        val layoutSize = if (horizontal) view.layoutParams?.width else view.layoutParams?.height
        val measuredSize = if (horizontal) view.measuredWidth else view.measuredHeight
        val actualSize = if (horizontal) view.width else view.height
        val minimumSize = if (horizontal) view.minimumWidth else view.minimumHeight
        return listOf(layoutSize, measuredSize, actualSize, minimumSize)
            .filterNotNull()
            .filter { it > 0 }
            .maxOrNull()
    }

    private fun dp(view: View, value: Float): Int {
        return maxOf(1, (view.resources.displayMetrics.density * value).toInt())
    }

    private fun suppressMessageRedDotViewLater(view: View?) {
        if (view == null) return
        if (shouldSuppressMessageRedDotView(view)) {
            suppressSmallDotView(view)
        }
        view.post {
            if (isRuntimeEnabled() && shouldSuppressMessageRedDotView(view)) {
                suppressSmallDotView(view)
            }
        }
    }

    private fun suppressSmallRedDotResourceViewLater(view: View) {
        suppressSmallDotView(view)
        view.post {
            if (isRuntimeEnabled()) {
                suppressSmallDotView(view)
            }
        }
    }

    private fun shouldSuppressMessageRedDotView(view: View): Boolean {
        if (!isMessageRedDotView(view)) return false
        val getRedNumMethod = messageRedDotGetRedNumMethod ?: return false
        return try {
            !hasDigit(getRedNumMethod.invoke(view) as? String)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isMessageRedDotView(view: View): Boolean {
        return hasClassInHierarchy(view.javaClass, StableTiebaHookPoints.MESSAGE_RED_DOT_VIEW_CLASS)
    }

    private fun hasClassInHierarchy(clazz: Class<*>, className: String): Boolean {
        var current: Class<*>? = clazz
        while (current != null) {
            if (current.name == className) return true
            current = current.superclass
        }
        return false
    }

    private fun isSmallRedDotResource(resId: Int?, smallRedDotResourceIds: Set<Int>): Boolean {
        if (resId == null || resId <= 0) return false
        return resId in smallRedDotResourceIds
    }

    private fun hasDigit(text: String?): Boolean {
        return text?.any { it.isDigit() } == true
    }

    private fun isRuntimeEnabled(): Boolean {
        return ConfigManager.snapshot().isHomeTabRedDotHidden
    }

    private fun isApplyingMsgTab(): Boolean = applyingMsgTab.get() == true

    private fun logApplyError(message: String, t: Throwable) {
        if (applyErrorLogged.compareAndSet(false, true)) {
            XposedCompat.log("[HomeTabRedDotBlockHook] $message: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
