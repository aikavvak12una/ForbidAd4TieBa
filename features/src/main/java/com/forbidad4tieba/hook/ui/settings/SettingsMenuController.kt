package com.forbidad4tieba.hook.ui.settings

import com.forbidad4tieba.hook.config.RemoteEnvironmentState
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.BlockCountStats
import com.forbidad4tieba.hook.feature.signin.AutoSignInManager
import com.forbidad4tieba.hook.feature.ui.HomeTabHook
import com.forbidad4tieba.hook.ui.AboutInfoManager
import com.forbidad4tieba.hook.ui.BottomTabLiquidGlassDialog
import com.forbidad4tieba.hook.ui.SETTINGS_ROOT_GROUP_GAP_DP
import com.forbidad4tieba.hook.ui.SETTINGS_ROOT_SECTION_BOTTOM_PADDING_RATIO
import com.forbidad4tieba.hook.ui.SETTINGS_ROOT_SECTION_TOP_PADDING_RATIO
import com.forbidad4tieba.hook.ui.SettingsMenuGroupActions
import com.forbidad4tieba.hook.ui.SettingsMenuGroupBuilder
import com.forbidad4tieba.hook.ui.SettingsSwitchSupport
import com.forbidad4tieba.hook.ui.SettingsSwitchSupportResolver
import com.forbidad4tieba.hook.ui.SettingsVersionInfoProvider
import com.forbidad4tieba.hook.ui.SwitchItem
import com.forbidad4tieba.hook.ui.TabCustomizationDialog
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.about.AboutItem
import com.forbidad4tieba.hook.ui.applySettingsBrandTagStyle
import com.forbidad4tieba.hook.ui.applySettingsCodeTextStyle
import com.forbidad4tieba.hook.ui.applySettingsMessageStyle
import com.forbidad4tieba.hook.ui.applySettingsSectionTitleStyle
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createAboutItem
import com.forbidad4tieba.hook.ui.createDivider
import com.forbidad4tieba.hook.ui.createSwitchRow
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsEffects.restartHostApp
import com.forbidad4tieba.hook.ui.settings.SettingsEffects.saveDetailedLog
import com.forbidad4tieba.hook.ui.settings.SettingsFormSupport.descriptionWithScanSupport
import com.forbidad4tieba.hook.ui.settings.SettingsFormSupport.labelWithScanSupport
import com.forbidad4tieba.hook.ui.settings.SettingsScanController.showSymbolScanActionDialog
import com.forbidad4tieba.hook.ui.settings.forms.AdBlockForm.showAdBlockDialog
import com.forbidad4tieba.hook.ui.settings.forms.BottomTabsForm.showBottomTabDialog
import com.forbidad4tieba.hook.ui.settings.forms.CustomPostFilterForm.showCustomPostFilterDialog
import com.forbidad4tieba.hook.ui.settings.forms.FreeCopyForm.showFreeCopyDialog
import com.forbidad4tieba.hook.ui.settings.forms.HomeTopTabsForm.showHomeTopTabDialog
import com.forbidad4tieba.hook.ui.settings.forms.KeywordFilterForm.showCustomPostFilterKeywordDialog
import com.forbidad4tieba.hook.ui.settings.forms.PbLikeAutoReplyForm.showPbLikeAutoReplyDialog
import com.forbidad4tieba.hook.ui.settings.forms.PerformanceForm.showPerformanceOptimizationDialog
import com.forbidad4tieba.hook.ui.settings.forms.ReplyVisibilityForm.showReplyVisibilityProbeDialog
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassForm.showHomeNativeGlassDialog
import com.forbidad4tieba.hook.ui.settings.modelscore.ModelScoreForm.showCustomPostModelScoreDialog
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.ui.settingsDialogTitleBottomPadding
import com.forbidad4tieba.hook.ui.settingsRootContentVerticalPadding
import com.forbidad4tieba.hook.ui.updateButtonEnabledState

internal object SettingsMenuController {
    private const val RESTRICTED_FEATURE_UNLOCK_TAP_COUNT = 7

    private const val RESTRICTED_FEATURE_CONFIRM_DELAY_SECONDS = 5

