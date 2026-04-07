package com.forbidad4tieba.hook.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.ScanLogger
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.utils.ViewExt
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object SettingsMenuHook {
    private const val DIALOG_CORNER_DP = 24f
    private const val DIALOG_INSET_DP = 14f

    private data class SwitchItem(
        val label: String,
        val description: String,
        val prefKey: String,
        val supported: Boolean,
        val defaultValue: Boolean = false,
        val actionIcon: String? = null,
        val onActionClick: (() -> Unit)? = null
    )

    private data class SettingGroup(
        val name: String,
        val items: List<SwitchItem>
    )

    private val sSettingsFieldCache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Class<*>, Field>())
    @Volatile private var sInitialScanDialogHookInstalled = false
    @Volatile private var sInitialScanDialogShown = false
    @Volatile private var sInitialScanResumeUnhooks: Array<Any>? = null

    internal fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val mod = XposedCompat.module ?: return
        val className = symbols.settingsClass
        val methodName = symbols.settingsInitMethod
        val containerField = symbols.settingsContainerField
        if (className == null || methodName == null || containerField == null) {
            XposedCompat.log("[SettingsMenuHook] SKIP - missing symbols: class=$className, method=$methodName, field=$containerField")
            return
        }
        try {
            val navClass = XposedCompat.findClassOrNull("com.baidu.tbadk.core.view.NavigationBar", cl)
            if (navClass == null) {
                XposedCompat.log("[SettingsMenuHook] NavigationBar class NOT FOUND")
                return
            }
            val method = XposedCompat.findMethodOrNull(className, cl, methodName, Context::class.java, navClass)
            if (method == null) {
                XposedCompat.log("[SettingsMenuHook] method NOT FOUND: $className.$methodName")
                return
            }
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                XposedCompat.logD("[SettingsMenuHook] > settings init intercepted")
                val context = chain.args[0] as Context
                try {
                    val settingsContainer = resolveSettingsContainer(chain.thisObject, containerField)
                    if (settingsContainer != null && ViewExt.markSettingsLongPressBound(settingsContainer)) {
                        settingsContainer.setOnLongClickListener {
                            showModuleSettingsDialog(settingsContainer.context ?: context, cl)
                            true
                        }
                        XposedCompat.logD("[SettingsMenuHook] > long-press listener bound")
                    }
                } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
                result
            }
            XposedCompat.log("[SettingsMenuHook] hook INSTALLED: $className.$methodName")
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] FAILED ($className.$methodName): ${t.message}")
            XposedCompat.log(t)
        }
    }

    fun ensureInitialScanDialogHook(classLoader: ClassLoader) {
        val mod = XposedCompat.module ?: return
        synchronized(this) {
            if (sInitialScanDialogHookInstalled) return
            sInitialScanDialogHookInstalled = true
        }
        try {
            val handles = mutableListOf<Any>()
            for (method in Activity::class.java.declaredMethods) {
                if (method.name != "onResume") continue
                method.isAccessible = true
                val handle = mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val activity = chain.thisObject as? Activity
                    if (activity != null && activity.packageName == Constants.TARGET_PACKAGE && !activity.isFinishing) {
                        val shouldShow = synchronized(this@SettingsMenuHook) {
                            if (sInitialScanDialogShown) false else {
                                sInitialScanDialogShown = true
                                true
                            }
                        }
                        if (shouldShow) {
                            unhookInitialScanDialogHook()
                            startSymbolScanWithDialog(activity, classLoader)
                        }
                    }
                    result
                }
                handles.add(handle)
            }
            sInitialScanResumeUnhooks = handles.toTypedArray()
        } catch (t: Throwable) {
            XposedCompat.log("Failed to hook initial scan dialog: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun unhookInitialScanDialogHook() {
        val handles = sInitialScanResumeUnhooks ?: return
        sInitialScanResumeUnhooks = null
        for (handle in handles) {
            try {
                (handle as? java.io.Closeable)?.close()
                    ?: (handle as? AutoCloseable)?.close()
            } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
        }
    }

    private fun resolveSettingsContainer(owner: Any?, preferredFieldName: String): View? {
        if (owner == null) return null
        val cls = owner.javaClass
        val cached = sSettingsFieldCache[cls]
        if (cached != null) {
            return try { cached.get(owner) as? View } catch (_: Throwable) { null }
        }
        try {
            val field = cls.getDeclaredField(preferredFieldName)
            if (View::class.java.isAssignableFrom(field.type)) {
                field.isAccessible = true
                sSettingsFieldCache[cls] = field
                return field.get(owner) as? View
            }
        } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
        
        val found = cls.declaredFields.firstOrNull {
            it.type.name == "android.widget.RelativeLayout"
        } ?: cls.declaredFields.firstOrNull {
            View::class.java.isAssignableFrom(it.type)
        } ?: return null
        return try {
            found.isAccessible = true
            sSettingsFieldCache[cls] = found
            found.get(owner) as? View
        } catch (_: Throwable) { null }
    }

    private fun showModuleSettingsDialog(context: Context, classLoader: ClassLoader?) {
        try {
            val prefs = ConfigManager.getPrefs(context)
            val density = context.resources.displayMetrics.density
            val padding = (20 * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, padding / 2)
            }

            val currentSymbols = HookSymbolResolver.getMemorySymbols()
            val homeTabsSupported = currentSymbols?.homeTabClass != null

            // 逻辑排序分组
            val groups = listOf(
                SettingGroup("内容屏蔽", listOf(
                    SwitchItem("屏蔽信息流广告", "屏蔽首页帖子间的推广广告", "block_ad", true, true),
                    SwitchItem("屏蔽信息流视频", "隐藏首页中带视频的帖子", "block_feed_video", true, false),
                    SwitchItem("屏蔽直播卡片", "移除信息流中直播帖子", "block_live", true, false),
                    SwitchItem("屏蔽首页我的进吧", "隐藏首页顶部最近访问的吧", "block_my_forum", true, false)
                )),
                SettingGroup("UI 净化", listOf(
                    SwitchItem("精简首页顶部 Tab", "仅保留推荐、搜索、发帖", "simplify_home_tabs", homeTabsSupported, false),
                    SwitchItem("精简底部 Tab", "移除底部导航条小卖部入口", "simplify_bottom_tabs", true, false),
                    SwitchItem("屏蔽帖子底部横幅", "隐藏底部导流横幅及贴吧群", "hide_pb_bottom_enter_bar", true, false),
                    SwitchItem("精简进吧页面", "仅保留我关注的吧并自动展开", "filter_enter_forum_web", true, false)
                )),
                SettingGroup("扩展", listOf(
                    SwitchItem("信息流预加载", "浏览时自动静默加载下一页", "enable_auto_load_more", true, false),
                    SwitchItem("禁止自动刷新", "避免首页被强制重置刷新列表", "disable_auto_refresh", true, false),
                    SwitchItem("开启自动签到", "启动时自动签到所有已关注的吧", "enable_auto_sign_in", true, false, "▶") {
                        com.forbidad4tieba.hook.feature.signin.AutoSignInManager.tryAutoSignIn(context, force = true)
                    }
                ))
            )
            
            val allSwitchViews = mutableListOf<Switch>()

            // 顶部一键开关
            val allEnabledInit = groups.flatMap { it.items }.all { prefs.getBoolean(it.prefKey, it.defaultValue) }
            val masterRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = "一键开启",
                description = "快速开启或关闭所有选项",
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = allEnabledInit,
                onCheckedChange = { isChecked ->
                    val editor = prefs.edit()
                    groups.forEach { g ->
                        g.items.forEach { editor.putBoolean(it.prefKey, isChecked) }
                    }
                    editor.apply()
                    
                    allSwitchViews.forEach { if (it.isEnabled) it.isChecked = isChecked }
                    val msg = if (isChecked) "已开启所有配置" else "已关闭所有配置"
                    Toast.makeText(context, "$msg（重启生效）", Toast.LENGTH_SHORT).show()
                }
            )
            root.addView(masterRow)
            root.addView(createDivider(context, padding))

            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    val gap = View(context)
                    gap.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (12 * density).toInt())
                    root.addView(gap)
                }
                
                val headerLabel = TextView(context).apply {
                    text = group.name
                    textSize = 13.5f
                    setTextColor(0xFF4C87F7.toInt())
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, (padding * 0.8f).toInt(), 0, (padding * 0.4f).toInt())
                }
                root.addView(headerLabel)

                group.items.forEach { item ->
                    val finalLabel = if (item.supported) item.label else "${item.label} (暂不支持)"
                    
                    val rowView = createSwitchRow(context, prefs, finalLabel, item.description, item.prefKey, padding, item.supported, item.defaultValue, item.actionIcon, item.onActionClick)
                    root.addView(rowView)
                    
                    if (rowView is ViewGroup) {
                        for (childIndex in 0 until rowView.childCount) {
                            val child = rowView.getChildAt(childIndex)
                            if (child is Switch) allSwitchViews.add(child)
                        }
                    }
                }
            }

            root.addView(createDivider(context, padding))
            val aboutContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, padding)
                
                val gap = View(context)
                gap.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (12 * density).toInt())
                addView(gap)
                
                addView(TextView(context).apply {
                    text = "关于"
                    textSize = 13.5f
                    setTextColor(0xFF4C87F7.toInt())
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, (padding * 0.8f).toInt(), 0, (padding * 0.4f).toInt())
                })
            }

            val moduleVersion = com.forbidad4tieba.hook.BuildConfig.VERSION_NAME
            val tiebaVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                "未知"
            }

            aboutContainer.addView(createAboutItem(context, density, padding, "版本", "$tiebaVersion   $moduleVersion"))
            aboutContainer.addView(createAboutItem(context, density, padding, "作者", "aikavvak12una", "https://github.com/aikavvak12una/ForbidAd4TieBa"))
            aboutContainer.addView(createAboutItem(context, density, padding, "提交反馈", "加入QQ群组", "https://qm.qq.com/q/wPYuwVuAGA"))

            root.addView(aboutContainer)

            val scrollContainer = ScrollView(context).apply {
                addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            val titleView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padding, padding, padding, padding / 2)

                addView(TextView(context).apply {
                    text = "模块设置"
                    textSize = 20f
                    setTextColor(0xFF000000.toInt())
                    typeface = Typeface.DEFAULT_BOLD
                })

                addView(android.widget.ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_popup_sync)
                    setColorFilter(0xFF4C87F7.toInt())
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                    val lp = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt())
                    lp.leftMargin = (6 * density).toInt()
                    layoutParams = lp
                    setOnClickListener {
                        it.animate().rotationBy(360f).setDuration(400).start()
                        startSymbolScanWithDialog(context, classLoader ?: context.classLoader)
                    }
                })
            }

            val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            builder.setCustomTitle(titleView)
            builder.setView(scrollContainer)
            builder.setPositiveButton("保存") { _, _ ->
                Toast.makeText(context, "设置已保存，重启贴吧生效。", Toast.LENGTH_SHORT).show()
            }

            val dialog = builder.create()
            dialog.show()
            dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
        } catch (t: Throwable) {
            XposedCompat.log("Failed to show settings dialog: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun startSymbolScanWithDialog(context: Context, classLoader: ClassLoader?) {
        val activity = findActivityFromContext(context)
        if (activity == null) {
            Toast.makeText(context, "TBHook: 无法获取界面上下文，改为后台扫描", Toast.LENGTH_SHORT).show()
            HookSymbolResolver.manualRescanAsync(context, classLoader ?: context.classLoader)
            return
        }
        val cl = classLoader ?: activity.classLoader
        if (cl == null) {
            Toast.makeText(activity, "TBHook: 无法获取类加载器", Toast.LENGTH_SHORT).show()
            return
        }

        val density = activity.resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val logs = StringBuilder(2048)
        val scanBeginMs = System.currentTimeMillis()
        val ui = Handler(Looper.getMainLooper())
        var finished = false

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val logView = TextView(activity).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF202020.toInt())
            setTextIsSelectable(true)
            text = "准备扫描..."
        }

        val scroll = ScrollView(activity).apply {
            addView(
                logView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (260 * density).toInt()))

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (12 * density).toInt(), 0, 0)
        }
        val cancelBtn = Button(activity).apply {
            text = "取消"
            styleScanActionButton(density, 0xFF2E3A46.toInt())
            updateButtonEnabledState(false)
        }
        val copyBtn = Button(activity).apply {
            text = "复制日志"
            styleScanActionButton(density, 0xFF4C87F7.toInt())
        }
        val restartBtn = Button(activity).apply {
            text = "重启"
            styleScanActionButton(density, 0xFF4C87F7.toInt())
            updateButtonEnabledState(false)
        }
        val btnLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val btnLpWithGap = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = (8 * density).toInt()
        }
        buttonRow.addView(cancelBtn, btnLp)
        buttonRow.addView(copyBtn, btnLpWithGap)
        buttonRow.addView(restartBtn, btnLpWithGap)
        root.addView(buttonRow)

        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("反混淆扫描")
            .setView(root)
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
        }

        fun appendLog(line: String) {
            ui.post {
                val elapsed = System.currentTimeMillis() - scanBeginMs
                logs.append('[').append(elapsed).append(" ms] ").append(line).append('\n')
                logView.text = logs.toString()
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }

        copyBtn.setOnClickListener {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = logs.toString()
            if (cm != null && text.isNotEmpty()) {
                cm.setPrimaryClip(ClipData.newPlainText("tbhook_scan_log", text))
                Toast.makeText(activity, "TBHook: 日志已复制", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "TBHook: 暂无可复制日志", Toast.LENGTH_SHORT).show()
            }
        }

        cancelBtn.setOnClickListener {
            if (finished) dialog.dismiss()
        }

        restartBtn.setOnClickListener {
            if (!finished) return@setOnClickListener
            appendLog("Restart requested")
            restartHostApp(activity)
        }

        dialog.show()
        appendLog("Scan started")
        appendLog("activity=${activity.javaClass.name}")
        appendLog("process=${activity.packageName}")
        appendLog("classLoader=${cl.javaClass.name}@${System.identityHashCode(cl)}")

        thread(name = "tbhook-symbol-scan", isDaemon = true) {
            var source = "unsupported"
            try {
                val symbols = HookSymbolResolver.resolve(
                    context = activity,
                    cl = cl,
                    forceRescan = true,
                    showToast = false,
                    logger = ScanLogger { line -> appendLog(line) },
                )
                source = symbols.source
                appendLog("Result source=${symbols.source}")
            } catch (t: Throwable) {
                appendLog("Exception: ${t.message ?: "unknown"}")
            } finally {
                ui.post {
                    finished = true
                    cancelBtn.updateButtonEnabledState(true)
                    restartBtn.updateButtonEnabledState(true)
                    val summary = when (source) {
                        "scan" -> "Scan completed"
                        "partial" -> "Scan partially completed"
                        else -> "Scan failed"
                    }
                    logs.append(summary).append('\n')
                    logView.text = logs.toString()
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }
    }

    private fun findActivityFromContext(context: Context?): Activity? {
        var current = context
        var guard = 0
        while (current != null && guard < 12) {
            if (current is Activity) return current
            current = if (current is ContextWrapper) current.baseContext else null
            guard++
        }
        return null
    }

    private fun restartHostApp(activity: Activity) {
        try {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                activity.startActivity(launchIntent)
            }
        } catch (t: Throwable) {
            XposedCompat.log("restart launch failed: ${t.message}")
            XposedCompat.log(t)
        }

        try { activity.finishAffinity() } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }

        try {
            Handler(Looper.getMainLooper()).postDelayed({
                try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
                try { exitProcess(0) } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
            }, 200)
        } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
    }

    private fun applyUnifiedDialogCardStyle(window: android.view.Window, density: Float) {
        val bg = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            cornerRadius = DIALOG_CORNER_DP * density
        }
        window.setBackgroundDrawable(InsetDrawable(bg, (DIALOG_INSET_DP * density).toInt()))
    }

    private fun Button.styleScanActionButton(density: Float, textColor: Int) {
        textSize = 13.5f
        setAllCaps(false)
        setTextColor(textColor)
        val hPadding = (6 * density).toInt()
        val vPadding = (4 * density).toInt()
        setPadding(hPadding, vPadding, hPadding, vPadding)
        minWidth = 0
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        setBackgroundColor(0x00000000)
    }

    private fun Button.updateButtonEnabledState(enabled: Boolean) {
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.45f
    }

    private fun createSwitchRow(
        context: Context, prefs: android.content.SharedPreferences,
        label: String, description: String?, prefKey: String?, padding: Int, enabled: Boolean = true,
        defaultValue: Boolean = false,
        actionIcon: String? = null,
        onActionClick: (() -> Unit)? = null,
        onCheckedChange: ((Boolean) -> Unit)? = null
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (padding * 0.6f).toInt(), 0, (padding * 0.6f).toInt())
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tvLabel = TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(if (enabled) 0xFF222222.toInt() else 0xFF999999.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        textContainer.addView(tvLabel)

        if (description != null) {
            val density = context.resources.displayMetrics.density
            val tvDesc = TextView(context).apply {
                text = description
                textSize = 12f
                setTextColor(if (enabled) 0xFF888888.toInt() else 0xFFBBBBBB.toInt())
                setPadding(0, (2 * density).toInt(), (12 * density).toInt(), 0)
            }
            textContainer.addView(tvDesc)
        }

        row.addView(textContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f))

        if (actionIcon != null && onActionClick != null) {
            val density = context.resources.displayMetrics.density
            val actionBtn = TextView(context).apply {
                text = actionIcon
                textSize = 18f
                setTextColor(if (enabled) 0xFF4C87F7.toInt() else 0xFF999999.toInt())
                gravity = Gravity.CENTER
                setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
                setOnClickListener { if (enabled) onActionClick() }
            }
            row.addView(actionBtn)
        }

        @Suppress("DEPRECATION")
        val sw = Switch(context).apply {
            isChecked = if (enabled && prefKey != null) prefs.getBoolean(prefKey, defaultValue) else defaultValue
            isEnabled = enabled
            
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )
            thumbTintList = android.content.res.ColorStateList(states, intArrayOf(
                0xFF4C87F7.toInt(),
                0xFFFAFAFA.toInt()
            ))
            trackTintList = android.content.res.ColorStateList(states, intArrayOf(
                0x704C87F7.toInt(),
                0x40000000.toInt()
            ))

            setOnClickListener { 
                // Using onClickListener instead of OnCheckedChangeListener to avoid firing when changed programmatically
            }
            setOnCheckedChangeListener { _, isChecked ->
                if (enabled) {
                    if (prefKey != null) {
                        prefs.edit().putBoolean(prefKey, isChecked).apply()
                    }
                    onCheckedChange?.invoke(isChecked)
                }
            }
        }
        row.addView(sw)

        return row
    }

    private fun createAboutItem(context: Context, density: Float, padding: Int, title: String, content: String, url: String? = null): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, padding / 2, 0, padding / 2)
            
            addView(TextView(context).apply {
                text = title
                textSize = 15.5f
                setTextColor(0xFF222222.toInt())
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            })
            
            addView(TextView(context).apply {
                text = content
                textSize = 14f
                setTextColor(if (url != null) 0xFF4C87F7.toInt() else 0xFF666666.toInt())
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(0, (4 * density).toInt(), 0, 0)
                
                if (url != null) {
                    setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    private fun createDivider(context: Context, padding: Int): View {
        val divider = View(context)
        divider.setBackgroundColor(0xFFEEEEEE.toInt())
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        lp.setMargins(0, padding / 4, 0, padding / 4)
        divider.layoutParams = lp
        return divider
    }
}
