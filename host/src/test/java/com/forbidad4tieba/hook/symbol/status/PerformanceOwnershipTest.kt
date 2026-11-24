package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*
import com.forbidad4tieba.hook.symbol.model.*
import org.junit.Assert.*
import org.junit.Test

class PerformanceOwnershipTest {
    @Test fun cookieAndPostAdPointsHaveSeparateOwners() {
        val cookie = buildHookSymbols { this[PerformanceContract.performanceAbMethods] = listOf("cookieRepeatedOpt") }
        val post = buildHookSymbols {
            this[PerformanceContract.performanceAbMethods] = listOf("isPbCommentFunAdABTest", "isPbPageBannerFunAdSdkTest")
        }
        assertEquals(setOf(HookFeatureKey.COOKIE_SYNC_DEDUP), PerformanceAbStatus.features(cookie).keys)
        assertEquals(HookFeatureState.FULL, PerformanceAbStatus.features(cookie).getValue(HookFeatureKey.COOKIE_SYNC_DEDUP).state)
        assertEquals(HookFeatureState.DISABLED, PerformanceAbStatus.features(post).getValue(HookFeatureKey.COOKIE_SYNC_DEDUP).state)
        assertEquals(listOf("PerformanceAB.cookieRepeatedOpt"), PerformanceAbStatus.hookPoints(post)
            .filter { it.isUnavailable() }.map { it.name })
        for (name in listOf("isPbCommentFunAdABTest", "isPbPageBannerFunAdSdkTest")) {
            assertEquals(listOf(HookFeatureKey.BLOCK_AD_POST_PAGE), SymbolContracts.featuresForPoint("PerformanceAB.$name"))
        }
    }

    @Test fun adChannelsAreIndependentAndUnknownNamesCannotSupplyTargets() {
        val facts = listOf(
            emptyList<String>() to HookFeatureState.DISABLED,
            listOf("AD_CSJ", "retiredSdk") to HookFeatureState.PARTIAL,
            listOf("AD_CSJ", "AD_GDT", "AD_BAIDU") to HookFeatureState.FULL,
        )
        for ((found, expected) in facts) {
            val symbols = buildHookSymbols { this[PerformanceContract.runtimeMethods] = found }
            val states = PerformanceContract.capabilities(symbols)
            assertEquals(expected, states.getValue(HookFeatureKey.DISABLE_AD_SDK_COMPONENTS).state)
            assertEquals(HookFeatureState.DISABLED, states.getValue(HookFeatureKey.COOKIE_SYNC_DEDUP).state)
            assertFalse(states.containsKey(HookFeatureKey.DISABLE_VIDEO_COMPONENTS))
            assertEquals(found.filter { it != "retiredSdk" }.toSet(), PerformanceRuntimeStatus.hookPoints(symbols)
                .filter { it.state == HookPointState.FOUND }.map { it.name.removePrefix("PerformanceRuntime.") }.toSet())
        }
        for (name in listOf("AD_CSJ", "AD_GDT", "AD_BAIDU")) {
            assertEquals(listOf(HookFeatureKey.DISABLE_AD_SDK_COMPONENTS), SymbolContracts.featuresForPoint("PerformanceRuntime.$name"))
        }
    }

    @Test fun oldPartialVideoCacheCannotActivateTheCompleteVideoGroup() {
        val symbols = buildHookSymbols { this[PerformanceContract.runtimeMethods] = listOf("VIDEO_PREFETCH", "VIDEO_OPEN") }
        assertEquals(HookFeatureState.DISABLED,
            VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS).state)
        assertFalse(PerformanceRuntimeStatus.hookPoints(symbols).any { it.name.contains("VIDEO_") })
    }
}
