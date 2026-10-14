package com.forbidad4tieba.hook.ui.settings.forms

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.forbidad4tieba.hook.config.CommentFilterPreferences
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.SwitchItem
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsRowDescriptionStyle
import com.forbidad4tieba.hook.ui.applySettingsRowTitleStyle
import com.forbidad4tieba.hook.ui.showSettingsDialog
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.createSwitchRow
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsSliderChangeListener
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding

internal object CommentLevelFilterForm {
    fun showCommentLevelFilterDialog(context: Context, prefs: SharedPreferences, items: List<SwitchItem>) {
        try {
            val density = context.resources.displayMetrics.density
            val tokens = UiStyle.tokens(context)
            val padding = settingsDialogPadding(density)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
            }
            val levelLabel = TextView(context).apply {
                applySettingsRowTitleStyle(tokens, density)
            }
            root.addView(levelLabel)
            root.addView(TextView(context).apply {
                text = UiText.Settings.COMMENT_MINIMUM_LEVEL_DESC
                applySettingsRowDescriptionStyle(tokens, density)
            })
            val slider = SeekBar(context).apply {
                min = CommentFilterPreferences.MIN_LEVEL
                max = CommentFilterPreferences.MAX_LEVEL
                progress = CommentFilterPreferences.MINIMUM_LEVEL.read(prefs)
                keyProgressIncrement = 1
                splitTrack = false
                progressTintList = ColorStateList.valueOf(tokens.accent)
                thumbTintList = ColorStateList.valueOf(tokens.accent)
                progressBackgroundTintList = ColorStateList.valueOf(tokens.divider)
                contentDescription = UiText.Settings.COMMENT_MINIMUM_LEVEL_LABEL
            }
            levelLabel.text = UiText.Settings.commentMinimumLevel(slider.progress)
            slider.setOnSeekBarChangeListener(SettingsSliderChangeListener(
                onValueChanged = { levelLabel.text = UiText.Settings.commentMinimumLevel(it) },
                onValueCommitted = { prefs.edit().putInt(CommentFilterPreferences.KEY_MINIMUM_LEVEL, it).apply() },
            ))
            root.addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * density).toInt()))
            items.forEach { item ->
                val row = createSwitchRow(
                    context = context, prefs = prefs, label = item.label, description = item.description,
                    prefKey = item.prefKey, padding = padding, enabled = true,
                    defaultValue = prefs.getBoolean(item.prefKey, item.defaultValue),
                )
                root.addView(row)
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.COMMENT_LEVEL_FILTER_LABEL)
                .setView(createDialogScrollContainer(context, root))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.showSettingsDialog(density)
        } catch (failure: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] comment filter form failed: ${failure.message}")
        }
    }
}
