package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.combineSubFeatureStatuses
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

object PostPageAdContract : SymbolContract("PostPageAd") {
    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = Unit

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val postDataStatus = PostAdDataContract.pathStatus(symbols)
        val pbEarlyStatus = PbEarlyAdContract.pathStatus(symbols)
        val pbFirstFloorRecommendStatus = PbFirstFloorRecommendContract.pathStatus(symbols)
        val pbFallingStatus = PbFallingContract.pathStatus(symbols)
        val pbRequestStatus = PbAdRequestContract.pathStatus(symbols)
        out[HookFeatureKey.BLOCK_AD_POST_PAGE] = combineSubFeatureStatuses(
            listOf(
                postDataStatus,
                pbEarlyStatus,
                pbFirstFloorRecommendStatus,
                pbFallingStatus,
                pbRequestStatus,
            ),
        )
        return out
    }

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true

    // Diagnostics are supplied by the consuming capability or the fixed entry installer.
    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = emptyList()
}
