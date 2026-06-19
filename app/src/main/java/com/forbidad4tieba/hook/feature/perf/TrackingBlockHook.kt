package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 在代码层阻断统计和埋点 SDK 的数据采集。
 *
 * 这类 SDK 会消耗 CPU、内存、磁盘 IO 和网络；拦住入口后，后续开销也不会继续产生。
 *
 * 由现有性能优化开关 isMonitorSyncComponentsDisabled 控制。
 */
object TrackingBlockHook {
    private const val TAG = "[TrackingBlockHook]"
    private val installed = AtomicBoolean(false)

    private data class MethodTarget(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val parameterTypeNames: List<String> = emptyList(),
        val staticMethod: Boolean,
    )

    private val TRACKING_TARGETS = arrayOf(
        MethodTarget(
            className = "com.baidu.searchbox.logsystem.basic.LokiService",
            methodName = "onStartCommand",
            returnTypeName = "int",
            parameterTypeNames = listOf("android.content.Intent", "int", "int"),
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.mobstat.StatService",
            methodName = "onEvent",
            returnTypeName = "void",
            parameterTypeNames = listOf(
                "android.content.Context",
                "java.lang.String",
                "java.lang.String",
                "int",
                "java.util.Map",
            ),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.mobstat.StatService",
            methodName = "onEvent",
            returnTypeName = "void",
            parameterTypeNames = listOf(
                "android.content.Context",
                "java.lang.String",
                "java.lang.String",
                "int",
            ),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.mobstat.StatService",
            methodName = "onEvent",
            returnTypeName = "void",
            parameterTypeNames = listOf("android.content.Context", "java.lang.String", "java.lang.String"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.mobstat.StatService",
            methodName = "onPageStart",
            returnTypeName = "void",
            parameterTypeNames = listOf("android.content.Context", "java.lang.String"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.mobstat.StatService",
            methodName = "onPageEnd",
            returnTypeName = "void",
            parameterTypeNames = listOf("android.content.Context", "java.lang.String"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.mobstat.StatService",
            methodName = "start",
            returnTypeName = "void",
            parameterTypeNames = listOf("android.content.Context"),
            staticMethod = true,
        ),
    )

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isMonitorSyncComponentsDisabled) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        var totalInstalled = 0
        for (target in TRACKING_TARGETS) {
            val clazz = XposedCompat.findClassOrNull(target.className, cl) ?: continue
            val method = resolveExactMethod(clazz, target, cl) ?: continue
            try {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (ConfigManager.isMonitorSyncComponentsDisabled) {
                        return@intercept nullReturnValue(method)
                    }
                    chain.proceed()
                }
                totalInstalled++
            } catch (t: Throwable) {
                XposedCompat.logD { "$TAG hook ${target.className}.${target.methodName} skipped: ${t.message}" }
            }
        }

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled")
        } else {
            XposedCompat.logD("$TAG no tracking SDK methods found in this version")
        }
    }

    private fun nullReturnValue(method: Method): Any? {
        return when (method.returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Void.TYPE -> null
            else -> null
        }
    }

    private fun resolveExactMethod(clazz: Class<*>, target: MethodTarget, cl: ClassLoader): Method? {
        val parameterTypes = target.parameterTypeNames.map { typeName ->
            resolveClass(typeName, cl) ?: run {
                XposedCompat.logD { "$TAG parameter class NOT FOUND: ${target.className}.${target.methodName} $typeName" }
                return null
            }
        }.toTypedArray()
        val method = try {
            clazz.getDeclaredMethod(target.methodName, *parameterTypes)
        } catch (_: NoSuchMethodException) {
            XposedCompat.logD { "$TAG method NOT FOUND: ${target.className}.${target.methodName}" }
            return null
        }
        val isStatic = Modifier.isStatic(method.modifiers)
        if (isStatic != target.staticMethod) {
            XposedCompat.logD { "$TAG method skipped: static mismatch ${target.className}.${target.methodName}" }
            return null
        }
        val returnType = resolveClass(target.returnTypeName, cl) ?: return null
        if (method.returnType != returnType) {
            XposedCompat.logD {
                "$TAG method skipped: return mismatch ${target.className}.${target.methodName} " +
                    "${method.returnType.name} != ${returnType.name}"
            }
            return null
        }
        return method
    }

    private fun resolveClass(typeName: String, cl: ClassLoader): Class<*>? {
        return when (typeName) {
            "void" -> Void.TYPE
            "boolean" -> Boolean::class.javaPrimitiveType
            "byte" -> Byte::class.javaPrimitiveType
            "char" -> Char::class.javaPrimitiveType
            "short" -> Short::class.javaPrimitiveType
            "int" -> Int::class.javaPrimitiveType
            "long" -> Long::class.javaPrimitiveType
            "float" -> Float::class.javaPrimitiveType
            "double" -> Double::class.javaPrimitiveType
            else -> XposedCompat.findClassOrNull(typeName, cl)
        }
    }
}
