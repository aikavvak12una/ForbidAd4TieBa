package com.forbidad4tieba.hook.feature.perf

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostPerformanceConfigHookTest {
    @Test
    fun lowEndPreservesAllServerEntriesIncludingRepeatedBlocks() {
        val actual = items(LowEndConfigPolicy.blockList(
            """["remote_only","disable_webview_proxy","disable_preload_feed_image","disable_webview_proxy"]""",
        ))

        assertEquals(listOf("remote_only", "disable_webview_proxy", "disable_preload_feed_image", "disable_webview_proxy"), actual)
    }

    @Test
    fun lowEndRetainsServerPolicyAndAddsMissingModuleBlocksOnlyOnce() {
        val original = """["remote_only","disable_webview_proxy"]"""
        val actual = LowEndConfigPolicy.blockList(original)

        assertEquals(
            listOf("remote_only", "disable_webview_proxy", "disable_preload_feed_image"),
            items(actual),
        )
        assertEquals(actual, LowEndConfigPolicy.blockList(actual))
    }

    @Test
    fun emptyOrMalformedServerPolicyStillAppliesLowEndBlocks() {
        for (original in listOf("", "not json", "[]")) {
            val actual = items(LowEndConfigPolicy.blockList(original))
            assertTrue(actual.contains("disable_preload_feed_image"))
            assertTrue(actual.contains("disable_webview_proxy"))
        }
    }

    private fun items(raw: String): List<String> {
        val array = JSONArray(raw)
        return List(array.length()) { array.getString(it) }
    }
}
