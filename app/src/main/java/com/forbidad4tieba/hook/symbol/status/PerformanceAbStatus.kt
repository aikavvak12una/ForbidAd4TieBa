package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTargets

internal object PerformanceAbStatus {
    fun hookPoints(symbols: HookSymbols): List<HookPointStatus> {
        val found = symbols.performanceAbMethods.orEmpty().toSet()
        return PerformanceAbTarget.entries.map { target ->
            HookPointStatus(
                name = "PerformanceAB.${target.methodName}",
                state = if (target.methodName in found) HookPointState.FOUND else HookPointState.MISSING,
                missing = if (target.methodName in found) emptyList() else listOf("methodOrConsumer"),
                target = "${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}.${target.methodName}()",
            )
        }
    }

    fun features(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val found = symbols.performanceAbMethods.orEmpty().toSet()
        return PerformanceAbTargets.featureRequirements.mapValues { (key, required) ->
            val missing = required.filter { it.methodName !in found }
                .map { "PerformanceAB.${it.methodName}" }.toMutableList()
            if (key == HookFeatureKey.FORCE_PB_PRELOAD && symbols.pbPreloadProviderMethodSpec.isNullOrBlank()) {
                missing += "pbPreloadProviderMethodSpec"
            }
            if (key == HookFeatureKey.FORCE_PB_PRELOAD && symbols.pbPreloadCardGetterMethodSpec.isNullOrBlank()) {
                missing += "pbPreloadCardGetterMethodSpec"
            }
            if (key == HookFeatureKey.FORCE_PB_PRELOAD && symbols.pbPreloadPageStateMutableField.isNullOrBlank()) {
                missing += "pbPreloadPageStateMutableField"
            }
            if (key == HookFeatureKey.FORCE_PB_PRELOAD && symbols.pbPreloadPageStateFlowField.isNullOrBlank()) {
                missing += "pbPreloadPageStateFlowField"
            }
            HookFeatureStatus(
                state = if (missing.isEmpty()) HookFeatureState.FULL else HookFeatureState.DISABLED,
                missingCritical = missing,
            )
        }
    }
}
