package com.forbidad4tieba.hook.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassPreferences
import com.forbidad4tieba.hook.config.BooleanPreference
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.settings.SettingsSliderChangeListener

/** Saves switches immediately and slider values after the user releases them. */
internal object BottomTabLiquidGlassDialog {
    fun show(context: Context, prefs: SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val initial = BottomTabLiquidGlassPreferences.read(prefs)
            val defaults = BottomTabLiquidGlassConfig.DEFAULT
            var savedConfig = initial
            var restoringDefaults = false
            fun save(config: BottomTabLiquidGlassConfig) {
                val normalized = config.normalized()
                if (normalized == savedConfig) return
                savedConfig = normalized
                BottomTabLiquidGlassPreferences.save(prefs, normalized)
            }
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
            }
            fun effect(preference: BooleanPreference, checked: Boolean): Switch {
                val presentation = checkNotNull(preference.presentation)
                val row = createSwitchRow(
                    context, prefs, presentation.label, presentation.description, null, padding, defaultValue = checked,
                )
                root.addView(row)
                return checkNotNull(findSwitchView(row))
            }
            val blur = effect(BottomTabLiquidGlassPreferences.BLUR_SETTING, initial.blurEnabled)
            val liquidGlass = effect(BottomTabLiquidGlassPreferences.LIQUID_GLASS_SETTING, initial.liquidGlassEnabled)
            val blurAmount = slider(
                root, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BLUR_AMOUNT,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BLUR_AMOUNT_DESC,
                0, BottomTabLiquidGlassConfig.MAX_BLUR_PERCENT, initial.blurPercent,
                onValueCommitted = { save(savedConfig.copy(blurPercent = it)) },
                format = UiText.Settings::liquidGlassPercent,
            )
            val width = slider(
                root, UiText.Settings.FLOATING_BAR_WIDTH,
                UiText.Settings.FLOATING_BAR_WIDTH_DESC,
                BottomTabLiquidGlassConfig.MIN_ADJUSTMENT,
                BottomTabLiquidGlassConfig.MAX_ADJUSTMENT, initial.widthAdjustment,
                onValueCommitted = { save(savedConfig.copy(widthAdjustment = it)) },
                format = UiText.Settings::liquidGlassAdjustment,
            )
            val height = slider(
                root, UiText.Settings.FLOATING_BAR_HEIGHT,
                UiText.Settings.FLOATING_BAR_HEIGHT_DESC,
                BottomTabLiquidGlassConfig.MIN_ADJUSTMENT,
                BottomTabLiquidGlassConfig.MAX_ADJUSTMENT, initial.heightAdjustment,
                onValueCommitted = { save(savedConfig.copy(heightAdjustment = it)) },
                format = UiText.Settings::liquidGlassAdjustment,
            )
            val gap = slider(
                root, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BOTTOM_GAP,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BOTTOM_GAP_DESC,
                BottomTabLiquidGlassConfig.MIN_ADJUSTMENT,
                BottomTabLiquidGlassConfig.MAX_ADJUSTMENT, initial.positionAdjustment,
                onValueCommitted = { save(savedConfig.copy(positionAdjustment = it)) },
                format = UiText.Settings::liquidGlassAdjustment,
            )

            fun updateAvailability() {
                blur.isEnabled = !liquidGlass.isChecked
                (blur.parent as? android.view.View)?.alpha = if (blur.isEnabled) 1f else 0.5f
                blurAmount.isEnabled = blur.isChecked || liquidGlass.isChecked
                (blurAmount.parent as? android.view.View)?.alpha = if (blurAmount.isEnabled) 1f else 0.5f
            }
            blur.setOnCheckedChangeListener { _, checked ->
                updateAvailability()
                if (!restoringDefaults) save(savedConfig.copy(blurEnabled = checked))
            }
            liquidGlass.setOnCheckedChangeListener { _, checked ->
                updateAvailability()
                if (!restoringDefaults) save(savedConfig.copy(liquidGlassEnabled = checked))
            }
            updateAvailability()

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.BOTTOM_BAR_STYLE_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNeutralButton(UiText.Settings.HOME_NATIVE_GLASS_RESTORE_DEFAULTS, null)
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    restoringDefaults = true
                    try {
                        gap.progress = defaults.positionAdjustment - BottomTabLiquidGlassConfig.MIN_ADJUSTMENT
                        blur.isChecked = defaults.blurEnabled
                        liquidGlass.isChecked = defaults.liquidGlassEnabled
                        blurAmount.progress = defaults.blurPercent
                        width.progress = defaults.widthAdjustment - BottomTabLiquidGlassConfig.MIN_ADJUSTMENT
                        height.progress = defaults.heightAdjustment - BottomTabLiquidGlassConfig.MIN_ADJUSTMENT
                    } finally {
                        restoringDefaults = false
                    }
                    updateAvailability()
                    save(defaults)
                }
            }
            dialog.showSettingsDialog(density)
        } catch (t: Throwable) {
            XposedCompat.logW("[BottomTabLiquidGlassDialog] show failed: ${t.message}")
        }
    }

    private fun slider(
        parent: LinearLayout,
        label: String,
        description: String,
        min: Int,
        max: Int,
        value: Int,
        onValueCommitted: (Int) -> Unit,
        format: (Int) -> String,
    ): SeekBar {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        val tokens = UiStyle.tokens(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = settingsRowVerticalPadding(density)
            setPadding(0, pad, 0, pad)
        }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = label
            applySettingsRowTitleStyle(tokens, density)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueText = TextView(context).apply {
            textSize = SETTINGS_VALUE_TEXT_SP
            setTextColor(tokens.accent)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        header.addView(valueText)
        row.addView(header)
        row.addView(TextView(context).apply {
            text = description
            applySettingsRowDescriptionStyle(tokens, density)
        })
        val slider = SeekBar(context).apply {
            this.max = max - min
            progress = value.coerceIn(min, max) - min
            splitTrack = false
            progressTintList = ColorStateList.valueOf(tokens.accent)
            thumbTintList = ColorStateList.valueOf(tokens.accent)
            progressBackgroundTintList = ColorStateList.valueOf(tokens.divider)
            contentDescription = label
        }
        fun update(progress: Int) { valueText.text = format(progress + min) }
        update(slider.progress)
        slider.setOnSeekBarChangeListener(SettingsSliderChangeListener(
            onValueChanged = ::update,
            onValueCommitted = { onValueCommitted(it + min) },
        ))
        row.addView(slider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        parent.addView(row)
        return slider
    }
}
