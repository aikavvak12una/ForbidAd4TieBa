package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PbCommentAutoLoadSymbols

/** Advance the native prefetch threshold; the host retains all paging and lifecycle decisions. */
object PbCommentAutoLoadHook {
    private const val PRELOAD_NOT_SEE_COMMENT_NUM = 20
    private var batch: PbCommentBatchLoader? = null

    internal fun hook(targets: PbCommentAutoLoadSymbols) {
        if (!ConfigManager.snapshot().isAutoLoadMoreEnabled) return
        val mod = XposedCompat.module ?: return
        try {
            RuntimeHooks.builder(mod, targets.configMethod, "PbCommentAutoLoadHook", "hook:configMethod").intercept { chain ->
                if (ConfigManager.snapshot().isAutoLoadMoreEnabled) PRELOAD_NOT_SEE_COMMENT_NUM else chain.proceed()
            }
            XposedCompat.log("[PbCommentAutoLoadHook] native threshold INSTALLED: " +
                "${targets.configMethod.declaringClass.name}.${targets.configMethod.name}, threshold=$PRELOAD_NOT_SEE_COMMENT_NUM")
            if (batch == null) targets.batch?.let {
                PbCommentBatchLoader(it).also { loader -> loader.install(mod); batch = loader }
            }
        } catch (t: Throwable) {
            XposedCompat.log("[PbCommentAutoLoadHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
