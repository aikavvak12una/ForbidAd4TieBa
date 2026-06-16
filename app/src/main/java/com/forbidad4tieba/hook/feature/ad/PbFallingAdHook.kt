package com.forbidad4tieba.hook.feature.ad

import android.view.View
import com.forbidad4tieba.hook.symbol.model.PbFallingAdSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import com.forbidad4tieba.hook.utils.ViewExt
import java.lang.reflect.Method

/**
 * Blocks PB falling ads through the shared ad-block switch.
 *
 * Target classes and methods must come from HookSymbolResolver; runtime callbacks only intercept
 * resolved init/show/clear entry points.
 */
object PbFallingAdHook {
    @Volatile private var hooked = false
    @Volatile private var hooking = false

    internal fun hook(targets: PbFallingAdSymbols) {
        if (!tryBeginHook()) return
        var success = false

        try {
            val mod = XposedCompat.module ?: return
            val targetClass = targets.targetClass
            val initMethod = targets.initMethod
            val showMethod = targets.showMethod
            val clearMethod = targets.clearMethod

            val methods = listOfNotNull(initMethod, showMethod, clearMethod)
            if (methods.isEmpty()) {
                XposedCompat.log("[PbFallingAdHook] no usable methods resolved on ${targetClass.name}")
                return
            }

            initMethod?.let { hookInitMethod(mod, it) }
            showMethod?.let { hookShowMethod(mod, it) }
            clearMethod?.let { hookClearMethod(mod, it) }

            XposedCompat.log(
                "[PbFallingAdHook] hooks dispatched: class=${targetClass.name}, " +
                    "init=${initMethod?.name ?: "-"}, show=${showMethod?.name ?: "-"}, clear=${clearMethod?.name ?: "-"}",
            )
            success = true
        } catch (t: Throwable) {
            XposedCompat.log("[PbFallingAdHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        } finally {
            finishHook(success)
        }
    }

    private fun hookInitMethod(mod: io.github.libxposed.api.XposedModule, method: Method) {
        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isPbFallingAdBlockEnabled) return@intercept chain.proceed()
            squashSelf(chain.thisObject)
            Unit
        }
        XposedCompat.log("[PbFallingAdHook] hook INSTALLED: ${ReflectionUtils.methodSignature(method)}")
    }

    private fun hookShowMethod(mod: io.github.libxposed.api.XposedModule, method: Method) {
        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isPbFallingAdBlockEnabled) return@intercept chain.proceed()
            squashSelf(chain.thisObject)
            Unit
        }
        XposedCompat.log("[PbFallingAdHook] hook INSTALLED: ${ReflectionUtils.methodSignature(method)}")
    }

    private fun hookClearMethod(mod: io.github.libxposed.api.XposedModule, method: Method) {
        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isPbFallingAdBlockEnabled) return@intercept chain.proceed()
            squashSelf(chain.thisObject)
            Unit
        }
        XposedCompat.log("[PbFallingAdHook] hook INSTALLED: ${ReflectionUtils.methodSignature(method)}")
    }

    private fun squashSelf(target: Any?) {
        val view = target as? View ?: return
        view.setTag(ViewExt.TAG_EMPTY_VIEW, true)
        ViewExt.squashViewRemembering(view)
    }

    private fun tryBeginHook(): Boolean {
        synchronized(this) {
            if (hooked || hooking) return false
            hooking = true
            return true
        }
    }

    private fun finishHook(success: Boolean) {
        synchronized(this) {
            hooking = false
            if (success) hooked = true
        }
    }
}
