package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import kotlin.math.min

internal object FreeCopyDialog {
    fun show(
        activity: Activity,
        title: String?,
        body: String,
    ): Boolean {
        val normalizedTitle = title?.trim().orEmpty()
        val normalizedBody = body.trim()
        val plainText = buildPlainText(title, body)
        if (plainText.isBlank() || activity.isFinishing || activity.isDestroyed) return false
        return try {
            val tokens = UiStyle.tokens(activity)
            val density = activity.resources.displayMetrics.density
            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                elevation = 12f * density
                setPadding(dp(density, 20), dp(density, 18), dp(density, 20), dp(density, 10))
                background = GradientDrawable().apply {
                    setColor(tokens.surface)
                    cornerRadius = 22f * density
                }
            }

            root.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(activity).apply {
                            text = UiText.FreeCopy.DIALOG_TITLE
                            textSize = 18f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(tokens.textPrimary)
                            includeFontPadding = false
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    if (normalizedTitle.isNotEmpty()) {
                        addView(
                            headerActionButton(
                                activity,
                                UiText.FreeCopy.BUTTON_COPY_TITLE,
                                tokens.accent,
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
                        addView(
                            headerActionButton(
                                activity,
                                UiText.FreeCopy.BUTTON_COPY_BODY,
                                tokens.accent,
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
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )

            val displayText = buildDisplayText(title, body)
            root.addView(
                BoundedScrollView(activity, contentMaxHeight(activity, density)).apply {
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    isFillViewport = false
                    addView(
                        TextView(activity).apply {
                            text = displayText
                            textSize = 16f
                            setTextColor(tokens.textPrimary)
                            setTextIsSelectable(true)
                            highlightColor = tokens.accentTrackOn
                            includeFontPadding = false
                            setLineSpacing(3f * density, 1f)
                            setPadding(
                                dp(density, 14),
                                dp(density, 13),
                                dp(density, 14),
                                dp(density, 13),
                            )
                            background = GradientDrawable().apply {
                                setColor(tokens.surfaceAlt)
                                cornerRadius = 12f * density
                            }
                        },
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(density, 14)
                    bottomMargin = dp(density, 8)
                },
            )

            root.addView(View(activity).apply {
                setBackgroundColor(tokens.divider)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

            root.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        actionButton(
                            activity,
                            UiText.FreeCopy.BUTTON_CANCEL,
                            tokens.textSecondary,
                        ) { dialog.dismiss() },
                        LinearLayout.LayoutParams(0, dp(density, 48), 1f),
                    )
                    addView(View(activity).apply {
                        setBackgroundColor(tokens.divider)
                    }, LinearLayout.LayoutParams(1, dp(density, 24)))
                    addView(
                        actionButton(
                            activity,
                            UiText.FreeCopy.BUTTON_COPY_ALL,
                            tokens.accent,
                        ) {
                            if (copyAll(activity, plainText)) dialog.dismiss()
                        },
                        LinearLayout.LayoutParams(0, dp(density, 48), 1f),
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(density, 48),
                ),
            )

            dialog.setContentView(root)
            dialog.setCanceledOnTouchOutside(true)
            dialog.show()
            styleWindow(dialog.window, tokens.surface)
            true
        } catch (t: Throwable) {
            XposedCompat.logW("[FreeCopyDialog] show failed: ${t.message}")
            false
        }
    }

    private fun buildPlainText(title: String?, body: String): String {
        val normalizedTitle = title?.trim().orEmpty()
        val normalizedBody = body.trim()
        return when {
            normalizedTitle.isEmpty() -> normalizedBody
            normalizedBody.isEmpty() -> normalizedTitle
            else -> "$normalizedTitle\n\n$normalizedBody"
        }
    }

    private fun buildDisplayText(title: String?, body: String): CharSequence {
        val normalizedTitle = title?.trim().orEmpty()
        val normalizedBody = body.trim()
        if (normalizedTitle.isEmpty()) return normalizedBody
        return SpannableStringBuilder().apply {
            val start = length
            append(normalizedTitle)
            setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (normalizedBody.isNotEmpty()) append("\n\n").append(normalizedBody)
        }
    }

    private fun actionButton(
        context: Context,
        label: String,
        color: Int,
        action: () -> Unit,
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(color)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun headerActionButton(
        context: Context,
        label: String,
        color: Int,
        action: () -> Unit,
    ): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(color)
            includeFontPadding = false
            setPadding(dp(density, 8), dp(density, 7), dp(density, 4), dp(density, 7))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun copyAll(context: Context, content: String): Boolean {
        return copyText(context, content, UiText.FreeCopy.TOAST_COPIED)
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

    private fun contentMaxHeight(context: Context, density: Float): Int {
        return min(dp(density, 420), (context.resources.displayMetrics.heightPixels * 0.58f).toInt())
    }

    private fun styleWindow(window: Window?, navigationBarColor: Int) {
        window ?: return
        val density = window.context.resources.displayMetrics.density
        val screenWidth = window.context.resources.displayMetrics.widthPixels
        val horizontalMargin = dp(density, 24)
        val targetWidth = min(
            (screenWidth - horizontalMargin * 2).coerceAtLeast(1),
            dp(density, 420),
        )
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(0.28f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setGravity(Gravity.CENTER)
        val attributes = window.attributes
        attributes.gravity = Gravity.CENTER
        attributes.x = 0
        attributes.y = 0
        attributes.width = targetWidth
        attributes.height = WindowManager.LayoutParams.WRAP_CONTENT
        window.attributes = attributes
        window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        window.navigationBarColor = navigationBarColor
        window.decorView.setPadding(0, 0, 0, 0)
    }

    private fun dp(density: Float, value: Int): Int = (value * density).toInt()

    private class BoundedScrollView(
        context: Context,
        private val maxHeight: Int,
    ) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
            )
        }
    }
}
