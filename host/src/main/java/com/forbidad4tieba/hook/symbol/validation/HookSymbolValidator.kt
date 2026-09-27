package com.forbidad4tieba.hook.symbol.validation

import com.forbidad4tieba.hook.symbol.contract.SymbolContracts
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics

/** Cache orchestration only. Each contract owns the validation of its descriptors. */
internal object HookSymbolValidator {
    fun isUsable(symbols: HookSymbols, cl: ClassLoader): Boolean = SymbolContracts.all.all { contract ->
        try {
            contract.isCacheValid(symbols, cl)
        } catch (error: Throwable) {
            HookSymbolScanDiagnostics.log(null, "${contract.id} cache validation failed: ${HookSymbolScanDiagnostics.formatScanException(error)}")
            false
        }
    }
}
