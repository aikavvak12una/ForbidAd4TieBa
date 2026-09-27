package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

object AdFamilyContract : SymbolContract("AdFamily") {
    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = Unit

    internal override val aggregates = mapOf(
        HookFeatureKey.BLOCK_AD to listOf(
            HookFeatureKey.BLOCK_AD_FEED,
            HookFeatureKey.BLOCK_AD_POST_PAGE,
            HookFeatureKey.BLOCK_AD_FORUM_PAGE,
            HookFeatureKey.BLOCK_AD_STRATEGY,
            HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT,
            HookFeatureKey.BLOCK_AD_HOME_TOP_BAR,
            HookFeatureKey.BLOCK_AD_MINE_TAB_WEB,
            HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB,
            HookFeatureKey.BLOCK_AD_HOME_BOTTOM_EASTER_EGG,
        ),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()

    // Diagnostics are supplied by the consuming capability or the fixed entry installer.
    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = emptyList()
}
