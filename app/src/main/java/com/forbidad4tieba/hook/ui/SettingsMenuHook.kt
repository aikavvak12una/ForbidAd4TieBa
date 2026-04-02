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
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object SettingsMenuHook {
    private const val DIALOG_CORNER_DP = 24f
    private const val DIALOG_INSET_DP = 14f

    private data class SwitchItem(
        val label: String,
        val prefKey: String,
        val supported: Boolean,
        val defaultValue: Boolean = false
    )

    private val sSettingsFieldCache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Class<*>, Field>())
    @Volatile private var sInitialScanDialogHookInstalled = false
    @Volatile private var sInitialScanDialogShown = false
    @Volatile private var sInitialScanResumeUnhooks: Array<XC_MethodHook.Unhook>? = null

    internal fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val className = symbols.settingsClass ?: return
        val methodName = symbols.settingsInitMethod ?: return
        val containerField = symbols.settingsContainerField ?: return
        try {
            val navClass = XposedHelpers.findClassIfExists("com.baidu.tbadk.core.view.NavigationBar", cl) ?: return
            XposedHelpers.findAndHookMethod(
                className, cl, methodName,
                Context::class.java,
                navClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as Context
                        try {
                            val settingsContainer = resolveSettingsContainer(param.thisObject, containerField)
                            if (settingsContainer != null && ViewExt.markSettingsLongPressBound(settingsContainer)) {
                                settingsContainer.setOnLongClickListener {
                                    showModuleSettingsDialog(settingsContainer.context ?: context, cl)
                                    true
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook settings menu($className.$methodName): ${t.message}")
        }
    }

    fun ensureInitialScanDialogHook(classLoader: ClassLoader) {
        synchronized(this) {
            if (sInitialScanDialogHookInstalled) return
            sInitialScanDialogHookInstalled = true
        }
        try {
            val unhooks = XposedBridge.hookAllMethods(Activity::class.java, "onResume", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    if (activity.packageName != Constants.TARGET_PACKAGE) return
                    if (activity.isFinishing) return
                    val shouldShow = synchronized(this@SettingsMenuHook) {
                        if (sInitialScanDialogShown) false else {
                            sInitialScanDialogShown = true
                            true
                        }
                    }
                    if (!shouldShow) return
                    unhookInitialScanDialogHook()
                    startSymbolScanWithDialog(activity, classLoader)
                }
            })
            sInitialScanResumeUnhooks = unhooks.toTypedArray()
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook initial scan dialog: ${t.message}")
        }
    }

    private fun unhookInitialScanDialogHook() {
        val unhooks = sInitialScanResumeUnhooks ?: return
        sInitialScanResumeUnhooks = null
        for (unhook in unhooks) {
            try {
                unhook.unhook()
            } catch (_: Throwable) {}
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
        } catch (_: Throwable) {}
        
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

            // 逻辑排序选项
            val switches = listOf(
                // 屏蔽类
                SwitchItem("屏蔽信息流广告", "block_ad", true, true),
                SwitchItem("屏蔽信息流视频", "block_feed_video", true, false),
                SwitchItem("屏蔽直播卡片", "block_live", true, false),
                SwitchItem("屏蔽首页我的吧", "block_my_forum", true, false),
                SwitchItem("屏蔽小卖部 Tab", "simplify_bottom_tabs", true, false),
                // UI 精简类
                SwitchItem("精简首页顶部 Tab", "simplify_home_tabs", homeTabsSupported, false),
                SwitchItem("屏蔽帖子底部横幅", "hide_pb_bottom_enter_bar", true, false),
                SwitchItem("精简进吧页面", "filter_enter_forum_web", true, false),
                // 功能类
                SwitchItem("信息流预加载", "enable_auto_load_more", true, false),
                SwitchItem("禁止自动刷新", "disable_auto_refresh", true, false),
                SwitchItem("开启自动签到", "enable_auto_sign_in", true, false),
            )
            
            val allSwitchViews = mutableListOf<Switch>()

            // 顶部一键开关
            val allEnabledInit = switches.all { prefs.getBoolean(it.prefKey, it.defaultValue) }
            val masterRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = "一键开关所有配置项",
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = allEnabledInit,
                onCheckedChange = { isChecked ->
                    val editor = prefs.edit()
                    switches.forEach { editor.putBoolean(it.prefKey, isChecked) }
                    editor.apply()
                    
                    allSwitchViews.forEach { if (it.isEnabled) it.isChecked = isChecked }
                    val msg = if (isChecked) "已开启所有配置" else "已关闭所有配置"
                    Toast.makeText(context, "$msg（重启生效）", Toast.LENGTH_SHORT).show()
                }
            )
            root.addView(masterRow)
            root.addView(createDivider(context, padding))

            for (i in switches.indices) {
                if (i > 0) root.addView(createDivider(context, padding))
                val item = switches[i]
                val label = item.label
                val prefKey = item.prefKey
                val supported = item.supported
                val finalLabel = if (supported) label else "$label (当前版本不支持)"
                
                val rowView = createSwitchRow(context, prefs, finalLabel, prefKey, padding, supported, item.defaultValue)
                root.addView(rowView)
                
                // 收集由于一键开启所需要的 Switch 实例
                if (rowView is ViewGroup) {
                    for (childIndex in 0 until rowView.childCount) {
                        val child = rowView.getChildAt(childIndex)
                        if (child is Switch) allSwitchViews.add(child)
                    }
                }
            }

            root.addView(createDivider(context, padding))
            root.addView(
                createActionRow(context, "手动反混淆", padding) {
                    startSymbolScanWithDialog(context, classLoader ?: context.classLoader)
                }
            )

            root.addView(createDivider(context, padding))
            root.addView(
                createActionRow(context, "手动签到", padding) {
                    com.forbidad4tieba.hook.feature.signin.AutoSignInManager.tryAutoSignIn(context, force = true)
                }
            )

            root.addView(createDivider(context, padding))
            val aboutView = TextView(context).apply {
                val versionName = com.forbidad4tieba.hook.BuildConfig.VERSION_NAME
                text = "版本: v$versionName\n作者: aikavvak12una"
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, padding, 0, padding / 2)
                setTextColor(0xFF888888.toInt())
                setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/aikavvak12una/ForbidAd4TieBa"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Throwable) {
                        Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            root.addView(aboutView)

            val scrollContainer = ScrollView(context).apply {
                addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            builder.setTitle("模块设置")
            builder.setView(scrollContainer)
            builder.setPositiveButton("保存") { _, _ ->
                Toast.makeText(context, "设置已保存，重启贴吧生效。", Toast.LENGTH_SHORT).show()
            }

            val dialog = builder.create()
            dialog.show()
            dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to show settings dialog: ${t.message}")
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
            styleScanActionButton(density, 0xFF1E4F8A.toInt())
        }
        val restartBtn = Button(activity).apply {
            text = "重启"
            styleScanActionButton(density, 0xFF1E4F8A.toInt())
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
            XposedBridge.log("${Constants.TAG}: restart launch failed: ${t.message}")
        }

        try { activity.finishAffinity() } catch (_: Throwable) {}

        try {
            Handler(Looper.getMainLooper()).postDelayed({
                try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Throwable) {}
                try { exitProcess(0) } catch (_: Throwable) {}
            }, 200)
        } catch (_: Throwable) {}
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
        label: String, prefKey: String?, padding: Int, enabled: Boolean = true,
        defaultValue: Boolean = false,
        onCheckedChange: ((Boolean) -> Unit)? = null
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, padding / 2, 0, padding / 2)
        }

        val tv = TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(if (enabled) 0xFF222222.toInt() else 0xFF999999.toInt())
        }
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f))

        @Suppress("DEPRECATION")
        val sw = Switch(context).apply {
            isChecked = if (enabled && prefKey != null) prefs.getBoolean(prefKey, defaultValue) else defaultValue
            isEnabled = enabled
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

    private fun createActionRow(context: Context, text: String, padding: Int, action: () -> Unit): View {
        return TextView(context).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, padding / 2, 0, padding / 2)
            setTextColor(0xFF0A66C2.toInt())
            setOnClickListener { action() }
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
