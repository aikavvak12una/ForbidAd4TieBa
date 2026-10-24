package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import com.forbidad4tieba.hook.symbol.contract.PostReplyLoadingArtwork
import kotlin.math.roundToInt

/** Static native artwork while post data is absent. The count line stays empty but keeps its space. */
internal class PostReplyPendingActions(
    context: Context,
    private val artwork: PostReplyLoadingArtwork,
    density: Float,
    night: Boolean,
) : View(context) {
    private val icons = artwork.newIcons()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val fontMetrics = Paint.FontMetricsInt()
    private var iconSize = 0
    private var padding = 0
    private var tint: Int? = null
    var contentHeight = 0
        private set

    init { update(density, night) }

    fun update(density: Float, night: Boolean) {
        val metrics = resources.displayMetrics
        iconSize = artwork.iconSize(metrics)
        textPaint.textSize = artwork.labelSize(metrics).toFloat()
        textPaint.getFontMetricsInt(fontMetrics)
        contentHeight = PostReplyGlassGeometry.actionContentHeight(iconSize,
            fontMetrics.bottom - fontMetrics.top, density)
        padding = (PostReplyGlassGeometry.ACTION_PADDING_DP * density).roundToInt()
        setTheme(night)
        invalidate()
    }

    fun setTheme(night: Boolean) {
        val color = artwork.color(night)
        if (tint == color) return
        tint = color
        icons.forEach { it.setTint(color) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val available = width - 2 * padding
        if (available <= 0 || contentHeight > height) return
        val top = (height - contentHeight) / 2
        var left = padding
        for ((index, icon) in icons.withIndex()) {
            val column = PostReplyGlassGeometry.actionColumnWidth(available, index, icons.size)
            val x = left + (column - iconSize) / 2
            icon.setBounds(x, top, x + iconSize, top + iconSize)
            icon.draw(canvas)
            left += column
        }
    }
}
