package com.forbidad4tieba.hook.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.HookInstaller
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.ModuleUserDataCleaner
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.ui.AboutInfoManager
import com.forbidad4tieba.hook.ui.MaxHeightScrollView
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_DESC_SP
import com.forbidad4tieba.hook.ui.SETTINGS_VALUE_TEXT_SP
import com.forbidad4tieba.hook.ui.ScanController
import com.forbidad4tieba.hook.ui.ScanMessages
import com.forbidad4tieba.hook.ui.InitialScanDialogInstaller
import com.forbidad4tieba.hook.ui.ModuleDialogQueue
import com.forbidad4tieba.hook.ui.PostScanEnvironmentWarningInstaller
import com.forbidad4tieba.hook.ui.SettingsVersionInfoProvider
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsBrandTagStyle
import com.forbidad4tieba.hook.ui.applySettingsCodeTextStyle
import com.forbidad4tieba.hook.ui.applySettingsDialogTitleStyle
import com.forbidad4tieba.hook.ui.applySettingsMessageStyle
import com.forbidad4tieba.hook.ui.applySettingsRowTitleStyle
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.createDivider
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsEffects.clearModuleDataAndRestart
import com.forbidad4tieba.hook.ui.settings.SettingsEffects.restartHostApp
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.ui.settingsRowVerticalPadding
import com.forbidad4tieba.hook.ui.updateButtonEnabledState
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.util.Collections
import kotlin.concurrent.thread

internal object SettingsScanController {
    private const val INITIAL_SCAN_ENVIRONMENT_WARNING_DELAY_SECONDS = 10

    private const val SCAN_RUNNING_PULSE_INITIAL_DELAY_MS = 1000L

    private const val SCAN_RUNNING_PULSE_INTERVAL_MS = 1500L

    fun ensureInitialScanDialogHook(classLoader: ClassLoader) {
        InitialScanDialogInstaller.ensureInstalled(classLoader) { activity, cl ->
            startSymbolScanWithDialog(activity, cl, clearUserData = false)
        }
    }

    fun ensurePostScanEnvironmentWarningHook() {
        PostScanEnvironmentWarningInstaller.ensureInstalled { activity ->
            if (!ConfigManager.hasPendingPostScanEnvironmentWarning(activity)) {
                return@ensureInstalled
            }
            ModuleDialogQueue.enqueue {
                if (
                    activity.isFinishing ||
                    activity.isDestroyed ||
                    !ConfigManager.consumePendingPostScanEnvironmentWarning(activity)
                ) {
                    ModuleDialogQueue.finishCurrent()
                    return@enqueue
                }
                showEnvironmentWarningDialog(activity) {
                    ModuleDialogQueue.finishCurrent()
                }
            }
        }
    }

    private fun showEnvironmentWarningDialog(
        activity: Activity,
        onConfirmed: () -> Unit,
    ) {
        try {
            if (!ConfigManager.shouldShowEnvironmentWarningDialog(activity)) {
                onConfirmed()
                return
            }

            val tokens = UiStyle.tokens(activity)
            val density = activity.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val messageView = TextView(activity).apply {
                text = UiText.Settings.INITIAL_SCAN_ENVIRONMENT_WARNING_MESSAGE
                applySettingsMessageStyle(tokens, density)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, padding)
            }
            val scroll = ScrollView(activity).apply {
                addView(
                    messageView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            }

            val handler = Handler(Looper.getMainLooper())
            var countdownRunnable: Runnable? = null
            val dialog = AlertDialog.Builder(activity, dialogThemeFor(activity))
                .setSettingsTitle(activity, UiText.Settings.INITIAL_SCAN_ENVIRONMENT_WARNING_TITLE)
                .setView(scroll)
                .setPositiveButton(
                    UiText.Settings.initialScanEnvironmentWarningConfirmWaiting(
                        INITIAL_SCAN_ENVIRONMENT_WARNING_DELAY_SECONDS
                    ),
                    null,
                )
                .create()
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)

            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    applyUnifiedDialogCardStyle(window, density)
                    UiStyle.animateDialogEntry(window.decorView, density)
                }
                val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                confirmButton.setTextColor(tokens.danger)
                var secondsLeft = INITIAL_SCAN_ENVIRONMENT_WARNING_DELAY_SECONDS
                confirmButton.text = UiText.Settings.initialScanEnvironmentWarningConfirmWaiting(secondsLeft)
                confirmButton.updateButtonEnabledState(false)

