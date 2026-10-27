package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.scan.SwanPreloadSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.scanSubStep
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method

object SwanPreloadContract : SymbolContract("SwanPreload") {
    val dispatch = text("swanPreloadDispatch")
    val schedule = text("swanPreloadSchedule")
    private val required = SymbolDependencies(dispatch, schedule)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) {
        val result = scan.runScanStep("SwanPreload", scan.logger, scan.scanErrors, null as String? to null) {
            SwanPreloadSymbolScanner.scan(scan.cl, scan.logger)
        }
        output[dispatch] = result.first
        output[schedule] = result.second
    }

    fun resolve(cl: ClassLoader, symbols: HookSymbols): List<Method>? {
        if (required.missing(symbols).isNotEmpty()) return null
        return scanSubStep("SwanPreload.Restore", null, null) {
            listOf(SwanPreloadSymbolScanner.restore(cl, symbols[dispatch]!!, false),
                SwanPreloadSymbolScanner.restore(cl, symbols[schedule]!!, true)).also {
                check(it[0].declaringClass == it[1].declaringClass)
            }
        }
    }

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        scanSubStep("SwanPreload.Cache", null, false) {
            val first = symbols[dispatch]?.let { SwanPreloadSymbolScanner.restore(cl, it, false) }
            val second = symbols[schedule]?.let { SwanPreloadSymbolScanner.restore(cl, it, true) }
            check(first == null || second == null || first.declaringClass == second.declaringClass)
            true
        }

    internal override fun capabilities(symbols: HookSymbols) =
        mapOf(HookFeatureKey.DISABLE_SWAN_PRELOAD to required.requiredStatus(symbols))

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add("SwanPreload.Dispatch", symbols[dispatch] ?: "preload next service", SymbolDependencies(dispatch).checks(symbols))
        add("SwanPreload.Schedule", symbols[schedule] ?: "preload before client binding", SymbolDependencies(schedule).checks(symbols))
    }.build()

    internal override val pointOwners = listOf(PointOwner("SwanPreload.", true, listOf(HookFeatureKey.DISABLE_SWAN_PRELOAD)))
}
