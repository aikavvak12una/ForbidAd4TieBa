package com.forbidad4tieba.hook.ui.settings.forms

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.SETTINGS_VALUE_TEXT_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsRowDescriptionStyle
import com.forbidad4tieba.hook.ui.applySettingsRowTitleStyle
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.createDivider
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.createSettingsInputBackground
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.prepareSettingsInputForKeyboard
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.ui.settingsRowVerticalPadding
import kotlin.math.max

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
                    ConfigManager.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ConfigManager.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                ),
                prefs.getInt(
                    ConfigManager.KEY_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                ).coerceIn(
                    ConfigManager.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ConfigManager.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                ),
            )
            val intervalRow = createNumberInputRow(
                UiText.Settings.REPLY_VISIBILITY_PROBE_INTERVAL_LABEL,
                UiText.Settings.replyVisibilityProbeIntervalDesc(
                    ConfigManager.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ConfigManager.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                ),
                prefs.getInt(
                    ConfigManager.KEY_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                ).coerceIn(
                    ConfigManager.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ConfigManager.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                ),
                UiText.Settings.REPLY_VISIBILITY_PROBE_INTERVAL_UNIT,
            )
            root.addView(maxAttemptsRow.first)
            root.addView(createDivider(context, padding))
            root.addView(intervalRow.first)

            fun parseNumber(input: android.widget.EditText, min: Int, max: Int): Int? {
                val value = input.text?.toString()?.trim()?.toIntOrNull() ?: return null
                return value.takeIf { it in min..max }
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.REPLY_VISIBILITY_PROBE_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val maxAttempts = parseNumber(
                        maxAttemptsRow.second,
                        ConfigManager.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                        ConfigManager.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    )
                    val intervalMs = parseNumber(
                        intervalRow.second,
                        ConfigManager.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                        ConfigManager.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    )
                    if (maxAttempts == null || intervalMs == null) {
                        Toast.makeText(
                            context,
                            UiText.Settings.REPLY_VISIBILITY_PROBE_CONFIG_INVALID,
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@setOnClickListener
                    }
                    prefs.edit()
                        .putInt(ConfigManager.KEY_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS, maxAttempts)
                        .putInt(ConfigManager.KEY_REPLY_VISIBILITY_PROBE_INTERVAL_MS, intervalMs)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.REPLY_VISIBILITY_PROBE_CONFIG_SAVED,
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showReplyVisibilityProbeDialog failed: ${t.message}")
        }
    }
}
