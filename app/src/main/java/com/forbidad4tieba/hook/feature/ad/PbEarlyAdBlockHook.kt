package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.PbEarlyAdBlockSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat

object PbEarlyAdBlockHook {
    @Volatile private var hooked = false

    internal fun hook(targets: PbEarlyAdBlockSymbols) {
        if (!ConfigManager.isPbEarlyAdBlockEnabled) {
            XposedCompat.log("[PbEarlyAdBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            var installed = 0
            for (target in targets.methods.distinctBy { it.method }) {
                mod.hook(target.method).intercept { chain ->
                    if (ConfigManager.isPbEarlyAdBlockEnabled) {
                        return@intercept blockedReturnValue(target.returnsSparseArray)
                    }
                    chain.proceed()
                }
                installed++
            }

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[PbEarlyAdBlockHook] no methods installed")
                return
            }
            XposedCompat.log("[PbEarlyAdBlockHook] hooks INSTALLED: class=${targets.targetClass.name}, count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbEarlyAdBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun blockedReturnValue(returnsSparseArray: Boolean): Any? {
        return if (returnsSparseArray) android.util.SparseArray<Any>() else null
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
