package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.BannerAdSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.BannerAdSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

object BannerAdContract : SymbolContract("BannerAd") {
    val bannerAdConversionMethodSpec = text("bannerAdConversionMethodSpec")
    private val required = SymbolDependencies(bannerAdConversionMethodSpec)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) {
        output[bannerAdConversionMethodSpec] = scan.runScanStep(
            "BannerAdHook.Conversion", scan.logger, scan.scanErrors, null as String?,
        ) { BannerAdSymbolScanner.scan(scan.cl, scan.logger) }
    }

    fun resolveBannerAdSymbols(cl: ClassLoader, symbols: HookSymbols?): BannerAdSymbols? =
        BannerAdSymbolScanner.restore(cl, symbols?.get(bannerAdConversionMethodSpec))

    internal fun pathStatus(symbols: HookSymbols): HookFeatureStatus = required.requiredStatus(symbols)

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        !required.anyPresent(symbols) || resolveBannerAdSymbols(cl, symbols) != null

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "BannerAdHook.Conversion",
            "${symbols[bannerAdConversionMethodSpec]}(tbclient.LayoutFactory, Map, boolean)",
            required.checks(symbols),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("BannerAdHook.", true, listOf(HookFeatureKey.BLOCK_AD_FEED)),
    )
}
