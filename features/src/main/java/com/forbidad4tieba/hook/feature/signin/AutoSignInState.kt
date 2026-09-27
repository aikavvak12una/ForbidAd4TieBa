package com.forbidad4tieba.hook.feature.signin

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal class AutoSignInStateStore(private val prefs: SharedPreferences, accountKey: String) {
    private val key = "auto_sign_state_v1_$accountKey"

    fun load(day: String): SignInDayState {
        val stored = prefs.getString(key, null)?.let(AutoSignInStateCodec::decode)
        return stored?.takeIf { it.day == day } ?: SignInDayState(day)
    }

    fun latestReport(): SignInReport? = prefs.getString(key, null)
        ?.let(AutoSignInStateCodec::decode)?.report

    // Called only by the sign-in worker. Progress must reach disk before another request.
    fun save(state: SignInDayState): Boolean = prefs.edit()
        .putString(key, AutoSignInStateCodec.encode(state)).commit()
}

internal object AutoSignInStateCodec {
    fun encode(state: SignInDayState): String = JSONObject().apply {
        put("version", 2)
        put("day", state.day)
        put("automaticDone", state.automaticDone)
        put("fetchAttempts", state.fetchAttempts)
        put("nextAutomaticAt", state.nextAutomaticAt)
        put("batchAttempted", state.batchAttempted)
        put("notifiedFingerprint", state.notifiedFingerprint)
        put("forums", JSONArray().apply {
            state.forums.values.forEach { entry -> put(JSONObject().apply {
                put("forum", forumJson(entry.forum))
                put("attempts", entry.attempts)
                put("signed", entry.signed)
                put("failure", entry.failure?.let(::failureJson))
            }) }
        })
        put("report", state.report?.let(::reportJson))
    }.toString()

    fun decode(raw: String): SignInDayState {
        val json = JSONObject(raw)
        val version = json.getInt("version")
        require(version in 1..2) { "Unsupported sign-in state version" }
        val day = json.getString("day")
        require(day.matches(Regex("\\d{8}"))) { "Invalid sign-in day" }
        val state = SignInDayState(day, automaticDone = json.getBoolean("automaticDone"),
            fetchAttempts = json.getInt("fetchAttempts"), nextAutomaticAt = json.getLong("nextAutomaticAt"),
            notifiedFingerprint = json.optString("notifiedFingerprint").takeIf { it.isNotEmpty() },
            batchAttempted = version == 2 && json.getBoolean("batchAttempted"))
        require(state.fetchAttempts >= 0 && state.nextAutomaticAt >= 0)
        val forums = json.getJSONArray("forums")
        for (index in 0 until forums.length()) {
            val item = forums.getJSONObject(index)
            val forum = forum(item.getJSONObject("forum"))
            val attempts = item.getInt("attempts")
            val maxAttempts = if (version == 1) 3 else AutoSignInTask.MAX_ATTEMPTS
            require(attempts in 0..maxAttempts && forum.key !in state.forums)
            state.forums[forum.key] = SignInForumState(forum, attempts, item.getBoolean("signed"),
                item.optJSONObject("failure")?.let(::failure))
        }
        state.report = json.optJSONObject("report")?.let(::report)
        require(state.report == null || state.report?.day == day)
        if (version == 1) {
            // v1 mixed batch and individual reservations. Start one fresh budget for
            // unsigned forums on upgrade; preserve every confirmed success.
            state.forums.values.filterNot { it.signed }.forEach {
                it.attempts = 0
                it.failure = null
            }
            if (state.automaticDone && (state.report?.allSucceeded != true ||
                    state.forums.values.any { !it.signed })) {
                state.automaticDone = false
                state.nextAutomaticAt = 0L
            }
        }
        return state
    }

    private fun forumJson(forum: SignInForum) = JSONObject().apply {
        put("id", forum.id); put("name", forum.name); put("level", forum.level); put("signed", forum.signed)
    }

    private fun forum(json: JSONObject) = SignInForum(json.getString("id"), json.getString("name"),
        json.getInt("level"), json.getBoolean("signed"))

    private fun failureJson(failure: SignInFailure) = JSONObject().apply {
        put("kind", failure.kind.name); put("code", failure.code); put("message", failure.message)
    }

    private fun failure(json: JSONObject) = SignInFailure(SignInFailureKind.valueOf(json.getString("kind")),
        json.optString("code").takeIf { it.isNotEmpty() }, json.getString("message"))

    private fun reportJson(report: SignInReport) = JSONObject().apply {
        put("day", report.day); put("finishedAt", report.finishedAt); put("total", report.total)
        put("signed", report.signed); put("alreadySigned", report.alreadySigned)
        put("taskFailure", report.taskFailure?.let(::failureJson))
        put("failures", JSONArray().apply {
            report.failures.forEach { entry -> put(JSONObject().apply {
                put("forum", forumJson(entry.forum)); put("failure", failureJson(entry.failure))
                put("attempts", entry.attempts)
            }) }
        })
    }

    private fun report(json: JSONObject): SignInReport {
        val failures = json.getJSONArray("failures")
        return SignInReport(json.getString("day"), json.getLong("finishedAt"), json.getInt("total"),
            json.getInt("signed"), json.getInt("alreadySigned"), List(failures.length()) { index ->
                val entry = failures.getJSONObject(index)
                SignInFailedForum(forum(entry.getJSONObject("forum")),
                    failure(entry.getJSONObject("failure")), entry.getInt("attempts"))
            }, json.optJSONObject("taskFailure")?.let(::failure))
    }
}

internal object AutoSignInNoticePolicy {
    fun accountKey(userId: String): String = digest(userId).take(24)

    fun fingerprint(report: SignInReport): String = digest(JSONObject().apply {
        put("day", report.day)
        put("failures", JSONArray().apply {
            report.failures.sortedBy { it.forum.key }.forEach { entry ->
                put(JSONArray(listOf(entry.forum.key, entry.failure.kind.name, entry.failure.code.orEmpty())))
            }
        })
        put("task", report.taskFailure?.let { JSONArray(listOf(it.kind.name, it.code.orEmpty())) })
    }.toString())

    fun shouldNotify(report: SignInReport, lastFingerprint: String?): Boolean =
        report.isFinal && fingerprint(report) != lastFingerprint

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
