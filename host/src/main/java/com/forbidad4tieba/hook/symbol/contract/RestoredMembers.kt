package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method

object RestoredMembers {
    internal data class CachedMethodSpec(
        val name: String,
        val returnTypeName: String,
        val parameterTypeNames: List<String>,
    )

    fun resolveMethodByCachedSpec(clazz: Class<*>, spec: String, expectedName: String): Method? {
        val parsed = parseCachedMethodSpec(spec) ?: return null
        if (parsed.name != expectedName) return null
        val paramTypes = parsed.parameterTypeNames.map { typeName ->
            resolveCachedClassName(typeName, clazz.classLoader) ?: return null
        }.toTypedArray()
        val method = ReflectionUtils.findMethodInHierarchy(clazz, parsed.name, *paramTypes) ?: return null
        return method.takeIf { it.returnType.name == parsed.returnTypeName }
    }

    internal fun parseCachedMethodSpec(raw: String): CachedMethodSpec? {
        val parts = raw.split('|', limit = 3)
        if (parts.size != 3) return null
        val name = parts[0].takeIf { it.isNotBlank() } ?: return null
        val returnType = parts[1].takeIf { it.isNotBlank() } ?: return null
        val params = parts[2].split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return CachedMethodSpec(name, returnType, params)
    }

    internal fun resolveCachedClassName(typeName: String, cl: ClassLoader?): Class<*>? {
        return when (typeName) {
            Void.TYPE.name -> Void.TYPE
            Boolean::class.javaPrimitiveType!!.name -> Boolean::class.javaPrimitiveType
            Byte::class.javaPrimitiveType!!.name -> Byte::class.javaPrimitiveType
            Char::class.javaPrimitiveType!!.name -> Char::class.javaPrimitiveType
            Short::class.javaPrimitiveType!!.name -> Short::class.javaPrimitiveType
            Int::class.javaPrimitiveType!!.name -> Int::class.javaPrimitiveType
            Long::class.javaPrimitiveType!!.name -> Long::class.javaPrimitiveType
            Float::class.javaPrimitiveType!!.name -> Float::class.javaPrimitiveType
            Double::class.javaPrimitiveType!!.name -> Double::class.javaPrimitiveType
            else -> runCatching { Class.forName(typeName, false, cl) }.getOrNull()
        }
    }
}
