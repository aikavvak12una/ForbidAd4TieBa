package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.PbFallingScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal object PbFallingSymbolScanner {
    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbFallingScanSymbols {
        val match = ScanReflection.runRules(candidates, cl, listOf(PbFallingViewRule()), logger, "pbFalling")
            ?: return PbFallingScanSymbols()
        val parts = match.methodName.split(",")
        if (parts.size < 3) return PbFallingScanSymbols(viewClass = match.className)
        return PbFallingScanSymbols(
            viewClass = match.className,
            initMethod = parts[0],
            showMethod = parts[1],
            clearMethod = parts[2],
        )
    }

}
