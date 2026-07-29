package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsMessageStyle
import com.forbidad4tieba.hook.ui.applySettingsRowTitleStyle
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.createSettingsDialogTitleView
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.ui.settingsRowVerticalPadding

internal object FreeCopyDialog {
    fun show(
        activity: Activity,
        title: String?,
        body: String,
    ): Boolean {
        val normalizedTitle = title?.trim().orEmpty()
        val normalizedBody = body.trim()
        val plainText = combineText(normalizedTitle, normalizedBody)
        if (plainText.isBlank() || activity.isFinishing || activity.isDestroyed) return false

        return try {
            val tokens = UiStyle.tokens(activity)
            val density = activity.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            lateinit var dialog: AlertDialog

            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                addView(
                    TextView(activity).apply {
                        text = buildDisplayText(normalizedTitle, normalizedBody)
                        applySettingsMessageStyle(tokens, density)
                        setTextIsSelectable(true)
                        highlightColor = tokens.accentTrackOn
                        setPadding(0, settingsRowVerticalPadding(density), 0, padding)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }

            val titleView = createSettingsDialogTitleView(
                activity,
                UiText.FreeCopy.DIALOG_TITLE,
            ) as LinearLayout
            val titleText = titleView.findViewById<TextView>(android.R.id.title)
            titleView.removeView(titleText)
            titleView.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        titleText,
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    if (normalizedTitle.isNotEmpty()) {
                        addView(
                            titleActionText(
                                context = activity,
                                label = UiText.FreeCopy.BUTTON_COPY_TITLE,
                                tokens = tokens,
                                density = density,
                            ) {
                                if (
                                    copyText(
                                        activity,
                                        normalizedTitle,
                                        UiText.FreeCopy.TOAST_TITLE_COPIED,
                                    )
                                ) {
                                    dialog.dismiss()
                                }
                            },
                        )
                        if (normalizedBody.isNotEmpty()) {
                            addView(
                                titleActionText(
                                    context = activity,
                                    label = UiText.FreeCopy.BUTTON_COPY_BODY,
                                    tokens = tokens,
                                    density = density,
                                ) {
                                    if (
                                        copyText(
                                            activity,
                                            normalizedBody,
                                            UiText.FreeCopy.TOAST_BODY_COPIED,
                                        )
                                    ) {
                                        dialog.dismiss()
                                    }
                                },
                            )
                        }
                    }
                },
                0,
            )
            dialog = AlertDialog.Builder(activity, dialogThemeFor(activity))
                .setCustomTitle(titleView)
                .setView(createDialogScrollContainer(activity, root))
                .setNegativeButton(UiText.FreeCopy.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.FreeCopy.BUTTON_COPY_ALL, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    if (copyText(activity, plainText, UiText.FreeCopy.TOAST_COPIED)) {
                        dialog.dismiss()
                    }
                }
            }
            dialog.show()
            true
        } catch (t: Throwable) {
            XposedCompat.logW("[FreeCopyDialog] show failed: ${t.message}")
            false
        }
    }

    private fun combineText(title: String, body: String): String {
        return when {
            title.isEmpty() -> body
            body.isEmpty() -> title
            else -> "$title\n\n$body"
        }
    }

    private fun buildDisplayText(title: String, body: String): CharSequence {
        if (title.isEmpty()) return body
        return SpannableStringBuilder().apply {
            val start = length
            append(title)
            setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (body.isNotEmpty()) append("\n\n").append(body)
        }
    }

    private fun titleActionText(
        context: Context,
        label: String,
        tokens: UiStyle.Tokens,
        density: Float,
        action: () -> Unit,
    ): TextView {
        return TextView(context).apply {
            text = label
            applySettingsRowTitleStyle(tokens, density)
            setTextColor(tokens.accent)
            gravity = Gravity.CENTER
            setPadding((8f * density).toInt(), 0, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun copyText(context: Context, content: String, successMessage: String): Boolean {
        if (content.isBlank()) {
            Toast.makeText(context, UiText.FreeCopy.TOAST_COPY_FAILED, Toast.LENGTH_SHORT).show()
            return false
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(context, UiText.FreeCopy.TOAST_COPY_FAILED, Toast.LENGTH_SHORT).show()
            return false
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(UiText.FreeCopy.CLIP_LABEL, content))
        Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
        return true
    }
}
