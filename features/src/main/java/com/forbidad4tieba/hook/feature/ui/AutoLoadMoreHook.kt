package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.AutoLoadMoreSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.config.ConfigManager

object AutoLoadMoreHook {

    private const val PRELOAD_NOT_SEE_THREAD_NUM = 20

    internal fun hook(symbols: AutoLoadMoreSymbols) {
        val mod = XposedCompat.module ?: return

        try {
            symbols.ubsMethod?.let { ubsMethod ->
                RuntimeHooks.builder(mod, ubsMethod, "AutoLoadMoreHook", "hook:ubsMethod").intercept { chain ->
                    if (ConfigManager.snapshot().isAutoLoadMoreEnabled) true else chain.proceed()
                }
                XposedCompat.log(
                    "[AutoLoadMoreHook] hook INSTALLED: " +
                        "${ubsMethod.declaringClass.name}.${ubsMethod.name}()",
                )
            }

            symbols.configMethod?.let { configMethod ->
                RuntimeHooks.builder(mod, configMethod, "AutoLoadMoreHook", "hook:configMethod").intercept { chain ->
                    if (ConfigManager.snapshot().isAutoLoadMoreEnabled) PRELOAD_NOT_SEE_THREAD_NUM else chain.proceed()
                }
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
