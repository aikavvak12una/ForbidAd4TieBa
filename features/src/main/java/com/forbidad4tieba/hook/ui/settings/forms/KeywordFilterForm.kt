package com.forbidad4tieba.hook.ui.settings.forms

import com.forbidad4tieba.hook.config.PostFilterPreferences
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_DESC_SP
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_TITLE_SP
import com.forbidad4tieba.hook.ui.SETTINGS_SECTION_TITLE_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.showSettingsDialog
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.doAfterTextChanged
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.styleMultilineInput
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding

internal object KeywordFilterForm {
    fun showCustomPostFilterKeywordDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val initialRaw = PostFilterPreferences.FILTER_POST_FORUM_KEYWORD_LIST.read(prefs).orEmpty()

            fun keywordCount(raw: String): Int {
                if (raw.isBlank()) return 0
                return raw.split('\n', ',', '\uff0c', ';', '\uff1b')
                    .asSequence()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .count()
            }

            val guideView = TextView(context).apply {
                text = UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_GUIDE
                textSize = SETTINGS_ROW_TITLE_SP
                setTextColor(tokens.textPrimary)
                includeFontPadding = false
                setLineSpacing(3f * density, 1f)
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            val inputLabel = TextView(context).apply {
                text = UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_INPUT_LABEL
                textSize = SETTINGS_SECTION_TITLE_SP
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                includeFontPadding = false
            }
            val counterView = TextView(context).apply {
                textSize = SETTINGS_ROW_DESC_SP
                setTextColor(tokens.textSecondary)
                includeFontPadding = false
                gravity = Gravity.END
                setPadding((8 * density).toInt(), 0, 0, 0)
            }
            fun refreshCounter(raw: String) {
                counterView.text = UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_COUNT_PREFIX + keywordCount(raw)
            }

            val input = android.widget.EditText(context).apply {
                styleMultilineInput(tokens, density)
                setText(initialRaw)
            }
            refreshCounter(initialRaw)
            input.doAfterTextChanged { raw ->
                refreshCounter(raw)
                val value = raw.trim()
                if (PostFilterPreferences.FILTER_POST_FORUM_KEYWORD_LIST.read(prefs) != value) {
                    prefs.edit().putString(PostFilterPreferences.KEY_FILTER_POST_FORUM_KEYWORD_LIST, value).apply()
                }
            }

            val inputHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (8 * density).toInt())
                addView(
                    inputLabel,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(
                    counterView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            val formatGuideView = TextView(context).apply {
                text = UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_FORMAT_GUIDE
                textSize = SETTINGS_ROW_DESC_SP
                setTextColor(tokens.textSecondary)
                includeFontPadding = false
                setLineSpacing(2f * density, 1f)
                setPadding(0, (8 * density).toInt(), 0, 0)
            }

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val padding = settingsDialogPadding(density)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                addView(
                    guideView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                addView(
                    inputHeader,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                addView(
                    input,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                addView(
                    formatGuideView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, container))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.showSettingsDialog(density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showCustomPostFilterKeywordDialog failed: ${t.message}")
        }
    }
}
