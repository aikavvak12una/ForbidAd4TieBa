// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted from miuix-blur 0.9.3 (Shaders.kt / HighlightStyle.kt), with KernelSU's
// iosIndicatorSpecular parameters. View/AGSL port; guards degenerate SDF normals.
package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.annotation.TargetApi
import android.os.Build
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Shared optical model for the outer capsule and the moving selection. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal class LiquidGlassLighting(private val density: Float, private val angleOffset: Float) {
    internal companion object {
        const val SHADER = """
uniform float2 halfView;
uniform float2 halfViewFloor;
uniform float radius;
uniform float strokeWidth;
uniform float innerBlurRadius;
uniform float highlightAlpha;
uniform float3 lightDir1;
uniform float3 lightDir2;

float roundedBoxSDF(float2 pos, float2 halfSize, float r) {
    float2 d = pos - halfSize + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}
float3 getNormal(float2 fragCoord, float sdf, float r) {
    float2 xy = fragCoord - halfViewFloor;
    float2 xyAbs = abs(xy);
    float t = smoothstep(-innerBlurRadius, 0.0, sdf);
    float z = sqrt(max(innerBlurRadius * innerBlurRadius - t * t, 0.0));
    float3 coord = float3(xyAbs, -z);
    float2 corner = min(halfView - r, xyAbs);
    float2 delta = coord.xy - corner;
    float2 dir = delta / max(length(delta), 0.0001);
    corner += dir * (r - innerBlurRadius);
    if (any(lessThan(xyAbs, corner))) return float3(0.0, 0.0, -1.0);
    float3 normal = coord - float3(corner, 0.0);
    normal /= max(length(normal), 0.0001);
    normal.xy *= sign(xy);
    return normal;
}
half4 main(float2 coord) {
    float sdf = roundedBoxSDF(abs(coord - halfView), halfView, radius);
    // The interior has no specular contribution. Avoid normal/light work there.
    if (sdf < -max(innerBlurRadius, strokeWidth)) return half4(0.0);
    half mask = half(1.0 - smoothstep(-1.0, 0.0, sdf));
    float stroke = smoothstep(-strokeWidth, -strokeWidth + 1.0, sdf);
    float3 n = getNormal(coord, sdf, max(radius, innerBlurRadius));
    float primary = dot(n.xy, lightDir1.xy);
    float secondary = dot(n.xy, lightDir2.xy);
    half light = half((0.12 * stroke * stroke + primary * primary
            + secondary * secondary * 0.4) * highlightAlpha);
    return half4(light, light, light, 1.0) * mask;
}
"""
    }

    private val shader = RuntimeShader(SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.PLUS }
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastAngle = Float.NaN

    init {
        shader.setFloatUniform("strokeWidth", density)
        shader.setFloatUniform("innerBlurRadius", 2f * density)
        val length = sqrt(0.1f * 0.1f + 0.5f * 0.5f)
        shader.setFloatUniform("lightDir2", 0f, 0.1f / length, -0.5f / length)
    }

    fun draw(canvas: Canvas, width: Int, height: Int, angle: Float, alpha: Float) {
        if (width <= 0 || height <= 0 || alpha <= 0f || !canvas.isHardwareAccelerated) return
        if (lastWidth != width || lastHeight != height) {
            lastWidth = width
            lastHeight = height
            shader.setFloatUniform("halfView", width * 0.5f, height * 0.5f)
            shader.setFloatUniform("halfViewFloor", floor(width * 0.5f), floor(height * 0.5f))
            shader.setFloatUniform("radius", height * 0.5f)
        }
        if (angle != lastAngle) {
            lastAngle = angle
            val rad = angle + angleOffset
            val length = sqrt(1f + 0.05f * 0.05f)
            shader.setFloatUniform("lightDir1", cos(rad) / length, sin(rad) / length, -0.05f / length)
        }
        shader.setFloatUniform("highlightAlpha", alpha.coerceIn(0f, 1f))
        // Paint snapshots the shader at recording time; cache the compiled program, not a
        // RenderEffect whose uniforms would be frozen at creation time.
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
