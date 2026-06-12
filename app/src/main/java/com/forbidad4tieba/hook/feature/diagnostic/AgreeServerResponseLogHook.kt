package com.forbidad4tieba.hook.feature.diagnostic

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.AgreeServerResponseLogSymbols
import org.json.JSONObject

object AgreeServerResponseLogHook {
    private const val TAG = "[AgreeServerResponseLogHook]"
    private const val MAX_LOG_CONTENT_CHARS = 8192
    private const val MAX_LOG_CHUNKS = 3

    @Volatile private var hooked = false

    internal fun hook(targets: AgreeServerResponseLogSymbols) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return
        try {
            mod.hook(targets.decodeLogicMethod).intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.shouldOutputDetailedLogs()) {
                    try {
                        val json = chain.args.getOrNull(1) as? JSONObject
                        if (json == null) {
                            XposedCompat.logD("$TAG decoded agree response: retJson=null")
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
                "$TAG hook INSTALLED: " +
                    "${targets.decodeLogicMethod.declaringClass.name}.${targets.decodeLogicMethod.name}(int,JSONObject)",
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
        copyKeys(
            target = summary,
            source = json,
            keys = arrayOf("errno", "errmsg", "error_code", "error_msg", "logid", "server_time", "time"),
        )

        val error = json.optJSONObject("error")
        if (error != null) {
            val errorSummary = JSONObject()
            copyKeys(errorSummary, error, arrayOf("errno", "errmsg", "error_code", "error_msg", "error_user_msg"))
            summary.put("error", errorSummary)
        }

        val data = json.optJSONObject("data")
        if (data != null) {
            val dataSummary = JSONObject()
            copyKeys(
                target = dataSummary,
                source = data,
                keys = arrayOf("toast", "toast_msg", "toastMsg", "is_first_agree"),
            )
            val agree = data.optJSONObject("agree")
            if (agree != null) {
                val agreeSummary = JSONObject()
                copyKeys(
                    target = agreeSummary,
                    source = agree,
                    keys = arrayOf(
                        "agree_num",
                        "disagree_num",
                        "diff_agree_num",
                        "agree_type",
                        "has_agree",
                        "score",
                        "is_first_agree",
                    ),
                )
                dataSummary.put("agree", agreeSummary)
            }
            if (data.has("contri_info")) {
                dataSummary.put(
                    "contri_info",
                    if (data.isNull("contri_info")) JSONObject.NULL else data.opt("contri_info"),
                )
            }
            summary.put("data", dataSummary)
        }

        return summary
    }

    private fun copyKeys(target: JSONObject, source: JSONObject, keys: Array<String>) {
        keys.forEach { key -> putIfPresent(target, key, source) }
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
