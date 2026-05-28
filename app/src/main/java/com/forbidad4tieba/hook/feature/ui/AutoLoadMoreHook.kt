package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.symbol.model.AutoLoadMoreSymbols
import com.forbidad4tieba.hook.core.XposedCompat

object AutoLoadMoreHook {

    private const val PRELOAD_NOT_SEE_THREAD_NUM = 20

    internal fun hook(symbols: AutoLoadMoreSymbols) {
        val mod = XposedCompat.module ?: return

        try {
            symbols.ubsMethod?.let { ubsMethod ->
                mod.hook(ubsMethod).intercept { true }
                XposedCompat.log(
                    "[AutoLoadMoreHook] hook INSTALLED: " +
                        "${ubsMethod.declaringClass.name}.${ubsMethod.name}()",
                )
            }

            symbols.configMethod?.let { configMethod ->
                mod.hook(configMethod).intercept { PRELOAD_NOT_SEE_THREAD_NUM }
                XposedCompat.log(
                    "[AutoLoadMoreHook] hook INSTALLED: " +
                        "config=${configMethod.declaringClass.name}.${configMethod.name}(), " +
                        "threshold=$PRELOAD_NOT_SEE_THREAD_NUM",
                )
            }
        } catch (t: Throwable) {
            XposedCompat.log("[AutoLoadMoreHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
