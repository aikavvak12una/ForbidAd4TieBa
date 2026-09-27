package com.forbidad4tieba.hook.ui.settings.forms

import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.SwitchItem
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.createSwitchRow
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.findSwitchView
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.modelscore.ModelScoreForm.showCustomPostModelScoreDialog
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding

internal object CustomPostFilterForm {
    fun showCustomPostFilterDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
        items: List<SwitchItem>,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val views = ArrayList<Pair<SwitchItem, Switch>>(items.size)
            var modelScoreStatsStartToastShown = false
            for (item in items) {
                val actionClick = if (item.prefKey == ConfigManager.KEY_FILTER_POST_MODEL_SCORE) {
                    { showCustomPostModelScoreDialog(context, prefs) }
                } else {
                    item.onActionClick
                }
                val row = createSwitchRow(
                    context = context,
                    prefs = prefs,
                    label = item.label,
                    description = null,
                    prefKey = null,
                    padding = padding,
                    enabled = true,
                    defaultValue = prefs.getBoolean(item.prefKey, item.defaultValue),
                    actionIcon = item.actionIcon,
                    actionContentDescription = item.actionContentDescription,
                    onActionClick = actionClick,
                )
                val switchView = findSwitchView(row)
                if (switchView == null) {
                    XposedCompat.logW("[SettingsMenuHook] showCustomPostFilterDialog failed: switch view missing for ${item.prefKey}")
                    return
                }
                if (item.prefKey == ConfigManager.KEY_FILTER_POST_MODEL_SCORE) {
                    switchView.setOnCheckedChangeListener { _, isChecked ->
                        if (
                            isChecked &&
                            !modelScoreStatsStartToastShown
                        ) {
                            modelScoreStatsStartToastShown = true
                            Toast.makeText(
                                context,
                                UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_STARTED,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                views.add(item to switchView)
                root.addView(row)
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val editor = prefs.edit()
                    var modelScoreStatsStarted = false
                    for ((item, switchView) in views) {
                        if (
                            item.prefKey == ConfigManager.KEY_FILTER_POST_MODEL_SCORE &&
                            !prefs.getBoolean(item.prefKey, item.defaultValue) &&
                            switchView.isChecked &&
                            !modelScoreStatsStartToastShown
                        ) {
                            modelScoreStatsStarted = true
                        }
                        editor.putBoolean(item.prefKey, switchView.isChecked)
                    }
                    editor.apply()
                    Toast.makeText(
                        context,
                        if (modelScoreStatsStarted) {
                            UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_STARTED
                        } else {
                            UiText.Settings.withRestartHint(UiText.Settings.CUSTOM_POST_FILTER_SAVED)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showCustomPostFilterDialog failed: ${t.message}")
        }
    }
}
