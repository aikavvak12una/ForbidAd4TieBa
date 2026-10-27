package com.forbidad4tieba.hook.feature.perf

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LowEndConfigPolicyCacheTest {
    @Test fun unchangedInputReusesResultButServerChangesAreObserved() {
        val cache = LowEndConfigPolicyCache()
        val original = """["server","disable_webview_proxy"]"""
        val normal = cache.apply(LowEndConfigPolicy.BLOCK_LIST, original)
        assertSame(normal, cache.apply(LowEndConfigPolicy.BLOCK_LIST, original))
        assertTrue(normal.contains("disable_webview_proxy"))
        assertTrue(normal.contains("server"))
        assertTrue(cache.apply(LowEndConfigPolicy.BLOCK_LIST, """["changed"]""").contains("changed"))
        assertEquals(normal, cache.apply(LowEndConfigPolicy.BLOCK_LIST, original))
    }

    @Test fun keyValueOverridesPreserveServerValuesAndStayIndependentOfBlockList() {
        val cache = LowEndConfigPolicyCache()
        val source = """{"server":5,"disable_re_run_idle":"false"}"""
        val result = cache.apply(LowEndConfigPolicy.KV_CONFIG, source)
        val json = JSONObject(result)
        assertEquals(5, json.getInt("server"))
        assertEquals("true", json.getString("disable_re_run_idle"))
        assertEquals("5000", json.getString("defer_video_autoplay_ms"))
        cache.apply(LowEndConfigPolicy.BLOCK_LIST, "[]")
        assertSame(result, cache.apply(LowEndConfigPolicy.KV_CONFIG, source))
        assertEquals(9, JSONObject(cache.apply(LowEndConfigPolicy.KV_CONFIG, """{"server":9}""")).getInt("server"))
        assertSame(source, cache.apply("unrelated", source))
    }
}
