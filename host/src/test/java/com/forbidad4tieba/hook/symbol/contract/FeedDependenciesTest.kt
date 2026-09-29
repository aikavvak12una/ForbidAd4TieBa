package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedDependenciesTest {
    // Baseline roles are deliberately independent of the production dependency groups.
    private val customRequired = listOf(
        "feedTemplateKeyMethod", "feedTemplatePayloadMethod",
        "feedTemplateLoadMoreMethod", "feedCardDataListField",
    )
    private val customOptional = listOf(
        "feedHeadParamsField", "feedCardSchemaGetterSpec",
        "feedRecommendCardNestedDataMethod", "feedRecommendCardNestedDataListField",
    )
    private val fixtureKeys = customRequired + customOptional + "feedCardBindMethodSpec"
    private val pointRequirements = linkedMapOf(
        "FeedAdHook.TemplateKey" to listOf("feedTemplateKeyMethod"),
        "CustomPostCardBlockHook.Payload" to listOf("feedTemplatePayloadMethod"),
        "FeedAdHook.LoadMore" to listOf("feedTemplateLoadMoreMethod"),
        "CustomPostCardBlockHook" to customRequired,
        "CustomPostCardBlockHook.HeadParams" to listOf("feedHeadParamsField"),
        "CustomPostCardBlockHook.TopicSchema" to listOf("feedCardSchemaGetterSpec"),
        "CustomPostCardBlockHook.RecommendCard" to listOf(
            "feedRecommendCardNestedDataMethod", "feedRecommendCardNestedDataListField",
        ),
        "FeedInfoLogHook.Bind" to listOf("feedCardBindMethodSpec"),
    )

    private fun fixture(missing: Set<String>, absentValue: String? = null): HookSymbols = buildHookSymbols {
        val fields = FeedContract.fields.associateBy { it.cacheKey }
        fixtureKeys.forEach { key ->
            @Suppress("UNCHECKED_CAST")
            val field = fields.getValue(key) as SymbolField<String?>
            this[field] = if (key in missing) absentValue else "fixture.$key"
        }
    }

    @Test fun everyPresenceCombinationPreservesCapabilityAndPointRoles() {
        repeat(1 shl fixtureKeys.size) { mask ->
            val missing = fixtureKeys.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            val symbols = fixture(missing)
            val capabilities = FeedContract.capabilities(symbols)
            assertEquals(listOf(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER, HookFeatureKey.BLOCK_AD_FEED),
                capabilities.keys.toList())
            assertEquals("custom filter mask=$mask",
                expectedStatus(customRequired.filter { it in missing }, customOptional.filter { it in missing }),
                capabilities.getValue(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER))
            assertEquals("ad filter mask=$mask",
                expectedStatus(listOf("feedTemplateKeyMethod").filter { it in missing },
                    listOf("feedTemplateLoadMoreMethod").filter { it in missing }),
                capabilities.getValue(HookFeatureKey.BLOCK_AD_FEED))

            val points = FeedContract.points(symbols)
            assertEquals(pointRequirements.keys.toList(), points.map { it.name })
            points.forEach { point ->
                val expectedMissing = pointRequirements.getValue(point.name).filter { it in missing }
                assertEquals("${point.name} mask=$mask", expectedMissing, point.missing)
                assertEquals("${point.name} mask=$mask",
                    if (expectedMissing.isEmpty()) HookPointState.FOUND else HookPointState.MISSING,
                    point.state)
            }
        }
    }

    @Test fun missingLoadMoreDegradesAdsButDisablesCustomFiltering() {
        val symbols = fixture(setOf("feedTemplateLoadMoreMethod"))
        val statuses = FeedContract.capabilities(symbols)
        assertEquals(HookFeatureStatus(HookFeatureState.PARTIAL,
            missingOptional = listOf("feedTemplateLoadMoreMethod")), statuses[HookFeatureKey.BLOCK_AD_FEED])
        assertEquals(HookFeatureStatus(HookFeatureState.DISABLED,
            missingCritical = listOf("feedTemplateLoadMoreMethod")), statuses[HookFeatureKey.ENABLE_CUSTOM_POST_FILTER])
        assertEquals(HookPointState.FOUND, FeedContract.points(symbols).first().state)
    }

    @Test fun blankDescriptorsHaveTheSameMissingRolesAsNull() {
        fixtureKeys.forEach { key ->
            val absent = fixture(setOf(key))
            listOf("", " \t").forEach { blank ->
                val symbols = fixture(setOf(key), blank)
                assertEquals(FeedContract.capabilities(absent), FeedContract.capabilities(symbols))
                assertEquals(FeedContract.points(absent).map { it.name to it.missing },
                    FeedContract.points(symbols).map { it.name to it.missing })
            }
        }
    }

    @Test fun partialCacheRoundTripPreservesDiagnosticsAndUnrelatedCapabilities() {
        val full = fixture(emptySet())
        val partial = fixture(setOf("feedTemplateLoadMoreMethod", "feedRecommendCardNestedDataListField"))
        val restored = requireNotNull(HookSymbols.fromJson(partial.toJson()))
        assertEquals(partial, restored)
        assertEquals(FeedContract.points(partial), FeedContract.points(restored))
        assertEquals(FeedContract.capabilities(partial), FeedContract.capabilities(restored))
        assertEquals(EnterForumContract.capabilities(full), EnterForumContract.capabilities(partial))
        assertEquals(EnterForumContract.points(full), EnterForumContract.points(partial))
    }

    private fun expectedStatus(required: List<String>, optional: List<String>) = HookFeatureStatus(
        state = when {
            required.isNotEmpty() -> HookFeatureState.DISABLED
            optional.isNotEmpty() -> HookFeatureState.PARTIAL
            else -> HookFeatureState.FULL
        },
        missingCritical = required,
        missingOptional = optional,
    )
}
