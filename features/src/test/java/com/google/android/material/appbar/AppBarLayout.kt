package com.google.android.material.appbar

/** JVM fixture for the stable Material APIs; it deliberately has mutable runtime state. */
open class AppBarLayout {
    var range = 80
    var lastOffset = 0
    var offsetNotifications = 0
    var expanded = true
    var animated = false

    fun getTotalScrollRange(): Int = range

    fun onOffsetChanged(offset: Int) {
        lastOffset = offset
        offsetNotifications++
    }

    fun setExpanded(value: Boolean, animate: Boolean) {
        expanded = value
        animated = animate
    }

    class LayoutParams {
        private var flags = 0
        fun getScrollFlags(): Int = flags
        fun setScrollFlags(value: Int) { flags = value }
    }

    open class BaseBehavior : ViewOffsetBehavior()
    class Behavior : BaseBehavior()
}

open class ViewOffsetBehavior {
    private var offset = 0
    fun getTopAndBottomOffset(): Int = offset
    fun setTopAndBottomOffset(value: Int): Boolean {
        val changed = value != offset
        offset = value
        return changed
    }
}
