package com.forbidad4tieba.hook.core

import com.forbidad4tieba.hook.config.ConfigManager
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap

/**
 * 这里放 API 101 模块生命周期持有处和工具函数。
 *
 * 功能 hook 直接使用 [module] 安装：
 * ```
 * XposedCompat.module?.hook(method)?.intercept { chain ->
 *     // 这里可用 chain.thisObject、chain.args、chain.proceed(args)
 * }
 * ```
 *
 * 这里提供这些工具：
 * - 模块引用管理
 * - 结构化日志
 * - 反射辅助，包含字段、方法和类查找
 */
object XposedCompat {

    @Volatile
    var module: XposedModule? = null

    private val installInfoOnce = ConcurrentHashMap.newKeySet<String>()

    // 日志

    fun log(msg: String) {
        if (isSuccessfulHookInstallLog(msg)) {
            if (!installInfoOnce.add(msg)) return
            val priority = if (ConfigManager.shouldOutputDetailedLogs()) {
                android.util.Log.DEBUG
            } else {
                android.util.Log.INFO
            }
            android.util.Log.println(priority, "TbHook", msg)
            module?.log(priority, "TbHook", msg)
            return
        }
        if (isStaticDispatchLog(msg)) {
            if (!ConfigManager.shouldOutputDetailedLogs()) return
            if (!installInfoOnce.add(msg)) return
            android.util.Log.d("TbHook", msg)
            module?.log(android.util.Log.DEBUG, "TbHook", msg)
            return
        }
        if (!ConfigManager.shouldOutputDetailedLogs()) {
            if (!isReleaseKeyInfo(msg)) return
        }
        android.util.Log.i("TbHook", msg)
        module?.log(android.util.Log.INFO, "TbHook", msg)
    }

    fun logD(msg: String) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        android.util.Log.d("TbHook", msg)
        module?.log(android.util.Log.DEBUG, "TbHook", msg)
    }

    inline fun logD(msg: () -> String) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        logD(msg())
    }

    fun logW(msg: String) {
        android.util.Log.w("TbHook", msg)
        module?.log(android.util.Log.WARN, "TbHook", msg)
    }

    fun log(t: Throwable) {
        if (!ConfigManager.shouldOutputDetailedLogs()) {
            val summary = "${t.javaClass.name}: ${t.message.orEmpty()}"
            android.util.Log.e("TbHook", summary)
            module?.log(android.util.Log.ERROR, "TbHook", summary)
            return
        }
        android.util.Log.e("TbHook", "Error", t)
        module?.log(android.util.Log.ERROR, "TbHook", android.util.Log.getStackTraceString(t))
    }

    private fun isSuccessfulHookInstallLog(msg: String): Boolean {
        if (msg.contains("FAILED", ignoreCase = true) || msg.contains("no hooks installed", ignoreCase = true)) {
            return false
        }
        return msg.contains("hook INSTALLED", ignoreCase = true) ||
            msg.contains("hooks INSTALLED", ignoreCase = true)
    }

    private fun isStaticDispatchLog(msg: String): Boolean {
        return msg.contains("All static hooks dispatched", ignoreCase = true)
    }

    private fun isReleaseKeyInfo(msg: String): Boolean {
        return msg.contains("[CustomPostModelScoreStats] auto percentile effective", ignoreCase = true) ||
            msg.contains("[PrivateReadReceiptBlockHook]", ignoreCase = true) ||
            msg.contains("failed", ignoreCase = true) ||
            msg.contains("error", ignoreCase = true) ||
            msg.contains("exception", ignoreCase = true) ||
            msg.contains("abort", ignoreCase = true) ||
            msg.contains("unsupported", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true)
    }

    // 类解析

    fun findClassOrNull(name: String, cl: ClassLoader): Class<*>? =
        try { Class.forName(name, false, cl) } catch (_: ClassNotFoundException) { null }

    // 反射辅助

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

    // 方法和构造函数解析

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
