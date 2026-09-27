package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.SymbolContracts
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy
import com.forbidad4tieba.hook.symbol.model.*

/** Aggregates declarations; feature-specific dependency policy stays with its contract. */
internal object HookFeatureStatusDeriver {
    val featureKeys: List<String> = HookFeatureKey.orderedKeys

    fun derive(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        SymbolContracts.all.forEach { contract ->
            contract.capabilities(symbols).forEach { (key, value) ->
                check(out.put(key, value) == null) { "Multiple capability owners: $key" }
            }
        }
        // Every capability has one declaration, including aggregate-only owners.
        SymbolContracts.all.forEach { contract ->
            contract.aggregates.keys.forEach { out[it] = HookFeatureStatus() }
        }
        check(out.keys == featureKeys.toSet()) { "Missing or unknown capability declaration" }
        HookSymbolStatusFormatter.collectHookPointStatuses(symbols).forEach { point ->
            if (point.isUnavailable()) SymbolContracts.featuresForPoint(point.name).forEach { key ->
                val current = out[key] ?: HookFeatureStatus()
                out[key] = current.copy(
                    state = if (current.state == HookFeatureState.DISABLED) HookFeatureState.DISABLED else HookFeatureState.PARTIAL,
                    missingOptional = (current.missingOptional + point.name).distinct(),
                )
            }
        }
        SymbolContracts.all.forEach { contract ->
            contract.aggregates.forEach { (key, children) ->
                out[key] = CapabilityPolicy.combineSubFeatureStatuses(children.map(out::getValue))
            }
        }
        return out
    }
}
