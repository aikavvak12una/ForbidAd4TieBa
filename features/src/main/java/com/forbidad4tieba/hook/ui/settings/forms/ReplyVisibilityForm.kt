package com.forbidad4tieba.hook.ui.settings.forms

import com.forbidad4tieba.hook.config.ReplyPreferences
import com.forbidad4tieba.hook.config.IntPreference
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.SETTINGS_VALUE_TEXT_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsRowDescriptionStyle
import com.forbidad4tieba.hook.ui.applySettingsRowTitleStyle
import com.forbidad4tieba.hook.ui.showSettingsDialog
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.createDivider
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.createSettingsInputBackground
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.doAfterTextChanged
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.prepareSettingsInputForKeyboard
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.ui.settingsRowVerticalPadding

internal object ReplyVisibilityForm {
    fun showReplyVisibilityProbeDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)

            fun createNumberInputRow(
                label: String,
                description: String,
                value: Int,
                unit: String? = null,
            ): Pair<View, android.widget.EditText> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val verticalPadding = settingsRowVerticalPadding(density)
                    setPadding(0, verticalPadding, 0, verticalPadding)
                }
                val textContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                textContainer.addView(TextView(context).apply {
                    text = label
                    applySettingsRowTitleStyle(tokens, density)
                })
                textContainer.addView(TextView(context).apply {
                    text = description
                    applySettingsRowDescriptionStyle(tokens, density)
                })
                row.addView(
                    textContainer,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
                )

                val input = android.widget.EditText(context).apply {
                    setSingleLine(true)
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    setText(value.toString())
                    textSize = SETTINGS_VALUE_TEXT_SP
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    setHintTextColor(tokens.textMuted)
                    includeFontPadding = false
                    background = createSettingsInputBackground(context, density)
                    setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                    prepareSettingsInputForKeyboard(this)
                }
                row.addView(
                    input,
                    LinearLayout.LayoutParams((64 * density).toInt(), (34 * density).toInt()).apply {
                        leftMargin = (10 * density).toInt()
                    },
                )
                if (unit != null) {
                    row.addView(
                        TextView(context).apply {
                            text = unit
                            textSize = SETTINGS_VALUE_TEXT_SP
                            setTextColor(tokens.textSecondary)
                            includeFontPadding = false
                            setPadding((6 * density).toInt(), 0, 0, 0)
                        },
                    )
                }
                return row to input
            }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
            }
            root.addView(TextView(context).apply {
                text = UiText.Settings.REPLY_VISIBILITY_PROBE_GUIDE
                applySettingsRowDescriptionStyle(tokens, density, rightPaddingDp = 0f, bottomPaddingDp = 0f)
                setPadding(0, 0, 0, (8 * density).toInt())
            })

            val maxAttemptsRow = createNumberInputRow(
                UiText.Settings.REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS_LABEL,
                UiText.Settings.replyVisibilityProbeMaxAttemptsDesc(
                    ReplyPreferences.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ReplyPreferences.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ReplyPreferences.DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                ),
                ReplyPreferences.REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS.read(prefs).coerceIn(
                    ReplyPreferences.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ReplyPreferences.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                ),
            )
            val intervalRow = createNumberInputRow(
                UiText.Settings.REPLY_VISIBILITY_PROBE_INTERVAL_LABEL,
                UiText.Settings.replyVisibilityProbeIntervalDesc(
                    ReplyPreferences.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ReplyPreferences.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ReplyPreferences.DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                ),
                ReplyPreferences.REPLY_VISIBILITY_PROBE_INTERVAL_MS.read(prefs).coerceIn(
                    ReplyPreferences.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ReplyPreferences.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                ),
                UiText.Settings.REPLY_VISIBILITY_PROBE_INTERVAL_UNIT,
            )
            root.addView(maxAttemptsRow.first)
            root.addView(createDivider(context, padding))
            root.addView(intervalRow.first)

            fun bindNumberInput(input: android.widget.EditText, preference: IntPreference, min: Int, max: Int) {
                input.doAfterTextChanged { raw ->
                    val value = raw.trim().toIntOrNull()?.takeIf { it in min..max }
                    input.error = if (value == null) UiText.Settings.REPLY_VISIBILITY_PROBE_CONFIG_INVALID else null
                    if (value != null && preference.read(prefs) != value) {
                        prefs.edit().putInt(preference.key, value).apply()
                    }
                }
            }
            bindNumberInput(
                maxAttemptsRow.second, ReplyPreferences.REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                ReplyPreferences.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS, ReplyPreferences.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
            )
            bindNumberInput(
                intervalRow.second, ReplyPreferences.REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                ReplyPreferences.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS, ReplyPreferences.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
            )
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.REPLY_VISIBILITY_PROBE_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.showSettingsDialog(density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showReplyVisibilityProbeDialog failed: ${t.message}")
        }
    }
}
