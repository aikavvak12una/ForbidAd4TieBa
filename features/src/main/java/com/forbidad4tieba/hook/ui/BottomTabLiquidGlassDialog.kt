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
import android.widget.Toast
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassPreferences
import com.forbidad4tieba.hook.core.XposedCompat

/** Edits a local draft, just like the other configurable module settings. */
internal object BottomTabLiquidGlassDialog {
    fun show(context: Context, prefs: SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val initial = BottomTabLiquidGlassPreferences.read(prefs)
            val defaults = BottomTabLiquidGlassConfig.DEFAULT
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
            }
            val height = slider(
                root, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_HEIGHT,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_HEIGHT_DESC,
                BottomTabLiquidGlassConfig.MIN_HEIGHT_DP, BottomTabLiquidGlassConfig.MAX_HEIGHT_DP,
                initial.heightDp, UiText.Settings::liquidGlassDp,
            )
            // The first tick is Auto; the rest of the range is the explicit window percentage.
            val autoWidthTick = BottomTabLiquidGlassConfig.MIN_WIDTH_PERCENT - 1
            val width = slider(
                root, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_WIDTH,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_WIDTH_DESC,
                autoWidthTick, BottomTabLiquidGlassConfig.MAX_WIDTH_PERCENT,
                initial.widthPercent.takeIf { it > 0 } ?: autoWidthTick,
            ) { value ->
                if (value == autoWidthTick) UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_AUTO
                else UiText.Settings.liquidGlassPercent(value)
            }
            val gap = slider(
                root, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BOTTOM_GAP,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BOTTOM_GAP_DESC,
                -1, BottomTabLiquidGlassConfig.MAX_BOTTOM_GAP_DP, initial.bottomGapDp,
            ) { value ->
                if (value < 0) UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_AUTO
                else UiText.Settings.liquidGlassDp(value)
            }

            fun effect(label: String, description: String, checked: Boolean): Switch {
                val row = createSwitchRow(
                    context, prefs, label, description, null, padding, defaultValue = checked,
                )
                root.addView(row)
                return checkNotNull(findSwitchView(row))
            }
            val blur = effect(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BLUR,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_BLUR_DESC, initial.blurEnabled)
            val refraction = effect(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_REFRACTION,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_REFRACTION_DESC, initial.refractionEnabled)
            val highlight = effect(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_HIGHLIGHT,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_HIGHLIGHT_DESC, initial.highlightEnabled)
            val shadow = effect(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_SHADOW,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_SHADOW_DESC, initial.shadowEnabled)
            val tilt = effect(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_TILT,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_TILT_DESC, initial.tiltEnabled)
            val press = effect(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_PRESS,
                UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_PRESS_DESC, initial.pressEffectEnabled)

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_LABEL)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setNeutralButton(UiText.Settings.HOME_NATIVE_GLASS_RESTORE_DEFAULTS, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { applyUnifiedDialogCardStyle(it, density) }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    height.progress = defaults.heightDp - BottomTabLiquidGlassConfig.MIN_HEIGHT_DP
                    width.progress = 0
                    gap.progress = 0
                    blur.isChecked = defaults.blurEnabled
                    refraction.isChecked = defaults.refractionEnabled
                    highlight.isChecked = defaults.highlightEnabled
                    shadow.isChecked = defaults.shadowEnabled
                    tilt.isChecked = defaults.tiltEnabled
                    press.isChecked = defaults.pressEffectEnabled
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    BottomTabLiquidGlassPreferences.save(prefs, BottomTabLiquidGlassConfig(
                        heightDp = height.progress + BottomTabLiquidGlassConfig.MIN_HEIGHT_DP,
                        widthPercent = if (width.progress == 0) 0 else width.progress + autoWidthTick,
                        bottomGapDp = gap.progress - 1,
                        blurEnabled = blur.isChecked,
                        refractionEnabled = refraction.isChecked,
                        highlightEnabled = highlight.isChecked,
                        shadowEnabled = shadow.isChecked,
                        tiltEnabled = tilt.isChecked,
                        pressEffectEnabled = press.isChecked,
                    ))
                    Toast.makeText(context,
                        UiText.Settings.withRestartTiebaHint(UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_SAVED),
                        Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
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
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = update(progress)
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        row.addView(slider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        parent.addView(row)
        return slider
    }
}
