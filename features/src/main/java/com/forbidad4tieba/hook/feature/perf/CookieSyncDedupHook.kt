package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import java.lang.reflect.Method

/** Keeps the host's changed-cookie updates while enabling its repeated-sync suppression. */
object CookieSyncDedupHook {
    private val overrides = arrayOf(
        UbsAbTestBooleanOverride(PerformanceAbTarget.COOKIE_REPEATED, true) {
            ConfigManager.snapshot().isCookieSyncDedupEnabled
        },
    )

    fun hook(abMethods: Map<String, Method>) {
        if (!UbsAbTestBooleanOverrideInstaller.hasEnabledOverride(overrides)) return
        val api = XposedCompat.module ?: return
        UbsAbTestBooleanOverrideInstaller.installEnabled("[CookieSyncDedupHook]", api, abMethods, overrides)
    }
}
