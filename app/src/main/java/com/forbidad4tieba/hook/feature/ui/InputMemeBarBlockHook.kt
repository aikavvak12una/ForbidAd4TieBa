package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.InputMemeBarSymbols
import java.util.concurrent.atomic.AtomicBoolean

object InputMemeBarBlockHook {
    private val installed = AtomicBoolean(false)

    internal fun hook(targets: InputMemeBarSymbols) {
        if (!ConfigManager.isInputMemeBarHidden) {
            XposedCompat.log("[InputMemeBarBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!installed.compareAndSet(false, true)) {
            XposedCompat.logD("[InputMemeBarBlockHook] already installed, skip")
            return
        }
        try {
            mod.hook(targets.enableMethod).intercept { chain ->
                if (ConfigManager.isInputMemeBarHidden) false else chain.proceed()
            }
            XposedCompat.log(
                "[InputMemeBarBlockHook] hook INSTALLED: " +
                    "${targets.enableMethod.declaringClass.name}.${targets.enableMethod.name}" +
                    "(Context,InputShowType,boolean)",
            )
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[InputMemeBarBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
