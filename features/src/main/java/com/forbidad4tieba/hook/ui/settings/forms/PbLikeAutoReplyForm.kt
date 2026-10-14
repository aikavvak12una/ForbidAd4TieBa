package com.forbidad4tieba.hook.ui.settings.forms

import com.forbidad4tieba.hook.config.ReplyPreferences
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import com.forbidad4tieba.hook.core.XposedCompat
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

internal object PbLikeAutoReplyForm {
    fun showPbLikeAutoReplyDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)

            val label = TextView(context).apply {
                text = UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_LABEL
                textSize = SETTINGS_SECTION_TITLE_SP
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textSecondary)
                includeFontPadding = false
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            val input = android.widget.EditText(context).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                styleMultilineInput(tokens, density)
                hint = UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_HINT
                setText(ReplyPreferences.PB_LIKE_AUTO_REPLY_TEXT.read(prefs).orEmpty())
            }
            input.doAfterTextChanged { raw ->
                val content = raw.trim()
                input.error = if (content.isBlank()) UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_EMPTY else null
                if (content.isNotBlank() && ReplyPreferences.PB_LIKE_AUTO_REPLY_TEXT.read(prefs) != content) {
                    prefs.edit().putString(ReplyPreferences.KEY_PB_LIKE_AUTO_REPLY_TEXT, content).apply()
                }
            }
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                addView(label)
                addView(
                    input,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.PB_LIKE_AUTO_REPLY_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, container))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.showSettingsDialog(density)
            input.post {
                input.requestFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showPbLikeAutoReplyDialog failed: ${t.message}")
        }
    }
}
