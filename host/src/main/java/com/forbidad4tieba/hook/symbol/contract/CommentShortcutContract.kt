package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.scan.CommentShortcutSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

object CommentShortcutContract : SymbolContract("CommentShortcut") {
    val targets = text("commentShortcutTargets")
    private val required = SymbolDependencies(targets)
    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) {
        output[targets] = scan.runScanStep("CommentShortcut", scan.logger, scan.scanErrors, null as String?) {
            CommentShortcutSymbolScanner.scan(scan.logger)?.also { CommentShortcutTargets(scan.cl, it) }
        }
    }
    fun resolve(cl: ClassLoader, symbols: HookSymbols?): CommentShortcutTargets? {
        val spec = symbols?.get(targets) ?: return null
        return try { CommentShortcutTargets(cl, spec) } catch (failure: Throwable) {
            Diagnostics.log("[CommentShortcut] invalid targets: ${failure.message}")
            null
        }
    }
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        !required.anyPresent(symbols) || resolve(cl, symbols) != null
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        mapOf(HookFeatureKey.COMMENT_SHORTCUT to required.requiredStatus(symbols))
    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add("CommentShortcut", "native level icon beside host title", required.checks(symbols))
    }.build()
    internal override val pointOwners = listOf(PointOwner("CommentShortcut", false, listOf(HookFeatureKey.COMMENT_SHORTCUT)))
}
