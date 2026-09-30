package com.forbidad4tieba.hook.ui.settings.forms

import com.forbidad4tieba.hook.config.ReplyPreferences
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.SETTINGS_INPUT_TEXT_SP
import com.forbidad4tieba.hook.ui.SETTINGS_SECTION_TITLE_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
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
                setSingleLine(false)
                maxLines = 6
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                hint = UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_HINT
                setText(ReplyPreferences.PB_LIKE_AUTO_REPLY_TEXT.read(prefs).orEmpty())
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textMuted)
                textSize = SETTINGS_INPUT_TEXT_SP
                includeFontPadding = false
                background = UiStyle.createPlainInputUnderlineBackground(tokens, density)
                setPadding(0, (2 * density).toInt(), 0, (8 * density).toInt())
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
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val content = input.text?.toString()?.trim().orEmpty()
                    if (content.isBlank()) {
                        Toast.makeText(context, UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_EMPTY, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    prefs.edit()
                        .putString(ReplyPreferences.KEY_PB_LIKE_AUTO_REPLY_TEXT, content)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
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
