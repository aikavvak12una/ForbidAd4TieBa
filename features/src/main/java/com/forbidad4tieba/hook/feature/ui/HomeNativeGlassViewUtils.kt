package com.forbidad4tieba.hook.feature.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val GLASS_INVALIDATE_PARENT_SCAN_DEPTH = 24

internal fun findViewByResolvedId(root: View, viewId: Int): View? {
    if (viewId <= 0 || viewId == View.NO_ID) return null
    return runCatching { root.findViewById<View>(viewId) }.getOrNull()
}

internal fun setTransparentBackgroundIfNeeded(view: View) {
    val background = view.background ?: return
    if ((background as? ColorDrawable)?.color == Color.TRANSPARENT) return
    view.setBackgroundColor(Color.TRANSPARENT)
}

internal fun setTransparentBackgroundPreservingPaddingIfNeeded(view: View) {
    val background = view.background ?: return
    if ((background as? ColorDrawable)?.color == Color.TRANSPARENT) return
    setBackgroundColorPreservingPadding(view, Color.TRANSPARENT)
}

internal fun setBackgroundColorPreservingPadding(view: View, color: Int) {
    if ((view.background as? ColorDrawable)?.color == color) return
    val left = view.paddingLeft
    val top = view.paddingTop
    val right = view.paddingRight
    val bottom = view.paddingBottom
    view.background = ColorDrawable(color)
    view.setPadding(left, top, right, bottom)
    view.invalidate()
}

internal fun isAncestorOf(candidate: View, descendant: View): Boolean {
    var current = descendant.parent as? View
    while (current != null) {
        if (current === candidate) return true
        current = current.parent as? View
    }
    return false
}

internal fun isViewWithinAncestor(view: View, ancestor: View): Boolean {
    if (view === ancestor) return true
    var current = view.parent
    var depth = 0
    while (current is View && depth < GLASS_INVALIDATE_PARENT_SCAN_DEPTH) {
        if (current === ancestor) return true
        current = current.parent
        depth++
    }
    return false
}

internal fun isTouchInsideView(view: View, event: MotionEvent): Boolean {
    return event.x >= 0f &&
        event.y >= 0f &&
        event.x <= view.width.toFloat() &&
        event.y <= view.height.toFloat()
}

internal fun clearForeground(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (view.foreground != null) {
            view.foreground = null
        }
    }
}

internal fun clearElevation(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        if (view.stateListAnimator != null) {
            view.stateListAnimator = null
        }
        if (view.elevation != 0f) {
            view.elevation = 0f
        }
        if (view.translationZ != 0f) {
            view.translationZ = 0f
        }
    }
}

internal fun findFieldInHierarchy(clazz: Class<*>, name: String): Field? {
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
        try {
            return current.getDeclaredField(name).apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            current = current.superclass
        }
    }
    return null
}

internal fun findMethodInHierarchy(
    clazz: Class<*>,
    name: String,
    vararg paramTypes: Class<*>,
): Method? {
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
        try {
            return current.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            current = current.superclass
        }
    }
    return null
}
