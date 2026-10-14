package com.forbidad4tieba.hook.ui.settings.modelscore

import com.forbidad4tieba.hook.config.PostFilterPreferences
import com.forbidad4tieba.hook.config.ModelScoreSettings
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreStats
import com.forbidad4tieba.hook.ui.CustomPostModelScoreUiCatalog
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_DESC_SP
import com.forbidad4tieba.hook.ui.SETTINGS_VALUE_TEXT_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsRowDescriptionStyle
import com.forbidad4tieba.hook.ui.applySettingsRowTitleStyle
import com.forbidad4tieba.hook.ui.showSettingsDialog
import com.forbidad4tieba.hook.ui.createDivider
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.createSettingsInputBackground
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.doAfterTextChanged
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.prepareSettingsDialogWindowForInput
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.prepareSettingsInputForKeyboard
import com.forbidad4tieba.hook.ui.settings.modelscore.ModelScoreStatsViews.createModelScoreStatsContent
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.ui.settingsRowVerticalPadding

internal object ModelScoreForm {
    fun showCustomPostModelScoreDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val thresholdByKey = ModelScoreSettings.parseModelScoreThresholds(
                PostFilterPreferences.FILTER_POST_MODEL_SCORE_THRESHOLDS.read(prefs)
            ).associate { it.key to it.threshold }.toMutableMap()
            val autoPercentiles = ModelScoreSettings.parseModelScoreAutoPercentiles(
                PostFilterPreferences.FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES.read(prefs)
            ).toMutableMap()
            val modelScoreUiItems = CustomPostModelScoreUiCatalog.items
            val statsRefreshers = ArrayList<() -> Unit>(modelScoreUiItems.size)
            val initialStatsPostLimit = PostFilterPreferences.FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT.read(prefs).coerceAtLeast(ModelScoreSettings.MIN_MODEL_SCORE_STATS_POST_LIMIT)

            fun persistModelScoreAutoThreshold(
                modelKey: String,
                percentile: Int,
                value: Double,
                applyThreshold: Boolean,
            ) {
                val roundedValue = ModelScoreSettings.roundModelScoreThreshold(value)
                autoPercentiles[modelKey] = ModelScoreSettings.normalizeModelScoreAutoPercentile(percentile)
                val merged = LinkedHashMap<String, Double>()
                for (threshold in ModelScoreSettings.parseModelScoreThresholds(
                    PostFilterPreferences.FILTER_POST_MODEL_SCORE_THRESHOLDS.read(prefs)
                )) {
                    merged[threshold.key] = threshold.threshold
                }
                if (applyThreshold) {
                    merged[modelKey] = roundedValue
                } else {
                    merged.remove(modelKey)
                }
                val thresholds = merged.map { (key, threshold) ->
                    ModelScoreSettings.ModelScoreThreshold(key, threshold)
                }
                prefs.edit()
                    .putString(
                        PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS,
                        ModelScoreSettings.serializeModelScoreThresholds(thresholds)
                    )
                    .putString(
                        PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES,
                        ModelScoreSettings.serializeModelScoreAutoPercentiles(autoPercentiles)
                    )
                    .apply()
            }

            fun persistModelScoreAutoDisabled(modelKey: String) {
                val thresholds = ModelScoreSettings.parseModelScoreThresholds(
                    PostFilterPreferences.FILTER_POST_MODEL_SCORE_THRESHOLDS.read(prefs)
                ).filter { it.key != modelKey }
                prefs.edit()
                    .putString(
                        PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS,
                        ModelScoreSettings.serializeModelScoreThresholds(thresholds)
                    )
                    .putString(
                        PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES,
                        ModelScoreSettings.serializeModelScoreAutoPercentiles(autoPercentiles)
                    )
                    .apply()
            }

            val tokens = UiStyle.tokens(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
            }

