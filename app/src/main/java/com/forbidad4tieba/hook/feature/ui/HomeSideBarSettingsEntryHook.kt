package com.forbidad4tieba.hook.feature.ui

import android.view.View
import android.view.ViewParent
import android.widget.ImageView
import android.widget.RelativeLayout
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.SettingsMenuHook
import com.forbidad4tieba.hook.utils.ViewExt

object HomeSideBarSettingsEntryHook {
    private const val SETTINGS_IMAGE_ARG_INDEX = 7
    private const val DRAWER_LAYOUT_CLASS_NAME = "androidx.drawerlayout.widget.DrawerLayout"
    private const val DRAWER_GRAVITY_LEFT = 3
    private const val SHOW_DIALOG_AFTER_DRAWER_CLOSE_DELAY_MS = 80L

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        try {
            val bindingClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.SIDEBAR_PERSON_INFO_BINDING_CLASS, cl)
            val stackImageClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.TB_STACK_IMAGE_VIEW_CLASS, cl)
            val headViewClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.HEAD_PENDANT_CLICKABLE_VIEW_CLASS, cl)
            val emTextViewClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.EM_TEXT_VIEW_CLASS, cl)
            val tbImageClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.TB_IMAGE_CLASS, cl)
            if (
                bindingClass == null ||
                stackImageClass == null ||
                headViewClass == null ||
                emTextViewClass == null ||
                tbImageClass == null
            ) {
                XposedCompat.log("[HomeSideBarSettingsEntryHook] SKIP - missing sidebar binding classes")
                return
            }

            val ctor = bindingClass.getDeclaredConstructor(
                RelativeLayout::class.java,
                ImageView::class.java,
                stackImageClass,
                headViewClass,
                emTextViewClass,
                RelativeLayout::class.java,
                tbImageClass,
                tbImageClass,
            ).apply { isAccessible = true }

            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                bindLongPress(chain.args.getOrNull(SETTINGS_IMAGE_ARG_INDEX) as? View, cl)
                result
            }
            XposedCompat.log(
                "[HomeSideBarSettingsEntryHook] hook INSTALLED: " +
                    "${StableTiebaHookPoints.SIDEBAR_PERSON_INFO_BINDING_CLASS}.<init>",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[HomeSideBarSettingsEntryHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun bindLongPress(view: View?, cl: ClassLoader) {
        if (view == null || !ViewExt.markSettingsLongPressBound(view)) return
        view.setOnLongClickListener {
            val context = it.context ?: view.context
            if (closeOwningDrawer(view)) {
                view.postDelayed(
                    { SettingsMenuHook.showModuleSettingsDialog(context, cl) },
                    SHOW_DIALOG_AFTER_DRAWER_CLOSE_DELAY_MS,
                )
            } else {
                SettingsMenuHook.showModuleSettingsDialog(context, cl)
            }
            true
        }
    }

    private fun closeOwningDrawer(view: View): Boolean {
        val drawerLayout = findOwningDrawerLayout(view) ?: return false
        val drawerClass = drawerLayout.javaClass
        try {
            val closeDrawer = drawerClass.getMethod(
                "closeDrawer",
                java.lang.Integer.TYPE,
                java.lang.Boolean.TYPE,
            )
            closeDrawer.invoke(drawerLayout, DRAWER_GRAVITY_LEFT, false)
            return true
        } catch (t: Throwable) {
            XposedCompat.logD("[HomeSideBarSettingsEntryHook] close drawer without animation failed: ${t.message}")
        }
        return try {
            val closeDrawer = drawerClass.getMethod("closeDrawer", java.lang.Integer.TYPE)
            closeDrawer.invoke(drawerLayout, DRAWER_GRAVITY_LEFT)
            true
        } catch (t: Throwable) {
            XposedCompat.logD("[HomeSideBarSettingsEntryHook] close drawer failed: ${t.message}")
            false
        }
    }

    private fun findOwningDrawerLayout(view: View): Any? {
        var current: ViewParent? = view.parent
        while (current != null) {
            if (current is View && isDrawerLayoutClass(current.javaClass)) return current
            current = current.parent
        }
        return null
    }

    private fun isDrawerLayoutClass(cls: Class<*>?): Boolean {
        var current = cls
        while (current != null) {
            if (current.name == DRAWER_LAYOUT_CLASS_NAME) return true
            current = current.superclass
        }
        return false
    }
}
