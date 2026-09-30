package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.CommentFilterTargets
import com.forbidad4tieba.hook.symbol.model.CommentProtocol
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.CommentFilterSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.CommentFilterSymbolScanner.Path
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

object CommentFilterContract : SymbolContract("CommentFilter") {
    val pageNative = text("commentFilterPageNative")
    val pageJson = text("commentFilterPageJson")
    val floorNative = text("commentFilterFloorNative")
    val floorJson = text("commentFilterFloorJson")
    val protocol = text("commentFilterProtocol")
    private val paths = linkedMapOf(
        pageNative to Path.PAGE_NATIVE, pageJson to Path.PAGE_JSON,
        floorNative to Path.FLOOR_NATIVE, floorJson to Path.FLOOR_JSON,
    )
    private val required = SymbolDependencies(pageNative, pageJson, floorNative, floorJson, protocol)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) {
        output[protocol] = scan.runScanStep("CommentLevelFilter.Protocol", scan.logger, scan.scanErrors, null as String?) {
            CommentProtocol(scan.cl)
            "wire-copy-v1"
        }
        paths.forEach { (field, path) ->
            output[field] = scan.runScanStep("CommentLevelFilter.${path.name}", scan.logger, scan.scanErrors, null as String?) {
                CommentFilterSymbolScanner.scan(path, scan.cl, scan.logger)
            }
        }
    }

    fun resolve(cl: ClassLoader, symbols: HookSymbols?): CommentFilterTargets? {
        if (symbols == null || required.missing(symbols).isNotEmpty()) return null
        return try {
            check(symbols[protocol] == "wire-copy-v1")
            CommentFilterTargets(paths.map { (field, path) ->
                CommentFilterSymbolScanner.restore(checkNotNull(symbols[field]), path, cl) to (path.protocol == CommentFilterSymbolScanner.FLOOR)
            }, CommentProtocol(cl))
        } catch (failure: Throwable) {
            Diagnostics.log("[CommentLevelFilter] invalid targets: ${failure.message}")
            null
        }
    }

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        // A missing path is a valid negative cache, not a reason to keep rescanning all features.
        return runCatching {
            symbols[protocol]?.let { check(it == "wire-copy-v1"); CommentProtocol(cl) }
            paths.forEach { (field, path) -> symbols[field]?.let { CommentFilterSymbolScanner.restore(it, path, cl) } }
        }.isSuccess
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        mapOf(HookFeatureKey.COMMENT_LEVEL_FILTER to required.requiredStatus(symbols))

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        paths.forEach { (field, path) -> add("CommentLevelFilter.${path.name}", symbols[field].orEmpty(), listOf(field.check(symbols))) }
        add("CommentLevelFilter.Protocol", "Wire builders and comment fields", listOf(protocol.check(symbols)))
    }.build()

    internal override val pointOwners = listOf(PointOwner("CommentLevelFilter.", true, listOf(HookFeatureKey.COMMENT_LEVEL_FILTER)))
}
