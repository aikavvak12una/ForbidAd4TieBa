package com.forbidad4tieba.hook.feature.ui

import kotlin.math.max

internal fun backgroundDecodeSizeForMaxEdge(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
    val normalizedWidth = width.coerceAtLeast(1)
    val normalizedHeight = height.coerceAtLeast(1)
    val maxDimension = max(normalizedWidth, normalizedHeight)
    if (maxDimension <= maxEdge) return normalizedWidth to normalizedHeight
    return (
        normalizedWidth.toLong() * maxEdge / maxDimension
        ).toInt().coerceAtLeast(1) to (
        normalizedHeight.toLong() * maxEdge / maxDimension
        ).toInt().coerceAtLeast(1)
}

internal fun backgroundRequestForStyle(
    style: HomeNativeGlassRuntimeStyle,
    targetWidth: Int,
    targetHeight: Int,
): BackgroundRequest? {
    if (!style.hasBackgroundImage) return null
    return BackgroundRequest(
        path = style.backgroundImagePath,
        blurCachePath = style.blurCacheImagePath,
        blurPercent = style.cardBlurPercent,
        targetWidth = targetWidth.coerceAtLeast(1),
        targetHeight = targetHeight.coerceAtLeast(1),
    )
}
