package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.AutoLoadMoreContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AutoLoadMoreStatusTest {
    private fun cached(vararg fields: Pair<String, String>): HookSymbols {
        val json = JSONObject(buildHookSymbols {}.toJson())
        fields.forEach { (key, value) -> json.put(key, value) }
        return requireNotNull(HookSymbols.fromJson(json.toString()))
    }

    @Test
    fun oldBottomListenersCannotClaimCurrentCommentsAreAvailable() {
        val symbols = cached(
            "autoLoadMoreConfigClass" to "test.FeedConfig",
            "autoLoadMoreConfigMethod" to "threshold",
            "pbCommentBottomRecyclerScrollClass" to "test.LegacyListener",
            "pbCommentBottomRecyclerScrollMethod" to "onScrolled",
            "pbCommentBottomRecyclerOwnerField" to "owner",
        )
        assertEquals(HookFeatureState.PARTIAL, AutoLoadMoreContract.capabilities(symbols)
            .getValue(HookFeatureKey.AUTO_LOAD_MORE).state)
        assertEquals(HookPointState.MISSING, AutoLoadMoreContract.points(symbols)
            .single { it.name == "PbCommentAutoLoadHook" }.state)
    }

    @Test
    fun nativeThresholdSurvivesCacheWithoutClaimingBatchSupport() {
        val symbols = cached(
            "autoLoadMoreConfigClass" to "test.FeedConfig",
            "autoLoadMoreConfigMethod" to "threshold",
            "pbCommentPreloadConfigMethodSpec" to "test.CommentConfig#threshold",
        )
        val restored = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        assertEquals("test.CommentConfig#threshold", JSONObject(restored.toJson())
            .getString("pbCommentPreloadConfigMethodSpec"))
        assertEquals(HookFeatureState.PARTIAL, AutoLoadMoreContract.capabilities(restored)
            .getValue(HookFeatureKey.AUTO_LOAD_MORE).state)
        assertEquals(listOf("pbCommentBatchSpec"), AutoLoadMoreContract.capabilities(restored)
            .getValue(HookFeatureKey.AUTO_LOAD_MORE).missingOptional)
        assertEquals(HookPointState.FOUND, AutoLoadMoreContract.points(restored)
            .single { it.name == "PbCommentAutoLoadHook" }.state)
        assertFalse(JSONObject(restored.toJson()).has("pbCommentBottomRecyclerScrollClass"))
    }

    @Test
    fun batchDescriptorIsRequiredForFullSupportAndSurvivesCache() {
        val symbols = cached(
            "autoLoadMoreConfigClass" to "test.FeedConfig",
            "autoLoadMoreConfigMethod" to "threshold",
            "pbCommentPreloadConfigMethodSpec" to "test.CommentConfig#threshold",
            "pbCommentBatchSpec" to "{\"scrollClass\":\"test.Scroll\"}",
        )
        val restored = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        assertEquals("{\"scrollClass\":\"test.Scroll\"}", restored[AutoLoadMoreContract.pbCommentBatchSpec])
        assertEquals(HookFeatureState.FULL, AutoLoadMoreContract.capabilities(restored)
            .getValue(HookFeatureKey.AUTO_LOAD_MORE).state)
    }
}
