package com.forbidad4tieba.hook.core

import io.github.libxposed.api.XposedModule

/**
 * API 101 module lifecycle holder and utility functions.
 *
 * Feature hooks should use [module] directly for hooking:
 * ```
 * XposedCompat.module?.hook(method)?.intercept { chain ->
 *     // chain.thisObject, chain.args, chain.proceed(args)
 * }
 * ```
 *
 * This file provides utilities for:
 * - Module reference management
 * - Structured logging
 * - Reflection helpers (field/method/class lookup)
 */
object XposedCompat {

    @Volatile
    var module: XposedModule? = null

    // ── Logging ─────────────────────────────────────────────────

    fun log(msg: String) {
        android.util.Log.i("TbHook", msg)
        module?.log(android.util.Log.INFO, "TbHook", msg)
    }

    fun logD(msg: String) {
        if (!com.forbidad4tieba.hook.BuildConfig.DEBUG) return
        android.util.Log.d("TbHook", msg)
        module?.log(android.util.Log.DEBUG, "TbHook", msg)
    }

    fun logW(msg: String) {
        android.util.Log.w("TbHook", msg)
        module?.log(android.util.Log.WARN, "TbHook", msg)
    }

    fun log(t: Throwable) {
        android.util.Log.e("TbHook", "Error", t)
        module?.log(android.util.Log.ERROR, "TbHook", android.util.Log.getStackTraceString(t))
    }

    // ── Class Resolution ────────────────────────────────────────

    fun findClassOrNull(name: String, cl: ClassLoader): Class<*>? =
        try { Class.forName(name, false, cl) } catch (_: ClassNotFoundException) { null }

    // ── Reflection Helpers ──────────────────────────────────────

    fun findField(clazz: Class<*>, fieldName: String): java.lang.reflect.Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldError(fieldName)
    }

    fun getObjectField(obj: Any, fieldName: String): Any? =
        findField(obj.javaClass, fieldName).get(obj)

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        var current: Class<*>? = obj.javaClass
        while (current != null) {
            for (method in current.declaredMethods) {
                if (method.name == methodName && method.parameterTypes.size == args.size) {
                    method.isAccessible = true
                    return method.invoke(obj, *args)
                }
            }
            current = current.superclass
        }
        throw NoSuchMethodError(methodName)
    }

    // ── Method/Constructor Resolution ───────────────────────────

    fun findMethodOrNull(
        className: String, cl: ClassLoader,
        methodName: String, vararg paramTypes: Class<*>,
    ): java.lang.reflect.Method? {
        val clazz = findClassOrNull(className, cl) ?: return null
        return findMethodOrNull(clazz, methodName, *paramTypes)
    }

    fun findMethodOrNull(
        clazz: Class<*>,
        methodName: String, vararg paramTypes: Class<*>,
    ): java.lang.reflect.Method? {
        return try {
            clazz.getDeclaredMethod(methodName, *paramTypes).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) { null }
    }

    fun findConstructorOrNull(
        className: String, cl: ClassLoader,
        vararg paramTypes: Class<*>,
    ): java.lang.reflect.Constructor<*>? {
        val clazz = findClassOrNull(className, cl) ?: return null
        return try {
            clazz.getDeclaredConstructor(*paramTypes).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) { null }
    }
}
