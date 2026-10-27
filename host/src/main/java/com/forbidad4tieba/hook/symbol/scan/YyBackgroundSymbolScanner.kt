package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.symbol.model.YyBackgroundSymbols
import com.forbidad4tieba.hook.symbol.model.YyBackgroundTarget
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object YyBackgroundSymbolScanner {
    fun scan(cl: ClassLoader, logger: ScanLogger?): Pair<List<String>, String?> {
        val manager = ScanDexQueries.methods(YyBackgroundTarget.MANAGER, logger)
        val constructor = manager.singleOrNull { it.isConstructor && it.paramTypeNames.isEmpty() }
        val readiness = uniqueSemanticCandidate("YyBackground.Readiness", manager.filter {
            isReadinessShape(it.modifiers, it.returnTypeName, it.paramTypeNames) &&
                it.calls("com.yy.transvod.player.VodPlayer", "isLibReady") &&
                constructor?.invokes?.any { call -> call.descriptor == it.descriptor } == true
        }, logger)?.getMethodInstance(cl)?.name
        val names = YyBackgroundTarget.entries.mapNotNull { target ->
            scanSubStep("YyBackground.${target.name}", logger, null as String?) {
                val methods = if (target.isCallback) ScanDexQueries.methods(target.owner, logger) else manager
                val found = methods.filter {
                    it.methodName == target.methodName && it.returnTypeName == target.result &&
                        it.paramTypeNames == target.params && !Modifier.isStatic(it.modifiers) &&
                        (target.anchor == null || target.anchor in it.usingStrings)
                }
                if (uniqueSemanticCandidate("YyBackground.${target.name}", found, logger) == null) return@scanSubStep null
                restoreMember(cl, target)
                target.name
            }
        }
        return names to readiness
    }

    internal fun isReadinessShape(modifiers: Int, result: String, params: List<String>) =
        !Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers) && result == "boolean" && params.isEmpty()

    fun restoreMember(cl: ClassLoader, target: YyBackgroundTarget): Executable {
        val owner = Class.forName(target.owner, false, cl)
        if (target == YyBackgroundTarget.CONSTRUCTOR) return owner.getDeclaredConstructor().apply { isAccessible = true }
        return owner.declaredMethods.singleOrNull {
            it.name == target.methodName && it.returnType.name == target.result &&
                it.parameterTypes.map { type -> type.name } == target.params && !Modifier.isStatic(it.modifiers) &&
                Modifier.isAbstract(it.modifiers) == target.isCallback
        }?.apply { isAccessible = true } ?: error("Invalid YY background target: $target")
    }

    fun restoreReadiness(cl: ClassLoader, name: String): Method =
        Class.forName(YyBackgroundTarget.MANAGER, false, cl).getDeclaredMethod(name).apply {
            check(isReadinessShape(modifiers, returnType.name, parameterTypes.map { it.name }))
            isAccessible = true
        }

    fun complete(names: List<String>?): Boolean = names != null &&
        names.size == YyBackgroundTarget.entries.size && names.toSet() == YyBackgroundTarget.entries.map { it.name }.toSet()

    fun resolve(cl: ClassLoader, names: List<String>?, readiness: String?): YyBackgroundSymbols? {
        if (!complete(names) || readiness.isNullOrBlank()) return null
        return scanSubStep("YyBackground.Restore", null, null) {
            YyBackgroundSymbols(YyBackgroundTarget.entries.associateWith { restoreMember(cl, it) }, restoreReadiness(cl, readiness))
        }
    }

    fun isCacheValid(cl: ClassLoader, names: List<String>?, readiness: String?): Boolean =
        scanSubStep("YyBackground.Cache", null, false) {
            check(names == null || names.size == names.distinct().size)
            names.orEmpty().forEach { restoreMember(cl, YyBackgroundTarget.valueOf(it)) }
            if (readiness != null) restoreReadiness(cl, readiness)
            true
        }
}
