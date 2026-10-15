package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.BlockedPostUser
import com.forbidad4tieba.hook.config.PostFilterPreferences
import org.json.JSONObject

internal sealed interface PostUserLookupResult {
    data class Found(val user: BlockedPostUser) : PostUserLookupResult
    data object Failed : PostUserLookupResult
    data object InvalidResponse : PostUserLookupResult
}

internal object PostUserLookupResponse {
    fun parse(profileId: String, raw: String?): PostUserLookupResult {
        if (raw.isNullOrBlank()) return PostUserLookupResult.Failed
        return try {
            val root = JSONObject(raw)
            if (!root.has("error_code")) return PostUserLookupResult.InvalidResponse
            if (root.optString("error_code") != "0") return PostUserLookupResult.Failed
            val user = root.optJSONObject("user") ?: return PostUserLookupResult.InvalidResponse
            val returnedProfileId = PostFilterPreferences.normalizeUserId(user.optString("tieba_uid"))
            if (returnedProfileId != profileId) return PostUserLookupResult.InvalidResponse
            val uid = PostFilterPreferences.normalizeUserId(user.optString("id"))
                ?.takeIf { it.toLongOrNull()?.let { number -> number > 0 } == true }
                ?: return PostUserLookupResult.InvalidResponse
            val nickname = (user.opt("name_show") as? String).orEmpty().trim()
                .ifEmpty { (user.opt("name") as? String).orEmpty().trim() }
            if (nickname.isEmpty()) return PostUserLookupResult.InvalidResponse
            PostUserLookupResult.Found(BlockedPostUser(uid, profileId, nickname))
        } catch (_: Exception) {
            PostUserLookupResult.InvalidResponse
        }
    }
}
