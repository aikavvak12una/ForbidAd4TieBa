package com.forbidad4tieba.hook.symbol.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HookSymbolsJsonTest {
    @Test
    fun jsonRoundTripPreservesStatusResourceAndHookPointFields() {
        val symbols = buildHookSymbols {
            autoRefreshTriggerMethod = "com.tieba.Feed#triggerRefresh"
            feedCardBindMethod = "com.tieba.FeedCard#bind"
            homeNativeGlassSubPbNextPageMoreViewId = 12345
            homeNativeGlassDynamicBackgroundColorIds = listOf(11, 22)
            featureStatusMap = mapOf(
                HookFeatureKey.DISABLE_AUTO_REFRESH to HookFeatureStatus(
                    state = HookFeatureState.FULL,
                ),
            )
            scanSupportState = ScanSupportState.SUPPORTED
            scanErrors = listOf("sample scan error")
        }

        val parsed = HookSymbols.fromJson(symbols.toJson())

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals("com.tieba.Feed#triggerRefresh", parsed.autoRefreshTriggerMethod)
        assertEquals("com.tieba.FeedCard#bind", parsed.feedCardBindMethod)
        assertEquals(12345, parsed.homeNativeGlassSubPbNextPageMoreViewId)
        assertEquals(listOf(11, 22), parsed.homeNativeGlassDynamicBackgroundColorIds)
        assertEquals(
            HookFeatureState.FULL,
            parsed.featureStatusMap.getValue(HookFeatureKey.DISABLE_AUTO_REFRESH).state,
        )
        assertEquals(ScanSupportState.SUPPORTED, parsed.scanSupportState)
        assertEquals(listOf("sample scan error"), parsed.scanErrors)
    }
}
