package com.forbidad4tieba.hook.feature.ui

import android.view.ScaleGestureDetector
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

object PbDisableGestureFontScaleHook {
    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()

    fun hook(
        cl: ClassLoader,
        symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols(),
    ) {
        val mod = XposedCompat.module ?: return
        val listenerClassName = symbols?.pbGestureScaleListenerClass
        val onScaleMethodName = symbols?.pbGestureScaleListenerOnScaleMethod
        if (listenerClassName.isNullOrBlank() || onScaleMethodName.isNullOrBlank()) {
            XposedCompat.log(
                "[PbDisableGestureFontScaleHook] skipped: missing symbol " +
                    "pbGestureScaleListenerClass/pbGestureScaleListenerOnScaleMethod",
            )
            return
        }

        try {
            val listenerClass = XposedCompat.findClassOrNull(listenerClassName, cl)
            if (listenerClass == null) {
                XposedCompat.log("[PbDisableGestureFontScaleHook] class NOT FOUND: $listenerClassName")
                return
            }

            val onScaleMethod = listenerClass.declaredMethods.firstOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == onScaleMethodName &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == ScaleGestureDetector::class.java
            }
            if (onScaleMethod == null) {
                XposedCompat.log(
                    "[PbDisableGestureFontScaleHook] method NOT FOUND: " +
                        "$listenerClassName.$onScaleMethodName(ScaleGestureDetector)",
                )
                return
            }
            onScaleMethod.isAccessible = true
            if (!installOnScaleHook(mod, onScaleMethod)) {
                XposedCompat.logD(
                    "[PbDisableGestureFontScaleHook] already installed: " +
                        ReflectionUtils.methodSignature(onScaleMethod),
                )
                return
            }
            XposedCompat.log(
                "[PbDisableGestureFontScaleHook] hook INSTALLED: " +
                    "${listenerClass.name}.${onScaleMethod.name}(ScaleGestureDetector)",
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