    internal fun showModuleSettingsDialog(context: Context, classLoader: ClassLoader?) {
        try {
            val prefs = ConfigManager.getPrefs(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val scanSymbols = HookSymbolResolver.loadCachedIfUsable(
                context = context,
                cl = classLoader ?: context.classLoader,
            )
            val featureStatusMap = HookSymbolResolver.featureStatusMap(scanSymbols)
            if (scanSymbols != null) {
                ConfigManager.applyScanAvailability(context, featureStatusMap, refreshRuntime = false)
            }
            HomeTabHook.refreshTopTabCatalog(context)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val verticalPadding = settingsRootContentVerticalPadding(padding)
                setPadding(padding, verticalPadding, padding, verticalPadding)
            }

            val restrictedFeaturesUnlocked = RemoteEnvironmentState.isRestrictedFeaturesUnlocked(context)
            val groups = SettingsMenuGroupBuilder.build(
                restrictedFeaturesUnlocked = restrictedFeaturesUnlocked,
                actions = SettingsMenuGroupActions(
                    onAdBlock = { items -> showAdBlockDialog(context, prefs, items, featureStatusMap) },
                    onCustomPostFilter = { items -> showCustomPostFilterDialog(context, prefs, items) },
                    onCustomPostModelScore = { showCustomPostModelScoreDialog(context, prefs) },
                    onCustomPostFilterKeyword = { showCustomPostFilterKeywordDialog(context, prefs) },
                    onPbLikeAutoReply = { showPbLikeAutoReplyDialog(context, prefs) },
                    onFreeCopy = { items -> showFreeCopyDialog(context, prefs, items, featureStatusMap) },
                    onPerformanceOptimization = { groups ->
                        showPerformanceOptimizationDialog(context, prefs, groups, featureStatusMap)
                    },
                    onAutoSignIn = { AutoSignInManager.showResult(context) },
                    onReplyVisibilityProbe = { showReplyVisibilityProbeDialog(context, prefs) },
                    onDetailedLogSave = { saveDetailedLog(context) },
                    onTabCustomization = { items ->
                        TabCustomizationDialog.show(context, prefs, items, featureStatusMap)
                    },
                    onHomeTopTab = { showHomeTopTabDialog(context, prefs) },
                    onHomeNativeGlass = { showHomeNativeGlassDialog(context, prefs) },
                    onBottomTab = { showBottomTabDialog(context, prefs) },
                    onBottomTabLiquidGlass = { BottomTabLiquidGlassDialog.show(context, prefs) },
                ),
            )

            val runtimeSupportByKey = mutableMapOf<String, SettingsSwitchSupport>()
            fun supportOf(item: SwitchItem): SettingsSwitchSupport {
                return runtimeSupportByKey.getOrPut(item.prefKey) {
                    SettingsSwitchSupportResolver.resolve(
                        prefKey = item.prefKey,
                        supported = item.supported,
                        featureStatusMap = featureStatusMap,
                    )
                }
            }

            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    root.addView(createDivider(context, padding))
                }

                val tokens = UiStyle.tokens(context)
                val headerLabel = TextView(context).apply {
                    text = group.name
                    applySettingsSectionTitleStyle(tokens, density)
                    setPadding(
                        0,
                        (padding * SETTINGS_ROOT_SECTION_TOP_PADDING_RATIO).toInt(),
                        0,
                        (padding * SETTINGS_ROOT_SECTION_BOTTOM_PADDING_RATIO).toInt(),
                    )
                }
                root.addView(headerLabel)

                group.items.forEach { item ->
                    val support = supportOf(item)
                    val finalLabel = labelWithScanSupport(item.label, support)
                    val finalDesc = descriptionWithScanSupport(item.description, support)

                    val rowView = createSwitchRow(
                        context = context,
                        prefs = prefs,
                        label = finalLabel,
                        description = finalDesc,
                        prefKey = item.prefKey,
                        padding = padding,
                        enabled = support.supported,
                        defaultValue = if (support.supported) item.defaultValue else false,
                        actionIcon = item.actionIcon,
                        actionContentDescription = item.actionContentDescription,
                        onActionClick = item.onActionClick,
                    )
                    root.addView(rowView)
                }
            }

            root.addView(createDivider(context, padding))
            val tokensForDefault = UiStyle.tokens(context)

            val defaultEnabledContent = TextView(context).apply {
                text = UiText.Settings.DEFAULT_ENABLED_FEATURES
                textSize = 11.5f
                setTextColor(tokensForDefault.textSecondary)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                includeFontPadding = false
                setLineSpacing(2f * density, 1f)
                setPadding(0, (padding * 0.3f).toInt(), 0, (padding * 0.3f).toInt())
                visibility = View.GONE
            }

