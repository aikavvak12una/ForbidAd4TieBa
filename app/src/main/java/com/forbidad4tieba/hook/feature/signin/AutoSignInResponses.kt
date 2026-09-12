package com.forbidad4tieba.hook.feature.signin

import org.json.JSONObject

internal object AutoSignInResponses {
    fun snapshot(json: JSONObject, nowSeconds: Long): SignInSnapshotResult {
        apiFailure(json)?.let { return SignInSnapshotResult(failure = it) }
        val body = payload(json)
        val array = body.optJSONArray("forum_info") ?: json.optJSONArray("forum_info")
            ?: return SignInSnapshotResult(failure = SignInFailure(SignInFailureKind.INVALID_RESPONSE))
        val forums = ArrayList<SignInForum>(array.length())
        val forumKeys = HashSet<String>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: return SignInSnapshotResult(failure = SignInFailure(SignInFailureKind.INVALID_RESPONSE))
            val forum = SignInForum(
                text(item, "forum_id").ifEmpty { text(item, "id") },
                text(item, "forum_name"), item.optInt("user_level", 0),
                text(item, "is_sign_in") == "1",
            )
            if (forum.id.isEmpty() && forum.name.isEmpty()) {
                return SignInSnapshotResult(failure = SignInFailure(SignInFailureKind.INVALID_RESPONSE))
            }
            if (!forumKeys.add(forum.key)) {
                return SignInSnapshotResult(failure = SignInFailure(SignInFailureKind.INVALID_RESPONSE))
            }
            forums.add(forum)
        }
        val user = body.optJSONObject("user")
        val vip = user?.optJSONObject("vipInfo") ?: user?.optJSONObject("vip_info")
        val isVip = (vip?.optInt("v_status", 0) ?: 0) >= 1 &&
            (vip?.optLong("e_time", 0L) ?: 0L) > nowSeconds
        val allLevels = when (text(body, "can_use")) { "1" -> true; "0" -> false; else -> isVip }
        return SignInSnapshotResult(SignInSnapshot(
            forums, text(body, "valid") == "1",
            body.optInt("sign_max_num_new", 50).takeIf { it > 0 } ?: 50,
            body.optInt("level", 7).takeIf { it > 0 } ?: 7, allLevels,
            if (text(body, "show_dialog") == "1") SignInFailure(SignInFailureKind.SERVER_NOTICE,
                message = text(body, "sign_notice")) else null,
        ))
    }

    fun single(json: JSONObject): SignInAttempt {
        val body = payload(json)
        if (listOfNotNull(body.optJSONObject("user_info"), json.optJSONObject("user_info"))
                .any { text(it, "is_sign_in") == "1" }) return SignInAttempt(true)
        apiFailure(json)?.let { return SignInAttempt(false, it) }
        return if (hasSuccessCode(json)) SignInAttempt(true)
        else SignInAttempt(false, SignInFailure(SignInFailureKind.UNCONFIRMED))
    }

    fun batch(json: JSONObject, requested: List<SignInForum>): SignInBatchResult {
        val failure = apiFailure(json)
        val body = payload(json)
        val info = body.optJSONArray("info") ?: json.optJSONArray("info")
        val items = linkedMapOf<String, JSONObject>()
        val duplicates = hashSetOf<String>()
        if (info != null) for (index in 0 until info.length()) {
            val item = info.optJSONObject(index) ?: continue
            val id = text(item, "forum_id").ifEmpty { text(item, "fid") }
                .ifEmpty { text(item, "forumId") }.ifEmpty { text(item, "id") }
            if (id.isEmpty()) continue
            if (items.put(id, item) != null) duplicates.add(id)
        }
        val outcomes = requested.associate { forum ->
            val item = items[forum.id].takeUnless { forum.id in duplicates }
            val signed = item?.let {
                text(it, "signed").ifEmpty { text(it, "is_sign_in") }.ifEmpty { text(it, "sign_status") }
            } == "1"
            val itemFailure = item?.let(::apiFailure)
            forum.key to when {
                signed && itemFailure == null -> SignInAttempt(true)
                else -> SignInAttempt(false, itemFailure ?: failure
                    ?: SignInFailure(SignInFailureKind.UNCONFIRMED))
            }
        }
        return SignInBatchResult(outcomes, failure)
    }

    fun apiFailure(json: JSONObject): SignInFailure? {
        val objects = listOfNotNull(json.optJSONObject("error"), json,
            payload(json).takeUnless { it === json }?.optJSONObject("error"),
            payload(json).takeUnless { it === json })
        for (obj in objects) {
            val code = text(obj, "errno").ifEmpty { text(obj, "error_code") }
                .ifEmpty { text(obj, "code") }
            if (code.isNotEmpty() && code != "0") {
                return SignInFailure(SignInFailureKind.API, code, errorMessage(obj))
            }
        }
        return null
    }

    private fun hasSuccessCode(json: JSONObject): Boolean {
        return listOfNotNull(json.optJSONObject("error"), json, payload(json).optJSONObject("error"), payload(json))
            .any { text(it, "errno") == "0" || text(it, "error_code") == "0" }
    }

    fun errorMessage(json: JSONObject): String = safeMessage(text(json, "error_msg")
        .ifEmpty { text(json, "errmsg") }.ifEmpty { text(json, "usermsg") }.ifEmpty { text(json, "msg") })

    fun safeMessage(message: String): String = message
        .replace(Regex("(?i)\"(BDUSS|STOKEN|tbs)\"\\s*:\\s*\"[^\"]*\""), "\"$1\":\"<redacted>\"")
        .replace(Regex("(?i)(BDUSS|STOKEN|tbs)\\s*[=:]\\s*[^&;\\s]+"), "$1=<redacted>")
        .replace(Regex("[\\r\\n\\t]+"), " ").trim().take(240)

    private fun payload(json: JSONObject): JSONObject = json.optJSONObject("data") ?: json

    private fun text(json: JSONObject, name: String): String =
        if (json.isNull(name)) "" else json.optString(name).trim()
}
