package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.SymbolContracts
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics

internal object HookSymbolStatusFormatter {
    fun formatFeatureStatusLines(
        statusMap: Map<String, HookFeatureStatus>,
        featureKeys: List<String>,
    ): List<String> {
        return featureKeys.map { key ->
            val status = statusMap[key] ?: HookFeatureStatus()
            val critical = if (status.missingCritical.isEmpty()) "-" else status.missingCritical.joinToString(",")
            val optional = if (status.missingOptional.isEmpty()) "-" else status.missingOptional.joinToString(",")
            "Feature[$key] state=${status.state} critical=$critical optional=$optional"
        }
    }

    fun formatHookPointStatusLines(symbols: HookSymbols?): List<String> =
        collectHookPointStatuses(symbols).map(HookPointStatus::formatLine)

    fun collectHookPointStatuses(symbols: HookSymbols?): List<HookPointStatus> {
        if (symbols == null) return listOf(HookPointStatus("SymbolCache", HookPointState.MISSING, listOf("symbols")))
        return SymbolContracts.all.flatMap { it.points(symbols) } + symbols.scanErrors.map { error ->
            val (name, detail) = HookSymbolScanDiagnostics.splitScanError(error)
            HookPointStatus(name, HookPointState.ERROR, listOf("exception"), detail)
        }
    }
}
