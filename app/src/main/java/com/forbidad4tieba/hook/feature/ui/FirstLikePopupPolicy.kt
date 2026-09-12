package com.forbidad4tieba.hook.feature.ui

import org.json.JSONException
import org.json.JSONObject

/** The JSON response is inspected before its toast is dispatched, without changing business data. */
internal object FirstLikePopupPolicy {
    private const val MAX_RESPONSE_CHARS = 65_536

    fun shouldBlock(raw: String?): Boolean {
        if (raw.isNullOrBlank() || raw.length > MAX_RESPONSE_CHARS) return false
        return try {
            val response = JSONObject(raw)
            if (response.optString("error_code") != "0") return false
            val error = response.optJSONObject("error")
            if (error != null && error.has("errno") && error.optString("errno") != "0") return false
            val agree = response.optJSONObject("data")?.optJSONObject("agree") ?: return false
            if (agree.optString("is_first_agree") != "1") return false
            val toast = response.optJSONObject("toast") ?: return false
            val content = toast.optJSONArray("content") ?: return false
            content.length() > 0
        } catch (_: JSONException) {
            // Unknown/malformed responses keep the host's normal error path.
            false
        }
    }
}
