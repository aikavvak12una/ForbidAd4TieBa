package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat

/**
 * 开启并调整首页信息流预加载行为。
 */
object AutoLoadMoreHook {

    private const val PRELOAD_NOT_SEE_THREAD_NUM = 20

    fun hook(
        cl: ClassLoader,
        symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols(),
    ) {
        if (!ConfigManager.isAutoLoadMoreEnabled) {
            XposedCompat.log("[AutoLoadMoreHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        val configClassName = symbols?.autoLoadMoreConfigClass
        val configMethodName = symbols?.autoLoadMoreConfigMethod

        try {
            // 强制开启预加载实验。
            val ubsMethod = XposedCompat.findMethodOrNull(
                StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS,
                cl,
                StableTiebaHookPoints.METHOD_IS_HOME_PRE_LOAD_MORE_OPT,
            )
            if (ubsMethod != null) {
                mod.hook(ubsMethod).intercept { true }
                XposedCompat.log(
                    "[AutoLoadMoreHook] hook INSTALLED: " +
                        "${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}." +
                        "${StableTiebaHookPoints.METHOD_IS_HOME_PRE_LOAD_MORE_OPT}()",
                )
            } else {
                XposedCompat.log("[AutoLoadMoreHook] ${StableTiebaHookPoints.METHOD_IS_HOME_PRE_LOAD_MORE_OPT} NOT FOUND")
            }

            // 动态调整未读帖子数阈值。
            if (configClassName.isNullOrBlank() || configMethodName.isNullOrBlank()) {
                XposedCompat.log(
                    "[AutoLoadMoreHook] config tuning skipped: " +
                        "missing symbol autoLoadMoreConfigClass/autoLoadMoreConfigMethod",
                )
            } else {
                val configMethod = XposedCompat.findMethodOrNull(configClassName, cl, configMethodName)
                if (configMethod != null) {
                    mod.hook(configMethod).intercept { PRELOAD_NOT_SEE_THREAD_NUM }
                    XposedCompat.log(
                        "[AutoLoadMoreHook] hook INSTALLED: " +
                            "config=${configClassName}.${configMethod.name}(), " +
                            "threshold=$PRELOAD_NOT_SEE_THREAD_NUM",
                    )
                } else {
                    XposedCompat.log(
                        "[AutoLoadMoreHook] config method NOT FOUND: " +
                            "$configClassName.$configMethodName()",
                    )
                }
            }
        } catch (t: Throwable) {
            XposedCompat.log("[AutoLoadMoreHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
