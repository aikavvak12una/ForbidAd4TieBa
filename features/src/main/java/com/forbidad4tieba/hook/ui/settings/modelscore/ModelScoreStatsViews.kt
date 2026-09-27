package com.forbidad4tieba.hook.ui.settings.modelscore

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreStats
import com.forbidad4tieba.hook.ui.ModelScoreDistributionView
import com.forbidad4tieba.hook.ui.ModelScoreUiItem
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_DESC_SP
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_TITLE_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applySettingsMessageStyle
import com.forbidad4tieba.hook.ui.applySettingsRowTitleStyle
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.ui.settingsRowVerticalPadding
import java.util.Locale

internal object ModelScoreStatsViews {
    fun createModelScoreStatsContent(
        context: Context,
        density: Float,
        item: ModelScoreUiItem,
        autoPercentile: Int?,
        onAutoPercentileClick: (Int, Double, Int, Boolean) -> Unit,
        onAutoPercentileSelected: (Int, Double, Int) -> Unit,
    ): View {
        val padding = (12 * density).toInt()
        val summary = CustomPostModelScoreStats.summary(item.key)
        val tokens = UiStyle.tokens(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createModelScoreExpandedStatsBackground(context, density)
            setPadding(padding, padding, padding, padding)

            if (summary.sampleCount <= 0) {
                addView(
                    TextView(context).apply {
                        text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_EMPTY
                        applySettingsMessageStyle(tokens, density, primary = false)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            } else {
                addView(
                    createModelScoreStatsMetricRow(
                        context,
                        density,
                        summary,
                        autoPercentile,
                        onAutoPercentileClick,
                        onAutoPercentileSelected,
                    )
                )
                addView(
                    ModelScoreDistributionView(context, summary),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (150 * density).toInt(),
                    ).apply {
                        topMargin = (10 * density).toInt()
                    }
                )
            }
        }
    }

    private fun createModelScoreStatsMetricRow(
        context: Context,
        density: Float,
        summary: CustomPostModelScoreStats.Summary,
        autoPercentile: Int?,
        onAutoPercentileClick: (Int, Double, Int, Boolean) -> Unit,
        onAutoPercentileSelected: (Int, Double, Int) -> Unit,
    ): View {
        val active = autoPercentile != null
        val displayPercentile = ConfigManager.normalizeModelScoreAutoPercentile(
            autoPercentile ?: ConfigManager.DEFAULT_MODEL_SCORE_AUTO_PERCENTILE
        )
        val percentileValue = summary.percentileValue(displayPercentile)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBaselineAligned(false)
            gravity = Gravity.CENTER_VERTICAL
            addView(
                createModelScoreStatsMetric(
                    context,
                    UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_SAMPLE_COUNT_LABEL,
                    summary.sampleCount.toString(),
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                createModelScoreStatsMetric(
                    context,
                    UiText.Settings.modelScoreAutoPercentileLabel(displayPercentile),
                    formatModelScoreValue(percentileValue),
                    active = active,
                    onClick = if (percentileValue != null) {
                        { onAutoPercentileClick(displayPercentile, percentileValue, summary.sampleCount, active) }
                    } else {
                        null
                    },
                    onLongClick = {
                        showModelScoreAutoPercentileDialog(
                            context,
                            summary,
                            displayPercentile,
                            onAutoPercentileSelected,
                        )
                    },
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = (8 * density).toInt()
                }
            )
            addView(
                createModelScoreStatsMetric(
                    context,
                    UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_AVERAGE_LABEL,
                    formatModelScoreValue(summary.average),
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = (8 * density).toInt()
                }
            )
        }
    }

    private fun createModelScoreStatsMetric(
        context: Context,
        label: String,
        value: String,
        active: Boolean = false,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
    ): View {
        val tokens = UiStyle.tokens(context)
        return LinearLayout(context).apply {
            val density = context.resources.displayMetrics.density
            val interactive = onClick != null || onLongClick != null
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                if (interactive) (8 * density).toInt() else 0,
                (6 * density).toInt(),
                if (interactive) (8 * density).toInt() else 0,
                (6 * density).toInt(),
            )
            if (interactive) {
                background = UiStyle.createMetricCellBackground(tokens, density, active)
                isClickable = true
                isFocusable = true
                if (onClick != null) setOnClickListener { onClick() }
                if (onLongClick != null) {
                    setOnLongClickListener {
                        onLongClick()
                        true
                    }
                }
            }
            addView(TextView(context).apply {
                text = label
                textSize = SETTINGS_ROW_DESC_SP
                setTextColor(if (active) tokens.accent else tokens.textSecondary)
                gravity = Gravity.CENTER
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                text = value
                textSize = SETTINGS_ROW_TITLE_SP
                setTextColor(if (active) tokens.accent else tokens.textPrimary)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, (4 * context.resources.displayMetrics.density).toInt(), 0, 0)
            })
        }
    }

    private fun createModelScoreAutoPercentileMetricBackground(density: Float, active: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(if (active) 0xFFEAF2FF.toInt() else 0xFFF5F7FB.toInt())
            cornerRadius = 9f * density
            setStroke((1 * density).toInt().coerceAtLeast(1), if (active) 0x664C87F7 else 0x244C87F7)
        }
    }

    private fun formatModelScoreValue(value: Double?): String {
        value ?: return "-"
        val text = String.format(Locale.US, "%.6f", value)
        return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    private fun showModelScoreAutoPercentileDialog(
        context: Context,
        summary: CustomPostModelScoreStats.Summary,
        currentPercentile: Int,
        onSelected: (Int, Double, Int) -> Unit,
    ) {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val padding = settingsDialogPadding(density)
        val percentiles = ConfigManager.SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES
        val checkedIndex = percentiles.indexOf(currentPercentile).coerceAtLeast(0)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
        }
        lateinit var dialog: AlertDialog
        percentiles.forEachIndexed { index, percentile ->
            val active = index == checkedIndex
            root.addView(
                TextView(context).apply {
                    text = UiText.Settings.modelScoreAutoPercentileLabel(percentile)
                    applySettingsRowTitleStyle(tokens, density)
                    if (active) {
                        setTextColor(tokens.accent)
                    }
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    setPadding(0, settingsRowVerticalPadding(density), 0, settingsRowVerticalPadding(density))
                    setOnClickListener {
                        val value = summary.percentileValue(percentile)
                        if (value != null) {
                            onSelected(percentile, value, summary.sampleCount)
                        }
                        dialog.dismiss()
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        dialog = AlertDialog.Builder(context, dialogThemeFor(context))
            .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_AUTO_PERCENTILE_DIALOG_TITLE)
            .setView(createDialogScrollContainer(context, root))
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        }
        dialog.show()
    }

    private fun createModelScoreExpandedStatsBackground(context: Context, density: Float): GradientDrawable {
        val tokens = UiStyle.tokens(context)
        return GradientDrawable().apply {
            setColor(tokens.surfaceAlt)
            cornerRadius = 10f * density
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }
    }
}
