package com.forbidad4tieba.hook.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus

internal object TabCustomizationDialog {
    fun show(
        context: Context,
        prefs: SharedPreferences,
        items: List<SwitchItem>,
        featureStatusMap: Map<String, HookFeatureStatus>,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
            }
            val switches = ArrayList<Pair<SwitchItem, Switch>>(items.size)
            for (item in items) {
                val support = SettingsSwitchSupportResolver.resolve(
                    item.prefKey, item.supported, featureStatusMap,
                )
                val label = when {
                    support.unknown -> UiText.Settings.withScanUnknownSuffix(item.label)
                    !support.supported -> UiText.Settings.withUnsupportedSuffix(item.label)
                    support.partial -> UiText.Settings.withPartialSuffix(item.label)
                    else -> item.label
                }
                val row = createSwitchRow(
                    context = context,
                    prefs = prefs,
                    label = label,
                    description = SettingsSwitchSupportResolver.mergeDescription(item.description, support.note),
                    prefKey = null,
                    padding = padding,
                    enabled = support.supported,
                    defaultValue = support.supported && prefs.getBoolean(item.prefKey, item.defaultValue),
                    actionIcon = item.actionIcon,
                    actionContentDescription = item.actionContentDescription,
                    onActionClick = item.onActionClick,
                )
                val switchView = checkNotNull(findSwitchView(row)) {
                    "Switch view missing for ${item.prefKey}"
                }
                switches.add(item to switchView)
                root.addView(row)
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.TAB_CUSTOMIZATION_LABEL)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { applyUnifiedDialogCardStyle(it, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val editor = prefs.edit()
                    for ((item, switchView) in switches) {
                        if (switchView.isEnabled) {
                            editor.putBoolean(item.prefKey, switchView.isChecked)
                        }
                    }
                    editor.apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartTiebaHint(UiText.Settings.TAB_CUSTOMIZATION_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[TabCustomizationDialog] show failed: ${t.message}")
        }
    }
}
