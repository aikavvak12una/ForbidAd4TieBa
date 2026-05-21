package com.forbidad4tieba.hook.utils

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView

object ClearableInputRow {
    fun create(
        activity: Activity,
        input: EditText,
        initialText: String,
        clearSymbol: String,
        verticalPadding: Int,
        onClear: () -> Unit,
    ): View {
        val density = activity.resources.displayMetrics.density
        val clearTouchSize = (28 * density).toInt().coerceAtLeast(24)

        val clearButton = TextView(activity).apply {
            text = clearSymbol
            textSize = 13f
            gravity = Gravity.CENTER
            minWidth = clearTouchSize
            minHeight = clearTouchSize
            setTextColor(0xFF8FA1BA.toInt())
            setPadding((4 * density).toInt(), verticalPadding, (2 * density).toInt(), verticalPadding)
            visibility = if (initialText.isBlank()) View.INVISIBLE else View.VISIBLE
            setOnClickListener {
                input.setText("")
                input.requestFocus()
                onClear()
            }
        }

        val baseRightPadding = input.paddingRight
        val clearAreaPadding = clearTouchSize + (6 * density).toInt()
        input.setPadding(
            input.paddingLeft,
            input.paddingTop,
            maxOf(baseRightPadding, clearAreaPadding),
            input.paddingBottom
        )
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                clearButton.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
            }
        })

        return FrameLayout(activity).apply {
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                clearButton,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL
                )
            )
        }
    }
}
