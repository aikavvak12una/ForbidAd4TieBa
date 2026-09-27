package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.DefaultPopupSymbols
import com.forbidad4tieba.hook.symbol.model.FirstLikePopupTargets
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.DefaultPopupSymbolScanner
import com.forbidad4tieba.hook.symbol.status.DefaultPopupStatus
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method

/** Owns the cached descriptors and host rules for this capability. */
object DefaultPopupsContract : SymbolContract("DefaultPopups") {
    val defaultPopups = nested("defaultPopups", DefaultPopupSymbols(), DefaultPopupSymbols::fromJson, DefaultPopupSymbols::toJson)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val defaultPopups = runScanStep("DefaultPopups", logger, scanErrors, DefaultPopupSymbols()) {
            DefaultPopupSymbolScanner.scan(context, cl, logger)
        }

        output[DefaultPopupsContract.defaultPopups] = defaultPopups
    }

    fun resolveFirstLikePopupSymbols(cl: ClassLoader, symbols: HookSymbols): FirstLikePopupTargets? =
        DefaultPopupSymbolScanner.restoreFirstLike(cl, symbols[DefaultPopupsContract.defaultPopups])

    fun resolveNotificationGuideSymbols(cl: ClassLoader, symbols: HookSymbols): Method? =
        DefaultPopupSymbolScanner.restoreNotification(cl, symbols[DefaultPopupsContract.defaultPopups])

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = DefaultPopupStatus.features(symbols[defaultPopups])
    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = DefaultPopupStatus.hookPoints(symbols[defaultPopups])

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!DefaultPopupSymbolScanner.isCacheValid(cl, symbols[DefaultPopupsContract.defaultPopups])) return false
        return true
    }
}
