package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PbPerformancePolicyTest {
    private data class Case(
        val performance: Boolean,
        val preload: Boolean,
        val hybrid: Boolean,
        val preserveProxy: Boolean,
    )

    private val cases = listOf(
        Case(false, false, false, false),
        Case(true, false, true, false),
        Case(false, true, false, true),
        Case(true, true, false, true),
    )

    @Test fun previewTakesPrecedenceAndRetainsProxyWithoutLosingOtherLowEndBlocks() {
        val cache = LowEndConfigPolicyCache()
        val source = """["server","disable_webview_proxy","disable_webview_proxy"]"""
        for (case in cases) {
            val settings = settings(case)
            val policy = PbPerformancePolicy.from(settings)
            assertSame(policy, PbPerformancePolicy.from(settings.copy()))
            assertEquals(case.toString(), case.preload, policy.forcePreload)
            assertEquals(case.toString(), case.hybrid, policy.enableHybridOptimization)
            assertEquals(case.toString(), case.preserveProxy, policy.preserveWebViewProxy)
            val result = JSONArray(cache.apply(LowEndConfigPolicy.BLOCK_LIST, source, policy.preserveWebViewProxy))
            val items = List(result.length()) { result.getString(it) }
            assertTrue(items.contains("server"))
            assertTrue(items.contains("disable_preload_feed_image"))
            assertEquals(case.toString(), !case.preserveProxy, items.contains("disable_webview_proxy"))
        }
    }

    @Test fun actualPlansAndAbCallbacksRespectEveryCombinationAndPublishedSnapshot() {
        val snapshotField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val original = snapshotField.get(ConfigManager)
        @Suppress("UNCHECKED_CAST")
        val overrides = PbPerformanceModeHook::class.java.getDeclaredField("booleanOverrides")
            .apply { isAccessible = true }.get(PbPerformanceModeHook) as Array<UbsAbTestBooleanOverride>
        try {
            for (case in cases) for (lowEnd in listOf(false, true)) for (adBlock in listOf(false, true)) {
                val settings = settings(case).copy(
                    isLowEndDeviceConfigForced = lowEnd,
                    isPostPageAdBlockEnabled = adBlock,
                )
                snapshotField.set(ConfigManager, settings)
                val expectedPlan = buildList {
                    if (case.performance || adBlock) add("PbPerformanceModeHook")
                    if (case.preload) add("PbForcePreloadHook")
                    if (lowEnd) add("HostPerformanceConfigHook")
                }
                assertEquals(expectedPlan, PerformanceFeature.entries(HookInstallContext("com.baidu.tieba", HookSymbols.unsupported()), settings).map { it.id })
                assertTrue(PerformanceFeature.entries(HookInstallContext("com.baidu.tieba:remote", null), settings).isEmpty())
                val expectedOverrides = buildMap {
                    if (case.hybrid) put(PerformanceAbTarget.HYBRID_PB, true)
                    if (case.performance) put(PerformanceAbTarget.IMAGE_PERF_LOG, false)
                    if (case.performance || adBlock) {
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

    @Test fun preloadConsumersCannotReintroduceSeparateSettingsPrecedence() {
        val root = File(checkNotNull(System.getProperty("project.root")),
            "features/src/main/java/com/forbidad4tieba/hook/feature/perf")
        val directReaders = root.walkTopDown().filter { it.extension == "kt" && "isPbPreloadForced" in it.readText() }
            .map { it.name }.toList()
        assertEquals(listOf("PbPerformancePolicy.kt"), directReaders)
    }

    private fun settings(case: Case) = SettingsSnapshot.bootstrap().copy(
        isPbPerformanceModeEnabled = case.performance,
        isPbPreloadForced = case.preload,
    )
}
