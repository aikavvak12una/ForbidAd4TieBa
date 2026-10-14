package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.forbidad4tieba.hook.ui.UiText
import kotlin.math.roundToInt

/** Static first-frame artwork; the session removes it as soon as native actions exist. */
internal class PostReplyPendingActions(context: Context, private val density: Float, night: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        textAlign = Paint.Align.CENTER
        textSize = 8.5f * density
    }
    private val labels = arrayOf(UiText.PostReplyBar.COMMENT, UiText.PostReplyBar.LIKE, UiText.PostReplyBar.COLLECT)
    private val icons = arrayOf(
        Path().apply {
            moveTo(12f, 3f)
            cubicTo(19f, 3f, 21f, 5f, 21f, 11f)
            cubicTo(21f, 17f, 18f, 19f, 14f, 19f)
            lineTo(11f, 21f)
            quadTo(10f, 21.5f, 9f, 19f)
            cubicTo(4f, 19f, 2f, 16f, 2f, 11f)
            cubicTo(2f, 5f, 4f, 3f, 12f, 3f)
            close()
            moveTo(8f, 11f)
            quadTo(11.5f, 14f, 15f, 11f)
        },
        Path().apply {
            moveTo(7f, 10f)
            lineTo(10f, 7f)
            lineTo(11f, 2f)
            quadTo(15f, 1f, 15f, 6f)
            lineTo(14.5f, 8f)
            lineTo(18f, 8f)
            quadTo(22f, 8f, 21f, 13f)
            lineTo(20f, 18f)
            quadTo(19.5f, 21f, 16f, 21f)
            lineTo(7f, 21f)
            close()
            moveTo(7f, 10f)
            lineTo(5f, 10f)
            quadTo(3f, 10f, 3f, 12f)
            lineTo(3f, 19f)
            quadTo(3f, 21f, 5f, 21f)
            lineTo(7f, 21f)
        },
        Path().apply {
            moveTo(12f, 2.5f)
            lineTo(15.1f, 8.5f)
            lineTo(21.7f, 9.5f)
            lineTo(17f, 14.2f)
            lineTo(18.1f, 20.8f)
            lineTo(12f, 17.7f)
            lineTo(5.9f, 20.8f)
            lineTo(7f, 14.2f)
            lineTo(2.3f, 9.5f)
            lineTo(8.9f, 8.5f)
            close()
        },
    )

    init {
        // A disabled clickable View consumes taps without forwarding them to the input row.
        isClickable = true
        isEnabled = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setTheme(night)
    }

    fun setTheme(night: Boolean) {
        val color = if (night) 0xFFCCCCCC.toInt() else 0xFF333333.toInt()
        if (paint.color == color) return
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val padding = (PostReplyGlassGeometry.ACTION_PADDING_DP * density).roundToInt()
        val contentWidth = width - 2 * padding
        val iconSize = 19f * density
        val iconTop = height / 2f - 2.5f * density - iconSize / 2f
        val labelBaseline = height / 2f + 17f * density
        var left = padding
        for (index in icons.indices) {
            val columnWidth = contentWidth / ACTION_COUNT + if (index < contentWidth % ACTION_COUNT) 1 else 0
            val centre = left + columnWidth / 2f
            val checkpoint = canvas.save()
            canvas.translate(centre - iconSize / 2f, iconTop)
            canvas.scale(iconSize / 24f, iconSize / 24f)
            paint.style = Paint.Style.STROKE
            canvas.drawPath(icons[index], paint)
            canvas.restoreToCount(checkpoint)
            paint.style = Paint.Style.FILL
            canvas.drawText(labels[index], centre, labelBaseline, paint)
            left += columnWidth
        }
    }

    companion object {
        const val ACTION_COUNT = 3
    }
}
