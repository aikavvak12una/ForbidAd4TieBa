package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotTopicCapabilityTest {
    @Test fun missingSchemaAccessIsVisibleWithoutDisablingOtherPostFilters() {
        val symbols = symbols(null)
        val feature = HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER)
        assertEquals(HookFeatureState.PARTIAL, feature.state)
        assertTrue(feature.missingCritical.isEmpty())
        assertTrue(feature.missingOptional.contains("feedCardSchemaGetterSpec"))
        assertEquals(HookPointState.MISSING, topicPoint(symbols).state)
    }

    @Test fun schemaAccessSurvivesCacheRoundTripAndRestoresFullCapability() {
        val symbols = requireNotNull(HookSymbols.fromJson(symbols("fixture.CardData#schema").toJson()))
        assertEquals("fixture.CardData#schema", symbols[FeedContract.feedCardSchemaGetterSpec])
        assertEquals(HookFeatureState.FULL,
            HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER).state)
        assertEquals(HookPointState.FOUND, topicPoint(symbols).state)
    }

    private fun symbols(schema: String?) = buildHookSymbols {
        this[FeedContract.feedTemplateKeyMethod] = "templateKey"
        this[FeedContract.feedTemplatePayloadMethod] = "payload"
        this[FeedContract.feedTemplateLoadMoreMethod] = "loadMore"
        this[FeedContract.feedCardDataListField] = "dataList"
        this[FeedContract.feedHeadParamsField] = "params"
        this[FeedContract.feedRecommendCardNestedDataMethod] = "nested"
        this[FeedContract.feedRecommendCardNestedDataListField] = "dataList"
        this[FeedContract.feedCardSchemaGetterSpec] = schema
    }

    private fun topicPoint(symbols: HookSymbols) = HookSymbolStatusFormatter.collectHookPointStatuses(
        symbols).single { it.name == "CustomPostCardBlockHook.TopicSchema" }
}
