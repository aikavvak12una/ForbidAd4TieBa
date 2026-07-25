package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PbFirstFloorRecommendInsertSymbols

object PbFirstFloorRecommendBlockHook {
    @Volatile private var hooked = false

    internal fun hook(targets: PbFirstFloorRecommendInsertSymbols) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            mod.hook(targets.method).intercept { chain ->
                if (ConfigManager.isPostAdBlockEnabled) {
                    false
                } else {
                    chain.proceed()
                }
            }
            XposedCompat.log(
                "[PbFirstFloorRecommendBlockHook] hook INSTALLED: " +
                    "${targets.method.declaringClass.name}.${targets.method.name}",
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbFirstFloorRecommendBlockHook] install FAILED: ${t.message}")
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
