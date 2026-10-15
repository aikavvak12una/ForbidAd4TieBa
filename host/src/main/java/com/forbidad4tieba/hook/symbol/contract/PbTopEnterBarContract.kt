package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbTopEnterBarScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbTopEnterBarSymbols
import com.forbidad4tieba.hook.symbol.scan.PbTopEnterBarSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

object PbTopEnterBarContract : SymbolContract("PbTopEnterBar") {
    val controllerClass = text("pbTopEnterBarControllerClass")
    val initializeMethod = text("pbTopEnterBarInitializeMethod")
    val containerField = text("pbTopEnterBarContainerField")
    val navigationBarField = text("pbTopEnterBarNavigationBarField")
    internal val required = SymbolDependencies(controllerClass, initializeMethod, containerField, navigationBarField)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val result = runScanStep("PbTopEnterBarBlockHook", logger, scanErrors, PbTopEnterBarScanSymbols()) {
            PbTopEnterBarSymbolScanner.scan(cl, logger)
        }
        output[controllerClass] = result.controllerClass
        output[initializeMethod] = result.initializeMethod
        output[containerField] = result.containerField
        output[navigationBarField] = result.navigationBarField
    }

    fun resolve(cl: ClassLoader, symbols: HookSymbols?): PbTopEnterBarSymbols? {
        if (symbols == null || !required.requiredStatus(symbols).isSupported()) {
            Diagnostics.log("[PbTopEnterBarBlockHook] skipped: incomplete scan symbols")
            return null
        }
        return try {
            PbTopEnterBarSymbolScanner.restore(
                PbTopEnterBarScanSymbols(
                    symbols[controllerClass], symbols[initializeMethod], symbols[containerField], symbols[navigationBarField],
                ), cl,
            ).also {
                if (it == null) Diagnostics.log("[PbTopEnterBarBlockHook] skipped: cached target structure changed")
            }
        } catch (failure: Throwable) {
            Diagnostics.log("[PbTopEnterBarBlockHook] symbol resolve FAILED: ${failure.message}")
            Diagnostics.log(failure)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        mapOf(HookFeatureKey.HIDE_PB_TOP_ENTER_BAR to required.requiredStatus(symbols))

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PbTopEnterBarBlockHook",
            "${symbols[controllerClass]}.${symbols[initializeMethod]}() " +
                "container=${symbols[containerField]} navigation=${symbols[navigationBarField]}",
            required.checks(symbols),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PbTopEnterBarBlockHook", false, listOf(HookFeatureKey.HIDE_PB_TOP_ENTER_BAR)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        !required.anyPresent(symbols) || resolve(cl, symbols) != null
}
