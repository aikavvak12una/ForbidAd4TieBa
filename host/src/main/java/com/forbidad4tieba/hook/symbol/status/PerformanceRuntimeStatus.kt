package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.PerformanceContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget

internal object PerformanceRuntimeStatus {
    fun hookPoints(symbols: HookSymbols): List<HookPointStatus> {
        val found = symbols[PerformanceContract.runtimeMethods].orEmpty().toSet()
        return PerformanceRuntimeTarget.entries.map { target ->
            HookPointStatus(
                name = "PerformanceRuntime.${target.name}",
                state = if (target.name in found) HookPointState.FOUND else HookPointState.MISSING,
                missing = if (target.name in found) emptyList() else listOf("methodOrConsumer"),
                target = "${target.className}.${target.methodName}(${target.parameterTypeNames.joinToString()})",
            )
        }
    }

    fun features(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val found = symbols[PerformanceContract.runtimeMethods].orEmpty().toSet()
        return PerformanceRuntimeTarget.entries.groupBy { it.featureKey }.mapValues { (_, targets) ->
            val missing = targets.filter { it.name !in found }.map { "PerformanceRuntime.${it.name}" }
            when {
                missing.isEmpty() -> HookFeatureStatus(HookFeatureState.FULL)
                missing.size == targets.size -> HookFeatureStatus(HookFeatureState.DISABLED, missingCritical = missing)
                else -> HookFeatureStatus(HookFeatureState.PARTIAL, missingOptional = missing)
            }
        }
    }
}
