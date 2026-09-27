package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.contracts.Diagnostics
import android.app.Service
import android.content.Context
import android.content.Intent
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.symbol.model.TrackingTarget
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object TrackingSymbolScanner {
    private const val TRACE_MANAGER = "com.baidu.searchbox.track.ui.TraceManager"

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): List<String> {
        val paths = listOfNotNull(context.applicationInfo?.sourceDir) +
            context.applicationInfo?.splitSourceDirs.orEmpty()
        return HookSymbolScanSession.withDexKitBridge(paths, logger) { source ->
            TrackingTarget.entries.mapNotNull { target ->
                scanSubStep("Tracking.${target.methodName}", logger, null as String?) {
                    restoreMethod(cl, target)
                    if (target == TrackingTarget.PAGE_TRACE) {
                        val registration = source.bridge.getClassData(TRACE_MANAGER)?.methods.orEmpty().filter {
                            it.methodName == "register" && Modifier.isPublic(it.modifiers) &&
                                !Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" &&
                                it.paramTypeNames == listOf("android.content.Context")
                        }.singleOrNull()
                        // One shared entry must own registration, so another SDK cannot bypass this hook.
                        val entry = registration?.callers.orEmpty().singleOrNull()
                        val hasConsumerChain = entry != null && entry.declaredClassName == target.className &&
                            entry.methodName == target.methodName && Modifier.isPublic(entry.modifiers) &&
                            !Modifier.isStatic(entry.modifiers) && entry.returnTypeName == "void" &&
                            entry.paramTypeNames == listOf("android.content.Context") && entry.callers.any {
                                !it.declaredClassName.startsWith("com.baidu.searchbox.track.")
                            }
                        if (!hasConsumerChain) {
                            HookSymbolScanDiagnostics.log(
                                logger, "Tracking.${target.methodName}: unavailable shared page trace registration chain",
                            )
                            return@scanSubStep null
                        }
                    }
                    // LokiService is entered by Android's Service lifecycle, not a Java call site.
                    target.name
                }
            }
        } ?: emptyList()
    }

    fun restore(cl: ClassLoader, names: List<String>?): Map<TrackingTarget, Method> =
        names.orEmpty().mapNotNull { name ->
            scanSubStep("Tracking.$name.Restore", null, null as Pair<TrackingTarget, Method>?) {
                val target = TrackingTarget.valueOf(name)
                target to restoreMethod(cl, target)
            }
        }.toMap()

    fun isCacheValid(cl: ClassLoader, names: List<String>?): Boolean =
        names == null || (names.size == names.distinct().size && restore(cl, names).size == names.size)

    private fun restoreMethod(cl: ClassLoader, target: TrackingTarget): Method {
        val owner = Class.forName(target.className, false, cl)
        val method = when (target) {
            TrackingTarget.PAGE_TRACE -> owner.getDeclaredMethod(target.methodName, Context::class.java)
            TrackingTarget.LOKI_SERVICE -> {
                check(Service::class.java.isAssignableFrom(owner)) { "Loki owner is not a Service" }
                owner.getDeclaredMethod(
                    target.methodName, Intent::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                )
            }
        }
        val expectedReturn = when (target) {
            TrackingTarget.LOKI_SERVICE -> Int::class.javaPrimitiveType
            TrackingTarget.PAGE_TRACE -> Void.TYPE
        }
        check(Modifier.isPublic(method.modifiers) && !Modifier.isStatic(method.modifiers) &&
            method.returnType == expectedReturn) { "invalid tracking signature: $target" }
        return method.apply { isAccessible = true }
    }
}
