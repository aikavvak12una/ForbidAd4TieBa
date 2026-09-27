package com.forbidad4tieba.hook.ui.settings.forms

import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.createSwitchRow
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.findSwitchView
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding

internal object BottomTabsForm {
    fun showBottomTabDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)

            val persistedSelection = ConfigManager.BottomTabSelection(
                homeEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_HOME, true),
                enterForumEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_ENTER_FORUM, true),
                retailStoreEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_RETAIL_STORE, true),
                messageEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_MESSAGE, true),
                mineEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_MINE, true),
            )
            val initialSelection = persistedSelection

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val homeRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_HOME_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.homeEnabled,
            )
            val enterForumRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_ENTER_FORUM_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.enterForumEnabled,
            )
            val retailStoreRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_RETAIL_STORE_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.retailStoreEnabled,
            )
            val messageRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_MESSAGE_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.messageEnabled,
            )
            val mineRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_MINE_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.mineEnabled,
            )

            root.addView(homeRow)
            root.addView(enterForumRow)
            root.addView(retailStoreRow)
            root.addView(messageRow)
            root.addView(mineRow)

            val homeSwitch = findSwitchView(homeRow)
            val enterForumSwitch = findSwitchView(enterForumRow)
            val retailStoreSwitch = findSwitchView(retailStoreRow)
            val messageSwitch = findSwitchView(messageRow)
            val mineSwitch = findSwitchView(mineRow)
            if (
                homeSwitch == null ||
                enterForumSwitch == null ||
                retailStoreSwitch == null ||
                messageSwitch == null ||
                mineSwitch == null
            ) {
                XposedCompat.logW("[SettingsMenuHook] showBottomTabDialog failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.BOTTOM_TAB_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val rawSelection = ConfigManager.BottomTabSelection(
                        homeEnabled = homeSwitch.isChecked,
                        enterForumEnabled = enterForumSwitch.isChecked,
                        retailStoreEnabled = retailStoreSwitch.isChecked,
                        messageEnabled = messageSwitch.isChecked,
                        mineEnabled = mineSwitch.isChecked,
                    )
                    if (!rawSelection.hasEnabledTab()) {
                        Toast.makeText(context, UiText.Settings.BOTTOM_TAB_AT_LEAST_ONE, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val normalized = ConfigManager.normalizeBottomTabSelection(rawSelection)

                    prefs.edit()
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_HOME, normalized.homeEnabled)
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_ENTER_FORUM, normalized.enterForumEnabled)
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_RETAIL_STORE, normalized.retailStoreEnabled)
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_MESSAGE, normalized.messageEnabled)
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_MINE, normalized.mineEnabled)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartTiebaHint(UiText.Settings.BOTTOM_TAB_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showBottomTabDialog failed: ${t.message}")
        }
    }
}
