package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceFeatureTest {
    @Test fun plansAndAbCallbacksKeepIndependentPerformanceAndAdControls() {
        val snapshotField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val original = snapshotField.get(ConfigManager)
        @Suppress("UNCHECKED_CAST")
        val overrides = PbPerformanceModeHook::class.java.getDeclaredField("booleanOverrides")
            .apply { isAccessible = true }.get(PbPerformanceModeHook) as Array<UbsAbTestBooleanOverride>
        try {
            for (performance in listOf(false, true)) for (lowEnd in listOf(false, true)) for (adBlock in listOf(false, true)) {
                val settings = SettingsSnapshot.bootstrap().copy(
                    isPbPerformanceModeEnabled = performance,
                    isLowEndDeviceConfigForced = lowEnd,
                    isPostPageAdBlockEnabled = adBlock,
                )
                snapshotField.set(ConfigManager, settings)
                val expectedPlan = buildList {
                    if (performance || adBlock) add("PbPerformanceModeHook")
                    if (lowEnd) add("HostPerformanceConfigHook")
                }
                assertEquals(expectedPlan, PerformanceFeature.entries(HookInstallContext("com.baidu.tieba", HookSymbols.unsupported()), settings).map { it.id })
                assertTrue(PerformanceFeature.entries(HookInstallContext("com.baidu.tieba:remote", null), settings).isEmpty())
                val expectedOverrides = buildMap {
                    if (performance) {
                        put(PerformanceAbTarget.HYBRID_PB, true)
                        put(PerformanceAbTarget.IMAGE_PERF_LOG, false)
                    }
                    if (performance || adBlock) {
                        put(PerformanceAbTarget.PB_COMMENT_AD, false)
                        put(PerformanceAbTarget.PB_BANNER_AD, false)
                    }
                }
                assertEquals(expectedOverrides, overrides.filter { it.enabled() }.associate { it.target to it.value })
            }
        } finally {
            snapshotField.set(ConfigManager, original)
        }
    }
}
