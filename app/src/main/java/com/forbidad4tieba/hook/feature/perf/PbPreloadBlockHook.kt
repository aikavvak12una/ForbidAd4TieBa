package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method

object PbPreloadBlockHook {
    @Volatile private var hooked = false

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isPbPreloadDisabled) {
            XposedCompat.log("[PbPreloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val pbFragmentClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_FRAGMENT_CLASS, cl)
            if (pbFragmentClass == null) {
                resetHooked()
                XposedCompat.log("[PbPreloadBlockHook] class NOT FOUND: ${StableTiebaHookPoints.PB_FRAGMENT_CLASS}")
                return
            }
            val method = resolvePreloadMethod(pbFragmentClass)
            if (method == null) {
                resetHooked()
                XposedCompat.log(
                    "[PbPreloadBlockHook] method NOT FOUND: " +
                        "${StableTiebaHookPoints.PB_FRAGMENT_CLASS}.${StableTiebaHookPoints.METHOD_ON_PRE_LOAD}",
                )
                return
            }

            mod.hook(method).intercept { chain ->
                if (ConfigManager.isPbPreloadDisabled) {
                    return@intercept null
                }
                chain.proceed()
            }
            XposedCompat.log("[PbPreloadBlockHook] hook INSTALLED: ${StableTiebaHookPoints.PB_FRAGMENT_CLASS}.${method.name}")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbPreloadBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolvePreloadMethod(pbFragmentClass: Class<*>): Method? {
        val matches = pbFragmentClass.declaredMethods.filter { method ->
            method.name == StableTiebaHookPoints.METHOD_ON_PRE_LOAD &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1
        }
        return matches.singleOrNull()?.apply { isAccessible = true }
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
