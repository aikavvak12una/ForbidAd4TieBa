package com.forbidad4tieba.hook.ui.settings.forms

import com.forbidad4tieba.hook.config.TabPreferences
import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.showSettingsDialog
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

            val persistedSelection = TabPreferences.BottomTabSelection(
                homeEnabled = TabPreferences.BOTTOM_TAB_HOME.read(prefs),
                enterForumEnabled = TabPreferences.BOTTOM_TAB_ENTER_FORUM.read(prefs),
                retailStoreEnabled = TabPreferences.BOTTOM_TAB_RETAIL_STORE.read(prefs),
                messageEnabled = TabPreferences.BOTTOM_TAB_MESSAGE.read(prefs),
                mineEnabled = TabPreferences.BOTTOM_TAB_MINE.read(prefs),
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

            var updatingSelection = false
            val switches = listOf(homeSwitch, enterForumSwitch, retailStoreSwitch, messageSwitch, mineSwitch)
            switches.forEach { switchView ->
                switchView.setOnCheckedChangeListener { _, _ ->
                    if (updatingSelection) return@setOnCheckedChangeListener
                    val selection = TabPreferences.BottomTabSelection(
                        homeEnabled = homeSwitch.isChecked,
                        enterForumEnabled = enterForumSwitch.isChecked,
                        retailStoreEnabled = retailStoreSwitch.isChecked,
                        messageEnabled = messageSwitch.isChecked,
                        mineEnabled = mineSwitch.isChecked,
                    )
                    if (!selection.hasEnabledTab()) {
                        updatingSelection = true
                        try {
                            switchView.isChecked = true
                        } finally {
                            updatingSelection = false
                        }
                        Toast.makeText(context, UiText.Settings.BOTTOM_TAB_AT_LEAST_ONE, Toast.LENGTH_SHORT).show()
                        return@setOnCheckedChangeListener
                    }
                    TabPreferences.saveBottomTabSelection(prefs, TabPreferences.normalizeBottomTabSelection(selection))
                }
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.BOTTOM_TAB_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.showSettingsDialog(density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showBottomTabDialog failed: ${t.message}")
        }
    }
}
