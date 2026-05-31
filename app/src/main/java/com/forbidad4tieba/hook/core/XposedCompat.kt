package com.forbidad4tieba.hook.core

import com.forbidad4tieba.hook.config.ConfigManager
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap

/**
 * 杩欓噷鏀?API 101 妯″潡鐢熷懡鍛ㄦ湡鎸佹湁澶勫拰宸ュ叿鍑芥暟銆? *
 * 鍔熻兘 hook 鐩存帴浣跨敤 [module] 瀹夎锛? * ```
 * XposedCompat.module?.hook(method)?.intercept { chain ->
 *     // 杩欓噷鍙敤 chain.thisObject銆乧hain.args銆乧hain.proceed(args)
 * }
 * ```
 *
 * 杩欓噷鎻愪緵杩欎簺宸ュ叿锛? * - 妯″潡寮曠敤绠＄悊
 * - 缁撴瀯鍖栨棩蹇? * - 鍙嶅皠杈呭姪锛屽寘鍚瓧娈点€佹柟娉曞拰绫绘煡鎵? */
object XposedCompat {

    @Volatile
    var module: XposedModule? = null

    private val installInfoOnce = ConcurrentHashMap.newKeySet<String>()

    // 鏃ュ織

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
