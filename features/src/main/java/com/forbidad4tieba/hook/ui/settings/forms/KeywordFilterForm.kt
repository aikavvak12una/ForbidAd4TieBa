package com.forbidad4tieba.hook.ui.settings.forms

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.SETTINGS_INPUT_TEXT_SP
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_DESC_SP
import com.forbidad4tieba.hook.ui.SETTINGS_SECTION_TITLE_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
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
            val initialRaw = prefs.getString(ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD_LIST, "").orEmpty()

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
                textSize = SETTINGS_ROW_DESC_SP
                setTextColor(tokens.textSecondary)
                includeFontPadding = false
                setLineSpacing(1f * density, 1f)
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            val counterView = TextView(context).apply {
                textSize = SETTINGS_SECTION_TITLE_SP
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.accent)
                includeFontPadding = false
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            fun refreshCounter(raw: String) {
                counterView.text = UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_COUNT_PREFIX + keywordCount(raw)
            }

            val input = android.widget.EditText(context).apply {
                setSingleLine(false)
                maxLines = 6
                minLines = 4
                gravity = Gravity.TOP or Gravity.START
                hint = UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_HINT
                setText(initialRaw)
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textMuted)
                textSize = SETTINGS_INPUT_TEXT_SP
                includeFontPadding = false
                background = UiStyle.createPlainInputUnderlineBackground(tokens, density)
                setPadding(
                    0,
                    (2 * density).toInt(),
                    0,
                    (8 * density).toInt()
                )
            }
            refreshCounter(initialRaw)
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    refreshCounter(s?.toString().orEmpty())
                }
            })

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
                    counterView,
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
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, container))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val raw = input.text?.toString().orEmpty().trim()
                    prefs.edit()
                        .putString(ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD_LIST, raw)
                        .apply()
                    val toastText = if (raw.isEmpty()) {
                        UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_EMPTY
                    } else {
                        UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_SAVED
                    }
                    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showCustomPostFilterKeywordDialog failed: ${t.message}")
        }
    }
}