            var defaultEnabledExpanded = false
            val defaultEnabledRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (padding * 0.5f).toInt(), 0, (padding * 0.3f).toInt())
            }
            val arrowSize = (20 * density).toInt()
            val defaultEnabledArrow = TextView(context).apply {
                text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_ICON
                textSize = 13f
                setTextColor(tokensForDefault.accent)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(arrowSize, arrowSize).apply {
                    leftMargin = (6 * density).toInt()
                }
            }
            val defaultEnabledLabel = TextView(context).apply {
                text = UiText.Settings.DEFAULT_ENABLED_DESC
                applySettingsSectionTitleStyle(tokensForDefault, density)
            }
            defaultEnabledRow.addView(defaultEnabledLabel)
            defaultEnabledRow.addView(defaultEnabledArrow)
            defaultEnabledRow.setOnClickListener {
                defaultEnabledExpanded = !defaultEnabledExpanded
                UiStyle.animateExpandArrow(defaultEnabledArrow, defaultEnabledExpanded)
                if (defaultEnabledExpanded) {
                    UiStyle.animateCardExpand(defaultEnabledContent)
                } else {
                    UiStyle.animateCardCollapse(defaultEnabledContent)
                }
            }

            root.addView(defaultEnabledRow)
            root.addView(defaultEnabledContent)

            root.addView(createDivider(context, padding))
            val aboutContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, padding)

                val gap = View(context)
                gap.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (SETTINGS_ROOT_GROUP_GAP_DP * density).toInt(),
                )
                addView(gap)

                addView(TextView(context).apply {
                    text = UiText.Settings.ABOUT
                    applySettingsSectionTitleStyle(tokensForDefault, density)
                    setPadding(
                        0,
                        (padding * SETTINGS_ROOT_SECTION_TOP_PADDING_RATIO).toInt(),
                        0,
                        (padding * SETTINGS_ROOT_SECTION_BOTTOM_PADDING_RATIO).toInt(),
                    )
                })
            }

            val versionInfo = SettingsVersionInfoProvider.build(context, scanSymbols)
            val defaultAboutItems = listOf(
                AboutItem(
                    UiText.Settings.VERSION,
                    UiText.Settings.aboutVersionSummary(
                        tiebaBuildType = versionInfo.tiebaBuildType,
                        tiebaVersion = versionInfo.tiebaVersion,
                        moduleBuildType = versionInfo.moduleBuildType,
                        moduleVersion = versionInfo.moduleVersion,
                    ),
                    null,
                ),
                AboutItem(UiText.Settings.AUTHOR, UiText.Settings.AUTHOR_NAME, "https://github.com/aikavvak12una/ForbidAd4TieBa"),
            )
            val aboutItemsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            aboutContainer.addView(aboutItemsContainer)

            var settingsDialog: AlertDialog? = null
            var versionTapCount = 0

            fun renderAboutItems(remoteAboutItems: List<AboutItem>) {
                aboutItemsContainer.removeAllViews()
                val aboutItems = defaultAboutItems + remoteAboutItems
                for (aboutItem in aboutItems) {
                    val onAboutClick = if (aboutItem.title == UiText.Settings.VERSION) {
                        if (restrictedFeaturesUnlocked) {
                            { showRuntimeEnvironmentDialog(context) }
                        } else {
                            versionClick@{
                                if (RemoteEnvironmentState.isRestrictedFeatureUnlockBlocked(context)) {
                                    Toast.makeText(
                                        context,
                                        UiText.Settings.RESTRICTED_FEATURE_UNSUPPORTED_ENVIRONMENT,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@versionClick
                                }
                                versionTapCount += 1
                                if (versionTapCount >= RESTRICTED_FEATURE_UNLOCK_TAP_COUNT) {
                                    versionTapCount = 0
                                    showRestrictedFeatureWarningDialog(context) {
                                        settingsDialog?.dismiss()
                                        Handler(Looper.getMainLooper()).post {
                                            showModuleSettingsDialog(context, classLoader)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    }
                    aboutItemsContainer.addView(
                        createAboutItem(
                            context = context,
                            density = density,
                            padding = padding,
                            title = aboutItem.title,
                            content = aboutItem.description,
                            url = aboutItem.url,
                            onClick = onAboutClick,
                        )
                    )
                }
            }

            renderAboutItems(AboutInfoManager.loadCachedItemsForSettings())

            root.addView(aboutContainer)
            val blockCountStats = BlockCountStats.snapshot(context)
            root.addView(TextView(context).apply {
                text = UiText.Settings.blockCountStatsSummary(
                    adCount = blockCountStats.adCount,
                    customPostCount = blockCountStats.customPostCount,
                )
                textSize = 10f
                setTextColor(tokensForDefault.textSecondary)
                gravity = Gravity.CENTER_HORIZONTAL
                includeFontPadding = false
                setPadding(padding, 0, padding, padding)
            })

            val scrollContainer = ScrollView(context).apply {
                addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            val tokensTitle = UiStyle.tokens(context)
            val titleView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, settingsDialogTitleBottomPadding(padding))

                val titleRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    addView(TextView(context).apply {
                        text = UiText.Settings.MODULE_SETTINGS
                        textSize = 22f
                        letterSpacing = 0.02f
                        setTextColor(tokensTitle.textPrimary)
                        typeface = Typeface.DEFAULT_BOLD
                    })

                    addView(android.widget.ImageView(context).apply {
                        setImageResource(android.R.drawable.ic_popup_sync)
                        setColorFilter(tokensTitle.accent)
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                        val lp = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt())
                        lp.leftMargin = (8 * density).toInt()
                        layoutParams = lp
                        setOnClickListener { v ->
                            UiStyle.animateIconTap(v)
                            showSymbolScanActionDialog(context, classLoader ?: context.classLoader)
                        }
                    })
                }
                addView(titleRow)

                val brandTag = TextView(context).apply {
                    text = UiText.Settings.BRAND_TAG
                    applySettingsBrandTagStyle(tokensTitle, density)
                }
                addView(brandTag)
                UiStyle.animateBrandTagShimmer(brandTag)
            }

            val dialogTheme = if (tokensTitle.night) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            }
            val builder = AlertDialog.Builder(context, dialogTheme)
            builder.setCustomTitle(titleView)
            builder.setView(scrollContainer)
            builder.setPositiveButton(UiText.Settings.SAVE_AND_RESTART) { _, _ ->
                Toast.makeText(context, UiText.Settings.SETTINGS_SAVED_RESTARTING, Toast.LENGTH_SHORT).show()
                restartHostApp(context)
            }

            val dialog = builder.create()
            settingsDialog = dialog
            dialog.show()
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] FAILED to show settings dialog: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun showRestrictedFeatureWarningDialog(
        context: Context,
        onConfirmed: () -> Unit,
    ) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val messageView = TextView(context).apply {
                text = UiText.Settings.RESTRICTED_FEATURE_WARNING_MESSAGE
                applySettingsMessageStyle(tokens, density)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, padding)
            }
            val scroll = ScrollView(context).apply {
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
            val dialogTheme = if (tokens.night) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            }
            val dialog = AlertDialog.Builder(context, dialogTheme)
                .setSettingsTitle(context, UiText.Settings.RESTRICTED_FEATURE_WARNING_TITLE)
                .setView(scroll)
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(
                    UiText.Settings.restrictedFeatureConfirmWaiting(RESTRICTED_FEATURE_CONFIRM_DELAY_SECONDS),
                    null
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
                var secondsLeft = RESTRICTED_FEATURE_CONFIRM_DELAY_SECONDS
                confirmButton.text = UiText.Settings.restrictedFeatureConfirmWaiting(secondsLeft)
                confirmButton.updateButtonEnabledState(false)

                countdownRunnable = object : Runnable {
                    override fun run() {
                        secondsLeft -= 1
                        if (secondsLeft <= 0) {
                            confirmButton.text = UiText.Settings.RESTRICTED_FEATURE_CONFIRM
                            confirmButton.updateButtonEnabledState(true)
                        } else {
                            confirmButton.text = UiText.Settings.restrictedFeatureConfirmWaiting(secondsLeft)
                            handler.postDelayed(this, 1000L)
                        }
                    }
                }
                handler.postDelayed(countdownRunnable!!, 1000L)

                confirmButton.setOnClickListener {
                    RemoteEnvironmentState.setRestrictedFeaturesUnlocked(context, true)
                    Toast.makeText(context, UiText.Settings.RESTRICTED_FEATURE_UNLOCKED, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onConfirmed()
                }
            }
            dialog.setOnDismissListener {
                countdownRunnable?.let { handler.removeCallbacks(it) }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showRestrictedFeatureWarningDialog failed: ${t.message}")
        }
    }

    private fun showRuntimeEnvironmentDialog(context: Context) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val contentView = TextView(context).apply {
                text = AboutInfoManager.runtimeEnvironmentJsonForSettings(context)
                applySettingsCodeTextStyle(tokens, density)
                setTextIsSelectable(true)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, padding)
            }
            val scroll = ScrollView(context).apply {
                addView(
                    contentView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            }
            val dialogTheme = if (tokens.night) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            }
            val dialog = AlertDialog.Builder(context, dialogTheme)
                .setSettingsTitle(context, UiText.Settings.RUNTIME_ENVIRONMENT)
                .setView(scroll)
                .setPositiveButton(UiText.Settings.BUTTON_OK, null)
                .create()
            dialog.show()
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showRuntimeEnvironmentDialog failed: ${t.message}")
        }
    }
}
