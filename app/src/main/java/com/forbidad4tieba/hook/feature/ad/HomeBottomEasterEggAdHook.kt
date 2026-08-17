package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.HomeBottomEasterEggAdSymbols

object HomeBottomEasterEggAdHook {
    @Volatile private var hooked = false

    internal fun hook(targets: HomeBottomEasterEggAdSymbols) {
        if (!ConfigManager.isHomeBottomEasterEggAdBlockEnabled) {
            XposedCompat.log("[HomeBottomEasterEggAdHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            mod.hook(targets.parserMethod).intercept { chain ->
                if (ConfigManager.isHomeBottomEasterEggAdBlockEnabled) {
                    return@intercept null
                }
                chain.proceed()
            }
            XposedCompat.log(
                "[HomeBottomEasterEggAdHook] hook INSTALLED: " +
                    "${targets.parserMethod.declaringClass.name}.${targets.parserMethod.name}",
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[HomeBottomEasterEggAdHook] install FAILED: ${t.message}")
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
