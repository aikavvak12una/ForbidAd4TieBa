package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import java.lang.reflect.Method

object PbPerformanceModeHook {
    private val booleanOverrides = arrayOf(
        UbsAbTestBooleanOverride(PerformanceAbTarget.HYBRID_PB, true) {
            // 强制帖子预加载开启时让位：hybrid webview 的 apiData 预加载通道需要 hybridPbOpt=false
            ConfigManager.isPbPerformanceModeEnabled && !ConfigManager.isPbPreloadForced
        },
        UbsAbTestBooleanOverride(PerformanceAbTarget.IMAGE_PERF_LOG, false) { ConfigManager.isPbPerformanceModeEnabled },
        UbsAbTestBooleanOverride(PerformanceAbTarget.PB_COMMENT_AD, false) {
            ConfigManager.isPbPerformanceModeEnabled || ConfigManager.isPbAdExperimentBlockEnabled
        },
        UbsAbTestBooleanOverride(PerformanceAbTarget.PB_BANNER_AD, false) {
            ConfigManager.isPbPerformanceModeEnabled || ConfigManager.isPbAdExperimentBlockEnabled
        },
    )

    @Volatile private var hooked = false

    fun hook(abMethods: Map<String, Method>) {
        if (!UbsAbTestBooleanOverrideInstaller.hasEnabledOverride(booleanOverrides)) {
            XposedCompat.log("[PbPerformanceModeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val installed = UbsAbTestBooleanOverrideInstaller.installEnabled(
                "[PbPerformanceModeHook]", mod, abMethods, booleanOverrides,
            )

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[PbPerformanceModeHook] no methods installed")
                return
            }
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