                countdownRunnable = object : Runnable {
                    override fun run() {
                        secondsLeft -= 1
                        if (secondsLeft <= 0) {
                            confirmButton.text = UiText.Settings.BUTTON_OK
                            confirmButton.updateButtonEnabledState(true)
                        } else {
                            confirmButton.text =
                                UiText.Settings.initialScanEnvironmentWarningConfirmWaiting(secondsLeft)
                            handler.postDelayed(this, 1000L)
                        }
                    }
                }
                handler.postDelayed(countdownRunnable!!, 1000L)

                confirmButton.setOnClickListener { dialog.dismiss() }
            }
            dialog.setOnDismissListener {
                countdownRunnable?.let { handler.removeCallbacks(it) }
                onConfirmed()
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showEnvironmentWarningDialog failed: ${t.message}")
            onConfirmed()
        }
    }

    private fun startSymbolScanWithDialog(context: Context, classLoader: ClassLoader?, clearUserData: Boolean) {
        val activity = ReflectionUtils.findActivityFromContext(context)
        if (activity == null) {
            Toast.makeText(context, UiText.Settings.CONTEXT_UNAVAILABLE, Toast.LENGTH_SHORT).show()
            ScanController.manualRescanAsync(
                context,
                classLoader ?: context.classLoader,
                clearUserData = clearUserData,
            )
            return
        }
        val cl = classLoader ?: activity.classLoader
        if (cl == null) {
            Toast.makeText(activity, UiText.Settings.CLASSLOADER_UNAVAILABLE, Toast.LENGTH_SHORT).show()
            return
        }

        val tokens = UiStyle.tokens(activity)
        val density = activity.resources.displayMetrics.density
        val padding = settingsDialogPadding(density)
        val ui = Handler(Looper.getMainLooper())
        var finished = false
        var progressSteps = 0
        var displayedProgress = 0f
        val scanLogLines = Collections.synchronizedList(mutableListOf<String>())
        var scanExceptionLine: String? = null
        var scanPulseRunnable: Runnable? = null

        fun appendScanLog(line: String) {
            scanLogLines.add(line)
        }

        fun snapshotScanLog(): String {
            return synchronized(scanLogLines) {
                scanLogLines.joinToString("\n")
            }
        }

        fun scanLogContains(prefix: String): Boolean {
            return synchronized(scanLogLines) {
                scanLogLines.any { it.startsWith(prefix) }
            }
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val scanContentMaxHeight = (activity.resources.displayMetrics.heightPixels * 0.62f)
            .toInt()
            .coerceAtLeast((220 * density).toInt())
        val scanContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Title area.
        val titleContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        titleContainer.addView(TextView(activity).apply {
            text = UiText.Settings.DIALOG_SCAN_TITLE
            applySettingsDialogTitleStyle(tokens, density)
        })
        titleContainer.addView(TextView(activity).apply {
            text = UiText.Settings.BRAND_TAG
            applySettingsBrandTagStyle(tokens, density)
        }.also { UiStyle.animateBrandTagShimmer(it) })
        root.addView(titleContainer)

        val scanScroll = MaxHeightScrollView(activity, scanContentMaxHeight).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            addView(
                scanContent,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        }
        root.addView(
            scanScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )

        // Progress bar.
        val progressBar = UiStyle.ThinProgressBar(activity, tokens).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (5 * density).toInt(),
            )
        }
        scanContent.addView(progressBar)

        fun setScanProgress(target: Float, animated: Boolean = true) {
            val clamped = target.coerceIn(0f, 1f)
            ui.post {
                if (clamped <= displayedProgress + 0.0005f) return@post
                displayedProgress = clamped
                progressBar.setProgress(clamped, animated)
            }
        }

        fun stopScanPulse() {
            scanPulseRunnable?.let { ui.removeCallbacks(it) }
            scanPulseRunnable = null
            progressBar.animate().cancel()
            progressBar.alpha = 1f
        }

        fun startScanPulse() {
            if (scanPulseRunnable != null) return
            scanPulseRunnable = object : Runnable {
                override fun run() {
                    if (finished) return
                    val drift = when {
                        displayedProgress < 0.18f -> 0.018f
                        displayedProgress < 0.60f -> 0.010f
                        else -> 0.004f
                    }
                    val nextProgress = (displayedProgress + drift).coerceAtMost(0.88f)
                    if (nextProgress > displayedProgress + 0.0005f) {
                        displayedProgress = nextProgress
                        progressBar.setProgress(nextProgress, animated = true)
                    }
                    UiStyle.animateProgressRunningPulse(progressBar)
                    ui.postDelayed(this, SCAN_RUNNING_PULSE_INTERVAL_MS)
                }
            }
            ui.postDelayed(scanPulseRunnable!!, SCAN_RUNNING_PULSE_INITIAL_DELAY_MS)
        }

        // Status text.
        val statusView = TextView(activity).apply {
            text = UiText.Settings.SCAN_PREPARING
            textSize = SETTINGS_VALUE_TEXT_SP
            setTextColor(tokens.textSecondary)
            includeFontPadding = false
            setLineSpacing(1f * density, 1f)
            setPadding(0, (12 * density).toInt(), 0, 0)
        }
        scanContent.addView(statusView)

        // Result card is hidden until scanning finishes.
        val resultCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyle.createResultCardBackground(tokens, density)
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (16 * density).toInt()
            layoutParams = lp
        }
        val resultStatusView = TextView(activity).apply {
            applySettingsRowTitleStyle(tokens, density)
        }
        val resultVersionView = TextView(activity).apply {
            applySettingsCodeTextStyle(tokens, density, muted = true)
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        resultCard.addView(resultStatusView)
        resultCard.addView(resultVersionView)
        scanContent.addView(resultCard)

        // Restart button.
        val restartBtn = Button(activity).apply {
            text = UiText.Settings.BUTTON_RESTART
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            UiStyle.setButtonEnabledState(this, false)
        }
        val copyLogBtn = Button(activity).apply {
            text = UiText.Settings.BUTTON_COPY_SCAN_LOG
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            UiStyle.setButtonEnabledState(this, false)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = (8 * density).toInt()
            }
        }
        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (16 * density).toInt(), 0, 0)
            visibility = View.GONE
        }
        buttonRow.addView(copyLogBtn)
        buttonRow.addView(restartBtn)
        root.addView(buttonRow)

        val dialogTheme = if (tokens.night) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        val dialog = AlertDialog.Builder(activity, dialogTheme)
            .setView(root)
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (finished) {
                    dialog.dismiss()
                    true
                } else {
                    true // Consume Back while scanning.
                }
            } else false
        }
        dialog.setOnDismissListener {
            stopScanPulse()
        }
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                window.attributes = window.attributes.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        }
        dialog.show()

        restartBtn.setOnClickListener {
            if (!finished) return@setOnClickListener
            restartHostApp(activity)
        }
        copyLogBtn.setOnClickListener {
            if (!finished) return@setOnClickListener
            val text = snapshotScanLog()
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("tbhook_symbol_scan", text))
            Toast.makeText(activity, UiText.Settings.SCAN_LOG_COPIED, Toast.LENGTH_SHORT).show()
        }

        // Map scan log hints to a rough progress ratio.
        fun advanceProgress(hint: String) {
            progressSteps++
            val ratio = when {
                hint.contains("fingerprint") -> 0.05f
                hint.contains("disk cache") -> 0.10f
                hint.contains("scan begin") -> 0.15f
                hint.contains("nav class") -> 0.25f
                hint.contains("settings match") -> 0.35f
                hint.contains("plainUrl") -> 0.55f
                hint.contains("pbEarlyAd") -> 0.65f
                hint.contains("collection") -> 0.75f
                hint.contains("history") -> 0.80f
                hint.contains("scan done") -> 0.95f
                hint.contains("cache updated") -> 0.98f
                hint.contains("durationMs") -> 1.0f
                else -> (0.15f + progressSteps * 0.008f).coerceAtMost(0.92f)
            }
            setScanProgress(ratio)
        }

        fun updateStatus(text: String) {
            ui.post { statusView.text = text }
        }

        updateStatus(UiText.Settings.SCAN_PREPARING)
        displayedProgress = 0.02f
        progressBar.setProgress(0.02f, animated = false)
        startScanPulse()

        thread(name = "tbhook-symbol-scan", isDaemon = true) {
            var source = "unsupported"
            var scanSymbols: HookSymbols? = null
            var runtimeEnvironmentJson: String? = null
            try {
                if (clearUserData) {
                    updateStatus(UiText.Settings.SCAN_CLEARING)
                    val clearResult = ModuleUserDataCleaner.clearBeforeManualScan(activity)
                    if (!clearResult.success) {
                        source = "cleanup_failed"
                        appendScanLog(UiText.Settings.scanUserDataClearFailed(clearResult.failedTargets.size))
                        return@thread
                    }
                    appendScanLog(UiText.Settings.scanUserDataCleared(clearResult.deletedTargets.size))
                }
                updateStatus(UiText.Settings.SCAN_RUNNING)
                val symbols = HookSymbolResolver.resolve(
                    context = activity,
                    cl = cl,
                    forceRescan = true,
                    logger = ScanLogger { line ->
                        appendScanLog(line)
                        advanceProgress(line)
                    },
                )
                ConfigManager.applyScanAvailability(
                    activity,
                    HookSymbolResolver.featureStatusMap(symbols),
                    refreshRuntime = true,
                )
                if (symbols.source != "unsupported") {
                    ConfigManager.markPostScanEnvironmentWarningPending(activity)
                }
                source = symbols.source
                scanSymbols = symbols
            } catch (t: Throwable) {
                val exceptionText = HookSymbolScanDiagnostics.formatScanException(t)
                scanExceptionLine = UiText.Settings.scanException(exceptionText)
                appendScanLog(scanExceptionLine ?: exceptionText)
                XposedCompat.log("[SettingsMenuHook] scan exception: $exceptionText")
                XposedCompat.log(t)
            } finally {
                if (!scanLogContains("Feature[")) {
                    HookSymbolResolver.formatFeatureStatusLines(scanSymbols).forEach(::appendScanLog)
                }
                if (!scanLogContains("HookPoint[")) {
                    HookSymbolResolver.formatHookPointStatusLines(scanSymbols).forEach(::appendScanLog)
                }
                ConfigManager.formatPerformanceStatusLines(ConfigManager.snapshot()).forEach(::appendScanLog)
                HookInstaller.snapshot().forEach { appendScanLog(it.formatLine()) }
                runtimeEnvironmentJson = runCatching {
                    AboutInfoManager.runtimeEnvironmentJsonForSettings(activity)
                }.getOrElse { t ->
                    UiText.Settings.scanException(t.message ?: UiText.Settings.UNKNOWN)
                }
                appendScanLog("${UiText.Settings.RUNTIME_ENVIRONMENT}:\n$runtimeEnvironmentJson")
                ui.post {
                    finished = true
                    stopScanPulse()
                    displayedProgress = 1f
                    progressBar.setProgress(1f)
                    UiStyle.animateProgressComplete(progressBar)
                    dialog.setCancelable(true)
                    buttonRow.visibility = View.VISIBLE
                    UiStyle.setButtonEnabledState(restartBtn, true)
                    UiStyle.setButtonEnabledState(copyLogBtn, true)
                    UiStyle.animateButtonEnable(restartBtn)
                    UiStyle.animateButtonEnable(copyLogBtn)

                    val versionInfo = SettingsVersionInfoProvider.build(activity, scanSymbols)

                    // Failed hook points decide the result severity color.
                    val failedLines = HookSymbolResolver.formatUnavailableHookPointStatusLines(scanSymbols)
                    val hasScanErrors = HookSymbolResolver.hasScanErrors(scanSymbols, failedLines)
                    val versionWarning = ScanMessages.versionWarning(scanSymbols)
                    val resultWarning = versionWarning ?: if (hasScanErrors) {
                        ScanMessages.featureWarning()
                    } else {
                        null
                    }

                    val summaryText = when {
                        !hasScanErrors -> UiText.Settings.SCAN_COMPLETED
                        source == "scan" || source == "partial" -> UiText.Settings.SCAN_PARTIALLY_COMPLETED
                        else -> UiText.Settings.SCAN_FAILED
                    }
                    val summaryColor = when {
                        !hasScanErrors && versionWarning == null -> tokens.success
                        !hasScanErrors -> tokens.warning
                        else -> tokens.danger
                    }

                    // Result card replaces the inline status row after completion.
                    statusView.visibility = View.GONE

                    resultStatusView.text = "${UiText.Settings.SCAN_RESULT_LABEL}  $summaryText"
                    resultStatusView.setTextColor(summaryColor)
                    resultVersionView.text = UiText.Settings.scanVersionSummary(
                        tiebaBuildType = versionInfo.tiebaBuildType,
                        tiebaVersion = versionInfo.tiebaVersion,
                        moduleBuildType = versionInfo.moduleBuildType,
                        moduleVersion = versionInfo.moduleVersion,
                    )

                    val runtimeEnvironmentView = TextView(activity).apply {
                        text = "${UiText.Settings.RUNTIME_ENVIRONMENT}\n$runtimeEnvironmentJson"
                        applySettingsCodeTextStyle(tokens, density, muted = true)
                        setPadding(0, (6 * density).toInt(), 0, 0)
                        setTextIsSelectable(true)
                    }
                    resultCard.addView(runtimeEnvironmentView)

                    if (resultWarning != null) {
                        val warningView = TextView(activity).apply {
                            text = resultWarning
                            textSize = SETTINGS_ROW_DESC_SP
                            setTextColor(summaryColor)
                            includeFontPadding = false
                            setLineSpacing(1f * density, 1f)
                            setPadding(0, (6 * density).toInt(), 0, 0)
                        }
                        resultCard.addView(warningView)
                    }

                    val exceptionLine = scanExceptionLine
                    if (exceptionLine != null) {
                        val exceptionView = TextView(activity).apply {
                            text = exceptionLine
                            applySettingsCodeTextStyle(tokens, density)
                            setTextColor(tokens.danger)
                            setPadding(0, (6 * density).toInt(), 0, 0)
                        }
                        resultCard.addView(exceptionView)
                    }

                    // Show failed or missing hook points.
                    if (failedLines.isNotEmpty()) {
                        val failedView = TextView(activity).apply {
                            text = failedLines.joinToString("\n")
                            applySettingsCodeTextStyle(tokens, density)
                            setTextColor(tokens.danger)
                            setPadding(0, (6 * density).toInt(), 0, 0)
                        }
                        resultCard.addView(failedView)
                    }

                    resultCard.visibility = View.VISIBLE
                    UiStyle.animateResultReveal(resultCard)
                }
            }
        }
    }

    fun showSymbolScanActionDialog(context: Context, classLoader: ClassLoader?) {
        val activity = ReflectionUtils.findActivityFromContext(context)
        if (activity == null) {
            Toast.makeText(context, UiText.Settings.CONTEXT_UNAVAILABLE, Toast.LENGTH_SHORT).show()
            startSymbolScanWithDialog(context, classLoader ?: context.classLoader, clearUserData = false)
            return
        }

        val tokens = UiStyle.tokens(activity)
        val density = activity.resources.displayMetrics.density
        val actions = arrayOf(
            UiText.Settings.SCAN_ACTION_RESCAN_ONLY,
            UiText.Settings.SCAN_ACTION_CLEAR_DATA_RESTART,
        )
        val padding = settingsDialogPadding(density)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
        }
        lateinit var dialog: AlertDialog
        actions.forEachIndexed { index, action ->
            if (index > 0) {
                root.addView(createDivider(activity, padding))
            }
            root.addView(
                TextView(activity).apply {
                    text = action
                    applySettingsRowTitleStyle(tokens, density)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    setPadding(0, settingsRowVerticalPadding(density), 0, settingsRowVerticalPadding(density))
                    setOnClickListener {
                        UiStyle.animateActionPress(this)
                        dialog.dismiss()
                        when (index) {
                            0 -> startSymbolScanWithDialog(activity, classLoader ?: activity.classLoader, clearUserData = false)
                            1 -> clearModuleDataAndRestart(activity)
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        dialog = AlertDialog.Builder(activity, dialogThemeFor(activity))
            .setSettingsTitle(activity, UiText.Settings.DIALOG_SCAN_ACTION_TITLE)
            .setView(createDialogScrollContainer(activity, root))
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        }
        dialog.show()
    }
}
