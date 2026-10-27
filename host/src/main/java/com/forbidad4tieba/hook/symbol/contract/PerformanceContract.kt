package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget
import com.forbidad4tieba.hook.symbol.scan.PerformanceAbSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PerformanceRuntimeSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import com.forbidad4tieba.hook.symbol.status.PerformanceAbStatus
import com.forbidad4tieba.hook.symbol.status.PerformanceRuntimeStatus
import java.lang.reflect.Method

/** Owns the cached descriptors and host rules for this capability. */
object PerformanceContract : SymbolContract("Performance") {
    val performanceAbMethods = texts("performanceAbMethods", preserveEmpty = false)
    val runtimeMethods = texts("performanceRuntimeMethods", preserveEmpty = false)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val performanceAbMethods = runScanStep(
            "PerformanceAB", logger, scanErrors, emptyList<String>(),
        ) {
            PerformanceAbSymbolScanner.scan(context, cl, logger)
        }

        output[PerformanceContract.performanceAbMethods] = performanceAbMethods
        output[runtimeMethods] = runScanStep("PerformanceRuntime", logger, scanErrors, emptyList<String>()) {
            PerformanceRuntimeSymbolScanner.scan(context, cl, logger)
        }
    }

    fun resolvePerformanceAbSymbols(cl: ClassLoader, symbols: HookSymbols): Map<String, Method> =
        PerformanceAbSymbolScanner.restore(cl, symbols[PerformanceContract.performanceAbMethods])

    fun resolveRuntimeSymbols(cl: ClassLoader, symbols: HookSymbols): Map<PerformanceRuntimeTarget, Method> =
        PerformanceRuntimeSymbolScanner.restore(cl, symbols[runtimeMethods])

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        PerformanceAbStatus.features(symbols) + PerformanceRuntimeStatus.features(symbols)
    internal override fun points(symbols: HookSymbols): List<HookPointStatus> =
        PerformanceAbStatus.hookPoints(symbols) + PerformanceRuntimeStatus.hookPoints(symbols)

    internal override val pointOwners = listOf(
        PointOwner("PerformanceAB.${PerformanceAbTarget.COOKIE_REPEATED.methodName}", true, listOf(HookFeatureKey.COOKIE_SYNC_DEDUP)),
        PointOwner("PerformanceAB.${PerformanceAbTarget.PB_COMMENT_AD.methodName}", true, listOf(HookFeatureKey.BLOCK_AD_POST_PAGE)),
        PointOwner("PerformanceAB.${PerformanceAbTarget.PB_BANNER_AD.methodName}", true, listOf(HookFeatureKey.BLOCK_AD_POST_PAGE)),
    ) + PerformanceRuntimeTarget.entries.map {
        PointOwner("PerformanceRuntime.${it.name}", true, listOf(it.featureKey))
    }

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!PerformanceAbSymbolScanner.isCacheValid(cl, symbols[PerformanceContract.performanceAbMethods])) return false
        if (!PerformanceRuntimeSymbolScanner.isCacheValid(cl, symbols[runtimeMethods])) return false
        return true
    }
}
