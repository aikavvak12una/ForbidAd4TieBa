package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.FirstLikePopupTargets
import java.util.concurrent.atomic.AtomicBoolean

object FirstLikePopupBlockHook {
    private val installed = AtomicBoolean(false)

    fun hook(targets: FirstLikePopupTargets) {
        val mod = XposedCompat.module ?: return
        if (!installed.compareAndSet(false, true)) return
        try {
            mod.hook(targets.parseToastMethod).intercept { chain ->
                if (targets.responseClass.isInstance(chain.thisObject) &&
                    FirstLikePopupPolicy.shouldBlock(chain.args.firstOrNull() as? String)
                ) null else chain.proceed()
            }
            XposedCompat.log("[FirstLikePopupBlockHook] hook INSTALLED: " + targets.parseToastMethod)
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[FirstLikePopupBlockHook] install failed: " + t.message)
            XposedCompat.log(t)
        }
    }
}
