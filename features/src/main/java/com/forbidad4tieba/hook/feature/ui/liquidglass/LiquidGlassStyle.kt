// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// KernelSU FloatingBottomBar material policy on the existing native View renderer.
// Source mapping and modification details: app/src/main/assets/open-source-licenses.txt.
package com.forbidad4tieba.hook.feature.ui.liquidglass

import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One material policy is consumed by both the Tab and split reply surfaces. */
internal object LiquidGlassStyle {
    const val PRESS_IN_MS = 180L
    const val PRESS_OUT_MS = 320L
    const val PRESS_SCALE = 0.95f
    const val DEFAULT_LIGHT_ANGLE = (-PI / 2).toFloat()
    const val ARTWORK_SCALE = 1.2f

    // Floating-bar material only; these values do not configure custom page backgrounds.
    const val MAX_BLUR_RADIUS_DP = 20f
    const val MATERIAL_SATURATION = 1.5f
    const val MATERIAL_CONTRAST = 1f
    // KernelSU uses surfaceContainer.copy(alpha = 0.4f) with miuix's base palette.
    const val LIGHT_SURFACE_ALPHA = 0x66
    const val DARK_SURFACE_ALPHA = 0x66
    const val LIGHT_SURFACE_RGB = 0xFFFFFF
    const val DARK_SURFACE_RGB = 0x242424
    const val SHADOW_RADIUS_DP = 10f
    const val SHADOW_OFFSET_Y_DP = 0f
    const val LIGHT_SHADOW_COLOR = 0x1A000000
    const val DARK_SHADOW_COLOR = 0x33000000

    data class Material(
        val density: Float,
        val minimumPadding: Float,
        val blurRadius: Float,
        val saturation: Float,
        val contrast: Float,
        val refractionHeight: Float,
        val refractionAmount: Float,
        val dispersion: Float,
        val depthEffect: Boolean,
        val tint: Int,
        val pressedTint: Int,
        val highlightAlpha: Float,
        val lightAngle: Float,
        val glowProgress: Float,
        val touchX: Float,
        val inputWidth: Int,
        val actionsLeft: Int,
    )

    fun params(
        width: Int,
        height: Int,
        density: Float,
        night: Boolean,
        config: BottomTabLiquidGlassConfig,
        inputWidth: Int = 0,
        actionsLeft: Int = 0,
        droplet: Boolean = false,
        press: Float = 0f,
        touchX: Float = width / 2f,
        lightAngle: Float = DEFAULT_LIGHT_ANGLE,
        glow: Float = press,
        drawHighlight: Boolean = true,
    ): Material {
        require(width > 0 && height > 0 && density > 0f)
        val split = inputWidth > 0 && actionsLeft > inputWidth && actionsLeft < width
        val progress = if (config.liquidGlassEnabled) press.coerceIn(0f, 1f) else 0f
        val refract = config.liquidGlassEnabled
        val amount = if (!refract) 0f else (if (droplet) 14f * progress else 24f) * density
        val blurRadius = if (config.needsBackdrop && !droplet) {
            MAX_BLUR_RADIUS_DP * config.blurPercent.coerceIn(0, BottomTabLiquidGlassConfig.MAX_BLUR_PERCENT) /
                BottomTabLiquidGlassConfig.MAX_BLUR_PERCENT * density
        } else 0f
        return Material(
            density = density,
            minimumPadding = maxOf(amount, if (!droplet && drawHighlight && refract) 40f * density else 0f),
            // Both material modes use the slider; the selection lens never treats the colors twice.
            blurRadius = blurRadius,
            saturation = if (blurRadius > 0f) MATERIAL_SATURATION else 1f,
            contrast = if (blurRadius > 0f) MATERIAL_CONTRAST else 1f,
            refractionHeight = if (!refract) 0f else (if (droplet) 10f * progress else 24f) * density,
            refractionAmount = amount,
            dispersion = if (droplet && refract) 0.5f else 0f,
            depthEffect = droplet,
            // Zero-radius blur remains translucent even though it needs no page capture.
            tint = if (droplet) selectionTint(night, progress) else surfaceTint(night, refract || config.blurEnabled),
            pressedTint = if (droplet) ((0.03f * progress * 255f).roundToInt() shl 24) else 0,
            highlightAlpha = if (refract && drawHighlight) (if (droplet) progress else 0.75f) else 0f,
            lightAngle = lightAngle + (if (droplet) PI / 2 else -PI / 4).toFloat(),
            glowProgress = if (!droplet && refract) glow.coerceAtLeast(0f) else 0f,
            touchX = touchX,
            inputWidth = if (split) inputWidth else 0,
            actionsLeft = if (split) actionsLeft else 0,
        )
    }

    fun selectionTint(night: Boolean, progress: Float): Int =
        ((0.1f * (1f - progress) * 255f).roundToInt() shl 24) or (if (night) 0xFFFFFF else 0)

    fun surfaceTint(night: Boolean, translucent: Boolean): Int {
        val alpha = if (!translucent) 0xFF else if (night) DARK_SURFACE_ALPHA else LIGHT_SURFACE_ALPHA
        return (alpha shl 24) or (if (night) DARK_SURFACE_RGB else LIGHT_SURFACE_RGB)
    }

    fun margin(material: Material): Int = maxOf(
        ceil(material.minimumPadding).toInt(),
        if (material.blurRadius > 0f) 13 * (1 shl LiquidGlassBlur.levels(material.blurRadius).high) else 0,
    )
}
