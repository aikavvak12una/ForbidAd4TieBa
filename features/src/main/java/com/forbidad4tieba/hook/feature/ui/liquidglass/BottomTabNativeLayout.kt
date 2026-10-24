package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout

/** Owns reversible layout/background edits while the floating decoration is active. */
internal class BottomTabNativeLayout(
    private val host: ViewGroup,
    private val wrapper: ViewGroup,
    private val row: ViewGroup,
    private val pager: ViewGroup,
) {
    private class Snapshot(row: ViewGroup, wrapper: ViewGroup, pager: ViewGroup) {
        val pagerLayout = copyLayout(pager.layoutParams)
        val padding = intArrayOf(row.paddingLeft, row.paddingTop, row.paddingRight, row.paddingBottom)
        val gravity = (row as? LinearLayout)?.gravity
        val transform = floatArrayOf(row.translationX, row.translationY, row.scaleX, row.scaleY, row.pivotX, row.pivotY)
        val overlapping = wrapper.getHasOverlappingRendering()
        val columns = linkedMapOf<View, ViewGroup.LayoutParams>()
        val clips = linkedMapOf<ViewGroup, Pair<Boolean, Boolean>>()
        val backgrounds = linkedMapOf<View, Drawable?>(row to row.background, wrapper to wrapper.background)
        val visibility = linkedMapOf<View, Int>()
    }

    private var snapshot: Snapshot? = null

    fun activate() {
        if (snapshot != null) return
        snapshot = Snapshot(row, wrapper, pager)
        var parent: ViewGroup? = row
        while (parent != null) {
            rememberClip(parent)
            parent.clipChildren = false
            parent.clipToPadding = false
            if (parent === host) break
            parent = parent.parent as? ViewGroup
        }
        wrapper.forceHasOverlappingRendering(false)
    }

    fun rememberColumn(view: View) {
        snapshot?.columns?.getOrPut(view) { copyLayout(view.layoutParams) }
    }

    fun rememberClip(view: ViewGroup) {
        snapshot?.clips?.getOrPut(view) { view.clipChildren to view.clipToPadding }
    }

    fun rememberBackground(view: View) {
        if (view.background != null) snapshot?.backgrounds?.set(view, view.background)
    }

    fun rememberVisibility(view: View) {
        snapshot?.visibility?.getOrPut(view) { view.visibility }
    }

    fun restore() {
        val native = snapshot ?: return
        snapshot = null
        for ((view, layout) in native.columns) if (view.parent === row) view.layoutParams = copyLayout(layout)
        pager.layoutParams = copyLayout(native.pagerLayout)
        row.setPadding(native.padding[0], native.padding[1], native.padding[2], native.padding[3])
        if (row is LinearLayout && native.gravity != null) row.gravity = native.gravity
        row.translationX = native.transform[0]
        row.translationY = native.transform[1]
        row.scaleX = native.transform[2]
        row.scaleY = native.transform[3]
        row.pivotX = native.transform[4]
        row.pivotY = native.transform[5]
        wrapper.forceHasOverlappingRendering(native.overlapping)
        for ((view, clip) in native.clips) {
            view.clipChildren = clip.first
            view.clipToPadding = clip.second
        }
        for ((view, background) in native.backgrounds) if (view.background == null) view.background = background
        for ((view, visibility) in native.visibility) view.visibility = visibility
    }

    private companion object {
        fun copyLayout(value: ViewGroup.LayoutParams): ViewGroup.LayoutParams = when (value) {
            is RelativeLayout.LayoutParams -> RelativeLayout.LayoutParams(value)
            is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(value)
            is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(value)
            is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(value)
            else -> ViewGroup.LayoutParams(value)
        }
    }
}
