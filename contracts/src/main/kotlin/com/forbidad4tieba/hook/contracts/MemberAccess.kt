package com.forbidad4tieba.hook.contracts

/** Shared JVM reflection helpers; callers supply already selected member identities. */
object MemberAccess {
    fun findClassOrNull(name: String, cl: ClassLoader): Class<*>? =
        try { Class.forName(name, false, cl) } catch (_: ClassNotFoundException) { null }

    // 鍙嶅皠杈呭姪

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

    // 鏂规硶鍜屾瀯閫犲嚱鏁拌В鏋?
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
}
