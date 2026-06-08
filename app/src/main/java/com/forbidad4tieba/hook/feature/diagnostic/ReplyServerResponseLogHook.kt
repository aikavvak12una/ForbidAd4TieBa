package com.forbidad4tieba.hook.feature.diagnostic

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.ReplyServerResponseLogSymbols
import org.json.JSONObject

object ReplyServerResponseLogHook {
    private const val TAG = "[ReplyServerResponseLogHook]"
    private const val MAX_LOG_CONTENT_CHARS = 8192
    private const val MAX_LOG_CHUNKS = 3

    @Volatile private var hooked = false

    internal fun hook(targets: ReplyServerResponseLogSymbols) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return
        try {
            mod.hook(targets.decodeMethod).intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.shouldOutputDetailedLogs()) {
                    try {
                        val json = targets.resultJsonField.get(chain.thisObject) as? JSONObject
                        if (json == null) {
                            XposedCompat.logD("$TAG decoded reply response: resultJSON=null")
                        } else {
                            logResponse(json)
                        }
                    } catch (t: Throwable) {
                        XposedCompat.logD("$TAG log failed: ${t.message}")
                    }
                }
                result
            }
            XposedCompat.log(
                "$TAG hook INSTALLED: ${targets.decodeMethod.declaringClass.name}.${targets.decodeMethod.name}(int,byte[])",
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("$TAG install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun logResponse(json: JSONObject) {
        val summary = buildSummary(json)
        logLong("$TAG summary=$summary response=$json")
    }

    private fun buildSummary(json: JSONObject): JSONObject {
        val summary = JSONObject()
        putIfPresent(summary, "tid", json)
        putIfPresent(summary, "pid", json)
        putIfPresent(summary, "msg", json)
        putIfPresent(summary, "pre_msg", json)
        putIfPresent(summary, "_ext_msg", json)

        val antiStat = json.optJSONObject("anti_stat")
        if (antiStat != null) {
            val antiSummary = JSONObject()
            putIfPresent(antiSummary, "reply_private_flag", antiStat)
            putIfPresent(antiSummary, "hide_stat", antiStat)
            putIfPresent(antiSummary, "block_stat", antiStat)
            putIfPresent(antiSummary, "ifpost", antiStat)
            putIfPresent(antiSummary, "ifposta", antiStat)
            putIfPresent(antiSummary, "forbid_flag", antiStat)
            putIfPresent(antiSummary, "forbid_info", antiStat)
            summary.put("anti_stat", antiSummary)
        }

        val error = json.optJSONObject("error")
        if (error != null) {
            val errorSummary = JSONObject()
            putIfPresent(errorSummary, "errno", error)
            putIfPresent(errorSummary, "errmsg", error)
            putIfPresent(errorSummary, "error_user_msg", error)
            summary.put("error", errorSummary)
        }

        return summary
    }

    private fun putIfPresent(target: JSONObject, key: String, source: JSONObject) {
        if (!source.has(key)) return
        target.put(key, if (source.isNull(key)) JSONObject.NULL else source.opt(key))
    }

    private fun logLong(msg: String) {
        val rawContent = if (msg.startsWith(TAG)) msg.substring(TAG.length).trimStart() else msg
        val content = if (rawContent.length > MAX_LOG_CONTENT_CHARS) {
            rawContent.take(MAX_LOG_CONTENT_CHARS) + "...[truncated ${rawContent.length - MAX_LOG_CONTENT_CHARS} chars]"
        } else {
            rawContent
        }
        val chunkPrefix = "$TAG "
        val maxContent = 3900 - chunkPrefix.length - 12
        if (content.length <= maxContent) {
            XposedCompat.logD("$chunkPrefix$content")
            return
        }

        val totalParts = ((content.length + maxContent - 1) / maxContent).coerceAtMost(MAX_LOG_CHUNKS)
        var offset = 0
        var part = 1
        while (offset < content.length && part <= MAX_LOG_CHUNKS) {
            val end = (offset + maxContent).coerceAtMost(content.length)
            XposedCompat.logD("$chunkPrefix[$part/$totalParts] ${content.substring(offset, end)}")
            offset = end
            part++
        }
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
