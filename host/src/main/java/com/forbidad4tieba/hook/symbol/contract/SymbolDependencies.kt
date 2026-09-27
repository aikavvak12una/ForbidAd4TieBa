package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols

/** One declaration drives capability gates, diagnostics and partial-cache validation. */
internal class SymbolDependencies(vararg val fields: SymbolField<*>) {
    fun checks(symbols: HookSymbols): List<Pair<String, Boolean>> = fields.map { it.check(symbols) }
    fun missing(symbols: HookSymbols): List<String> = checks(symbols).filterNot { it.second }.map { it.first }
    fun anyPresent(symbols: HookSymbols): Boolean = fields.any { symbols[it] != it.default }
    fun requiredStatus(symbols: HookSymbols): HookFeatureStatus = missing(symbols).let { missing ->
        HookFeatureStatus(
            state = if (missing.isEmpty()) HookFeatureState.FULL else HookFeatureState.DISABLED,
            missingCritical = missing,
        )
    }

    operator fun plus(other: SymbolDependencies): SymbolDependencies = SymbolDependencies(*fields, *other.fields)
}
