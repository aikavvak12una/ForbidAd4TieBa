package com.forbidad4tieba.hook.ui.settings.forms

import com.forbidad4tieba.hook.config.FreeCopyPreferences
import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.ui.SwitchItem
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.showSettingsDialog
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.createSwitchRow
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.findSwitchView
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsFormSupport.descriptionWithScanSupport
import com.forbidad4tieba.hook.ui.settings.SettingsFormSupport.labelWithScanSupport
import com.forbidad4tieba.hook.ui.settings.SettingsFormSupport.resolveSwitchSupport
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding

internal object FreeCopyForm {
    fun showFreeCopyDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
        items: List<SwitchItem>,
        featureStatusMap: Map<String, HookFeatureStatus>,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            val views = ArrayList<Pair<SwitchItem, Switch>>(items.size)
            for (item in items) {
                val support = resolveSwitchSupport(item, featureStatusMap)
                val row = createSwitchRow(
                    context = context,
                    prefs = prefs,
                    label = labelWithScanSupport(item.label, support),
                    description = descriptionWithScanSupport(item.description, support),
                    prefKey = null,
                    padding = padding,
                    enabled = support.supported,
                    defaultValue = if (support.supported) {
                        prefs.getBoolean(item.prefKey, item.defaultValue)
                    } else {
                        false
                    },
                )
                val switchView = findSwitchView(row)
                if (switchView == null) {
                    XposedCompat.logW(
                        "[SettingsMenuHook] showFreeCopyDialog failed: switch view missing for ${item.prefKey}",
                    )
                    return
                }
                views.add(item to switchView)
                root.addView(row)
            }

            val postButtonSwitch = views.firstOrNull {
                it.first.prefKey == FreeCopyPreferences.KEY_FREE_COPY_POST_BODY
            }?.second
            val longPressSwitch = views.firstOrNull {
                it.first.prefKey == FreeCopyPreferences.KEY_FREE_COPY_POST_LONG_PRESS
            }?.second
            if (postButtonSwitch != null && longPressSwitch != null) {
                val normalized = FreeCopyPreferences.normalizeFreeCopyPostModes(
                    postButtonEnabled = postButtonSwitch.isChecked,
                    longPressEnabled = longPressSwitch.isChecked,
                )
                postButtonSwitch.isChecked = normalized.postButtonEnabled
                longPressSwitch.isChecked = normalized.longPressEnabled
            }
            var updatingSelection = false
            for ((item, switchView) in views) {
                switchView.setOnCheckedChangeListener { _, isChecked ->
                    if (updatingSelection || !switchView.isEnabled) return@setOnCheckedChangeListener
                    updatingSelection = true
                    try {
                        val otherMode = when (item.prefKey) {
                            FreeCopyPreferences.KEY_FREE_COPY_POST_BODY -> longPressSwitch
                            FreeCopyPreferences.KEY_FREE_COPY_POST_LONG_PRESS -> postButtonSwitch
                            else -> null
                        }
                        if (isChecked && otherMode?.isEnabled == true) otherMode.isChecked = false
                        val editor = prefs.edit()
                        for ((changedItem, changedSwitch) in views) {
                            if (changedSwitch.isEnabled) editor.putBoolean(changedItem.prefKey, changedSwitch.isChecked)
                        }
                        editor.apply()
                    } finally {
                        updatingSelection = false
                    }
                }
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.FREE_COPY_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.showSettingsDialog(density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showFreeCopyDialog failed: ${t.message}")
        }
    }
}
