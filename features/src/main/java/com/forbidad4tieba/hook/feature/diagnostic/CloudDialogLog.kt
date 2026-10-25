package com.forbidad4tieba.hook.feature.diagnostic

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/** Decodes the current host's existing dialog logs. Never retains payloads or guesses ad identity. */
internal class CloudDialogLog(private val emit: (String) -> Unit) {
    private val configs = HostLogRateBudget(maxEntries = 2, maxCharacters = 2 * MAX_CONFIG_INPUT)
    private val events = HostLogRateBudget(maxEntries = 16, maxCharacters = 64 * 1024)
    private var batch = 0L
    private var oversize = 0L
    private var malformed = 0L

    @Synchronized
    fun record(message: String, nowMillis: Long) {
        val prefix = when {
            message.startsWith(SYNC_CONFIG) -> SYNC_CONFIG
            message.startsWith(CACHED_CONFIG) -> CACHED_CONFIG
            else -> null
        }
        if (prefix == null && !isEvent(message)) return
        if (message.length > if (prefix != null) MAX_CONFIG_INPUT else MAX_EVENT_INPUT) {
            oversize++
            return
        }
        val dropped = (if (prefix != null) configs else events).acquire(message.length, nowMillis)
        if (dropped < 0) return
        loss(if (prefix != null) "config_rate" else "event_rate", dropped)
        try {
            if (prefix != null) recordConfig(message.substring(prefix.length), prefix == SYNC_CONFIG)
            else event(message)?.let { emit(it.toString()) } ?: run { malformed++ }
        } catch (_: Exception) {
            // JSON/URI exceptions may contain the original payload. Only record the failure count.
            malformed++
        }
    }

    @Synchronized
    fun flushSuppressed() {
        loss("config_rate", configs.drainSuppressed())
        loss("event_rate", events.drainSuppressed())
        loss("oversize_input", oversize)
        loss("malformed_input", malformed)
        oversize = 0
        malformed = 0
    }

    private fun loss(reason: String, count: Long) {
        if (count > 0) emit(base("loss").put("reason", reason).put("count", count).toString())
    }

    private fun recordConfig(raw: String, sync: Boolean) {
        val input = if (raw.trim() == "null") JSONArray() else JSONArray(raw)
        val id = ++batch
        var recorded = 0
        var characters = 0
        for (index in 0 until minOf(input.length(), MAX_CONFIG_ITEMS)) {
            val item = input.optJSONObject(index) ?: continue
            val row = base(if (sync) "config_sync" else "config_cache")
                .put("batch", id).put("index", index)
                .put("dialogName", identifier(item.opt("dialogName"), 128) ?: JSONObject.NULL)
                .put("url", url(item.opt("dialogUrl") as? String))
            val times = item.optJSONArray("dialogTime")
            row.put("triggers", identifiers(times)).put("triggerCount", times?.length() ?: 0)
            val strategies = item.optJSONArray("dialogStrategy")
            val types = JSONArray()
            for (i in 0 until minOf(strategies?.length() ?: 0, MAX_LIST_ITEMS)) {
                types.put(identifier(strategies?.optJSONObject(i)?.opt("type"), 64) ?: JSONObject.NULL)
            }
            row.put("strategies", types).put("strategyCount", strategies?.length() ?: 0)
            val serialized = row.toString()
            if (characters + serialized.length > MAX_CONFIG_OUTPUT) break
            emit(serialized)
            characters += serialized.length
            recorded++
        }
        emit(base("config_summary").put("source", if (sync) "sync" else "cache")
            .put("batch", id).put("total", input.length())
            .put("recorded", recorded).put("omitted", input.length() - recorded).toString())
    }

    private fun isEvent(message: String): Boolean =
        message.startsWith(TRIGGER) || message.startsWith(RENDER) || message.startsWith(SUCCESS) ||
            message.startsWith(RELEASE) ||
            (message.startsWith("BaseWebDialogProcessor ") && message.endsWith(" 准备展示")) ||
            (message.startsWith("云弹窗 ") && (message.contains(DISPATCH) || message.contains(CANDIDATES)))

