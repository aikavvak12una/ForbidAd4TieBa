package com.forbidad4tieba.hook.symbol.contract

import android.webkit.WebView
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object CacheTargetValidation {

    internal const val TAG = "[HookSymbolResolver]"

    internal fun resolveCachedParameterClass(typeName: String, cl: ClassLoader): Class<*>? {
        return when (typeName) {
            "boolean" -> java.lang.Boolean.TYPE
            "byte" -> java.lang.Byte.TYPE
            "char" -> java.lang.Character.TYPE
            "short" -> java.lang.Short.TYPE
            "int" -> Integer.TYPE
            "long" -> java.lang.Long.TYPE
            "float" -> java.lang.Float.TYPE
            "double" -> java.lang.Double.TYPE
            else -> ScanReflection.safeFindClass(typeName, cl)
        }
    }

    internal fun findWebMethodInHierarchy(
        clazz: Class<*>,
        methodName: String,
        predicate: (Method) -> Boolean,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            val method = current.declaredMethods.firstOrNull { it.name == methodName && predicate(it) }
            if (method != null) return method
            current = current.superclass
        }
        return null
    }

    internal fun isStringLoadUrlMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == String::class.java
    }

    internal fun isGetUrlMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == String::class.java &&
            method.parameterTypes.isEmpty()
    }

    internal fun isGetInnerWebViewMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            WebView::class.java.isAssignableFrom(method.returnType) &&
            method.parameterTypes.isEmpty()
    }
}
