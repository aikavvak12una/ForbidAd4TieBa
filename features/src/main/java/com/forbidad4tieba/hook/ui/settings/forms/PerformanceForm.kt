package com.forbidad4tieba.hook.ui.settings.forms

import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.ui.SETTINGS_SUBMENU_SECTION_BOTTOM_PADDING_RATIO
import com.forbidad4tieba.hook.ui.SETTINGS_SUBMENU_SECTION_TOP_PADDING_RATIO
import com.forbidad4tieba.hook.ui.SettingGroup
import com.forbidad4tieba.hook.ui.SwitchItem
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsSectionTitleStyle
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.createDivider
import com.forbidad4tieba.hook.ui.createSwitchRow
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.findSwitchView
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsFormSupport.descriptionWithScanSupport
import com.forbidad4tieba.hook.ui.settings.SettingsFormSupport.labelWithScanSupport
import com.forbidad4tieba.hook.ui.settings.SettingsFormSupport.resolveSwitchSupport
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding

internal object PerformanceForm {
    fun showPerformanceOptimizationDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
        groups: List<SettingGroup>,
        featureStatusMap: Map<String, HookFeatureStatus>,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val tokens = UiStyle.tokens(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val views = ArrayList<Pair<SwitchItem, Switch>>(groups.sumOf { it.items.size })
            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    root.addView(createDivider(context, padding))
                }
                root.addView(TextView(context).apply {
                    text = group.name
                    applySettingsSectionTitleStyle(tokens, density)
                    setPadding(
                        0,
                        (padding * SETTINGS_SUBMENU_SECTION_TOP_PADDING_RATIO).toInt(),
                        0,
                        (padding * SETTINGS_SUBMENU_SECTION_BOTTOM_PADDING_RATIO).toInt(),
                    )
                })
                for (item in group.items) {
                    val support = resolveSwitchSupport(item, featureStatusMap)
                    val row = createSwitchRow(
                        context = context,
                        prefs = prefs,
                        label = labelWithScanSupport(item.label, support),
                        description = descriptionWithScanSupport(item.description, support),
                        prefKey = null,
                        padding = padding,
                        enabled = support.supported,
                        defaultValue = if (support.supported) resolvePerformanceItemChecked(prefs, item) else false,
                        actionIcon = item.actionIcon,
                        actionContentDescription = item.actionContentDescription,
                        onActionClick = item.onActionClick,
                    )
                    val switchView = findSwitchView(row)
                    if (switchView == null) {
                        XposedCompat.logW("[SettingsMenuHook] showPerformanceOptimizationDialog failed: switch view missing for ${item.prefKey}")
                        return
                    }
                    views.add(item to switchView)
                    root.addView(row)
                }
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.PERFORMANCE_OPTIMIZATION_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val editor = prefs.edit()
                    for ((item, switchView) in views) {
                        if (switchView.isEnabled) {
                            editor.putBoolean(item.prefKey, switchView.isChecked)
                        }
                    }
                    editor.apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.PERFORMANCE_OPTIMIZATION_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showPerformanceOptimizationDialog failed: ${t.message}")
        }
    }

    private fun resolvePerformanceItemChecked(
        prefs: android.content.SharedPreferences,
        item: SwitchItem,
    ): Boolean {
        if (prefs.contains(item.prefKey)) {
            return prefs.getBoolean(item.prefKey, item.defaultValue)
        }
        return item.defaultValue
    }
}
