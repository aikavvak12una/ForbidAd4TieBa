package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.*
import org.junit.Test

class FeedBannerCapabilityTest {
    @Test fun independentAdapterAndBannerPathsAreCombinedWithoutDuplicateCapabilityOwners() {
        for (adapter in listOf(false, true)) for (loadMore in listOf(false, true)) for (banner in listOf(false, true)) {
            val symbols = fixture(adapter, loadMore, banner)
            val status = FeedContract.capabilities(symbols).getValue(HookFeatureKey.BLOCK_AD_FEED)
            val expected = when {
                !adapter && !banner -> HookFeatureState.DISABLED
                adapter && loadMore && banner -> HookFeatureState.FULL
                else -> HookFeatureState.PARTIAL
            }
            assertEquals("adapter=$adapter loadMore=$loadMore banner=$banner", expected, status.state)
            assertEquals(adapter || banner, status.isSupported())
            assertEquals(adapter, FeedContract.templateAdapterCapability(symbols).isSupported())
            assertEquals(if (adapter) emptyList<String>() else listOf("feedTemplateKeyMethod"),
                FeedContract.templateAdapterCapability(symbols).missingCritical)
            assertEquals(if (banner) emptyList<String>() else listOf("bannerAdConversionMethodSpec"),
                BannerAdContract.pathStatus(symbols).missingCritical)
            assertTrue(BannerAdContract.capabilities(symbols).isEmpty())
            assertEquals(1, SymbolContracts.all.count { HookFeatureKey.BLOCK_AD_FEED in it.capabilities(symbols) })
        }
    }

    @Test fun bannerPresenceCannotChangeStrategyOrCustomFilterAvailability() {
        for (adapter in listOf(false, true)) for (loadMore in listOf(false, true)) {
            val absent = fixture(adapter, loadMore, false)
            val present = fixture(adapter, loadMore, true)
            assertEquals(StrategyAdContract.capabilities(absent), StrategyAdContract.capabilities(present))
            assertEquals(FeedContract.templateAdapterCapability(absent), FeedContract.templateAdapterCapability(present))
            assertEquals(FeedContract.capabilities(absent)[HookFeatureKey.ENABLE_CUSTOM_POST_FILTER],
                FeedContract.capabilities(present)[HookFeatureKey.ENABLE_CUSTOM_POST_FILTER])
        }
    }

    private fun fixture(adapter: Boolean, loadMore: Boolean, banner: Boolean) = buildHookSymbols {
        if (adapter) this[FeedContract.feedTemplateKeyMethod] = "key"
        if (loadMore) this[FeedContract.feedTemplateLoadMoreMethod] = "append"
        if (banner) this[BannerAdContract.bannerAdConversionMethodSpec] = "fixture.Owner#convert"
    }
}
