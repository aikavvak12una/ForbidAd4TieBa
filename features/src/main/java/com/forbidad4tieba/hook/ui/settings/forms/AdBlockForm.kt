package com.forbidad4tieba.hook.ui.settings.forms

import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
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

internal object AdBlockForm {
    fun showAdBlockDialog(
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

            for (item in items) {
                val support = resolveSwitchSupport(item, featureStatusMap)
                val row = createSwitchRow(
                    context = context,
                    prefs = prefs,
                    label = labelWithScanSupport(item.label, support),
                    description = descriptionWithScanSupport(item.description, support),
                    prefKey = item.prefKey,
                    padding = padding,
                    enabled = support.supported,
                    defaultValue = if (support.supported) {
                        prefs.getBoolean(item.prefKey, item.defaultValue)
                    } else {
                        false
                    },
                    actionIcon = item.actionIcon,
                    actionContentDescription = item.actionContentDescription,
                    onActionClick = item.onActionClick,
                )
                val switchView = findSwitchView(row)
                if (switchView == null) {
                    XposedCompat.logW("[SettingsMenuHook] showAdBlockDialog failed: switch view missing for ${item.prefKey}")
                    return
                }
                root.addView(row)
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.BLOCK_AD_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.showSettingsDialog(density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showAdBlockDialog failed: ${t.message}")
        }
    }
}
