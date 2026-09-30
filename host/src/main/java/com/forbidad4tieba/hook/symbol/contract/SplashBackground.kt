package com.forbidad4tieba.hook.symbol.contract

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable

/** Shared launch artwork rules for the system splash, host window and temporary cover. */
object SplashBackground {
    internal fun artwork(drawable: Drawable?): BitmapDrawable? {
        val layers = drawable as? LayerDrawable ?: return null
        if (layers.numberOfLayers != 2 || layers.getDrawable(0) !is ColorDrawable) return null
        return layers.getDrawable(1) as? BitmapDrawable
    }

    /** Replace only recognized launch artwork, keeping its layout and shared resources intact. */
    fun recolor(drawable: Drawable?, color: Int, resources: Resources, branding: Bitmap): Drawable? = when (drawable) {
        is ColorDrawable -> ColorDrawable(color)
        is LayerDrawable -> {
            val original = artwork(drawable)
            if (original == null) null else {
                // The host already resolved its layer geometry. Supplying Resources here can
                // rescale insets and dimensions again when the drawable has a different density.
                val copy = drawable.constantState?.newDrawable()?.mutate() as? LayerDrawable
                copy?.apply {
                    setDrawable(0, ColorDrawable(color))
                    setDrawable(1, BitmapDrawable(resources, branding).apply {
                        gravity = original.gravity
                        alpha = original.alpha
                        isAutoMirrored = original.isAutoMirrored
                        isFilterBitmap = original.isFilterBitmap
                        setTileModeXY(original.tileModeX, original.tileModeY)
                    })
                    bounds = drawable.bounds
                    level = drawable.level
                    state = drawable.state
                    layoutDirection = drawable.layoutDirection
                }
            }
        }
        else -> null
    }
}
