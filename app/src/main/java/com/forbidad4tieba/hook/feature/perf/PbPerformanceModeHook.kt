package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat

object PbPerformanceModeHook {
    private val booleanOverrides = arrayOf(
        UbsAbTestBooleanOverride("hybridPbOpt", true) { ConfigManager.isPbPerformanceModeEnabled },
        UbsAbTestBooleanOverride("imagePerfLog", false) { ConfigManager.isPbPerformanceModeEnabled },
        UbsAbTestBooleanOverride("isEnableHybridScrollLog", false) { ConfigManager.isPbPerformanceModeEnabled },
        UbsAbTestBooleanOverride("isPbCommentFunAdABTest", false) {
            ConfigManager.isPbPerformanceModeEnabled || ConfigManager.isAdBlockEnabled
        },
        UbsAbTestBooleanOverride("isPbPageBannerFunAdSdkTest", false) {
            ConfigManager.isPbPerformanceModeEnabled || ConfigManager.isAdBlockEnabled
        },
    )

    @Volatile private var hooked = false

    fun hook(cl: ClassLoader) {
        if (!UbsAbTestBooleanOverrideInstaller.hasEnabledOverride(booleanOverrides)) {
            XposedCompat.log("[PbPerformanceModeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val helperClass = UbsAbTestBooleanOverrideInstaller.findHelperClass(cl)
            if (helperClass == null) {
                resetHooked()
                XposedCompat.log("[PbPerformanceModeHook] class NOT FOUND: ${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}")
                return
            }

            var installed = 0
            for (entry in booleanOverrides) {
                val methodName = entry.methodName
                val method = UbsAbTestBooleanOverrideInstaller.findMethod(helperClass, methodName)
                if (method == null) {
                    XposedCompat.log(
                        "[PbPerformanceModeHook] method NOT FOUND or invalid: " +
                            UbsAbTestBooleanOverrideInstaller.methodSignature(methodName),
                    )
                    continue
                }
                UbsAbTestBooleanOverrideInstaller.install(mod, method, entry)
                installed++
            }

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[PbPerformanceModeHook] no methods installed")
                return
            }
            XposedCompat.log("[PbPerformanceModeHook] hooks INSTALLED: count=$installed/${booleanOverrides.size}")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbPerformanceModeHook] install FAILED: ${t.message}")
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
