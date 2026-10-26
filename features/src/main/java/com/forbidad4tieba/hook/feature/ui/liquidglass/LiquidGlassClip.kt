package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.graphics.Path
import kotlin.math.min

/** Each capsule keeps its local clip until its dimensions change. */
internal class LiquidGlassClip {
    private val path = Path()
    private var width = Int.MIN_VALUE
    private var height = Int.MIN_VALUE

    fun forSize(w: Int, h: Int): Path {
        if (width != w || height != h) {
            val radius = min(w, h) / 2f
            path.reset()
            path.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radius, radius, Path.Direction.CW)
            width = w
            height = h
        }
        return path
    }
}
