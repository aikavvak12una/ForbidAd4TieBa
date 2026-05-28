package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.symbol.model.PbGestureScaleSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object PbDisableGestureFontScaleHook {
    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()

    internal fun hook(targets: PbGestureScaleSymbols) {
        val mod = XposedCompat.module ?: return
        val onScaleMethod = targets.onScaleMethod

        try {
            if (!installOnScaleHook(mod, onScaleMethod)) {
                XposedCompat.logD(
                    "[PbDisableGestureFontScaleHook] already installed: " +
                        ReflectionUtils.methodSignature(onScaleMethod),
                )
                return
            }
            XposedCompat.log(
                "[PbDisableGestureFontScaleHook] hook INSTALLED: " +
                    "${onScaleMethod.declaringClass.name}.${onScaleMethod.name}(ScaleGestureDetector)",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbDisableGestureFontScaleHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installOnScaleHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        mod.hook(method).intercept { true }
        return true
    }
}
