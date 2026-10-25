package com.forbidad4tieba.hook.feature.ui

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.forbidad4tieba.hook.symbol.contract.RedDotLayer
import java.lang.ref.WeakReference

/** Text, including overflow counts and labels, is content rather than a plain dot. */
internal fun isPlainRedDot(label: CharSequence?): Boolean = label.isNullOrBlank()

internal fun maskPlainRedDot(view: View, layer: RedDotLayer, enabled: () -> Boolean) {
    val source = when (layer) {
        RedDotLayer.BACKGROUND -> view.background
        RedDotLayer.IMAGE -> (view as? ImageView)?.drawable
    } ?: return
    if (source is RedDotDrawable) return
    val text = (view as? TextView)?.let(::WeakReference)
    val masked = RedDotDrawable(source, { text?.get()?.text }, enabled)
    when (layer) {
        RedDotLayer.BACKGROUND -> {
            val left = view.paddingLeft
            val top = view.paddingTop
            val right = view.paddingRight
            val bottom = view.paddingBottom
            view.background = masked
            if (view.paddingLeft != left || view.paddingTop != top ||
                view.paddingRight != right || view.paddingBottom != bottom
            ) view.setPadding(left, top, right, bottom)
        }
        RedDotLayer.IMAGE -> (view as ImageView).setImageDrawable(masked)
    }
    // View setters detach the previous drawable after the wrapper was constructed.
    source.callback = masked
}

/** Preserve the source geometry, state and skin; only its actual paint is conditional. */
internal class RedDotDrawable(
    source: Drawable,
    private val label: () -> CharSequence?,
    private val enabled: () -> Boolean,
) : DrawableWrapper(source) {
    override fun draw(canvas: Canvas) {
        if (!enabled() || !isPlainRedDot(label())) super.draw(canvas)
    }
}

