package com.forbidad4tieba.hook.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.forbidad4tieba.hook.utils.ClearableInputRow

/** Presents search input; page ownership and cache/request decisions stay with the caller. */
internal object CollectionSearchDialog {
    fun show(
        activity: Activity,
        currentQuery: String,
        onSearch: (String) -> Unit,
        onClearQuery: () -> Unit,
        onRefresh: () -> Unit,
    ) {
        val density = activity.resources.displayMetrics.density
        val tokens = UiStyle.tokens(activity)
        val hPad = (24 * density).toInt()
        val vPad = (10 * density).toInt()
        val input = EditText(activity).apply {
            hint = UiText.CollectionSearch.DIALOG_HINT
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(currentQuery)
            setSelection(text?.length ?: 0)
            setPadding(0, vPad, 0, vPad)
            textSize = 14.5f
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textMuted)
            background = null
        }
        val inputRow = ClearableInputRow.create(
            activity = activity,
            input = input,
            initialText = currentQuery,
            clearSymbol = UiText.CollectionSearch.INPUT_CLEAR_SYMBOL,
            verticalPadding = vPad,
            onClear = onClearQuery
        )
        val underline = View(activity).apply {
            setBackgroundColor(tokens.accent)
            alpha = 0.64f
        }
        input.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            underline.alpha = if (hasFocus) 1.0f else 0.64f
        }
        val inputContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, (8 * density).toInt(), hPad, (4 * density).toInt())
            addView(
                inputRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                underline,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (2.0f * density).toInt().coerceAtLeast(1)
                ).apply {
                    topMargin = (2 * density).toInt()
                }
            )
        }

        val dialog = AlertDialog.Builder(activity, dialogThemeFor(activity))
            .setTitle(UiText.CollectionSearch.DIALOG_TITLE)
            .setView(inputContainer)
            .setPositiveButton(UiText.CollectionSearch.DIALOG_ACTION_SEARCH) { _, _ ->
                onSearch(input.text?.toString().orEmpty().trim())
            }
            .setNeutralButton(UiText.CollectionSearch.DIALOG_ACTION_SYNC) { _, _ ->
                onRefresh()
            }
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positive?.isAllCaps = false
            neutral?.isAllCaps = false
            negative?.isAllCaps = false
            positive?.setTextColor(tokens.accent)
            neutral?.setTextColor(tokens.accent)
            negative?.setTextColor(tokens.textSecondary)
        }
        dialog.showSettingsDialog(density)
    }

}
