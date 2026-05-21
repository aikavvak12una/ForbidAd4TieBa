package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object PbEarlyAdBlockHook {
    private data class MethodTarget(
        val name: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
    )

    @Volatile private var hooked = false

    fun hook(cl: ClassLoader, symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols()) {
        if (!ConfigManager.isAdBlockEnabled) {
            XposedCompat.log("[PbEarlyAdBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val className = symbols?.pbEarlyAdInsertClass?.takeIf { it.isNotBlank() }
            val specs = symbols?.pbEarlyAdInsertMethodSpecs.orEmpty()
            if (className == null || specs.isEmpty()) {
                resetHooked()
                XposedCompat.log("[PbEarlyAdBlockHook] skipped: missing scan symbols")
                return
            }

            val targetClass = XposedCompat.findClassOrNull(className, cl)
            if (targetClass == null) {
                resetHooked()
                XposedCompat.log("[PbEarlyAdBlockHook] class NOT FOUND: $className")
                return
            }

            var installed = 0
            for (spec in specs.distinct()) {
                val target = parseMethodTarget(spec)
                if (target == null) {
                    XposedCompat.logD("[PbEarlyAdBlockHook] invalid method spec: $spec")
                    continue
                }
                val method = resolveMethod(targetClass, target, cl)
                if (method == null) {
                    XposedCompat.logD("[PbEarlyAdBlockHook] method not resolved: $spec")
                    continue
                }
                val returnsSparseArray = android.util.SparseArray::class.java.isAssignableFrom(method.returnType)
                mod.hook(method).intercept { chain ->
                    if (ConfigManager.isAdBlockEnabled) {
                        return@intercept blockedReturnValue(returnsSparseArray)
                    }
                    chain.proceed()
                }
                installed++
            }

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[PbEarlyAdBlockHook] no methods installed")
                return
            }
            XposedCompat.log("[PbEarlyAdBlockHook] hooks INSTALLED: class=$className, count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbEarlyAdBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun blockedReturnValue(returnsSparseArray: Boolean): Any? {
        return if (returnsSparseArray) android.util.SparseArray<Any>() else null
    }

    private fun parseMethodTarget(spec: String): MethodTarget? {
        val parts = spec.split('|', limit = 3)
        if (parts.size != 3) return null
        val name = parts[0].takeIf { it.isNotBlank() } ?: return null
        val returnTypeName = parts[1].takeIf { it.isNotBlank() } ?: return null
        val paramTypeNames = if (parts[2].isBlank()) {
            emptyList()
        } else {
            parts[2].split(',').filter { it.isNotBlank() }
        }
        return MethodTarget(name, returnTypeName, paramTypeNames)
    }

    private fun resolveMethod(targetClass: Class<*>, target: MethodTarget, cl: ClassLoader): Method? {
        val returnType = resolveType(target.returnTypeName, cl) ?: return null
        val paramTypes = target.paramTypeNames.map { typeName ->
            resolveType(typeName, cl) ?: return null
        }.toTypedArray()
        return try {
            targetClass.getDeclaredMethod(target.name, *paramTypes).takeIf { method ->
                Modifier.isStatic(method.modifiers) &&
                    (method.returnType == returnType || returnType.isAssignableFrom(method.returnType))
            }?.apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private fun resolveType(typeName: String, cl: ClassLoader): Class<*>? {
        return when (typeName) {
            "void" -> Void.TYPE
            "boolean" -> Boolean::class.javaPrimitiveType
            "int" -> Int::class.javaPrimitiveType
            else -> XposedCompat.findClassOrNull(typeName, cl)
        }
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
