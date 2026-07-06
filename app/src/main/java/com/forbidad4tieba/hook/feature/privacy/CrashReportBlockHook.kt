package com.forbidad4tieba.hook.feature.privacy

import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Blocks host crash / exception report initialization and upload entry points.
 */
object CrashReportBlockHook {
    private const val TAG = "[CrashReportBlockHook]"
    private val installed = AtomicBoolean(false)

    private data class MethodTarget(
        val className: String,
        val methodName: String,
        val returnValue: Any?,
        val parameterTypeNames: List<String> = emptyList(),
        val staticMethod: Boolean,
    )

    private val CRASH_REPORT_TARGETS = arrayOf(
        MethodTarget(
            className = "com.baidu.searchbox.logsystem.basic.Loki",
            methodName = "init",
            returnValue = null,
            parameterTypeNames = listOf("android.content.Context"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.logsystem.basic.Loki",
            methodName = "init",
            returnValue = null,
            parameterTypeNames = listOf(
                "android.content.Context",
                "com.baidu.searchbox.logsystem.basic.javacrash.BaseUncaughtExceptionHandler",
            ),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.logsystem.basic.Loki",
            methodName = "initNative",
            returnValue = null,
            parameterTypeNames = listOf("android.content.Context"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.logsystem.basic.Loki",
            methodName = "initNative",
            returnValue = null,
            parameterTypeNames = listOf("android.content.Context", "boolean"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.logsystem.basic.Loki",
            methodName = "initService",
            returnValue = null,
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.logsystem.basic.Loki",
            methodName = "initService",
            returnValue = null,
            parameterTypeNames = listOf("com.baidu.searchbox.logsystem.basic.LogSystemProcessor"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.logsystem.basic.Loki",
            methodName = "retryUpload",
            returnValue = null,
            parameterTypeNames = listOf("android.content.Context"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.logsystem.basic.Loki",
            methodName = "setEnableNativeJavaStacktrace",
            returnValue = null,
            parameterTypeNames = listOf("boolean"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.crashpad.ZwCrashpad",
            methodName = "doInitGeneric",
            returnValue = null,
            parameterTypeNames = listOf("android.content.Context", "java.lang.String"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.crashpad.ZwCrashpad",
            methodName = "setEnabled",
            returnValue = null,
            parameterTypeNames = listOf("boolean"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.crashpad.ZwCrashpad",
            methodName = "setCollectJavaStackTraceEnabled",
            returnValue = null,
            parameterTypeNames = listOf("boolean"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.crashpad.ZwCrashpad",
            methodName = "setCrashKeyValue",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.String", "java.lang.String"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.crashpad.ZwCrashpad",
            methodName = "setCuid",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.String"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.crashpad.ZwCrashpad",
            methodName = "setEmulator",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.String"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.crashpad.ZwCrashpad",
            methodName = "setStatisticParam",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.String"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.crashpad.ZeusLogUploader",
            methodName = "UploadLogDirectory",
            returnValue = false,
            parameterTypeNames = listOf(
                "java.lang.String",
                "java.lang.String",
                "boolean",
                "com.baidu.crashpad.ZeusLogUploader\$OnFinishedListener",
            ),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.crashpad.ZeusLogUploader",
            methodName = "setEnabled",
            returnValue = null,
            parameterTypeNames = listOf("boolean"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.webkit.sdk.dumper.ZeusCrashHandler",
            methodName = "init",
            returnValue = null,
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.webkit.sdk.dumper.ZeusCrashHandler",
            methodName = "logException",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.Throwable"),
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.webkit.sdk.dumper.ZeusCrashHandler",
            methodName = "onJavaCrash",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.Thread", "java.lang.Throwable"),
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.webkit.sdk.dumper.ZeusLogUploader",
            methodName = "UploadLogDirectory",
            returnValue = false,
            parameterTypeNames = listOf(
                "java.lang.String",
                "java.lang.String",
                "boolean",
                "com.baidu.webkit.sdk.dumper.ZeusLogUploader\$OnFinishedListener",
            ),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.webkit.sdk.dumper.ZeusLogUploader",
            methodName = "setEnabled",
            returnValue = null,
            parameterTypeNames = listOf("boolean"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.yy.sdk.crashreportbaidu.CrashHandler",
            methodName = "init",
            returnValue = null,
            parameterTypeNames = listOf("*"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.yy.sdk.crashreportbaidu.CrashHandler",
            methodName = "initNativeHandler",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.String"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.yy.sdk.crashreportbaidu.CrashHandler",
            methodName = "b",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.Throwable"),
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.disasterrecovery.jnicrash.NativeCrashCapture",
            methodName = "init",
            returnValue = null,
            parameterTypeNames = listOf("android.content.Context", "*", "boolean"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.disasterrecovery.jnicrash.NativeCrashCapture",
            methodName = "beginNativeCrash",
            returnValue = null,
            staticMethod = true,
        ),
        MethodTarget(
            className = "com.baidu.disasterrecovery.jnicrash.NativeCrashCapture",
            methodName = "uncaughtNativeCrash",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.String", "int", "int"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "org.chromium.base.JavaExceptionReporter",
            methodName = "installHandler",
            returnValue = null,
            parameterTypeNames = listOf("boolean"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "org.chromium.base.JavaExceptionReporter",
            methodName = "reportException",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.Throwable"),
            staticMethod = true,
        ),
        MethodTarget(
            className = "org.chromium.base.JavaExceptionReporter",
            methodName = "reportStackTrace",
            returnValue = null,
            parameterTypeNames = listOf("java.lang.String"),
            staticMethod = true,
        ),
    )

    fun hook(cl: ClassLoader) {
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        var totalInstalled = 0
        for (target in CRASH_REPORT_TARGETS) {
            val clazz = XposedCompat.findClassOrNull(target.className, cl) ?: continue
            val method = resolveExactMethod(clazz, target, cl) ?: continue
            try {
                method.isAccessible = true
                mod.hook(method).intercept { target.returnValue }
                totalInstalled++
            } catch (t: Throwable) {
                XposedCompat.logD { "$TAG hook ${target.className}.${target.methodName} skipped: ${t.message}" }
            }
        }

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled")
        } else {
            XposedCompat.logD("$TAG no crash report methods found in this version")
        }
    }

    private fun resolveExactMethod(clazz: Class<*>, target: MethodTarget, cl: ClassLoader): Method? {
        val parameterTypes = target.parameterTypeNames.map { typeName ->
            if (typeName == "*") return@map null
            resolveClass(typeName, cl) ?: run {
                XposedCompat.logD { "$TAG parameter class NOT FOUND: ${target.className}.${target.methodName} $typeName" }
                return null
            }
        }.toTypedArray()
        val method = if (target.parameterTypeNames.any { it == "*" }) {
            clazz.declaredMethods.firstOrNull { candidate ->
                candidate.name == target.methodName &&
                    candidate.parameterTypes.size == parameterTypes.size &&
                    candidate.parameterTypes.zip(parameterTypes).all { (actual, expected) ->
                        expected == null || actual == expected
                    }
            } ?: run {
                XposedCompat.logD { "$TAG method NOT FOUND: ${target.className}.${target.methodName}" }
                return null
            }
        } else {
            try {
                clazz.getDeclaredMethod(target.methodName, *parameterTypes.requireNoNulls())
            } catch (_: NoSuchMethodException) {
                XposedCompat.logD { "$TAG method NOT FOUND: ${target.className}.${target.methodName}" }
                return null
            }
        }
        if (Modifier.isStatic(method.modifiers) != target.staticMethod) {
            XposedCompat.logD { "$TAG method skipped: static mismatch ${target.className}.${target.methodName}" }
            return null
        }
        return method
    }

    private fun resolveClass(typeName: String, cl: ClassLoader): Class<*>? {
        return when (typeName) {
            "*" -> null
            "void" -> Void.TYPE
            "boolean" -> Boolean::class.javaPrimitiveType
            "int" -> Int::class.javaPrimitiveType
            "long" -> Long::class.javaPrimitiveType
            "android.content.Context" -> android.content.Context::class.java
            "java.lang.String" -> String::class.java
            "java.lang.Thread" -> Thread::class.java
            "java.lang.Throwable" -> Throwable::class.java
            else -> XposedCompat.findClassOrNull(typeName, cl)
        }
    }
}
