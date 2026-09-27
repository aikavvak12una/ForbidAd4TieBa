package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.TrackingTarget
import com.forbidad4tieba.hook.symbol.scan.TrackingSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import com.forbidad4tieba.hook.symbol.status.TrackingStatus
import java.lang.reflect.Method

/** Owns the cached descriptors and host rules for this capability. */
object TrackingContract : SymbolContract("Tracking") {
    val trackingMethods = texts("trackingMethods", preserveEmpty = false)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val trackingMethods = runScanStep("Tracking", logger, scanErrors, emptyList<String>()) {
            TrackingSymbolScanner.scan(context, cl, logger)
        }

        output[TrackingContract.trackingMethods] = trackingMethods
    }

    fun resolveTrackingSymbols(cl: ClassLoader, symbols: HookSymbols): Map<TrackingTarget, Method> =
        TrackingSymbolScanner.restore(cl, symbols[TrackingContract.trackingMethods])

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        out[HookFeatureKey.DISABLE_MONITOR_SYNC_COMPONENTS] = TrackingStatus.feature(symbols)
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = TrackingStatus.hookPoints(symbols)

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!TrackingSymbolScanner.isCacheValid(cl, symbols[TrackingContract.trackingMethods])) return false
        return true
    }
}
