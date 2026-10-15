package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.forbidad4tieba.hook.symbol.contract.PostReplyBarContract
import kotlin.math.roundToInt

/** Owns the complete native icon/label stack while preserving its parents and click handlers. */
internal class PostReplyActionLayout(private val content: PostReplyBarContract.ActionContent) {
    private val body = content.body
    private val originalLayout = FrameLayout.LayoutParams(body.layoutParams as FrameLayout.LayoutParams)
    private val originalPadding = intArrayOf(body.paddingLeft, body.paddingTop, body.paddingRight, body.paddingBottom)
    private val originalIconWidth = content.icon.layoutParams.width
    private val originalIconHeight = content.icon.layoutParams.height
    private val originalTranslationX = content.artwork.translationX
    private val originalTranslationY = content.artwork.translationY
    private val originalAlpha = body.alpha
    private val originalBodyClip = body.clipChildren
    private val originalBodyClipPadding = body.clipToPadding
    private val iconGroup = content.icon as? ViewGroup
    private val originalClip = iconGroup?.clipChildren
    private val originalClipPadding = iconGroup?.clipToPadding
    private val offset = Rect()
    private var iconSize = 0
    private var labelWidth = 0
    private var labelHeight = 0
    private var gap = 0

    fun prepare(width: Int, size: Int, density: Float) {
        iconSize = size
        val label = content.label
        if (label.visibility != View.GONE) {
            label.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            labelWidth = label.measuredWidth
            labelHeight = label.measuredHeight
        } else {
            labelWidth = 0
            labelHeight = 0
        }
        gap = if (labelHeight > 0) density.roundToInt() else 0
        val height = iconSize + gap + labelHeight
        val layout = body.layoutParams as FrameLayout.LayoutParams
        if (layout.width != ViewGroup.LayoutParams.MATCH_PARENT || layout.height != height || layout.gravity != Gravity.CENTER) {
            layout.width = ViewGroup.LayoutParams.MATCH_PARENT
            layout.height = height
            layout.gravity = Gravity.CENTER
            body.layoutParams = layout
        }
        if (body.paddingLeft != 0 || body.paddingTop != 0 || body.paddingRight != 0 || body.paddingBottom != 0) {
            body.setPadding(0, 0, 0, 0)
        }
        val iconLayout = content.icon.layoutParams
        if (iconLayout.width != size || iconLayout.height != size) {
            iconLayout.width = size
            iconLayout.height = size
            content.icon.layoutParams = iconLayout
        }
        // The native like animation has a larger transparent canvas than its visible icon.
        if (body.clipChildren) body.clipChildren = false
        if (body.clipToPadding) body.clipToPadding = false
        if (iconGroup?.clipChildren == true) iconGroup.clipChildren = false
        if (iconGroup?.clipToPadding == true) iconGroup.clipToPadding = false
    }

    /** Host constraints place the icon at the top and text at the bottom independently. */
    fun layout() {
        val iconLeft = (body.width - iconSize) / 2
        content.icon.layout(iconLeft, 0, iconLeft + iconSize, iconSize)
        val labelLeft = (body.width - labelWidth) / 2
        content.label.layout(labelLeft, iconSize + gap, labelLeft + labelWidth, iconSize + gap + labelHeight)
        val image = content.artwork
        if (image !== content.icon && iconGroup != null &&
            LiquidGlassGeom.layoutOffsetInAncestor(image, iconGroup, offset)) {
            image.translationX = (iconSize - image.width) / 2f - offset.left
            image.translationY = (iconSize - image.height) / 2f - offset.top
        }
    }

    fun setAlpha(value: Float) { body.alpha = originalAlpha * value }

    fun restore() {
        body.alpha = originalAlpha
        body.clipChildren = originalBodyClip
        body.clipToPadding = originalBodyClipPadding
        body.layoutParams = originalLayout
        body.setPadding(originalPadding[0], originalPadding[1], originalPadding[2], originalPadding[3])
        content.icon.layoutParams = content.icon.layoutParams.apply {
            width = originalIconWidth
            height = originalIconHeight
        }
        content.artwork.translationX = originalTranslationX
        content.artwork.translationY = originalTranslationY
        if (iconGroup != null) {
            iconGroup.clipChildren = checkNotNull(originalClip)
            iconGroup.clipToPadding = checkNotNull(originalClipPadding)
        }
        body.requestLayout()
    }
}
