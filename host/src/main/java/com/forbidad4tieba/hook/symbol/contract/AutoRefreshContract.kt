package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.AutoRefreshSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.AutoRefreshSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.HomeRefreshFlowScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns lifecycle suppression, initialization-cache targets and their capability. */
object AutoRefreshContract : SymbolContract("AutoRefresh") {
    val autoRefreshFlow = text("autoRefreshFlow")
    val autoRefreshCacheRestoreMethod = text("autoRefreshCacheRestoreMethod")
    private val lifecycle = SymbolDependencies(autoRefreshFlow)
    private val cacheAvailability = SymbolDependencies(autoRefreshCacheRestoreMethod)
    private val required = lifecycle + cacheAvailability

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        output[autoRefreshFlow] = runScanStep("AutoRefreshHook.Lifecycle", logger, scanErrors, null as String?) {
            HomeRefreshFlowScanner.scan(cl, logger)?.let(HomeRefreshFlowCodec::encode)
        }
        output[autoRefreshCacheRestoreMethod] = runScanStep("AutoRefreshHook.Cache", logger, scanErrors, null as String?) {
            AutoRefreshSymbolScanner.scanHomeCacheRestore(context, cl, logger)?.ownerMethodName
        }
    }

    fun resolveAutoRefreshSymbols(cl: ClassLoader, symbols: HookSymbols?): AutoRefreshSymbols? = try {
        if (symbols == null || required.missing(symbols).isNotEmpty()) {
            Diagnostics.log("[AutoRefreshHook] skipped: missing lifecycle/cache descriptors")
            null
        } else {
            val flow = HomeRefreshFlowCodec.restore(requireNotNull(symbols[autoRefreshFlow]), cl)
            val cacheRestore = requireNotNull(restoreCacheAvailability(symbols, cl)) { "Invalid home cache availability target" }
            AutoRefreshSymbols(flow, cacheRestore)
        }
    } catch (failure: Throwable) {
        Diagnostics.log("[AutoRefreshHook] symbol resolve failed: ${failure.message}")
        Diagnostics.log(failure)
        null
    }

    private fun restoreCacheAvailability(symbols: HookSymbols, cl: ClassLoader): Method? {
        val name = symbols[autoRefreshCacheRestoreMethod]?.takeIf(String::isNotBlank) ?: return null
        val owner = ScanReflection.safeFindClass(StableTiebaHookPoints.LOW_SCORE_SCHEDULER_CLASS, cl) ?: return null
        return owner.declaredMethods.singleOrNull { method ->
            method.name == name && !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers) &&
                method.returnType == java.lang.Boolean.TYPE && method.parameterTypes.contentEquals(arrayOf(String::class.java))
        }?.apply { isAccessible = true }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = linkedMapOf(
        HookFeatureKey.DISABLE_AUTO_REFRESH to CapabilityPolicy.statusFromMissing(required.missing(symbols)),
    )

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add("AutoRefreshHook.Lifecycle", "startup / foreground / initialization cache", lifecycle.checks(symbols))
        add("AutoRefreshHook.Cache", "${StableTiebaHookPoints.LOW_SCORE_SCHEDULER_CLASS}.${symbols[autoRefreshCacheRestoreMethod]}",
            cacheAvailability.checks(symbols))
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("AutoRefreshHook.Lifecycle", false, listOf(HookFeatureKey.DISABLE_AUTO_REFRESH)),
        PointOwner("AutoRefreshHook.Cache", false, listOf(HookFeatureKey.DISABLE_AUTO_REFRESH)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = try {
        symbols[autoRefreshFlow]?.let { HomeRefreshFlowCodec.restore(it, cl) }
        if (symbols[autoRefreshCacheRestoreMethod] != null) {
            requireNotNull(restoreCacheAvailability(symbols, cl)) { "Invalid home cache availability target" }
        }
        true
    } catch (failure: Throwable) {
        Diagnostics.log("[AutoRefreshHook] lifecycle/cache restore failed: ${failure.message}")
        false
    }
}
