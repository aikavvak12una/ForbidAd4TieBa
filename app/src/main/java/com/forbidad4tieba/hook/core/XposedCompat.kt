package com.forbidad4tieba.hook.core

import com.forbidad4tieba.hook.config.ConfigManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the API 101 module lifecycle object and shared Xposed helpers.
 *
 * Feature hooks install through [module]:
 * ```
 * XposedCompat.module?.hook(method)?.intercept { chain ->
 *     // chain.thisObject, chain.args and chain.proceed(args) are available here.
 * }
 * ```
 *
 * This object centralizes:
 * - module reference management
 * - structured logging
 * - reflection helpers for fields, methods, and classes
 */
object XposedCompat {
    private const val MODULE_LOG_TAG = "TbHook"
    private const val HOST_LOG_TAG = "TiebaHost"
    private const val MAX_LOGCAT_MESSAGE_CHARACTERS = 3_800

    @Volatile
    var module: XposedModule? = null
        private set

    private val installInfoOnce = ConcurrentHashMap.newKeySet<String>()

    fun attachModule(xposedModule: XposedModule) {
        module = xposedModule
    }

    fun interceptHook(
        featureId: String,
        executable: Executable,
        hooker: XposedInterface.Hooker,
    ): XposedInterface.HookHandle? {
        val mod = module ?: run {
            log("[XposedCompat] hook skipped, module unavailable: feature=$featureId")
            return null
        }
        return mod.hook(executable).intercept(hooker)
    }

    // Logging.

    fun log(msg: String) {
        if (isSuccessfulHookInstallLog(msg)) {
            if (!installInfoOnce.add(msg)) return
            val detailedLogging = ConfigManager.shouldOutputDetailedLogs()
            val priority = if (detailedLogging) {
                android.util.Log.DEBUG
            } else {
                android.util.Log.INFO
            }
            if (detailedLogging) recordModuleLog(priority, msg)
            android.util.Log.println(priority, MODULE_LOG_TAG, msg)
            module?.log(priority, MODULE_LOG_TAG, msg)
            return
        }
        if (isStaticDispatchLog(msg)) {
            if (!ConfigManager.shouldOutputDetailedLogs()) return
            if (!installInfoOnce.add(msg)) return
            recordModuleLog(android.util.Log.DEBUG, msg)
            android.util.Log.d(MODULE_LOG_TAG, msg)
            module?.log(android.util.Log.DEBUG, MODULE_LOG_TAG, msg)
            return
        }
        if (!ConfigManager.shouldOutputDetailedLogs()) {
            if (!isReleaseKeyInfo(msg)) return
        } else {
            recordModuleLog(android.util.Log.INFO, msg)
        }
        android.util.Log.i(MODULE_LOG_TAG, msg)
        module?.log(android.util.Log.INFO, MODULE_LOG_TAG, msg)
    }

    fun logD(msg: String) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        recordModuleLog(android.util.Log.DEBUG, msg)
        android.util.Log.d(MODULE_LOG_TAG, msg)
        module?.log(android.util.Log.DEBUG, MODULE_LOG_TAG, msg)
    }

    inline fun logD(msg: () -> String) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        logD(msg())
    }

    fun logW(msg: String) {
        if (ConfigManager.shouldOutputDetailedLogs()) {
            recordModuleLog(android.util.Log.WARN, msg)
        }
        android.util.Log.w(MODULE_LOG_TAG, msg)
        module?.log(android.util.Log.WARN, MODULE_LOG_TAG, msg)
    }

    fun log(t: Throwable) {
        if (!ConfigManager.shouldOutputDetailedLogs()) {
            val summary = "${t.javaClass.name}: ${t.message.orEmpty()}"
            android.util.Log.e(MODULE_LOG_TAG, summary)
            module?.log(android.util.Log.ERROR, MODULE_LOG_TAG, summary)
            return
        }
        val stackTrace = android.util.Log.getStackTraceString(t)
        recordModuleLog(android.util.Log.ERROR, stackTrace)
        android.util.Log.e(MODULE_LOG_TAG, "Error", t)
        module?.log(android.util.Log.ERROR, MODULE_LOG_TAG, stackTrace)
    }

    fun emitTiebaHostLog(
        priority: Int,
        tag: String,
        message: String,
    ) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        val line = "[$tag] $message"
        val boundedLine = if (line.length > MAX_LOGCAT_MESSAGE_CHARACTERS) {
            line.take(MAX_LOGCAT_MESSAGE_CHARACTERS - LOG_TRUNCATED_SUFFIX.length) +
                LOG_TRUNCATED_SUFFIX
        } else {
            line
        }
        android.util.Log.println(priority, HOST_LOG_TAG, boundedLine)
        module?.log(priority, HOST_LOG_TAG, boundedLine)
    }

    private fun recordModuleLog(priority: Int, message: String) {
        DetailedLogSession.recordModule(
            level = priorityName(priority),
            tag = MODULE_LOG_TAG,
            message = message,
        )
    }

    private fun priorityName(priority: Int): String {
        return when (priority) {
            android.util.Log.VERBOSE -> "VERBOSE"
            android.util.Log.DEBUG -> "DEBUG"
            android.util.Log.WARN -> "WARN"
            android.util.Log.ERROR -> "ERROR"
            android.util.Log.ASSERT -> "ASSERT"
            else -> "INFO"
        }
    }

    private const val LOG_TRUNCATED_SUFFIX = "...[truncated]"

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
            msg.contains("failed", ignoreCase = true) ||
            msg.contains("error", ignoreCase = true) ||
            msg.contains("exception", ignoreCase = true) ||
            msg.contains("abort", ignoreCase = true) ||
            msg.contains("unsupported", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true)
    }

    // 绫昏В鏋?
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
