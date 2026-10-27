package com.forbidad4tieba.hook.feature.perf

import org.json.JSONArray
import org.json.JSONObject

internal object LowEndConfigPolicy {
    const val BLOCK_LIST = "low_score_block_list"
    const val KV_CONFIG = "low_score_kv_config"
    private const val PRELOAD_FEED_IMAGE = "disable_preload_feed_image"
    private const val WEBVIEW_PROXY = "disable_webview_proxy"

    fun blockList(original: String): String {
        val source = try { JSONArray(original) } catch (_: Exception) { JSONArray() }
        val result = JSONArray()
        val existing = HashSet<String>()
        for (i in 0 until source.length()) {
            val item = source.opt(i)
            result.put(item)
            if (item is String) existing.add(item)
        }
        if (existing.add(PRELOAD_FEED_IMAGE)) result.put(PRELOAD_FEED_IMAGE)
        if (existing.add(WEBVIEW_PROXY)) result.put(WEBVIEW_PROXY)
        return result.toString()
    }

    fun keyValues(original: String): String {
        val result = try { JSONObject(original) } catch (_: Exception) { JSONObject() }
        result.put("disable_re_run_idle", "true")
        result.put("defer_video_autoplay_ms", "5000")
        return result.toString()
    }
}

/** Two immutable entries: concurrent misses may recompute, but never wait for another callback. */
internal class LowEndConfigPolicyCache {
    private data class Entry(val source: String, val result: String)
    @Volatile private var blockList: Entry? = null
    @Volatile private var keyValues: Entry? = null

    fun apply(key: String, original: String): String = when (key) {
        LowEndConfigPolicy.BLOCK_LIST -> {
            val cached = blockList
            if (cached != null && cached.source == original) {
                cached.result
            } else {
                LowEndConfigPolicy.blockList(original).also {
                    blockList = Entry(original, it)
                }
            }
        }
        LowEndConfigPolicy.KV_CONFIG -> {
            val cached = keyValues
            if (cached != null && cached.source == original) cached.result
            else LowEndConfigPolicy.keyValues(original).also { keyValues = Entry(original, it) }
        }
        else -> original
    }
}
