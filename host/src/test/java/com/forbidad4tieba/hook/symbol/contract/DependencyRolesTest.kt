package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.status.HookPointState
import com.forbidad4tieba.hook.symbol.textSymbols
import org.junit.Assert.*
import org.junit.Test

class DependencyRolesTest {
    private val customRequired = listOf("feedTemplateKeyMethod", "feedTemplatePayloadMethod", "feedTemplateLoadMoreMethod",
        "feedCardDataListField", "feedCardBindMethodSpec")
    private val customOptional = listOf("feedHeadParamsField", "feedCardSchemaGetterSpec",
        "feedRecommendCardNestedDataMethod", "feedRecommendCardNestedDataListField")
    private val loadMore = listOf("autoLoadMoreConfigClass", "autoLoadMoreConfigMethod",
        "pbCommentPreloadConfigMethodSpec", "pbCommentBatchSpec")

    @Test fun eachFeedRoleRetainsItsIndependentRequiredOrOptionalMeaning() {
        val keys = customRequired + customOptional
        val ready = textSymbols(keys)
        assertEquals(HookFeatureState.FULL, FeedContract.capabilities(ready).getValue(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER).state)
        for (missing in keys) {
            val symbols = textSymbols(keys, setOf(missing))
            val status = FeedContract.capabilities(symbols).getValue(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER)
            assertEquals(missing, if (missing in customRequired) HookFeatureState.DISABLED else HookFeatureState.PARTIAL, status.state)
            assertEquals(if (missing in customRequired) listOf(missing) else emptyList<String>(), status.missingCritical)
            assertEquals(if (missing in customOptional) listOf(missing) else emptyList<String>(), status.missingOptional)
        }
        val schemaMissing = textSymbols(keys, setOf("feedCardSchemaGetterSpec"))
        assertEquals(HookPointState.MISSING, FeedContract.points(schemaMissing).single { it.name.endsWith(".TopicSchema") }.state)
    }

    @Test fun loadMoreFailureDisablesCustomFilteringButRetainsFeedAds() {
        val partial = textSymbols(customRequired + customOptional, setOf("feedTemplateLoadMoreMethod"))
        assertEquals(HookFeatureState.PARTIAL, FeedContract.templateAdapterCapability(partial).state)
        assertEquals(listOf("feedTemplateLoadMoreMethod"), FeedContract.templateAdapterCapability(partial).missingOptional)
        assertEquals(HookFeatureState.DISABLED, FeedContract.capabilities(partial).getValue(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER).state)
        assertEquals(HookPointState.FOUND, FeedContract.points(partial).single { it.name == "FeedAdHook.TemplateKey" }.state)
    }

    @Test fun adapterAndBannerPathsHaveOneCombinedOwnerAndDoNotEnableOtherFilters() {
        val facts = listOf(
            emptyList<String>() to HookFeatureState.DISABLED,
            listOf("bannerAdConversionMethodSpec") to HookFeatureState.PARTIAL,
            listOf("feedTemplateKeyMethod", "feedTemplateLoadMoreMethod") to HookFeatureState.PARTIAL,
            listOf("feedTemplateKeyMethod", "feedTemplateLoadMoreMethod", "bannerAdConversionMethodSpec") to HookFeatureState.FULL,
        )
        for ((keys, expected) in facts) {
            val symbols = textSymbols(keys)
            assertEquals(keys.toString(), expected, FeedContract.capabilities(symbols).getValue(HookFeatureKey.BLOCK_AD_FEED).state)
            assertTrue(BannerAdContract.capabilities(symbols).isEmpty())
            assertFalse(FeedContract.capabilities(symbols).getValue(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER).isSupported())
        }
        val banner = textSymbols(listOf("bannerAdConversionMethodSpec"))
        assertEquals(StrategyAdContract.capabilities(buildHookSymbols {}), StrategyAdContract.capabilities(banner))
        assertEquals(1, SymbolContracts.all.count { HookFeatureKey.BLOCK_AD_FEED in it.capabilities(banner) })
    }

    @Test fun independentLoadMorePointsAndBatchPrerequisiteHaveExplicitMissingFacts() {
        val pointNames = listOf("AutoLoadMoreHook.Config", "PbCommentAutoLoadHook", "PbCommentAutoLoadHook.Batch")
        val requirements = listOf(loadMore.take(2), listOf("pbCommentPreloadConfigMethodSpec"),
            listOf("pbCommentPreloadConfigMethodSpec", "pbCommentBatchSpec"))
        val facts = listOf(
            emptyList<String>() to listOf(HookPointState.FOUND, HookPointState.FOUND, HookPointState.FOUND),
            listOf("autoLoadMoreConfigClass") to listOf(HookPointState.MISSING, HookPointState.FOUND, HookPointState.FOUND),
            listOf("autoLoadMoreConfigMethod") to listOf(HookPointState.MISSING, HookPointState.FOUND, HookPointState.FOUND),
            listOf("pbCommentPreloadConfigMethodSpec") to listOf(HookPointState.FOUND, HookPointState.MISSING, HookPointState.OPTIONAL),
            listOf("pbCommentBatchSpec") to listOf(HookPointState.FOUND, HookPointState.FOUND, HookPointState.OPTIONAL),
            loadMore to listOf(HookPointState.MISSING, HookPointState.MISSING, HookPointState.OPTIONAL),
        )
        for ((missing, expectedPoints) in facts) {
            val symbols = textSymbols(loadMore, missing.toSet())
            val capability = AutoLoadMoreContract.capabilities(symbols).getValue(HookFeatureKey.AUTO_LOAD_MORE)
            assertEquals(if (missing.isEmpty()) HookFeatureState.FULL else HookFeatureState.PARTIAL, capability.state)
            assertEquals(missing, capability.missingOptional)
            val points = AutoLoadMoreContract.points(symbols)
            assertEquals(pointNames, points.map { it.name })
            assertEquals(expectedPoints, points.map { it.state })
            points.forEachIndexed { index, point -> assertEquals(requirements[index].filter { it in missing }, point.missing) }
        }
    }

    @Test fun emptyResultsAreNegativeCachedButPresentIncompleteGroupsMustBeValidated() {
        val loader = javaClass.classLoader!!
        assertTrue(AutoLoadMoreContract.isCacheValid(buildHookSymbols {}, loader))
        for (key in loadMore) assertFalse(key, AutoLoadMoreContract.isCacheValid(textSymbols(listOf(key)), loader))
        val config = buildHookSymbols {
            this[AutoLoadMoreContract.autoLoadMoreConfigClass] = Config::class.java.name
            this[AutoLoadMoreContract.autoLoadMoreConfigMethod] = "threshold"
        }
        assertTrue(AutoLoadMoreContract.isCacheValid(config, loader))
        assertEquals(Config::class.java.getDeclaredMethod("threshold"),
            AutoLoadMoreContract.resolveAutoLoadMoreSymbols(loader, config)?.configMethod)
    }

    class Config { fun threshold(): Int = 5 }
}
