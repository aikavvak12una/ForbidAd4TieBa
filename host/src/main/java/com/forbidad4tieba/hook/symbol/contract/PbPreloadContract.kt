package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbPreloadSymbols
import com.forbidad4tieba.hook.symbol.model.PbPreloadTargets
import com.forbidad4tieba.hook.symbol.scan.PbForcePreloadSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object PbPreloadContract : SymbolContract("PbPreload") {
    val pbPreloadProviderMethodSpec = text("pbPreloadProviderMethodSpec")
    val pbPreloadCardGetterMethodSpec = text("pbPreloadCardGetterMethodSpec")
    val pbPreloadPageStateMutableField = text("pbPreloadPageStateMutableField")
    val pbPreloadPageStateFlowField = text("pbPreloadPageStateFlowField")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val pbPreload = runScanStep(
            "PbForcePreloadHook",
            logger,
            scanErrors,
            PbPreloadSymbols(),
        ) {
            PbForcePreloadSymbolScanner.scan(context, cl, logger)
        }

        output[PbPreloadContract.pbPreloadProviderMethodSpec] = pbPreload.providerMethodSpec
        output[PbPreloadContract.pbPreloadCardGetterMethodSpec] = pbPreload.cardGetterMethodSpec
        output[PbPreloadContract.pbPreloadPageStateMutableField] = pbPreload.pageStateMutableField
        output[PbPreloadContract.pbPreloadPageStateFlowField] = pbPreload.pageStateFlowField
    }

    fun resolvePbPreloadTargets(cl: ClassLoader, symbols: HookSymbols): PbPreloadTargets? =
        PbForcePreloadSymbolScanner.restore(
            cl, symbols[PbPreloadContract.pbPreloadProviderMethodSpec], symbols[PbPreloadContract.pbPreloadCardGetterMethodSpec],
            symbols[PbPreloadContract.pbPreloadPageStateMutableField], symbols[PbPreloadContract.pbPreloadPageStateFlowField],
        )

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PbForcePreloadHook.Provider",
            symbols[PbPreloadContract.pbPreloadProviderMethodSpec] ?: "",
            listOf(
                PbPreloadContract.pbPreloadProviderMethodSpec.check(symbols),
            ),
        )
        add(
            "PbForcePreloadHook.CardGetter",
            symbols[PbPreloadContract.pbPreloadCardGetterMethodSpec] ?: "",
            listOf(PbPreloadContract.pbPreloadCardGetterMethodSpec.check(symbols)),
        )
        add(
            "PbForcePreloadHook.PageState",
            "PageBrowserViewModel.${symbols[PbPreloadContract.pbPreloadPageStateMutableField]}/${symbols[PbPreloadContract.pbPreloadPageStateFlowField]}",
            listOf(
                PbPreloadContract.pbPreloadPageStateMutableField.check(symbols),
                PbPreloadContract.pbPreloadPageStateFlowField.check(symbols),
            ),
        )
    }.build()

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!PbForcePreloadSymbolScanner.isCacheValid(
                cl, symbols[PbPreloadContract.pbPreloadProviderMethodSpec], symbols[PbPreloadContract.pbPreloadCardGetterMethodSpec],
                symbols[PbPreloadContract.pbPreloadPageStateMutableField], symbols[PbPreloadContract.pbPreloadPageStateFlowField],
            )) return false
        return true
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
