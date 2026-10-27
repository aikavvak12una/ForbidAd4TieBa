package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method

internal object PerformanceRuntimeSymbolScanner {
    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): List<String> {
        val paths = listOfNotNull(context.applicationInfo?.sourceDir) + context.applicationInfo?.splitSourceDirs.orEmpty()
        return HookSymbolScanSession.withDexKitBridge(paths, logger) { source ->
            PerformanceRuntimeTarget.entries.mapNotNull { target ->
                scanSubStep("PerformanceRuntime.${target.name}", logger, null as String?) {
                    val methods = source.bridge.getClassData(target.className)?.methods.orEmpty()
                    val candidates = methods.filter { method ->
                        method.methodName == target.methodName &&
                            target.matchesSignature(method.modifiers, method.returnTypeName, method.paramTypeNames)
                    }
                    val method = candidates.singleOrNull()
                    val callerOwners = method?.callers.orEmpty().map { it.declaredClassName }.toSet()
                    val ownerStrings = methods.flatMap { it.usingStrings }.toSet()
                    if (!hasRequiredEvidence(target, candidates.size, callerOwners, ownerStrings)) {
                        HookSymbolScanDiagnostics.log(logger,
                            "PerformanceRuntime.${target.name}: unavailable candidates=${candidates.size} " +
                                "consumer=${callerOwners.any { it != target.className }} semantic=${target.semanticKey == null || target.semanticKey in ownerStrings}")
                        null
                    } else {
                        restoreMethod(cl, target)
                        target.name
                    }
                }
            }
        } ?: emptyList()
    }

    internal fun hasRequiredEvidence(
        target: PerformanceRuntimeTarget,
        candidateCount: Int,
        callerOwners: Set<String>,
        ownerStrings: Set<String>,
    ): Boolean = candidateCount == 1 && callerOwners.any { it != target.className } &&
        (target.semanticKey == null || target.semanticKey in ownerStrings)

    fun restore(cl: ClassLoader, names: List<String>?): Map<PerformanceRuntimeTarget, Method> =
        names.orEmpty().mapNotNull { name ->
            scanSubStep("PerformanceRuntime.$name.Restore", null, null as Pair<PerformanceRuntimeTarget, Method>?) {
                val target = PerformanceRuntimeTarget.valueOf(name)
                target to restoreMethod(cl, target)
            }
        }.toMap()

    private fun restoreMethod(cl: ClassLoader, target: PerformanceRuntimeTarget): Method {
        val owner = Class.forName(target.className, false, cl)
        return owner.declaredMethods.singleOrNull(target::matches)
            ?.apply { isAccessible = true }
            ?: error("invalid performance runtime signature: ${target.name}")
    }

    fun isCacheValid(cl: ClassLoader, names: List<String>?): Boolean =
        names == null || (names.size == names.distinct().size && restore(cl, names).size == names.size)
}
