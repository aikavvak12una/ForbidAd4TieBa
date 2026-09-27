package com.forbidad4tieba.hook.feature.perf

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostPerformanceConfigHookTest {
    @Test
    fun forcedPreloadRemovesEveryServerProxyBlockAndPreservesUnrelatedEntries() {
        val actual = merge(
            """["remote_only","disable_webview_proxy","disable_preload_feed_image","disable_webview_proxy"]""",
            forcePreload = true,
        )

        assertEquals(listOf("remote_only", "disable_preload_feed_image"), actual)
    }

    @Test
    fun lowEndWithoutPreloadRetainsServerPolicyAndAddsMissingModuleBlocksOnlyOnce() {
        val original = """["remote_only","disable_webview_proxy"]"""
        val actual = LowEndConfigPolicy.blockList(original, forcePbPreload = false)

        assertEquals(
            listOf("remote_only", "disable_webview_proxy", "disable_preload_feed_image"),
            items(actual),
        )
        assertEquals(actual, LowEndConfigPolicy.blockList(actual, forcePbPreload = false))
    }

    @Test
    fun emptyOrMalformedServerPolicyStillPreservesPreload() {
        for (original in listOf("", "not json", "[]")) {
            val actual = merge(original, forcePreload = true)
            assertTrue(actual.contains("disable_preload_feed_image"))
            assertFalse(actual.contains("disable_webview_proxy"))
        }
    }

    private fun merge(original: String, forcePreload: Boolean): List<String> =
        items(LowEndConfigPolicy.blockList(original, forcePbPreload = forcePreload))

    private fun items(raw: String): List<String> {
        val array = JSONArray(raw)
        return List(array.length()) { array.getString(it) }
    }
}
