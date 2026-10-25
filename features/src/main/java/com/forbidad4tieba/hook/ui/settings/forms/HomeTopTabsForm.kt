package com.forbidad4tieba.hook.ui.settings.forms

import com.forbidad4tieba.hook.config.TabPreferences
import com.forbidad4tieba.hook.config.ConfigManager
import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
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

internal object HomeTopTabsForm {
    fun showHomeTopTabDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val catalog = TabPreferences.readHomeTopTabCatalog(context)
            val disabledKeys = TabPreferences.readHomeTopTabDisabledKeys(prefs)
            val fixedEnabled = TabPreferences.HOME_TOP_TAB_FIXED_ENABLED.read(prefs)
            val fixedSelection = TabPreferences.readFixedHomeTopTabs(prefs)
            val fixedKeys = TabPreferences.fixedHomeTopTabKeys(fixedSelection)
            val missing = if (fixedEnabled) TabPreferences.unavailableFixedHomeTopTabs(catalog, fixedSelection)
                else emptyList()
            val fixedAvailable = ConfigManager.isScanFeatureAvailable(TabPreferences.KEY_HOME_TOP_TAB_FIXED_ENABLED)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            data class RowState(
                val entry: TabPreferences.HomeTopTabCatalogEntry,
                val switchView: Switch,
                val available: Boolean,
                val label: String,
            )

            val presentation = checkNotNull(TabPreferences.HOME_TOP_TAB_FIXED_ENABLED.presentation)
            val fixedRow = createSwitchRow(
                context, prefs, presentation.label,
                if (fixedAvailable) presentation.description else UiText.Settings.HOME_TOP_TAB_FIXED_UNAVAILABLE,
                prefKey = null, padding = padding, defaultValue = fixedEnabled,
            )
            val fixedSwitch = checkNotNull(findSwitchView(fixedRow))
            root.addView(fixedRow)
            val rows = ArrayList<RowState>(catalog.size + missing.size)
            (catalog + missing).forEachIndexed { index, entry ->
                val available = index < catalog.size
                val label = when (entry.key) {
                    TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED -> UiText.Settings.HOME_TOP_TAB_FOLLOWED_LABEL
                    else -> entry.label.ifBlank { UiText.Settings.homeTopTabFallbackLabel(index + 1) }
                }
                val row = createSwitchRow(
                    context = context,
                    prefs = prefs,
                    label = label,
                    description = if (available) null else UiText.Settings.HOME_TOP_TAB_TEMPORARILY_UNAVAILABLE,
                    prefKey = null,
                    padding = padding,
                    enabled = true,
                    defaultValue = if (fixedEnabled) TabPreferences.canonicalHomeTopTabKey(entry.key) in fixedKeys
                        else entry.key !in disabledKeys,
                )
                val switchView = findSwitchView(row)
                if (switchView == null) {
                    XposedCompat.logW("[SettingsMenuHook] showHomeTopTabDialog failed: switch view missing")
                    return
                }
                rows += RowState(entry, switchView, available, label)
                root.addView(row)
            }

            val hasHostCatalog = catalog.any { it.source == TabPreferences.HOME_TOP_TAB_SOURCE_HOST }
            var updatingSelection = false

            fun rejectChange(switch: Switch, message: String) {
                updatingSelection = true
                try { switch.isChecked = !switch.isChecked } finally { updatingSelection = false }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }

            fun saveSelection() {
                TabPreferences.saveHomeTopTabSelection(
                    prefs, rows.map { it.entry },
                    rows.filter { it.switchView.isChecked }.map {
                        TabPreferences.FixedHomeTopTab(it.entry.key, it.label)
                    }, fixedSwitch.isChecked,
                )
            }

            fixedSwitch.setOnCheckedChangeListener { _, checked ->
                if (updatingSelection) return@setOnCheckedChangeListener
                val failure = when {
                    checked && !fixedAvailable -> UiText.Settings.HOME_TOP_TAB_FIXED_UNAVAILABLE
                    checked && !hasHostCatalog -> UiText.Settings.HOME_TOP_TAB_FIXED_NEEDS_CATALOG
                    hasHostCatalog && rows.none { it.available && it.switchView.isChecked } -> UiText.Settings.HOME_TOP_TAB_AT_LEAST_ONE_AVAILABLE
                    else -> null
                }
                if (failure != null) rejectChange(fixedSwitch, failure) else saveSelection()
            }
            rows.forEach { row ->
                row.switchView.setOnCheckedChangeListener { _, _ ->
                    if (updatingSelection) return@setOnCheckedChangeListener
                    if (hasHostCatalog && rows.none { it.available && it.switchView.isChecked }) {
                        rejectChange(row.switchView, UiText.Settings.HOME_TOP_TAB_AT_LEAST_ONE_AVAILABLE)
                        return@setOnCheckedChangeListener
                    }
                    saveSelection()
                }
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.HOME_TOP_TAB_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.showSettingsDialog(density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showHomeTopTabDialog failed: ${t.message}")
        }
    }
}
