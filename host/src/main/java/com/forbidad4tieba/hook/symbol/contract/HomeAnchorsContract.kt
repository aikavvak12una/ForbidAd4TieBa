package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object HomeAnchorsContract : SymbolContract("HomeAnchors") {
    val homePersonalizeAnchorClasses = texts("homePersonalizeAnchorClasses", preserveEmpty = true)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val anchorCandidates = listOf(
            StableTiebaHookPoints.HOME_SEARCH_BOX_OWNER_CLASS,
            "com.baidu.tieba.homepage.personalize.PersonalizePageView",
            "com.baidu.searchbox.task.view.mainactivity.InitPersonalizeViewTask",
        )

        val resolvedAnchors = anchorCandidates.filter { ScanReflection.safeFindClass(it, cl) != null }

        val homePersonalizeAnchorClasses: List<String>? = if (resolvedAnchors.isNotEmpty()) resolvedAnchors else null

        output[HomeAnchorsContract.homePersonalizeAnchorClasses] = homePersonalizeAnchorClasses
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "StrategyAdHook.HomePersonalizeAnchors",
            "anchors={${listTarget(symbols[HomeAnchorsContract.homePersonalizeAnchorClasses])}}",
            listOf(HomeAnchorsContract.homePersonalizeAnchorClasses.check(symbols)),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("StrategyAdHook.HomePersonalizeAnchors", false, listOf(HookFeatureKey.BLOCK_AD_HOME_TOP_BAR, HookFeatureKey.HOME_NATIVE_GLASS)),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
