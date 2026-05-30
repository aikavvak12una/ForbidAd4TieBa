package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat

object ForceFeedUiOptHook {
    private val forceFeedUiOptOverride = UbsAbTestBooleanOverride("isFeedUIOpt", true) {
        ConfigManager.shouldForceFeedUiOpt()
    }

    @Volatile private var hooked = false

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.shouldForceFeedUiOpt()) {
            XposedCompat.logD("[ForceFeedUiOptHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val helperClass = UbsAbTestBooleanOverrideInstaller.findHelperClass(cl)
            if (helperClass == null) {
                resetHooked()
                XposedCompat.log("[ForceFeedUiOptHook] class NOT FOUND: ${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}")
                return
            }

            val method = UbsAbTestBooleanOverrideInstaller.findMethod(helperClass, forceFeedUiOptOverride.methodName)
            if (method == null) {
                resetHooked()
                XposedCompat.log("[ForceFeedUiOptHook] method NOT FOUND or invalid: isFeedUIOpt()")
                return
            }
            UbsAbTestBooleanOverrideInstaller.install(mod, method, forceFeedUiOptOverride)
            XposedCompat.log("[ForceFeedUiOptHook] hook INSTALLED: isFeedUIOpt() -> true")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[ForceFeedUiOptHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
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
