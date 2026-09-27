package com.forbidad4tieba.hook.ui.settings.glass

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageAnalysis
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageFiles
import com.forbidad4tieba.hook.ui.HomeNativeGlassImagePickerBridge
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageSelectionState
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_TITLE_SP
import com.forbidad4tieba.hook.ui.SETTINGS_VALUE_TEXT_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsRowDescriptionStyle
import com.forbidad4tieba.hook.ui.applySettingsRowTitleStyle
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.createSettingsInputBackground
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormState.homeNativeGlassDialogTokens
import com.forbidad4tieba.hook.ui.settingsRowVerticalPadding
import kotlin.math.max

internal object HomeNativeGlassFormViews {
    private const val HOME_NATIVE_GLASS_MODE_SELECTOR_MIN_FILL_ALPHA = 36

    private const val HOME_NATIVE_GLASS_MODE_SELECTOR_MAX_FILL_ALPHA = 128

    fun refreshHomeNativeGlassStyledViews(
        view: View,
        tokens: UiStyle.Tokens,
        density: Float,
    ) {
        applyHomeNativeGlassStyleRole(view, tokens, density)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                refreshHomeNativeGlassStyledViews(view.getChildAt(index), tokens, density)
            }
        }
    }

    private fun applyHomeNativeGlassStyleRole(
        view: View,
        tokens: UiStyle.Tokens,
        density: Float,
    ) {
        when (view.tag as? HomeNativeGlassStyleRole) {
            HomeNativeGlassStyleRole.ROW_TITLE -> (view as? TextView)
                ?.setTextColor(tokens.textPrimary)
            HomeNativeGlassStyleRole.ROW_DESCRIPTION -> (view as? TextView)
                ?.setTextColor(tokens.textSecondary)
            HomeNativeGlassStyleRole.MUTED_TEXT -> (view as? TextView)
                ?.setTextColor(tokens.textMuted)
            HomeNativeGlassStyleRole.ACCENT_TEXT -> (view as? TextView)
                ?.setTextColor(homeNativeGlassSliderAccent(tokens))
            HomeNativeGlassStyleRole.BUTTON_ACCENT -> (view as? Button)
                ?.let { UiStyle.paintScanActionButton(it, density, tokens.accent) }
            HomeNativeGlassStyleRole.BUTTON_SECONDARY -> (view as? Button)
                ?.let { UiStyle.paintScanActionButton(it, density, tokens.textSecondary) }
            HomeNativeGlassStyleRole.INPUT_TEXT -> (view as? TextView)?.let { textView ->
                textView.setTextColor(tokens.textPrimary)
                textView.background = createSettingsInputBackground(tokens, density)
            }
            HomeNativeGlassStyleRole.SEEK_BAR -> (view as? android.widget.SeekBar)
                ?.let { applyHomeNativeGlassSeekBarTint(it, tokens) }
            HomeNativeGlassStyleRole.SWITCH -> (view as? Switch)
                ?.let { applyHomeNativeGlassSwitchTint(it, tokens) }
            null -> Unit
        }
    }

    fun createHomeNativeGlassModeSelectorRow(
        context: Context,
        density: Float,
        selectedDarkMode: Boolean,
        lightModeState: HomeNativeGlassModeConfigState,
        darkModeState: HomeNativeGlassModeConfigState,
        onSelected: (Boolean) -> Unit,
    ): Pair<View, (Boolean) -> Unit> {
        val initialTokens = homeNativeGlassDialogTokens(
            context,
            if (selectedDarkMode) darkModeState else lightModeState,
            selectedDarkMode,
        )
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val verticalPadding = settingsRowVerticalPadding(density)
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        val segment = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createHomeNativeGlassModeSegmentBackground(initialTokens, density)
            setPadding((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
        }
        val lightOption = TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_MODE_LIGHT_LABEL
            gravity = Gravity.CENTER
            includeFontPadding = false
            setOnClickListener { onSelected(false) }
        }
        val darkOption = TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_MODE_DARK_LABEL
            gravity = Gravity.CENTER
            includeFontPadding = false
            setOnClickListener { onSelected(true) }
        }
        segment.addView(
            lightOption,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        segment.addView(
            darkOption,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        root.addView(
            segment,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (36 * density).toInt()),
        )

        fun update(selectedDark: Boolean) {
            val tokens = homeNativeGlassDialogTokens(
                context,
                if (selectedDark) darkModeState else lightModeState,
                selectedDark,
            )
            segment.background = createHomeNativeGlassModeSegmentBackground(tokens, density)
            applyHomeNativeGlassModeOptionStyle(
                lightOption,
                selected = !selectedDark,
                fillColor = homeNativeGlassModeTintFillColor(lightModeState, darkMode = false),
                tokens,
                density,
            )
            applyHomeNativeGlassModeOptionStyle(
                darkOption,
                selected = selectedDark,
                fillColor = homeNativeGlassModeTintFillColor(darkModeState, darkMode = true),
                tokens,
                density,
            )
        }
        update(selectedDarkMode)
        return root to ::update
    }

    private fun homeNativeGlassModeTintFillColor(
        state: HomeNativeGlassModeConfigState,
        darkMode: Boolean,
    ): Int {
        val offset = state.tintAlphaPercent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
        val alpha = (kotlin.math.abs(offset) * 255 / 100)
            .coerceAtLeast(HOME_NATIVE_GLASS_MODE_SELECTOR_MIN_FILL_ALPHA)
            .coerceAtMost(HOME_NATIVE_GLASS_MODE_SELECTOR_MAX_FILL_ALPHA)
        val overlay = when {
            offset < 0 -> Color.BLACK
            offset > 0 -> Color.WHITE
            darkMode -> Color.BLACK
            else -> Color.WHITE
        }
        return Color.argb(
            alpha,
            Color.red(overlay),
            Color.green(overlay),
            Color.blue(overlay),
        )
    }

    private fun createHomeNativeGlassModeSegmentBackground(
        tokens: UiStyle.Tokens,
        density: Float,
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = tokens.cardCornerPx
            setColor(Color.TRANSPARENT)
            setStroke((1f * density).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    private fun applyHomeNativeGlassModeOptionStyle(
        option: TextView,
        selected: Boolean,
        fillColor: Int,
        tokens: UiStyle.Tokens,
        density: Float,
    ) {
        option.textSize = SETTINGS_ROW_TITLE_SP
        option.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        option.setTextColor(if (selected) tokens.accent else tokens.textSecondary)
        option.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = tokens.cardCornerPx
            setColor(if (selected) fillColor else Color.TRANSPARENT)
        }
    }

    fun createHomeNativeGlassImagePickerRow(
        context: Context,
        state: HomeNativeGlassImageSelectionState,
        density: Float,
        darkModeProvider: () -> Boolean = { false },
        refreshPalette: (() -> Unit)? = null,
        onImportedAnalysis: ((HomeNativeGlassImageAnalysis) -> Unit)? = null,
    ): Pair<View, TextView> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val verticalPadding = settingsRowVerticalPadding(density)
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_LABEL
            applySettingsRowTitleStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_TITLE
        })
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_DESC
            applySettingsRowDescriptionStyle(tokens, density, rightPaddingDp = 0f, bottomPaddingDp = 8f)
            tag = HomeNativeGlassStyleRole.ROW_DESCRIPTION
        })

        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val display = TextView(context).apply {
            text = HomeNativeGlassImageFiles.displayText(state.path)
            textSize = SETTINGS_VALUE_TEXT_SP
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextColor(tokens.textPrimary)
            includeFontPadding = false
            background = createSettingsInputBackground(context, density)
            tag = HomeNativeGlassStyleRole.INPUT_TEXT
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener {
                HomeNativeGlassImagePickerBridge.launch(
                    context,
                    state,
                    this,
                    darkModeProvider(),
                    refreshPalette,
                    onImportedAnalysis,
                )
            }
        }
        controlRow.addView(
            display,
            LinearLayout.LayoutParams(0, (40 * density).toInt(), 1.0f),
        )

        val chooseButton = Button(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_CHOOSE
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            tag = HomeNativeGlassStyleRole.BUTTON_ACCENT
            setOnClickListener {
                HomeNativeGlassImagePickerBridge.launch(
                    context,
                    state,
                    display,
                    darkModeProvider(),
                    refreshPalette,
                    onImportedAnalysis,
                )
            }
        }
        controlRow.addView(
            chooseButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (8 * density).toInt()
            },
        )

        val clearButton = Button(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_CLEAR
            UiStyle.paintScanActionButton(this, density, tokens.textSecondary)
            tag = HomeNativeGlassStyleRole.BUTTON_SECONDARY
            setOnClickListener {
                state.path = ""
                state.tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                state.paletteColors = emptyList()
                state.defaultTintColor = null
                display.text = HomeNativeGlassImageFiles.displayText(state.path)
                refreshPalette?.invoke()
                HomeNativeGlassImagePickerBridge.clearIfState(state)
            }
        }
        controlRow.addView(
            clearButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (4 * density).toInt()
            },
        )
        root.addView(controlRow)
        return root to display
    }

    fun createHomeNativeGlassSwitchRow(
        context: Context,
        label: String,
        description: String,
        checked: Boolean,
        density: Float,
    ): Pair<View, Switch> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
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
            tag = HomeNativeGlassStyleRole.ROW_TITLE
        })
        textContainer.addView(TextView(context).apply {
            text = description
            applySettingsRowDescriptionStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_DESCRIPTION
        })
        root.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
        )
        val switch = Switch(context).apply {
            isChecked = checked
            applyHomeNativeGlassSwitchTint(this, tokens)
            tag = HomeNativeGlassStyleRole.SWITCH
        }
        root.addView(
            switch,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.setOnClickListener {
            switch.isChecked = !switch.isChecked
        }
        return root to switch
    }

    private fun applyHomeNativeGlassSwitchTint(
        switch: Switch,
        tokens: UiStyle.Tokens,
    ) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        )
        switch.thumbTintList = ColorStateList(states, intArrayOf(tokens.accent, tokens.accentThumbOff))
        switch.trackTintList = ColorStateList(states, intArrayOf(tokens.accentTrackOn, tokens.accentTrackOff))
    }

    fun addHomeNativeGlassSettingRow(
        root: LinearLayout,
        row: View,
        density: Float,
        topMarginDp: Int = 0,
    ) {
        applyHomeNativeGlassSettingRowStyle(row, density)
        root.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = (topMarginDp * density).toInt()
            },
        )
    }

    private fun applyHomeNativeGlassSettingRowStyle(row: View, density: Float) {
        row.setPadding(
            0,
            row.paddingTop,
            0,
            row.paddingBottom,
        )
        row.background = null
    }

    fun createSeekBarRow(
        context: Context,
        label: String,
        description: String,
        minValue: Int,
        maxValue: Int,
        value: Int,
        suffix: String,
        density: Float,
        onValueChanged: ((Int) -> Unit)? = null,
        onStopTrackingTouch: ((Int) -> Unit)? = null,
    ): Pair<View, android.widget.SeekBar> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val verticalPadding = settingsRowVerticalPadding(density)
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        textContainer.addView(TextView(context).apply {
            text = label
            applySettingsRowTitleStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_TITLE
        })
        textContainer.addView(TextView(context).apply {
            text = description
            applySettingsRowDescriptionStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_DESCRIPTION
        })
        header.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
        )
        val valueText = TextView(context).apply {
            textSize = SETTINGS_VALUE_TEXT_SP
            setTextColor(homeNativeGlassSliderAccent(tokens))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            includeFontPadding = false
            setPadding((8 * density).toInt(), 0, 0, 0)
            tag = HomeNativeGlassStyleRole.ACCENT_TEXT
        }
        header.addView(
            valueText,
            LinearLayout.LayoutParams((72 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(header)

        val safeValue = value.coerceIn(minValue, maxValue)
        val seekBar = android.widget.SeekBar(context).apply {
            max = maxValue - minValue
            progress = safeValue - minValue
            splitTrack = false
            applyHomeNativeGlassSeekBarTint(this, tokens)
            tag = HomeNativeGlassStyleRole.SEEK_BAR
        }
        fun updateValueText(progress: Int) {
            valueText.text = "${progress + minValue}$suffix"
        }
        updateValueText(seekBar.progress)
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                updateValueText(progress)
                onValueChanged?.invoke(progress + minValue)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val progress = seekBar?.progress ?: return
                onStopTrackingTouch?.invoke(progress + minValue)
            }
        })
        root.addView(
            seekBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return root to seekBar
    }

    private fun applyHomeNativeGlassSeekBarTint(
        seekBar: android.widget.SeekBar,
        tokens: UiStyle.Tokens,
    ) {
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_enabled),
        )
        val accent = homeNativeGlassSliderAccent(tokens)
        val trackColor = homeNativeGlassSliderTrackColor(tokens)
        seekBar.progressTintList = ColorStateList(states, intArrayOf(tokens.textMuted, accent))
        seekBar.thumbTintList = ColorStateList(states, intArrayOf(tokens.textMuted, accent))
        seekBar.progressBackgroundTintList = ColorStateList(states, intArrayOf(tokens.divider, trackColor))
        seekBar.secondaryProgressTintList = ColorStateList(states, intArrayOf(tokens.divider, trackColor))
    }

    private fun homeNativeGlassSliderAccent(tokens: UiStyle.Tokens): Int {
        return tokens.accent
    }

    private fun homeNativeGlassSliderTrackColor(tokens: UiStyle.Tokens): Int {
        return tokens.inputStroke
    }
}
