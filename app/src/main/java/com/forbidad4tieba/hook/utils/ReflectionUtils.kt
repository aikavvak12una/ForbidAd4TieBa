package com.forbidad4tieba.hook.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.lang.reflect.Method

/**
 * 多个 hook 类共用的反射工具。
 * 放在这里可以减少重复代码，并让行为保持一致。
 */
object ReflectionUtils {

    /**
     * 沿类继承链按名称和参数类型查找声明方法。
     *
     * 遇到 [Any] 也就是 java.lang.Object 就停止，避免扫到框架内部。
     * 返回的方法会设置为可访问。
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
     * 沿类继承链查找声明方法。
     * 方法需要同时匹配 [name] 和自定义 [predicate]。
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
     * 从 [Context] 里取宿主 [Activity]。
     * 最多向外拆 [maxDepth] 层 [ContextWrapper]。
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
     * 为 [Method] 生成便于阅读的唯一键。
     * 内容包含声明类、方法名和参数类型。
     */
    fun methodSignature(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.declaringClass.name}#${method.name}($params)"
    }
}
