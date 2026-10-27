package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceFeatureTest {
    @Test fun videoPreloadDoesNotEnableAdSuppressionAndCookieStaysInMain() {
        for (cookie in listOf(false, true)) for (ads in listOf(false, true)) for (video in listOf(false, true)) {
            val settings = SettingsSnapshot.bootstrap().copy(
                isCookieSyncDedupEnabled = cookie,
                isAdSdkComponentsDisabled = ads,
                isVideoPreloadDisabled = video,
            )
            val expected = buildSet {
                if (cookie) add("CookieSyncDedupHook")
                if (ads) add("AdSdkInitBlockHook")
            }
            val context = HookInstallContext("com.baidu.tieba", HookSymbols.unsupported())
            val actual = PerformanceFeature.entries(context, settings).map { it.id }
            assertEquals("cookie=$cookie ads=$ads video=$video", expected, actual.toSet())
            assertEquals(expected.size, actual.size)
            for (process in listOf("com.baidu.tieba:remote", "com.baidu.tieba:media", "com.baidu.tieba:nadmedia")) {
                val entries = PerformanceFeature.entries(HookInstallContext(process, HookSymbols.unsupported()), settings)
                assertEquals("$process cookie=$cookie ads=$ads video=$video",
                    emptyList<String>(), entries.map { it.id })
            }
            for (process in listOf("com.baidu.tieba:loki", "com.baidu.tieba:bdservice_v1", "com.other.app", "com.android.systemui")) {
                assertTrue(PerformanceFeature.entries(HookInstallContext(process, null), settings).isEmpty())
            }
        }
    }
}
