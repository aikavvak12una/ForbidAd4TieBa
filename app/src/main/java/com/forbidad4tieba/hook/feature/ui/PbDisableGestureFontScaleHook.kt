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
        val dispatchMethod = targets.dispatchMethod

        try {
            if (!installDispatchHook(mod, dispatchMethod)) {
                XposedCompat.logD(
                    "[PbDisableGestureFontScaleHook] already installed: " +
                        ReflectionUtils.methodSignature(dispatchMethod),
                )
                return
            }
            XposedCompat.log(
                "[PbDisableGestureFontScaleHook] hook INSTALLED: " +
                    "${dispatchMethod.declaringClass.name}.${dispatchMethod.name}(MotionEvent)",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbDisableGestureFontScaleHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installDispatchHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        mod.hook(method).intercept { false }
        return true
    }
}
