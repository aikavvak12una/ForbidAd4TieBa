package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbPreloadSymbols
import com.forbidad4tieba.hook.symbol.model.PbPreloadBodySymbols
import com.forbidad4tieba.hook.symbol.model.PbPreloadTargets
import com.forbidad4tieba.hook.symbol.scan.PbForcePreloadSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object PbPreloadContract : SymbolContract("PbPreload") {
    val pbPreloadProviderMethodSpec = text("pbPreloadProviderMethodSpec")
    val pbPreloadCardGetterMethodSpec = text("pbPreloadCardGetterMethodSpec")
    val pbPreloadPageStateMutableField = text("pbPreloadPageStateMutableField")
    val pbPreloadPageStateFlowField = text("pbPreloadPageStateFlowField")
    val pbPreloadCommitMethodSpec = text("pbPreloadCommitMethodSpec")
    val pbPreloadHybridMethodSpec = text("pbPreloadHybridMethodSpec")
    val pbPreloadResultSuccessField = text("pbPreloadResultSuccessField")
    val pbPreloadResultModelField = text("pbPreloadResultModelField")
    val pbPreloadResultStageField = text("pbPreloadResultStageField")
    val pbPreloadResultResponseField = text("pbPreloadResultResponseField")
    val pbPreloadModelThreadField = text("pbPreloadModelThreadField")
    val pbPreloadModelWebViewField = text("pbPreloadModelWebViewField")
    private val card = SymbolDependencies(pbPreloadProviderMethodSpec, pbPreloadCardGetterMethodSpec)
    private val page = SymbolDependencies(pbPreloadPageStateMutableField, pbPreloadPageStateFlowField)
    private val body = SymbolDependencies(pbPreloadCommitMethodSpec, pbPreloadHybridMethodSpec,
        pbPreloadResultSuccessField, pbPreloadResultModelField, pbPreloadResultStageField,
        pbPreloadResultResponseField, pbPreloadModelThreadField, pbPreloadModelWebViewField)
    internal val required = card + page + body

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val pbPreload = runScanStep(
            "PbForcePreloadHook",
            logger,
            scanErrors,
            PbPreloadSymbols(),
        ) {
            PbForcePreloadSymbolScanner.scan(context, cl, logger)
        }

        output[pbPreloadProviderMethodSpec] = pbPreload.providerMethodSpec
        output[pbPreloadCardGetterMethodSpec] = pbPreload.cardGetterMethodSpec
        output[pbPreloadPageStateMutableField] = pbPreload.pageStateMutableField
        output[pbPreloadPageStateFlowField] = pbPreload.pageStateFlowField
        output[pbPreloadCommitMethodSpec] = pbPreload.body.commit
        output[pbPreloadHybridMethodSpec] = pbPreload.body.hybrid
        output[pbPreloadResultSuccessField] = pbPreload.body.success
        output[pbPreloadResultModelField] = pbPreload.body.model
        output[pbPreloadResultStageField] = pbPreload.body.stage
        output[pbPreloadResultResponseField] = pbPreload.body.response
        output[pbPreloadModelThreadField] = pbPreload.body.thread
        output[pbPreloadModelWebViewField] = pbPreload.body.webView
    }

    fun resolvePbPreloadTargets(cl: ClassLoader, symbols: HookSymbols): PbPreloadTargets? =
        PbForcePreloadSymbolScanner.restore(cl, read(symbols))

    private fun read(symbols: HookSymbols): PbPreloadSymbols = PbPreloadSymbols(
        symbols[pbPreloadProviderMethodSpec], symbols[pbPreloadCardGetterMethodSpec],
        symbols[pbPreloadPageStateMutableField], symbols[pbPreloadPageStateFlowField],
        PbPreloadBodySymbols(symbols[pbPreloadCommitMethodSpec], symbols[pbPreloadHybridMethodSpec],
            symbols[pbPreloadResultSuccessField], symbols[pbPreloadResultModelField],
            symbols[pbPreloadResultStageField], symbols[pbPreloadResultResponseField],
            symbols[pbPreloadModelThreadField], symbols[pbPreloadModelWebViewField]),
    )

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add("PbForcePreloadHook.Card", "${symbols[pbPreloadProviderMethodSpec]}/${symbols[pbPreloadCardGetterMethodSpec]}", card.checks(symbols))
        add(
            "PbForcePreloadHook.PageState",
            "PageBrowserViewModel.${symbols[pbPreloadPageStateMutableField]}/${symbols[pbPreloadPageStateFlowField]}",
            page.checks(symbols),
        )
        add("PbForcePreloadHook.Body", "${symbols[pbPreloadCommitMethodSpec]}/${symbols[pbPreloadHybridMethodSpec]}", body.checks(symbols))
    }.build()

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        PbForcePreloadSymbolScanner.isCacheValid(cl, read(symbols))

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
