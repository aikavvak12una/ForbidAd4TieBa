package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.lowend.LowEndConfigSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.LowEndConfigSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object LowEndContract : SymbolContract("LowEnd") {
    val lowEndConfig = nested("lowEndConfig", LowEndConfigSymbols(), LowEndConfigSymbols::fromJson, LowEndConfigSymbols::toJson)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val lowEndConfig = runScanStep("LowEndConfig", logger, scanErrors, LowEndConfigSymbols()) {
            LowEndConfigSymbolScanner.scan(context, cl, logger)
        }

        output[LowEndContract.lowEndConfig] = lowEndConfig
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        out[HookFeatureKey.FORCE_LOW_END_DEVICE_CONFIG] = symbols[LowEndContract.lowEndConfig].featureStatus()
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = symbols[lowEndConfig].hookPoints()

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!symbols[LowEndContract.lowEndConfig].isCacheValid(cl)) return false
        return true
    }
}
