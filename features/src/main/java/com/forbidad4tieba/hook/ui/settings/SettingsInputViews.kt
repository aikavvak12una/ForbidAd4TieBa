package com.forbidad4tieba.hook.ui.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.forbidad4tieba.hook.ui.SETTINGS_INPUT_TEXT_SP
import com.forbidad4tieba.hook.ui.UiStyle

internal object SettingsInputViews {
    fun EditText.styleMultilineInput(tokens: UiStyle.Tokens, density: Float) {
        setSingleLine(false)
        setHorizontallyScrolling(false)
        minLines = 1
        maxLines = 6
        minimumHeight = (48 * density).toInt()
        gravity = Gravity.TOP or Gravity.START
        textSize = SETTINGS_INPUT_TEXT_SP
        includeFontPadding = false
        setTextColor(tokens.textPrimary)
        setHintTextColor(tokens.textMuted)
        background = createSettingsInputBackground(tokens, density)
        setPadding((4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt())
    }

    fun EditText.doAfterTextChanged(onChanged: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onChanged(s?.toString().orEmpty())
        })
    }

    fun createSettingsInputBackground(context: Context, density: Float): Drawable {
        val tokens = UiStyle.tokens(context)
        return createSettingsInputBackground(tokens, density)
    }

    fun createSettingsInputBackground(
        tokens: UiStyle.Tokens,
        density: Float,
    ): Drawable {
        return UiStyle.createModelScoreInputBackground(tokens, density)
    }

    @Suppress("DEPRECATION")
    fun prepareSettingsDialogWindowForInput(window: android.view.Window) {
        window.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        )
    }

    fun prepareSettingsInputForKeyboard(input: android.widget.EditText) {
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.setOnClickListener {
            showSettingsKeyboard(input)
        }
        input.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showSettingsKeyboard(input)
        }
    }

    fun showSettingsKeyboard(input: android.widget.EditText) {
        input.post {
            input.requestFocus()
            val imm = input.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
