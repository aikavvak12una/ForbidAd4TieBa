package com.forbidad4tieba.hook.symbol.contract

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** The launch activity and its temporary cover, independent of the host's symbol cache. */
object LaunchSplashContract {
    fun resolve(classLoader: ClassLoader): Method = validate(
        Class.forName(StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS, false, classLoader),
    )

    internal fun validate(activity: Class<*>): Method {
        check(Activity::class.java.isAssignableFrom(activity)) { "Launch target is not an Activity" }
        return activity.getDeclaredMethod("onCreate", Bundle::class.java).apply {
            check(!Modifier.isStatic(modifiers) && returnType == Void.TYPE) { "Invalid launch onCreate" }
            isAccessible = true
        }
    }

    /** Only the empty full-screen sibling painted with the window's launch artwork qualifies. */
    fun placeholder(content: ViewGroup, windowBackground: Drawable): View? {
        val artwork = SplashBackground.artwork(windowBackground) ?: return null
        var match: View? = null
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child.javaClass != FrameLayout::class.java ||
                (child as FrameLayout).childCount != 0 || child.visibility != View.VISIBLE
            ) continue
            val layout = child.layoutParams ?: continue
            if (layout.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                layout.height != ViewGroup.LayoutParams.MATCH_PARENT
            ) continue
            val candidate = SplashBackground.artwork(child.background) ?: continue
            if (candidate.bitmap !== artwork.bitmap || candidate.gravity != artwork.gravity) continue
            if (match != null) return null // Ambiguous covers must not recolor unrelated content.
            match = child
        }
        return match
    }
}
