package com.forbidad4tieba.hook.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.lang.reflect.Method

/**
 * Shared reflection helpers used by multiple hook modules.
 */
object ReflectionUtils {

    /**
     * Finds a declared method by name and parameter types along the class hierarchy.
     * Stops before [Any] and returns an accessible method.
     */
    fun findMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        vararg paramTypes: Class<*>,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredMethod(name, *paramTypes)
                    .apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    /**
     * Finds a declared method whose name and custom [predicate] both match.
     */
    fun findMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        predicate: (Method) -> Boolean,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            val method = current.declaredMethods.firstOrNull { it.name == name && predicate(it) }
            if (method != null) {
                method.isAccessible = true
                return method
            }
            current = current.superclass
        }
        return null
    }

    /**
     * Extracts the host [Activity] from a [Context], unwrapping up to [maxDepth] [ContextWrapper]s.
     */
    fun findActivityFromContext(context: Context?, maxDepth: Int = 12): Activity? {
        var current = context
        var guard = 0
        while (current != null && guard < maxDepth) {
            if (current is Activity) return current
            current = if (current is ContextWrapper) current.baseContext else null
            guard++
        }
        return null
    }

    /**
     * Builds a compact readable signature for a [Method].
     */
    fun methodSignature(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.declaringClass.name}#${method.name}($params)"
    }
}
