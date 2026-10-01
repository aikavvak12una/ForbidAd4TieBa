package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.scan.InlineReplySymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

object InlineReplyContract : SymbolContract("InlineReplyRepair") {
    val targets = text("inlineReplyRepairTargets")
    private val required = SymbolDependencies(targets)
    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) {
        output[targets] = scan.runScanStep("InlineReplyRepair", scan.logger, scan.scanErrors, null as String?) {
            InlineReplySymbolScanner.scan(scan.cl, scan.logger)?.also { InlineReplyTargets(scan.cl, it) }
        }
    }
    fun resolve(cl: ClassLoader, symbols: HookSymbols?): InlineReplyTargets? {
        val spec = symbols?.get(targets) ?: return null
        return try { InlineReplyTargets(cl, spec) } catch (failure: Throwable) {
            Diagnostics.log("[InlineReplyRepair] invalid targets: ${failure.message}")
            null
        }
    }
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        !required.anyPresent(symbols) || resolve(cl, symbols) != null
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        mapOf(HookFeatureKey.INLINE_REPLY_REPAIR to required.requiredStatus(symbols))
    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add("InlineReplyRepair", "native floor request and inline preview", required.checks(symbols))
    }.build()
    internal override val pointOwners = listOf(PointOwner("InlineReplyRepair", false, listOf(HookFeatureKey.INLINE_REPLY_REPAIR)))
}
