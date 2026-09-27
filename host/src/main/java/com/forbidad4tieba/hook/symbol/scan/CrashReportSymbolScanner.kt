package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object CrashReportSymbolScanner {
    const val HANDLER_CLASS = "com.yy.sdk.crashreportbaidu.CrashHandler"

    fun scan(cl: ClassLoader, logger: ScanLogger?): String? {
        val owner = ScanReflection.safeFindClass(HANDLER_CLASS, cl) ?: return null
        val entry = uniqueSemanticCandidate("CrashReport.UncaughtException", ScanDexQueries.methods(owner.name, logger).filter {
            !Modifier.isStatic(it.modifiers) && it.methodName == "uncaughtException" &&
                it.returnTypeName == "void" && it.paramTypeNames == listOf("java.lang.Thread", "java.lang.Throwable")
        }, logger) ?: return null
        return selectReportMethod(owner, logger) { method ->
            ScanDexQueries.method(method, logger)?.let { dex ->
                entry.invokes.any { it.descriptor == dex.descriptor } &&
                    dex.calls(owner.name, "generateDump") && dex.calls(owner.name, "crashGenFinishCallback")
            } == true
        }?.name
    }

    internal fun selectReportMethod(owner: Class<*>, logger: ScanLogger?, hasReportEvidence: (Method) -> Boolean): Method? {
        if (!Thread.UncaughtExceptionHandler::class.java.isAssignableFrom(owner)) return null
        return uniqueSemanticCandidate("CrashReport.ExceptionReport", owner.declaredMethods.filter {
            isReportMethod(it) && hasReportEvidence(it)
        }, logger)
    }

    internal fun isReportMethod(method: Method): Boolean =
        !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(Throwable::class.java))
}
