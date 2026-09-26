package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PerformanceAbSymbolScanner {
    private const val OWNER = StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): List<String> {
        val paths = listOfNotNull(context.applicationInfo?.sourceDir) +
            context.applicationInfo?.splitSourceDirs.orEmpty()
        return HookSymbolScanSession.withDexKitBridge(paths, logger) { source ->
            val methods = source.bridge.getClassData(OWNER)?.methods.orEmpty()
            val helper = ScanReflection.safeFindClass(OWNER, cl) ?: return@withDexKitBridge emptyList()
            PerformanceAbTarget.entries.mapNotNull { target ->
                scanSubStep("PerformanceAB.${target.methodName}", logger, null as String?) {
                    val candidates = methods.filter {
                        it.methodName == target.methodName && Modifier.isStatic(it.modifiers) &&
                            it.paramTypeNames.isEmpty() && it.returnTypeName == "boolean"
                    }
                    val method = candidates.singleOrNull()
                    // A retained AB wrapper with no callers is not a working hook point.
                    val hasConsumer = method?.callers.orEmpty().any { it.declaredClassName != OWNER }
                    if (method == null || !hasConsumer || !isValidMethod(helper.getDeclaredMethod(target.methodName))) {
                        HookSymbolScanDiagnostics.log(
                            logger,
                            "PerformanceAB.${target.methodName}: unavailable candidates=${candidates.size} consumer=$hasConsumer",
                        )
                        null
                    } else {
                        target.methodName
                    }
                }
            }
        } ?: emptyList()
    }

    fun restore(cl: ClassLoader, names: List<String>?): Map<String, Method> {
        if (names.isNullOrEmpty()) return emptyMap()
        val helper = ScanReflection.safeFindClass(OWNER, cl) ?: return emptyMap()
        val allowed = PerformanceAbTarget.entries.map { it.methodName }.toSet()
        return names.mapNotNull { name ->
            scanSubStep("PerformanceAB.$name.Restore", null, null as Pair<String, Method>?) {
                check(name in allowed) { "unknown performance AB target: $name" }
                val method = helper.getDeclaredMethod(name)
                check(isValidMethod(method)) { "invalid performance AB signature: $name" }
                name to method.apply { isAccessible = true }
            }
        }.toMap()
    }

    fun isCacheValid(cl: ClassLoader, names: List<String>?): Boolean =
        names == null || restore(cl, names).size == names.distinct().size

    internal fun isValidMethod(method: Method): Boolean =
        Modifier.isStatic(method.modifiers) && method.parameterTypes.isEmpty() &&
            method.returnType == Boolean::class.javaPrimitiveType
}