    private fun event(message: String): JSONObject? {
        if (message.startsWith(TRIGGER)) {
            val value = message.substring(TRIGGER.length)
            val split = value.lastIndexOf("  ")
            if (split < 0) return null
            val trigger = identifier(value.substring(0, split), 64) ?: return null
            val attempt = value.substring(split + 2).takeIf(ATTEMPT_ID::matches) ?: return null
            return base("trigger").put("trigger", trigger).put("attemptId", attempt)
        }
        if (message.startsWith(SUCCESS)) {
            val value = message.substring(SUCCESS.length)
            val split = value.lastIndexOf("   ")
            if (split < 0) return null
            val name = identifier(value.substring(0, split), 128) ?: return null
            val attempt = value.substring(split + 3).takeIf(ATTEMPT_ID::matches) ?: return null
            return base("render_success").put("dialogName", name).put("attemptId", attempt)
        }
        if (message.startsWith(RENDER)) return named("render_requested", message.substring(RENDER.length))
        if (message.startsWith(RELEASE)) return named("released", message.substring(RELEASE.length))
        if (message.startsWith("BaseWebDialogProcessor ") && message.endsWith(" 准备展示")) {
            return named("web_prepare", message.removePrefix("BaseWebDialogProcessor ").removeSuffix(" 准备展示"))
        }
        if (message.startsWith("云弹窗 ")) {
            val value = message.removePrefix("云弹窗 ")
            val dispatched = value.indexOf(DISPATCH)
            if (dispatched >= 0) {
                val row = named("render_dispatched", value.substring(0, dispatched)) ?: return null
                return row.put("url", url(value.substring(dispatched + DISPATCH.length)))
            }
            val candidates = value.indexOf(CANDIDATES)
            if (candidates >= 0) {
                val trigger = identifier(value.substring(0, candidates), 64) ?: return null
                val names = JSONArray(value.substring(candidates + CANDIDATES.length).trim())
                return base("candidates").put("trigger", trigger).put("dialogNames", identifiers(names, 128))
                    .put("candidateCount", names.length())
            }
        }
        return null
    }

    private fun named(phase: String, name: String): JSONObject? =
        identifier(name, 128)?.let { base(phase).put("dialogName", it) }

    private fun base(phase: String): JSONObject = JSONObject().put("v", 1).put("phase", phase)

    private fun identifiers(values: JSONArray?, limit: Int = 64): JSONArray = JSONArray().apply {
        for (i in 0 until minOf(values?.length() ?: 0, MAX_LIST_ITEMS)) {
            put(identifier(values?.opt(i), limit) ?: JSONObject.NULL)
        }
    }

    private fun identifier(value: Any?, limit: Int): String? = (value as? String)?.takeIf {
        it.isNotEmpty() && it.length <= limit && IDENTIFIER.matches(it)
    }

    private fun url(raw: String?): JSONObject {
        val result = JSONObject()
        if (raw == null || raw.length > MAX_URL_INPUT) return result.put("valid", false)
        val uri = try { URI(raw) } catch (_: Exception) { return result.put("valid", false) }
        result.put("valid", true).put("scheme", identifier(uri.scheme, 32) ?: JSONObject.NULL)
        if (uri.isOpaque) return result.put("opaque", true)
        result.put("host", identifier(uri.host, 128) ?: JSONObject.NULL)
        val path = uri.rawPath
        result.put("path", path?.takeIf { it.length <= 256 }?.let { HostLogContent.sanitize(it, 256) } ?: JSONObject.NULL)
        val query = uri.rawQuery
        val keys = JSONArray()
        val count = if (query.isNullOrEmpty()) 0 else query.count { it == '&' } + 1
        if (query != null) {
            var start = 0
            repeat(minOf(count, MAX_LIST_ITEMS)) {
                val end = query.indexOf('&', start).let { if (it < 0) query.length else it }
                val equal = query.indexOf('=', start).takeIf { it in start until end } ?: end
                val key = try {
                    URLDecoder.decode(query.substring(start, equal), StandardCharsets.UTF_8.name())
                } catch (_: Exception) { null }
                keys.put(identifier(key, 32) ?: JSONObject.NULL)
                start = end + 1
            }
        }
        // Neither query values, user-info nor fragments are included, even in error messages.
        return result.put("queryKeys", keys).put("queryKeyCount", count)
    }

    private companion object {
        const val SYNC_CONFIG = "sync更新本地云弹窗配置，配置为："
        const val CACHED_CONFIG = "加载本地云弹窗配置: "
        const val TRIGGER = "云弹窗时机触发:"
        const val RENDER = "弹窗准备渲染"
        const val SUCCESS = "弹窗渲染成功"
        const val RELEASE = "unMarkShowingDialogName:"
        const val DISPATCH = " 弹出显示，展示链接："
        const val CANDIDATES = " 时机到达，准备展示的弹窗："
        const val MAX_CONFIG_INPUT = 128 * 1024
        const val MAX_EVENT_INPUT = 16 * 1024
        const val MAX_CONFIG_OUTPUT = 64 * 1024
        const val MAX_CONFIG_ITEMS = 128
        const val MAX_LIST_ITEMS = 12
        const val MAX_URL_INPUT = 8 * 1024
        val IDENTIFIER = Regex("[A-Za-z0-9_.-]+")
        val ATTEMPT_ID = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    }
}
