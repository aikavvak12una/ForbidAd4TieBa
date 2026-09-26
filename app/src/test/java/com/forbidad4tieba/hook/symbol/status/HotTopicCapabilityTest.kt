package com.forbidad4tieba.hook.symbol.status

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
        assertEquals("fixture.CardData#schema", symbols.feedCardSchemaGetterSpec)
        assertEquals(HookFeatureState.FULL,
            HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER).state)
        assertEquals(HookPointState.FOUND, topicPoint(symbols).state)
    }

    private fun symbols(schema: String?) = buildHookSymbols {
        feedTemplateKeyMethod = "templateKey"
        feedTemplatePayloadMethod = "payload"
        feedTemplateLoadMoreMethod = "loadMore"
        feedCardDataListField = "dataList"
        feedHeadParamsField = "params"
        feedRecommendCardNestedDataMethod = "nested"
        feedRecommendCardNestedDataListField = "dataList"
        feedCardSchemaGetterSpec = schema
    }

    private fun topicPoint(symbols: HookSymbols) = HookSymbolStatusFormatter.collectHookPointStatuses(
        symbols, "unused", "unused", "unused",
    ).single { it.name == "CustomPostCardBlockHook.TopicSchema" }
}
