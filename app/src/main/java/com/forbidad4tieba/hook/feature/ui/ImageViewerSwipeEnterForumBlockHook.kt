package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.util.concurrent.atomic.AtomicBoolean

object ImageViewerSwipeEnterForumBlockHook {
    private val hookInstalled = AtomicBoolean(false)
    private val booleanType: Class<Boolean> = Boolean::class.javaPrimitiveType!!

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!hookInstalled.compareAndSet(false, true)) return

        try {
            val targetClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.GALLERY_SWIPE_LAYOUT_CLASS, cl)
            if (targetClass == null) {
                hookInstalled.set(false)
                XposedCompat.log("[ImageViewerSwipeEnterForumBlockHook] skipped: class not found")
                return
            }
            val setGuideVisibilityMethod = XposedCompat.findMethodOrNull(
                targetClass,
                StableTiebaHookPoints.METHOD_SET_GUIDE_VISIBILITY,
                booleanType,
            )
            val setGuideTouchingMethod = XposedCompat.findMethodOrNull(
                targetClass,
                StableTiebaHookPoints.METHOD_SET_GUIDE_TOUCHING,
                booleanType,
            )
            if (setGuideVisibilityMethod == null && setGuideTouchingMethod == null) {
                hookInstalled.set(false)
                XposedCompat.log("[ImageViewerSwipeEnterForumBlockHook] skipped: guide methods not found")
                return
            }

            setGuideVisibilityMethod?.let { method ->
                mod.hook(method).intercept { chain ->
                    val view = chain.thisObject as? View
                    if (view != null && isImageViewerScene(view)) {
                        return@intercept chain.proceed(arrayOf<Any?>(false))
                    }
                    chain.proceed()
                }
            }
            setGuideTouchingMethod?.let { method ->
                mod.hook(method).intercept { chain ->
                    val view = chain.thisObject as? View
                    if (view != null && isImageViewerScene(view)) {
                        return@intercept chain.proceed(arrayOf<Any?>(false))
                    }
                    chain.proceed()
                }
            }
            XposedCompat.log("[ImageViewerSwipeEnterForumBlockHook] hook INSTALLED")
        } catch (t: Throwable) {
            hookInstalled.set(false)
            XposedCompat.log("[ImageViewerSwipeEnterForumBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun isImageViewerScene(view: View): Boolean {
        val activity = ReflectionUtils.findActivityFromContext(view.context) ?: return false
        return activity.javaClass.name == StableTiebaHookPoints.IMAGE_VIEWER_ACTIVITY_CLASS
    }
}
