package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object PbPerformanceModeHook {
    private data class BooleanOverride(
        val methodName: String,
        val value: Boolean,
        val enabled: () -> Boolean,
    )

    private val booleanOverrides = arrayOf(
        BooleanOverride("hybridPbOpt", true) { ConfigManager.isPbPerformanceModeEnabled },
        BooleanOverride("imagePerfLog", false) { ConfigManager.isPbPerformanceModeEnabled },
        BooleanOverride("isEnableHybridScrollLog", false) { ConfigManager.isPbPerformanceModeEnabled },
        BooleanOverride("isPbCommentFunAdABTest", false) {
            ConfigManager.isPbPerformanceModeEnabled || ConfigManager.isAdBlockEnabled
        },
        BooleanOverride("isPbPageBannerFunAdSdkTest", false) {
            ConfigManager.isPbPerformanceModeEnabled || ConfigManager.isAdBlockEnabled
        },
    )

    @Volatile private var hooked = false

    fun hook(cl: ClassLoader) {
        if (!hasEnabledOverride()) {
            XposedCompat.log("[PbPerformanceModeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val helperClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS, cl)
            if (helperClass == null) {
                resetHooked()
                XposedCompat.log("[PbPerformanceModeHook] class NOT FOUND: ${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}")
                return
            }

            var installed = 0
            for (entry in booleanOverrides) {
                val methodName = entry.methodName
                val method = XposedCompat.findMethodOrNull(helperClass, methodName)
                if (method == null || !isStaticNoArgBoolean(method)) {
                    XposedCompat.log(
                        "[PbPerformanceModeHook] method NOT FOUND or invalid: " +
                            "${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}.$methodName()",
                    )
                    continue
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (entry.enabled()) {
                        return@intercept entry.value
                    }
                    chain.proceed()
                }
                installed++
            }

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[PbPerformanceModeHook] no methods installed")
                return
            }
            XposedCompat.log("[PbPerformanceModeHook] hooks INSTALLED: count=$installed/${booleanOverrides.size}")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbPerformanceModeHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hasEnabledOverride(): Boolean {
        return booleanOverrides.any { it.enabled() }
    }

    private fun isStaticNoArgBoolean(method: Method): Boolean {
        return Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.javaObjectType)
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
