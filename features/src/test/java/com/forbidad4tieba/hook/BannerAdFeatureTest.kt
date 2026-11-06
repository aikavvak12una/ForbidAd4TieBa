package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerAdFeatureTest {
    @Test fun bannerSharesTheFeedOptionButNotTheStrategyOption() {
        val symbols = symbols(adapter = true, banner = true)
        assertEquals(setOf("FeedAdHook", "BannerAdHook"), entries(symbols, feed = true))
        assertEquals(setOf("FeedAdHook"), entries(symbols, strategy = true))
        assertTrue(entries(symbols).isEmpty())
        assertTrue(entries(symbols, feed = true, process = Constants.TARGET_PACKAGE + ":remote").isEmpty())
    }

    @Test fun eachAvailablePathInstallsWithoutMakingItsSiblingAPrerequisite() {
        assertEquals(setOf("BannerAdHook"), entries(symbols(adapter = false, banner = true), feed = true))
        assertEquals(setOf("FeedAdHook"), entries(symbols(adapter = true, banner = false), feed = true))
        assertTrue(entries(symbols(adapter = false, banner = false), feed = true).isEmpty())
        assertFalse(entries(symbols(adapter = false, banner = true), strategy = true).contains("FeedAdHook"))
    }

    private fun entries(
        symbols: HookSymbols,
        feed: Boolean = false,
        strategy: Boolean = false,
        process: String = Constants.TARGET_PACKAGE,
    ): Set<String> = FeatureCatalog.symbolPlan(
        process,
        symbols,
        SettingsSnapshot.bootstrap().copy(isFeedAdBlockEnabled = feed, isStrategyAdBlockEnabled = strategy),
    ).entries.map { it.id }.filter { it == "FeedAdHook" || it == "BannerAdHook" }.toSet()

    private fun symbols(adapter: Boolean, banner: Boolean): HookSymbols {
        val json = JSONObject()
        if (adapter) {
            json.put("feedTemplateKeyMethod", "key")
            json.put("feedTemplateLoadMoreMethod", "append")
        }
        if (banner) json.put("bannerAdConversionMethodSpec", "example.HomeFeed#convert")
        return requireNotNull(HookSymbols.fromJson(json.toString()))
    }
}
