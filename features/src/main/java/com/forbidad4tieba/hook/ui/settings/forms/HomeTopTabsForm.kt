package com.forbidad4tieba.hook.ui.settings.forms

import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
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

internal object HomeTopTabsForm {
    fun showHomeTopTabDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val catalog = ConfigManager.readHomeTopTabCatalog(context)
            val disabledKeys = ConfigManager.readHomeTopTabDisabledKeys(prefs)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            data class RowState(
                val entry: ConfigManager.HomeTopTabCatalogEntry,
                val switchView: Switch,
            )

            val rows = ArrayList<RowState>(catalog.size)
            catalog.forEachIndexed { index, entry ->
                val label = when (entry.key) {
                    ConfigManager.HOME_TOP_TAB_KEY_FOLLOWED -> UiText.Settings.HOME_TOP_TAB_FOLLOWED_LABEL
                    else -> entry.label.ifBlank { UiText.Settings.homeTopTabFallbackLabel(index + 1) }
                }
                val row = createSwitchRow(
                    context = context,
                    prefs = prefs,
                    label = label,
                    description = null,
                    prefKey = null,
                    padding = padding,
                    enabled = true,
                    defaultValue = entry.key !in disabledKeys,
                )
                val switchView = findSwitchView(row)
                if (switchView == null) {
                    XposedCompat.logW("[SettingsMenuHook] showHomeTopTabDialog failed: switch view missing")
                    return
                }
                rows += RowState(entry, switchView)
                root.addView(row)
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.HOME_TOP_TAB_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val hasHostCatalog = rows.any { it.entry.source == ConfigManager.HOME_TOP_TAB_SOURCE_HOST }
                    if (hasHostCatalog && rows.none { it.switchView.isChecked }) {
                        Toast.makeText(context, UiText.Settings.HOME_TOP_TAB_AT_LEAST_ONE, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val nextDisabledKeys = rows.asSequence()
                        .filter { !it.switchView.isChecked }
                        .map { it.entry.key }
                        .toCollection(LinkedHashSet())
                        .let(ConfigManager::expandHomeTopTabDisabledKeys)

                    prefs.edit()
                        .putString(
                            ConfigManager.KEY_HOME_TOP_TAB_DISABLED_KEYS,
                            ConfigManager.serializeHomeTopTabDisabledKeys(nextDisabledKeys),
                        )
                        .putBoolean(
                            ConfigManager.KEY_HOME_TOP_TAB_MATERIAL,
                            ConfigManager.isLegacyHomeTopTabEnabled(
                                nextDisabledKeys,
                                ConfigManager.HOME_TOP_TAB_KEY_MATERIAL,
                            ),
                        )
                        .putBoolean(
                            ConfigManager.KEY_HOME_TOP_TAB_RECOMMEND,
                            ConfigManager.isLegacyHomeTopTabEnabled(
                                nextDisabledKeys,
                                ConfigManager.HOME_TOP_TAB_KEY_RECOMMEND,
                            ),
                        )
                        .putBoolean(
                            ConfigManager.KEY_HOME_TOP_TAB_LIVE,
                            ConfigManager.isLegacyHomeTopTabEnabled(
                                nextDisabledKeys,
                                ConfigManager.HOME_TOP_TAB_KEY_LIVE,
                            ),
                        )
                        .putBoolean(
                            ConfigManager.KEY_HOME_TOP_TAB_FOLLOWED,
                            ConfigManager.isLegacyHomeTopTabEnabled(
                                nextDisabledKeys,
                                ConfigManager.HOME_TOP_TAB_KEY_FOLLOWED,
                            ),
                        )
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartTiebaHint(UiText.Settings.HOME_TOP_TAB_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showHomeTopTabDialog failed: ${t.message}")
        }
    }
}
