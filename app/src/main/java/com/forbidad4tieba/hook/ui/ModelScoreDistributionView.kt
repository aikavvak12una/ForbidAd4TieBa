package com.forbidad4tieba.hook.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreStats
import java.util.Locale
import kotlin.math.max

internal class ModelScoreDistributionView(
    context: Context,
    private val summary: CustomPostModelScoreStats.Summary,
) : View(context) {
    private val tokens = UiStyle.tokens(context)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.chartBg
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.chartAxis
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.chartGrid
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.chartBar
        style = Paint.Style.FILL
    }
    private val highlightBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.chartBarHighlight
        style = Paint.Style.FILL
    }
    private val cumulativePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.accent
        strokeWidth = 2.2f
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.textSecondary
        textSize = 10.5f * context.resources.displayMetrics.density * context.resources.configuration.fontScale
    }
    private val selectedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.accent
        textSize = 11f * context.resources.displayMetrics.density * context.resources.configuration.fontScale
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val cardRect = RectF()
    private val plotRect = RectF()
    private val barRect = RectF()
    private val barRects = ArrayList<RectF>()
    private val cumulativePath = Path()
    private var selectedBucketIndex = -1

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        cardRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(cardRect, 10f * density, 10f * density, bgPaint)

        val leftInset = 38f * density
        val rightInset = 10f * density
        val topInset = 34f * density
        val bottomInset = 28f * density
        plotRect.set(leftInset, topInset, width - rightInset, height - bottomInset)
        if (plotRect.width() <= 0f || plotRect.height() <= 0f) return
        canvas.drawLine(plotRect.left, plotRect.top, plotRect.left, plotRect.bottom, axisPaint)
        canvas.drawLine(plotRect.left, plotRect.bottom, plotRect.right, plotRect.bottom, axisPaint)

        val buckets = summary.buckets
        if (buckets.isEmpty()) return
        if (selectedBucketIndex >= buckets.size) selectedBucketIndex = -1
        val maxCount = max(1, buckets.maxOf { it.count })
        drawSelectedBucketInfo(canvas, density, buckets)
        drawAxisLabels(canvas, density, maxCount)
        val gap = (if (buckets.size > 32) 1f else 2f) * density
        val barWidth = ((plotRect.width() - gap * (buckets.size - 1)) / buckets.size).coerceAtLeast(1f)
        barRects.clear()
        buckets.forEachIndexed { index, bucket ->
            val left = plotRect.left + index * (barWidth + gap)
            val top = plotRect.bottom - plotRect.height() * (bucket.count.toFloat() / maxCount)
            barRect.set(left, top, left + barWidth, plotRect.bottom)
            barRects.add(RectF(barRect))
            canvas.drawRoundRect(
                barRect,
                2f * density,
                2f * density,
                if (index == selectedBucketIndex) highlightBarPaint else barPaint
            )
        }
        drawCumulativeCurve(canvas, buckets)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP -> {
                val index = findBucketIndex(event.x, event.y)
                if (index >= 0 && selectedBucketIndex != index) {
                    selectedBucketIndex = index
                    invalidate()
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawAxisLabels(canvas: Canvas, density: Float, maxCount: Int) {
        val halfY = plotRect.top + plotRect.height() / 2f
        canvas.drawLine(plotRect.left, halfY, plotRect.right, halfY, gridPaint)

        labelPaint.textAlign = Paint.Align.RIGHT
        val yLabelX = plotRect.left - 6f * density
        canvas.drawText(maxCount.toString(), yLabelX, plotRect.top + 4f * density, labelPaint)
        canvas.drawText((maxCount / 2).toString(), yLabelX, halfY + 4f * density, labelPaint)
        canvas.drawText("0", yLabelX, plotRect.bottom, labelPaint)

        val min = summary.displayMin ?: return
        val maxValue = summary.displayMax ?: return
        val xLabelY = plotRect.bottom + 16f * density
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(formatAxisValue(min), plotRect.left, xLabelY, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatAxisValue(maxValue), plotRect.right, xLabelY, labelPaint)
    }

    private fun drawSelectedBucketInfo(
        canvas: Canvas,
        density: Float,
        buckets: List<CustomPostModelScoreStats.Bucket>,
    ) {
        val selected = buckets.getOrNull(selectedBucketIndex) ?: return
        val text = UiText.Settings.modelScoreBucketInfo(
            formatAxisValue(selected.start),
            formatAxisValue(selected.end),
            selected.count,
            selected.cumulativeCount,
            formatPercent(selected.cumulativeRatio),
        )
        canvas.drawText(text, width / 2f, 20f * density, selectedLabelPaint)
    }

    private fun drawCumulativeCurve(
        canvas: Canvas,
        buckets: List<CustomPostModelScoreStats.Bucket>,
    ) {
        if (buckets.isEmpty() || barRects.size != buckets.size) return
        cumulativePath.reset()
        buckets.forEachIndexed { index, bucket ->
            val rect = barRects[index]
            val x = rect.centerX()
            val y = plotRect.bottom - plotRect.height() * bucket.cumulativeRatio.coerceIn(0.0, 1.0).toFloat()
            if (index == 0) {
                cumulativePath.moveTo(x, y)
            } else {
                cumulativePath.lineTo(x, y)
            }
        }
        canvas.drawPath(cumulativePath, cumulativePaint)
    }

    private fun findBucketIndex(x: Float, y: Float): Int {
        if (y < plotRect.top || y > plotRect.bottom) return -1
        if (barRects.isEmpty() || x < plotRect.left || x > plotRect.right) return -1
        val ratio = ((x - plotRect.left) / plotRect.width()).coerceIn(0f, 0.999999f)
        return (ratio * barRects.size).toInt().coerceIn(0, barRects.lastIndex)
    }

    private fun formatAxisValue(value: Double): String {
        val text = String.format(Locale.US, "%.4f", value)
        return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    private fun formatPercent(value: Double): String {
        return String.format(Locale.US, "%.1f%%", value.coerceIn(0.0, 1.0) * 100.0)
    }
}
