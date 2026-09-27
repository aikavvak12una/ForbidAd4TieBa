package com.forbidad4tieba.hook.ui.settings.glass

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageAnalyzer
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageSelectionState
import com.forbidad4tieba.hook.ui.SETTINGS_INPUT_TEXT_SP
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_DESC_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsRowDescriptionStyle
import com.forbidad4tieba.hook.ui.applySettingsRowTitleStyle
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.ui.settingsRowVerticalPadding

internal object HomeNativeGlassTintPalette {
    private fun parseHomeNativeGlassUserTintColor(raw: String): Int? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val hex = when {
            value.startsWith("#") -> value.substring(1)
            value.startsWith("0x", ignoreCase = true) -> value.substring(2)
            else -> value
        }
        if (hex.length != 6 && hex.length != 8) return null
        if (hex.any { digit ->
                digit !in '0'..'9' &&
                    digit !in 'a'..'f' &&
                    digit !in 'A'..'F'
            }
        ) {
            return null
        }
        val rgb = (hex.toLongOrNull(16) ?: return null) and 0xFFFFFFL
        return ConfigManager.normalizeHomeNativeGlassTintColor(
            Color.rgb(
                ((rgb ushr 16) and 0xFFL).toInt(),
                ((rgb ushr 8) and 0xFFL).toInt(),
                (rgb and 0xFFL).toInt(),
            )
        )
    }

    fun createHomeNativeGlassTintColorRow(
        context: Context,
        state: HomeNativeGlassImageSelectionState,
        density: Float,
    ): Pair<View, () -> Unit> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val verticalPadding = settingsRowVerticalPadding(density)
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_LABEL
            applySettingsRowTitleStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_TITLE
        })
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_DESC
            applySettingsRowDescriptionStyle(tokens, density, rightPaddingDp = 0f, bottomPaddingDp = 8f)
            tag = HomeNativeGlassStyleRole.ROW_DESCRIPTION
        })

        val swatchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                swatchRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val emptyText = TextView(context).apply {
            textSize = SETTINGS_ROW_DESC_SP
            setTextColor(tokens.textMuted)
            includeFontPadding = false
            setLineSpacing(1f * density, 1f)
            setPadding(0, (6 * density).toInt(), 0, 0)
            tag = HomeNativeGlassStyleRole.MUTED_TEXT
        }
        root.addView(emptyText)

        lateinit var refresh: () -> Unit
        refresh = {
            swatchRow.removeAllViews()
            val selectedAuto = state.tintColor == ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
            swatchRow.addView(
                createHomeNativeGlassTintDefaultSwatch(
                    context,
                    tokens,
                    density,
                    state.defaultTintColor,
                    selectedAuto,
                ) {
                    state.tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                    refresh()
                },
                LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                    rightMargin = (8 * density).toInt()
                },
            )
            val paletteColors = displayedHomeNativeGlassPaletteColors(state)
            paletteColors.forEachIndexed { index, color ->
                swatchRow.addView(
                    createHomeNativeGlassTintSwatch(
                        context = context,
                        color = color,
                        selected = state.tintColor == color,
                        density = density,
                        tokens = tokens,
                        index = index,
                    ) {
                        state.tintColor = color
                        refresh()
                    },
                    LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                        rightMargin = (8 * density).toInt()
                    },
                )
            }
            if (state.path.isNotBlank()) {
                swatchRow.addView(
                    createHomeNativeGlassTintAddSwatch(
                        context = context,
                        tokens = tokens,
                        density = density,
                    ) {
                        showHomeNativeGlassAddTintColorDialog(
                            context = context,
                            state = state,
                            density = density,
                            refreshPalette = refresh,
                        )
                    },
                    LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                        rightMargin = (8 * density).toInt()
                    },
                )
            }
            emptyText.text = if (state.path.isBlank()) {
                UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_EMPTY
            } else {
                UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_UNAVAILABLE
            }
            emptyText.visibility = if (paletteColors.isEmpty()) View.VISIBLE else View.GONE
        }
        refresh()
        return root to refresh
    }

    private fun displayedHomeNativeGlassPaletteColors(
        state: HomeNativeGlassImageSelectionState,
    ): List<Int> {
        val defaultColor = state.defaultTintColor ?: return state.paletteColors
        val minDistanceSquared = HomeNativeGlassImageAnalyzer.TINT_PALETTE_MIN_DISTANCE *
            HomeNativeGlassImageAnalyzer.TINT_PALETTE_MIN_DISTANCE
        return state.paletteColors.filter { color ->
            state.tintColor == color ||
                HomeNativeGlassImageAnalyzer.tintColorDistanceSquared(color, defaultColor) >= minDistanceSquared
        }
    }

    private fun showHomeNativeGlassAddTintColorDialog(
        context: Context,
        state: HomeNativeGlassImageSelectionState,
        density: Float,
        refreshPalette: () -> Unit,
    ) {
        try {
            val tokens = UiStyle.tokens(context)
            val padding = settingsDialogPadding(density)
            val input = android.widget.EditText(context).apply {
                setSingleLine(true)
                hint = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD_HINT
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textMuted)
                textSize = SETTINGS_INPUT_TEXT_SP
                includeFontPadding = false
                background = UiStyle.createPlainInputUnderlineBackground(tokens, density)
                setPadding(
                    0,
                    (2 * density).toInt(),
                    0,
                    (8 * density).toInt(),
                )
            }
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                addView(
                    input,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, container))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val color = parseHomeNativeGlassUserTintColor(input.text?.toString().orEmpty())
                    if (color == null) {
                        input.error = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD_INVALID
                        return@setOnClickListener
                    }
                    val existed = state.paletteColors.any { it == color }
                    if (!existed) {
                        state.paletteColors = state.paletteColors + color
                    }
                    state.tintColor = color
                    refreshPalette()
                    Toast.makeText(
                        context,
                        if (existed) {
                            UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_DUPLICATED
                        } else {
                            UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADDED
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
                input.post {
                    input.requestFocus()
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showHomeNativeGlassAddTintColorDialog failed: ${t.message}")
        }
    }

    private fun createHomeNativeGlassTintDefaultSwatch(
        context: Context,
        tokens: UiStyle.Tokens,
        density: Float,
        previewColor: Int?,
        selected: Boolean,
        onClick: () -> Unit,
    ): View {
        return View(context).apply {
            background = createHomeNativeGlassTintDefaultSwatchBackground(
                previewColor,
                selected,
                tokens,
                density,
            )
            contentDescription = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_DEFAULT
            isSelected = selected
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createHomeNativeGlassTintDefaultSwatchBackground(
        previewColor: Int?,
        selected: Boolean,
        tokens: UiStyle.Tokens,
        density: Float,
    ): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(previewColor ?: tokens.accentSoft)
            cornerRadius = 999f * density
            setStroke(
                ((if (selected) 3f else 1f) * density).toInt().coerceAtLeast(1),
                if (selected) tokens.accent else tokens.divider,
            )
        }
    }

    private fun createHomeNativeGlassTintAddSwatch(
        context: Context,
        tokens: UiStyle.Tokens,
        density: Float,
        onClick: () -> Unit,
    ): View {
        return TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.accent)
            tag = HomeNativeGlassStyleRole.ACCENT_TEXT
            background = createHomeNativeGlassTintAddSwatchBackground(tokens, density)
            contentDescription = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD_DESC
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createHomeNativeGlassTintAddSwatchBackground(
        tokens: UiStyle.Tokens,
        density: Float,
    ): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(tokens.surfaceAlt)
            cornerRadius = 999f * density
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    private fun createHomeNativeGlassTintSwatch(
        context: Context,
        color: Int,
        selected: Boolean,
        density: Float,
        tokens: UiStyle.Tokens,
        index: Int,
        onClick: () -> Unit,
    ): View {
        return View(context).apply {
            background = createHomeNativeGlassTintSwatchBackground(color, selected, tokens, density)
            contentDescription = UiText.Settings.homeNativeGlassTintColorSwatch(index + 1)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createHomeNativeGlassTintSwatchBackground(
        color: Int,
        selected: Boolean,
        tokens: UiStyle.Tokens,
        density: Float,
    ): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 999f * density
            setStroke(
                ((if (selected) 3f else 1f) * density).toInt().coerceAtLeast(1),
                if (selected) tokens.accent else tokens.divider,
            )
        }
    }
}