            val statsLimitRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (10 * density).toInt())
            }
            val statsLimitTextContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            statsLimitTextContainer.addView(
                TextView(context).apply {
                    text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_LABEL
                    applySettingsRowTitleStyle(tokens, density)
                }
            )
            statsLimitTextContainer.addView(
                TextView(context).apply {
                    text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_DESC
                    applySettingsRowDescriptionStyle(tokens, density)
                }
            )
            statsLimitRow.addView(
                statsLimitTextContainer,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            )
            val clearStatsButton = TextView(context).apply {
                text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_CLEAR_ICON
                contentDescription = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_CLEAR_DESC
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(tokens.danger)
                typeface = Typeface.DEFAULT_BOLD
                setOnClickListener {
                    CustomPostModelScoreStats.clear()
                    statsRefreshers.forEach { refresh -> refresh() }
                    Toast.makeText(
                        context,
                        UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_CLEARED,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            statsLimitRow.addView(
                clearStatsButton,
                LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt()).apply {
                    rightMargin = (6 * density).toInt()
                }
            )
            val statsLimitInput = android.widget.EditText(context).apply {
                setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                hint = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_HINT
                setText(initialStatsPostLimit.toString())
                textSize = SETTINGS_VALUE_TEXT_SP
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textMuted)
                includeFontPadding = false
                background = createSettingsInputBackground(context, density)
                setPadding(
                    (4 * density).toInt(),
                    0,
                    (4 * density).toInt(),
                    0,
                )
                prepareSettingsInputForKeyboard(this)
            }
            statsLimitInput.doAfterTextChanged { raw ->
                val value = raw.trim().toIntOrNull()?.takeIf { it >= ModelScoreSettings.MIN_MODEL_SCORE_STATS_POST_LIMIT }
                statsLimitInput.error = if (value == null) UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_INVALID else null
                if (value != null && PostFilterPreferences.FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT.read(prefs) != value) {
                    prefs.edit().putInt(PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT, value).apply()
                }
            }
            statsLimitRow.addView(
                statsLimitInput,
                LinearLayout.LayoutParams((50 * density).toInt(), (34 * density).toInt())
            )
            root.addView(statsLimitRow)
            root.addView(createDivider(context, padding))

            root.addView(
                TextView(context).apply {
                    text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_GUIDE
                    textSize = SETTINGS_ROW_DESC_SP
                    setTextColor(tokens.textSecondary)
                    includeFontPadding = false
                    setLineSpacing(1f * density, 1f)
                    setPadding(0, 0, 0, (8 * density).toInt())
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            for (item in modelScoreUiItems) {
                val itemContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val verticalPadding = settingsRowVerticalPadding(density)
                    setPadding(0, verticalPadding, 0, verticalPadding)
                }
                val textContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                textContainer.addView(
                    TextView(context).apply {
                        text = item.label
                        applySettingsRowTitleStyle(tokens, density)
                    }
                )
                textContainer.addView(
                    TextView(context).apply {
                        text = item.description
                        applySettingsRowDescriptionStyle(tokens, density)
                    }
                )
                row.addView(
                    textContainer,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                )

                val statsContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                }
                lateinit var input: android.widget.EditText
                var updatingThresholdInput = false
                fun updateThresholdInput(value: String) {
                    updatingThresholdInput = true
                    try {
                        input.setText(value)
                        input.error = null
                        input.setSelection(input.text?.length ?: 0)
                    } finally {
                        updatingThresholdInput = false
                    }
                }
                var expanded = false
                lateinit var refreshStatsContent: () -> Unit

                fun enableAutoPercentile(percentile: Int, value: Double, sampleCount: Int) {
                    val roundedValue = ModelScoreSettings.roundModelScoreThreshold(value)
                    val valueText = formatModelScoreThreshold(roundedValue)
                    val applyThreshold = sampleCount > ModelScoreSettings.MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT
                    if (applyThreshold) {
                        updateThresholdInput(valueText)
                        thresholdByKey[item.key] = roundedValue
                    } else {
                        updateThresholdInput("")
                        thresholdByKey.remove(item.key)
                    }
                    persistModelScoreAutoThreshold(item.key, percentile, roundedValue, applyThreshold)
                    Toast.makeText(
                        context,
                        if (applyThreshold) {
                            UiText.Settings.modelScoreAutoPercentileEnabled(percentile, valueText)
                        } else {
                            UiText.Settings.modelScoreAutoPercentilePending(
                                percentile,
                                sampleCount,
                                ModelScoreSettings.MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT,
                            )
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                    if (expanded) refreshStatsContent()
                }

                fun disableAutoPercentile(percentile: Int) {
                    autoPercentiles.remove(item.key)
                    thresholdByKey.remove(item.key)
                    updateThresholdInput("")
                    persistModelScoreAutoDisabled(item.key)
                    Toast.makeText(
                        context,
                        UiText.Settings.modelScoreAutoPercentileDisabled(percentile),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (expanded) refreshStatsContent()
                }

                refreshStatsContent = {
                    statsContainer.removeAllViews()
                    statsContainer.addView(
                        createModelScoreStatsContent(
                            context,
                            density,
                            item,
                            autoPercentiles[item.key],
                            onAutoPercentileClick = { percentile, value, sampleCount, enabled ->
                                if (enabled) {
                                    disableAutoPercentile(percentile)
                                } else {
                                    enableAutoPercentile(percentile, value, sampleCount)
                                }
                            },
                            onAutoPercentileSelected = { percentile, value, sampleCount ->
                                enableAutoPercentile(percentile, value, sampleCount)
                            },
                        )
                    )
                }
                statsRefreshers.add {
                    if (expanded) refreshStatsContent()
                }
                val expandButton = TextView(context).apply {
                    text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_ICON
                    contentDescription = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_DESC
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(tokens.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener {
                        expanded = !expanded
                        UiStyle.animateExpandArrow(this, expanded)
                        if (expanded) {
                            contentDescription = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_COLLAPSE_DESC
                            refreshStatsContent()
                            UiStyle.animateCardExpand(statsContainer)
                        } else {
                            contentDescription = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_DESC
                            UiStyle.animateCardCollapse(statsContainer)
                        }
                    }
                }
                row.addView(
                    expandButton,
                    LinearLayout.LayoutParams((34 * density).toInt(), (40 * density).toInt()).apply {
                        rightMargin = (6 * density).toInt()
                    }
                )

                input = android.widget.EditText(context).apply {
                    setSingleLine(true)
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    hint = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_HINT
                    setText(formatModelScoreThreshold(thresholdByKey[item.key]))
                    textSize = SETTINGS_VALUE_TEXT_SP
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    setHintTextColor(tokens.textMuted)
                    includeFontPadding = false
                    background = createSettingsInputBackground(context, density)
                    setPadding(
                        (4 * density).toInt(),
                        0,
                        (4 * density).toInt(),
                        0,
                    )
                    prepareSettingsInputForKeyboard(this)
                }
                input.doAfterTextChanged { text ->
                    if (updatingThresholdInput) return@doAfterTextChanged
                    val raw = text.trim()
                    val value = raw.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                    input.error = if (raw.isNotEmpty() && value == null) UiText.Settings.modelScoreThresholdInvalid(item.label) else null
                    if (raw.isNotEmpty() && value == null) return@doAfterTextChanged
                    val merged = ModelScoreSettings.parseModelScoreThresholds(
                        PostFilterPreferences.FILTER_POST_MODEL_SCORE_THRESHOLDS.read(prefs),
                    ).associate { it.key to it.threshold }.toMutableMap()
                    if (raw.isEmpty()) {
                        if (thresholdByKey[item.key] != null) autoPercentiles.remove(item.key)
                        merged.remove(item.key)
                    } else if (autoPercentiles.containsKey(item.key) &&
                        CustomPostModelScoreStats.summary(item.key).sampleCount <= ModelScoreSettings.MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT
                    ) {
                        merged.remove(item.key)
                    } else {
                        merged[item.key] = checkNotNull(value)
                    }
                    thresholdByKey.clear()
                    thresholdByKey.putAll(merged)
                    prefs.edit()
                        .putString(
                            PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS,
                            ModelScoreSettings.serializeModelScoreThresholds(merged.map { (key, threshold) ->
                                ModelScoreSettings.ModelScoreThreshold(key, threshold)
                            }),
                        )
                        .putString(
                            PostFilterPreferences.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES,
                            ModelScoreSettings.serializeModelScoreAutoPercentiles(autoPercentiles),
                        )
                        .apply()
                    if (expanded) refreshStatsContent()
                }
                row.addView(
                    input,
                    LinearLayout.LayoutParams((50 * density).toInt(), (34 * density).toInt())
                )
                itemContainer.addView(row)
                itemContainer.addView(
                    statsContainer,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = (6 * density).toInt()
                    }
                )
                root.addView(itemContainer)
            }

            val scroll = ScrollView(context).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_DIALOG_TITLE)
                .setView(scroll)
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> prepareSettingsDialogWindowForInput(window) }
            }
            // Trimming removes samples, so wait until editing has ended instead of using intermediate numbers.
            dialog.setOnDismissListener {
                val limit = PostFilterPreferences.FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT.read(prefs)
                if (limit != initialStatsPostLimit) CustomPostModelScoreStats.trimToPostLimitAsync(limit)
            }
            dialog.showSettingsDialog(density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showCustomPostModelScoreDialog failed: ${t.message}")
        }
    }

    private fun formatModelScoreThreshold(value: Double?): String {
        return ModelScoreSettings.formatModelScoreThresholdValue(value)
    }
}
