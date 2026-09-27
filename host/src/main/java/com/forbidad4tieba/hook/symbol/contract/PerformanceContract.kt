package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.PerformanceAbSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import com.forbidad4tieba.hook.symbol.status.PerformanceAbStatus
import java.lang.reflect.Method

/** Owns the cached descriptors and host rules for this capability. */
object PerformanceContract : SymbolContract("Performance") {
    val performanceAbMethods = texts("performanceAbMethods", preserveEmpty = false)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val performanceAbMethods = runScanStep(
            "PerformanceAB", logger, scanErrors, emptyList<String>(),
        ) {
            PerformanceAbSymbolScanner.scan(context, cl, logger)
        }

        output[PerformanceContract.performanceAbMethods] = performanceAbMethods
    }

    fun resolvePerformanceAbSymbols(cl: ClassLoader, symbols: HookSymbols): Map<String, Method> =
        PerformanceAbSymbolScanner.restore(cl, symbols[PerformanceContract.performanceAbMethods])

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = PerformanceAbStatus.features(symbols)
    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = PerformanceAbStatus.hookPoints(symbols)

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!PerformanceAbSymbolScanner.isCacheValid(cl, symbols[PerformanceContract.performanceAbMethods])) return false
        return true
    }
}
