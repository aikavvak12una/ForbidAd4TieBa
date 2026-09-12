package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.TrackingTarget

internal object TrackingStatus {
    fun hookPoints(symbols: HookSymbols): List<HookPointStatus> {
        val found = symbols.trackingMethods.orEmpty().toSet()
        return TrackingTarget.entries.map { target ->
            val available = target.name in found
            HookPointStatus(
                name = "Tracking.${target.methodName}",
                state = if (available) HookPointState.FOUND else HookPointState.MISSING,
                missing = if (available) emptyList() else listOf("methodOrConsumer"),
                target = "${target.className}.${target.methodName}",
            )
        }
    }

    fun feature(symbols: HookSymbols): HookFeatureStatus {
        val missing = hookPoints(symbols).filter { it.state != HookPointState.FOUND }.map { it.name }
        return HookFeatureStatus(
            state = if (missing.isEmpty()) HookFeatureState.FULL else HookFeatureState.DISABLED,
            missingCritical = missing,
        )
    }
}
