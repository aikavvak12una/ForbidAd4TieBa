package com.forbidad4tieba.hook

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.SparseBooleanArray
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        try {
            XposedHelpers.findAndHookMethod(
                android.app.Application::class.java, "attach",
                Context::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (sAppContext == null) {
                            sAppContext = param.args[0] as Context
                        }
                    }
                })
        } catch (_: Throwable) {}

        XposedBridge.log("$TAG: initialized. version=$MODULE_VERSION_NAME")

        val cl = lpparam.classLoader

        // 1. Strategy layer: fake VIP ad-free status
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberCloseAdVipClose", 1)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "isMemberCloseAdVipClose", true)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberCloseAdIsOpen", 1)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "isMemberCloseAdIsOpen", true)
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberType", 2)
        hookReturnConstant(cl, "com.baidu.tbadk.data.CloseAdData", "G1", 1)
        hookReturnConstant(cl, "com.baidu.tbadk.data.CloseAdData", "J1", 1)
        hookReturnConstant(cl, "com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt", "a", true)
        hookReturnConstant(cl, "com.baidu.tieba.nd7", "i0", true)
        hookReturnConstant(cl, "com.baidu.tieba.nd7", "l", 0)

        // 2. SDK switches & crash prevention
        hookSwitchManager(cl)
        hookZga(cl)

        // 3. Resolve ad classes once — shared by feed & post hooks
        val adClasses = resolveAdClasses(cl)

        // 4. Feed page (RecyclerView): hide ad views
        hookFeedAdViews(cl, adClasses)
        hookFeedLiveCards(cl)
        hookFeedMyForumCards(cl)

        // 5. Post page (TypeAdapter/ListView): replace ad views with empty views
        hookPostAdViews(cl, adClasses)

        // 6. Module Settings (Long press settings icon)
        hookSettingsMenu(cl)

        // 7. Home Tabs (Hide Live, Youliao, etc.)
        hookHomeTabs(cl)

        // 8. Bottom Tabs (Hide Small Shop)
        hookBottomTabs(cl)
    }

    // =============================================
    // Ad class resolution (done once at init)
    // =============================================

    private fun resolveAdClasses(cl: ClassLoader): Array<Class<*>> {
        val list = ArrayList<Class<*>>(AD_CLASS_NAMES.size)
        for (name in AD_CLASS_NAMES) {
            val c = XposedHelpers.findClassIfExists(name, cl)
            if (c != null) list.add(c)
        }
        return list.toTypedArray()
    }

    // =============================================
    // Home Tabs
    // =============================================

    private fun hookHomeTabs(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod("com.baidu.tieba.epa", cl, "j", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!isHomeTabSimplifyEnabled) return
                    @Suppress("UNCHECKED_CAST")
                    val list = XposedHelpers.getObjectField(param.thisObject, "b") as? MutableList<Any?> ?: return
                    val it = list.iterator()
                    while (it.hasNext()) {
                        val pm6 = it.next()
                        if (pm6 != null) {
                            val type = XposedHelpers.getIntField(pm6, "a")
                            if (type != 0 && type != 1) it.remove()
                        }
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook home tabs: ${t.message}")
        }
    }

    // =============================================
    // Bottom Tabs
    // =============================================

    private fun hookBottomTabs(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tbadk.abtest.UbsABTestHelper", cl,
                "retailStorePage", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isBottomTabSimplifyEnabled) param.result = false
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook bottom tabs: ${t.message}")
        }
    }

    // =============================================
    // Settings Menu
    // =============================================

    private fun hookSettingsMenu(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.g9d", cl, "h",
                Context::class.java,
                XposedHelpers.findClass("com.baidu.tbadk.core.view.NavigationBar", cl),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as Context
                        try {
                            val settingsContainer =
                                XposedHelpers.getObjectField(param.thisObject, "f") as? View
                            settingsContainer?.setOnLongClickListener {
                                showModuleSettingsDialog(context)
                                true
                            }
                        } catch (_: Throwable) {}
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook settings menu: ${t.message}")
        }
    }

    // =============================================
    // Feed page ad blocking (BdTypeRecyclerView)
    // =============================================

    private fun hookFeedAdViews(cl: ClassLoader, adClasses: Array<Class<*>>) {
        hookFeedAppAdCard(cl)
        hookFeedAdViewHolder(cl)

        // Reuse a single hook instance for all ad view classes (same logic)
        val squashOnAdBlock = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (isAdBlockEnabled && param.thisObject is View) {
                    squashView(param.thisObject as View)
                }
            }
        }

        val forceGone = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!isAdBlockEnabled) return
                val args = param.args
                if (args != null && args.isNotEmpty() && args[0] is Int) {
                    args[0] = View.GONE
                }
            }
        }

        for (clazz in adClasses) {
            try {
                XposedBridge.hookAllConstructors(clazz, squashOnAdBlock)
                XposedBridge.hookAllMethods(clazz, "onAttachedToWindow", squashOnAdBlock)
                XposedBridge.hookAllMethods(clazz, "setVisibility", forceGone)
            } catch (_: Throwable) {}
        }
    }

    private fun hookFeedLiveCards(cl: ClassLoader) {
        // Data-driven: remove live_card & my_forum items at the source
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.feed.list.TemplateAdapter", cl, "setList", List::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val args = param.args
                        if (args == null || args.isEmpty()) return
                        val arg0 = args[0]
                        if (arg0 !is List<*>) return

                        val blockLive = isLiveBlockEnabled
                        val blockForum = isMyForumBlockEnabled
                        if (!blockLive && !blockForum) return

                        // Build the set of keys to remove
                        val keysToRemove = HashSet<String>(2)
                        if (blockLive) keysToRemove.add("live_card")
                        if (blockForum) keysToRemove.add("card_feed_my_forum")

                        val filtered = filterOutTemplateKeys(arg0, keysToRemove)
                        if (filtered !== arg0) args[0] = filtered
                    }
                })
        } catch (_: Throwable) {}

        // View recycling: restore if this FeedCardView was squashed before
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.feed.card.FeedCardView", cl, "w", "com.baidu.tieba.y89",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isLiveBlockEnabled) return
                        if (param.thisObject is View) {
                            restoreViewIfSquashed(param.thisObject as View)
                        }
                    }
                })
        } catch (_: Throwable) {}

        // Safety net: squash FeedCardView ancestor when CardLiveView attaches
        try {
            val cardLiveViewClass = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.feed.component.CardLiveView", cl
            ) ?: return

            XposedBridge.hookAllConstructors(cardLiveViewClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!isLiveBlockEnabled || param.thisObject !is View) return
                    val liveView = param.thisObject as View

                    liveView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            squashAncestorFeedCard(v)
                        }
                        override fun onViewDetachedFromWindow(v: View) {}
                    })

                    // If already attached, apply immediately
                    if (liveView.isAttachedToWindow) squashAncestorFeedCard(liveView)
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookFeedMyForumCards(cl: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.feed.component.CardFeedMyForumView", cl
            ) ?: return

            val squashHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isMyForumBlockEnabled && param.thisObject is View) {
                        squashView(param.thisObject as View)
                    }
                }
            }

            XposedBridge.hookAllConstructors(clazz, squashHook)
            XposedBridge.hookAllMethods(clazz, "onAttachedToWindow", squashHook)

            // On measure: force 0-size to prevent gaps
            XposedBridge.hookAllMethods(clazz, "onMeasure", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isMyForumBlockEnabled) return
                    if (param.thisObject is View) squashView(param.thisObject as View)
                    param.result = null
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookFeedAdViewHolder(cl: ClassLoader) {
        try {
            XposedBridge.hookAllConstructors(
                XposedHelpers.findClass("com.baidu.tieba.funad.adapter.FeedAdViewHolder", cl),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!isAdBlockEnabled) return
                        try {
                            val itemView = XposedHelpers.getObjectField(param.thisObject, "itemView") as? View
                            if (itemView != null) squashView(itemView)
                        } catch (_: Throwable) {}
                    }
                })
        } catch (_: Throwable) {}
    }

    // =============================================
    // Post page ad blocking (TypeAdapter/ListView)
    // =============================================

    private fun hookPostAdViews(cl: ClassLoader, adClasses: Array<Class<*>>) {
        try {
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass("com.baidu.adp.widget.ListView.TypeAdapter", cl),
                "getView",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val args = param.args
                        if (!isAdBlockEnabled || args == null || args.size < 3) return

                        val cache = getOrCreateAdTypeCache(param.thisObject) ?: return
                        val position = castToInt(args[0])
                        if (position < 0) return

                        val viewType = safeGetItemViewType(param.thisObject, position)
                        if (viewType < 0 || !cache.get(viewType)) return

                        val ctx = resolveContext(args) ?: return
                        val convertView = args[1] as? View
                        param.result = obtainEmptyView(ctx, convertView)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!isAdBlockEnabled) return
                        val result = param.result as? View ?: return
                        if (isOurEmptyView(result)) return

                        val args = param.args
                        val position = castToInt(args[0])
                        val viewType = if (position >= 0) safeGetItemViewType(param.thisObject, position) else -1
                        val cache = getOrCreateAdTypeCache(param.thisObject)

                        // Skip if already cached
                        if (cache != null && viewType >= 0 && cache.get(viewType)) return

                        var isAd = isAdView(result, adClasses)
                        if (!isAd && result is ViewGroup) {
                            isAd = containsAdChild(result, adClasses)
                        }

                        if (isAd) {
                            if (cache != null && viewType >= 0) cache.put(viewType, true)
                            val convertView = if (args.size > 1 && args[1] is View) args[1] as View else null
                            param.result = obtainEmptyView(result.context, convertView)
                        }
                    }
                })

            // Hook PbFirstFloorRecommendAdapter
            try {
                XposedBridge.hookAllMethods(
                    XposedHelpers.findClass(
                        "com.baidu.tieba.pb.widget.adapter.PbFirstFloorRecommendAdapter", cl
                    ),
                    "onCreateViewHolder",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!isAdBlockEnabled) return
                            val holder = param.result ?: return
                            try {
                                val itemView = XposedHelpers.getObjectField(holder, "itemView") as? View
                                if (itemView != null) squashView(itemView)
                            } catch (_: Throwable) {}
                        }
                    })
            } catch (_: Throwable) {}

            XposedBridge.log("$TAG: Post page ad view interception installed")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook post page ads: ${t.message}")
        }
    }

    // =============================================
    // Utility hooks
    // =============================================

    private fun hookReturnConstant(cl: ClassLoader, className: String, methodName: String, value: Any) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isAdBlockEnabled) param.result = value
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookSwitchManager(cl: ClassLoader) {
        try {
            // Pre-build the set for O(1) lookup instead of chained equals()
            val blockedKeys = HashSet<String>(4).apply {
                add("ad_baichuan_open")
                add("bear_wxb_download")
                add("pref_key_fun_ad_sdk_enable")
            }

            XposedHelpers.findAndHookMethod(
                "com.baidu.adp.lib.featureSwitch.SwitchManager", cl, "findType", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isAdBlockEnabled) return
                        if (param.args[0] in blockedKeys) {
                            param.result = 0
                        }
                    }
                })
        } catch (_: Throwable) {}
    }

    private fun hookZga(cl: ClassLoader) {
        try {
            // Single hook instance reused for both methods
            val safeStringHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result == null) param.result = ""
                }
            }
            XposedHelpers.findAndHookMethod("com.baidu.tieba.zga", cl, "d", String::class.java, safeStringHook)
            XposedHelpers.findAndHookMethod("com.baidu.tieba.zga", cl, "f", String::class.java, safeStringHook)
        } catch (_: Throwable) {}
    }

    private fun hookFeedAppAdCard(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.recapp.lego.view.AdCardBaseView", cl, "x",
                "com.baidu.tieba.recapp.lego.model.AdCard",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!isAdBlockEnabled || param.args[0] == null) return
                        try {
                            val appInfo = XposedHelpers.callMethod(param.args[0], "getAdvertAppInfo")
                            if (appInfo != null && XposedHelpers.callMethod(appInfo, "isAppAdvert") as Boolean) {
                                squashView(param.thisObject as View)
                            }
                        } catch (_: Throwable) {}
                    }
                })
        } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "TiebaHook"
        private const val TARGET_PACKAGE = "com.baidu.tieba"
        private const val MODULE_VERSION_NAME = "20260316"

        private const val AIF_SQUASH_STATE = "AIF_SQUASH"
        private const val AIF_EMPTY_VIEW = "AIF_EMPTY"
        private const val AIF_TYPEADAPTER_AD_TYPES = "AIF_AD_TYPES"

        // Sentinel value for "no LayoutParams" in SquashState (avoids Integer boxing)
        private const val LP_UNSET = Int.MIN_VALUE

        // Centralized ad view class names — shared by feed & post hooks
        private val AD_CLASS_NAMES = arrayOf(
            "com.baidu.tieba.funad.view.AbsFeedAdxView",
            "com.baidu.tieba.recapp.lego.view.AdCardBaseView",
            "com.baidu.tieba.funad.view.TbAdVideoView",
            "com.baidu.tieba.feed.ad.compact.DelegateFunAdView",
            "com.baidu.tieba.pb.pb.main.view.PbImageAlaRecommendView",
            "com.baidu.tieba.core.widget.recommendcard.RecommendCardView",
        )

        // ---------- Cached settings ----------
        // Volatile for visibility across Xposed callback threads.
        @Volatile
        private var sCachedPrefs: SharedPreferences? = null
        @Volatile
        private var sAppContext: Context? = null

        private val prefs: SharedPreferences?
            get() {
                sCachedPrefs?.let { return it }
                val ctx = sAppContext ?: return null
                val p = ctx.getSharedPreferences("tiebahook_settings", Context.MODE_PRIVATE)
                sCachedPrefs = p
                return p
            }

        private fun getPref(key: String): Boolean {
            val p = prefs
            return p == null || p.getBoolean(key, true)
        }

        // Inline accessors — zero-allocation, single SP lookup per call
        private val isAdBlockEnabled: Boolean get() = getPref("block_ad")
        private val isLiveBlockEnabled: Boolean get() = getPref("block_live")
        private val isMyForumBlockEnabled: Boolean get() = getPref("block_my_forum")
        private val isHomeTabSimplifyEnabled: Boolean get() = getPref("simplify_home_tabs")
        private val isBottomTabSimplifyEnabled: Boolean get() = getPref("simplify_bottom_tabs")

        // =============================================
        // Settings dialog
        // =============================================

        /**
         * Settings dialog — uses a data-driven approach to eliminate per-switch boilerplate.
         */
        private fun showModuleSettingsDialog(context: Context) {
            try {
                val prefs = context.getSharedPreferences("tiebahook_settings", Context.MODE_PRIVATE)
                val density = context.resources.displayMetrics.density
                val padding = (20 * density).toInt()

                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(padding, padding / 2, padding, padding / 2)
                }

                // Define all switches: [label, pref key]
                val switches = arrayOf(
                    arrayOf("屏蔽广告", "block_ad"),
                    arrayOf("屏蔽直播内容", "block_live"),
                    arrayOf("屏蔽首页Tab (仅保留推荐)", "simplify_home_tabs"),
                    arrayOf("屏蔽小卖部Tab", "simplify_bottom_tabs"),
                    arrayOf("屏蔽推荐进吧卡片", "block_my_forum"),
                )

                for (i in switches.indices) {
                    if (i > 0) root.addView(createDivider(context, padding))
                    root.addView(createSwitchRow(context, prefs, switches[i][0], switches[i][1], padding))
                }

                val builder = AlertDialog.Builder(
                    context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                )
                builder.setTitle("模块设置")
                builder.setView(root)
                builder.setPositiveButton("保存") { _, _ ->
                    Toast.makeText(context, "设置已保存，重启应用生效", Toast.LENGTH_SHORT).show()
                }

                val dialog = builder.create()
                dialog.show()

                dialog.window?.let { window ->
                    val gd = GradientDrawable().apply {
                        setColor(0xFFFFFFFF.toInt())
                        cornerRadius = 16f * density
                    }
                    window.setBackgroundDrawable(InsetDrawable(gd, (16 * density).toInt()))
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: Failed to show settings dialog: ${t.message}")
            }
        }

        /** Creates a single switch row: [label ---- switch] */
        private fun createSwitchRow(
            context: Context,
            prefs: SharedPreferences,
            label: String,
            prefKey: String,
            padding: Int
        ): View {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, padding / 2, 0, padding / 2)
            }

            val tv = TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(0xFF222222.toInt())
            }
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f))

            @Suppress("DEPRECATION")
            val sw = Switch(context).apply {
                isChecked = prefs.getBoolean(prefKey, true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean(prefKey, isChecked).apply()
                }
            }
            row.addView(sw)

            return row
        }

        /** Creates a 1px horizontal divider */
        private fun createDivider(context: Context, padding: Int): View {
            val divider = View(context)
            divider.setBackgroundColor(0xFFEEEEEE.toInt())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            lp.setMargins(0, padding / 4, 0, padding / 4)
            divider.layoutParams = lp
            return divider
        }

        // =============================================
        // Ad view detection helpers
        // =============================================

        private fun isAdView(view: View, adClasses: Array<Class<*>>): Boolean {
            for (cls in adClasses) {
                if (cls.isInstance(view)) return true
            }
            return false
        }

        private fun containsAdChild(parent: ViewGroup, adClasses: Array<Class<*>>): Boolean {
            val count = parent.childCount
            for (i in 0 until count) {
                val child = parent.getChildAt(i)
                for (cls in adClasses) {
                    if (cls.isInstance(child)) return true
                }
            }
            return false
        }

        private fun castToInt(obj: Any?): Int {
            return if (obj is Int) obj else -1
        }

        private fun getOrCreateAdTypeCache(adapter: Any?): SparseBooleanArray? {
            if (adapter == null) return null
            return try {
                val existing = XposedHelpers.getAdditionalInstanceField(adapter, AIF_TYPEADAPTER_AD_TYPES)
                if (existing is SparseBooleanArray) {
                    existing
                } else {
                    val created = SparseBooleanArray()
                    XposedHelpers.setAdditionalInstanceField(adapter, AIF_TYPEADAPTER_AD_TYPES, created)
                    created
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun safeGetItemViewType(adapter: Any, position: Int): Int {
            return try {
                val vt = XposedHelpers.callMethod(adapter, "getItemViewType", position)
                if (vt is Int) vt else -1
            } catch (_: Throwable) {
                -1
            }
        }

        /** Resolve a Context from getView() args: [position, convertView, parent] */
        private fun resolveContext(args: Array<Any?>): Context? {
            if (args.size > 2 && args[2] is ViewGroup) return (args[2] as ViewGroup).context
            if (args.size > 1 && args[1] is View) return (args[1] as View).context
            return null
        }

        private fun isOurEmptyView(view: View): Boolean {
            return try {
                java.lang.Boolean.TRUE == XposedHelpers.getAdditionalInstanceField(view, AIF_EMPTY_VIEW)
            } catch (_: Throwable) {
                false
            }
        }

        private fun obtainEmptyView(context: Context?, reuseIfPossible: View?): View? {
            if (context == null) return reuseIfPossible
            if (reuseIfPossible != null && isOurEmptyView(reuseIfPossible)) return reuseIfPossible

            val emptyView = View(context)
            try {
                XposedHelpers.setAdditionalInstanceField(emptyView, AIF_EMPTY_VIEW, java.lang.Boolean.TRUE)
            } catch (_: Throwable) {}
            configureEmptyView(emptyView)
            return emptyView
        }

        private fun configureEmptyView(view: View) {
            try {
                view.visibility = View.GONE
                view.minimumHeight = 0
                view.minimumWidth = 0
                view.setPadding(0, 0, 0, 0)
                val lp = view.layoutParams
                if (lp !is AbsListView.LayoutParams || lp.width != 0 || lp.height != 0) {
                    view.layoutParams = AbsListView.LayoutParams(0, 0)
                }
            } catch (_: Throwable) {}
        }

        // =============================================
        // Template filtering (single-pass for multiple keys)
        // =============================================

        /**
         * Removes items whose template key matches any key in [keysToRemove].
         * Single-pass, lazy-copy (only allocates when a match is found).
         */
        private fun filterOutTemplateKeys(list: List<*>?, keysToRemove: Set<String>): List<*>? {
            if (list == null || keysToRemove.isEmpty()) return list
            val size = list.size
            if (size == 0) return list

            var filtered: ArrayList<Any?>? = null
            for (i in 0 until size) {
                val item: Any? = try { list[i] } catch (_: Throwable) { null }
                val key = getTemplateKey(item)
                if (key != null && key in keysToRemove) {
                    if (filtered == null) {
                        filtered = ArrayList(size - 1)
                        // Copy items before the first match
                        for (j in 0 until i) {
                            try { filtered.add(list[j]) } catch (_: Throwable) {}
                        }
                    }
                } else if (filtered != null) {
                    filtered.add(item)
                }
            }
            return filtered ?: list
        }

        private fun getTemplateKey(item: Any?): String? {
            if (item == null) return null
            return try {
                val key = XposedHelpers.callMethod(item, "b")
                if (key is String) key else null
            } catch (_: Throwable) {
                null
            }
        }

        // =============================================
        // View ancestor search
        // =============================================

        private fun findAncestorViewByClassName(view: View?, className: String?): View? {
            if (view == null || className == null) return null
            try {
                var parent: ViewParent? = view.parent
                while (parent is View) {
                    if (className == parent.javaClass.name) return parent
                    parent = parent.parent
                }
            } catch (_: Throwable) {}
            return null
        }

        // =============================================
        // View squash / restore
        // =============================================

        /** Tag-based marker to skip redundant squashView calls (cheaper than checking 10+ properties) */
        private val SQUASH_MARKER = Any()

        private fun squashView(view: View?) {
            if (view == null) return
            // Fast path: skip if already squashed by us
            if (view.getTag(android.R.id.icon) === SQUASH_MARKER) return
            try {
                view.visibility = View.GONE
                view.minimumHeight = 0
                view.minimumWidth = 0
                view.setPadding(0, 0, 0, 0)
                val lp = view.layoutParams
                if (lp != null) {
                    lp.width = 0
                    lp.height = 0
                    if (lp is ViewGroup.MarginLayoutParams) {
                        lp.setMargins(0, 0, 0, 0)
                    }
                    view.layoutParams = lp
                }
                view.setTag(android.R.id.icon, SQUASH_MARKER)
            } catch (_: Throwable) {}
        }

        private fun squashAncestorFeedCard(v: View) {
            val feedCard = findAncestorViewByClassName(v, "com.baidu.tieba.feed.card.FeedCardView")
            if (feedCard != null) squashViewRemembering(feedCard)
        }

        private fun squashViewRemembering(view: View?) {
            if (view == null) return
            try {
                val existing = XposedHelpers.getAdditionalInstanceField(view, AIF_SQUASH_STATE)
                var state = existing as? SquashState

                if (state == null || !state.squashed) {
                    state = SquashState(view)
                    XposedHelpers.setAdditionalInstanceField(view, AIF_SQUASH_STATE, state)
                }
                state.squashed = true
            } catch (_: Throwable) {}
            squashView(view)
        }

        private fun restoreViewIfSquashed(view: View?) {
            if (view == null) return
            try {
                val obj = XposedHelpers.getAdditionalInstanceField(view, AIF_SQUASH_STATE)
                if (obj !is SquashState) return
                if (!obj.squashed) return
                obj.restore(view)
                obj.squashed = false
                view.setTag(android.R.id.icon, null) // Clear squash marker
            } catch (_: Throwable) {}
        }

        /**
         * Lightweight snapshot of view layout properties for restore-after-recycle.
         * Uses primitive ints with LP_UNSET sentinel to avoid Integer boxing overhead.
         */
        private class SquashState(view: View) {
            val visibility: Int = view.visibility
            val minWidth: Int = view.minimumWidth
            val minHeight: Int = view.minimumHeight
            val padL: Int = view.paddingLeft
            val padT: Int = view.paddingTop
            val padR: Int = view.paddingRight
            val padB: Int = view.paddingBottom
            val lpW: Int
            val lpH: Int
            val mL: Int
            val mT: Int
            val mR: Int
            val mB: Int
            var squashed: Boolean = false

            init {
                val lp = view.layoutParams
                if (lp != null) {
                    lpW = lp.width
                    lpH = lp.height
                    if (lp is ViewGroup.MarginLayoutParams) {
                        mL = lp.leftMargin
                        mT = lp.topMargin
                        mR = lp.rightMargin
                        mB = lp.bottomMargin
                    } else {
                        mL = LP_UNSET
                        mT = LP_UNSET
                        mR = LP_UNSET
                        mB = LP_UNSET
                    }
                } else {
                    lpW = LP_UNSET
                    lpH = LP_UNSET
                    mL = LP_UNSET
                    mT = LP_UNSET
                    mR = LP_UNSET
                    mB = LP_UNSET
                }
            }

            fun restore(view: View) {
                try {
                    view.visibility = visibility
                    view.minimumWidth = minWidth
                    view.minimumHeight = minHeight
                    view.setPadding(padL, padT, padR, padB)

                    val lp = view.layoutParams
                    if (lp != null) {
                        if (lpW != LP_UNSET) lp.width = lpW
                        if (lpH != LP_UNSET) lp.height = lpH
                        if (lp is ViewGroup.MarginLayoutParams && mL != LP_UNSET) {
                            lp.setMargins(mL, mT, mR, mB)
                        }
                        view.layoutParams = lp
                    } else {
                        view.requestLayout()
                    }
                } catch (_: Throwable) {}
            }
        }
    }
}
