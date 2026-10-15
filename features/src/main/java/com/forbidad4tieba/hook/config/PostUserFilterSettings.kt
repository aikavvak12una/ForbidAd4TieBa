package com.forbidad4tieba.hook.config

import org.json.JSONArray
import org.json.JSONObject

/** A saved entry always contains the identity confirmed by a profile-ID lookup. */
internal data class BlockedPostUser(
    val userId: String,
    val profileId: String,
    val nickname: String,
)

internal object PostUserFilterSettings {
    private const val VERSION = 1
    private val sharedProfileId = Regex("(?=#\\s*([0-9]+)\\s*#)")

    fun filterProfileIdInput(raw: String): String {
        val matches = sharedProfileId.findAll(raw).take(2).toList()
        if (matches.isNotEmpty()) return matches.singleOrNull()?.groupValues?.get(1).orEmpty()
        val firstHash = raw.indexOf('#')
        if (firstHash >= 0 && raw.indexOf('#', firstHash + 1) >= 0) return ""
        return raw.filter { it in '0'..'9' }
    }

    fun parseProfileIdInput(raw: String): String? =
        PostFilterPreferences.normalizeUserId(filterProfileIdInput(raw))

    fun decode(raw: String?): List<BlockedPostUser> {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return emptyList()
        return try {
            val root = JSONObject(value)
            if (root.opt("version") != VERSION) return emptyList()
            val entries = root.optJSONArray("users") ?: return emptyList()
            val users = (0 until entries.length()).mapNotNull { index ->
                val item = entries.optJSONObject(index) ?: return@mapNotNull null
                val uid = PostFilterPreferences.normalizeUserId(item.optString("userId"))
                    ?: return@mapNotNull null
                val profileId = PostFilterPreferences.normalizeUserId(item.optString("profileId"))
                    ?: return@mapNotNull null
                val nickname = (item.opt("nickname") as? String)?.trim()?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                if (uid.toLongOrNull()?.takeIf { it > 0 } == null) return@mapNotNull null
                BlockedPostUser(uid, profileId, nickname)
            }
            val conflictingProfiles = users.groupBy { it.profileId }
                .filterValues { group -> group.map { it.userId }.distinct().size > 1 }.keys
            val conflictingUsers = users.groupBy { it.userId }
                .filterValues { group -> group.map { it.profileId }.distinct().size > 1 }.keys
            users.filter { it.profileId !in conflictingProfiles && it.userId !in conflictingUsers }
                .distinctBy { it.userId }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encode(users: List<BlockedPostUser>): String {
        val entries = JSONArray()
        for (user in users) {
            val item = JSONObject().put("userId", user.userId)
                .put("profileId", user.profileId).put("nickname", user.nickname)
            entries.put(item)
        }
        return JSONObject().put("version", VERSION).put("users", entries).toString()
    }
}
