package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.scan.YyBackgroundSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointState
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

object YyBackgroundContract : SymbolContract("YyBackground") {
    val targets = texts("yyBackgroundTargets")
    val readiness = text("yyBackgroundReadiness")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) {
        val result = scan.runScanStep("YyBackground", scan.logger, scan.scanErrors, emptyList<String>() to null) {
            YyBackgroundSymbolScanner.scan(scan.cl, scan.logger)
        }
        output[targets] = result.first
        output[readiness] = result.second
    }

    fun resolve(cl: ClassLoader, symbols: HookSymbols) =
        YyBackgroundSymbolScanner.resolve(cl, symbols[targets], symbols[readiness])

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader) =
        YyBackgroundSymbolScanner.isCacheValid(cl, symbols[targets], symbols[readiness])

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> {
        val names = symbols[targets].orEmpty()
        val validNames = names.size == names.distinct().size && names.all { name -> YyBackgroundTarget.entries.any { it.name == name } }
        return (YyBackgroundTarget.entries.map { it.name to (validNames && it.name in names) } +
            ("Readiness" to !symbols[readiness].isNullOrBlank())).map { (name, found) ->
            HookPointStatus("YyBackground.$name", if (found) HookPointState.FOUND else HookPointState.MISSING,
                if (found) emptyList() else listOf(name), "YY background initialization: $name")
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val missing = points(symbols).filter { it.state != HookPointState.FOUND }.map { it.name }
        return mapOf(HookFeatureKey.DISABLE_YY_BACKGROUND to HookFeatureStatus(
            if (missing.isEmpty()) HookFeatureState.FULL else HookFeatureState.DISABLED, missingCritical = missing))
    }

    internal override val pointOwners = listOf(PointOwner("YyBackground.", true, listOf(HookFeatureKey.DISABLE_YY_BACKGROUND)))
}
