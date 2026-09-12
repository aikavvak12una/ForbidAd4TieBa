package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

object NotificationGuideBlockHook {
    private val installed = AtomicBoolean(false)

    fun hook(tryShow: Method) {
        val mod = XposedCompat.module ?: return
        if (!installed.compareAndSet(false, true)) return
        try {
            // False means no guide was shown, so callers retain their normal success feedback.
            mod.hook(tryShow).intercept { false }
            XposedCompat.log("[NotificationGuideBlockHook] hook INSTALLED: " + tryShow)
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[NotificationGuideBlockHook] install failed: " + t.message)
            XposedCompat.log(t)
        }
    }
}
