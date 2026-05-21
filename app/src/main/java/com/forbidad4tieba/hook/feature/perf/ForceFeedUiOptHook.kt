package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object ForceFeedUiOptHook {
    @Volatile private var hooked = false

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isFeedUiOptForced) {
            XposedCompat.logD("[ForceFeedUiOptHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val helperClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS, cl)
            if (helperClass == null) {
                resetHooked()
                XposedCompat.log("[ForceFeedUiOptHook] class NOT FOUND: ${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}")
                return
            }

            val method = XposedCompat.findMethodOrNull(helperClass, "isFeedUIOpt")
            if (method == null || !isStaticNoArgBoolean(method)) {
                resetHooked()
                XposedCompat.log("[ForceFeedUiOptHook] method NOT FOUND or invalid: isFeedUIOpt()")
                return
            }
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (ConfigManager.isFeedUiOptForced) return@intercept true
                chain.proceed()
            }
            XposedCompat.log("[ForceFeedUiOptHook] hook INSTALLED: isFeedUIOpt() -> true")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[ForceFeedUiOptHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun isStaticNoArgBoolean(method: Method): Boolean {
        return Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.javaObjectType)
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
