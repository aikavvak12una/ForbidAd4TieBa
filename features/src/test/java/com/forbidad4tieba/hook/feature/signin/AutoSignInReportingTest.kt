package com.forbidad4tieba.hook.feature.signin

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AutoSignInReportingTest {
    private val forum = SignInForum("42", "失效吧")
    private val failure = SignInFailure(SignInFailureKind.API, "fixture_error", "贴吧不存在")
    private fun report(day: String = "20260912") = SignInReport(day, 1000L, 4, 1, 2,
        listOf(SignInFailedForum(forum, failure, 4)))

    @Test fun persistedBudgetsAndReportsAreIsolatedBetweenAccounts() {
        val prefs = preferences()
        val first = AutoSignInStateStore(prefs, AutoSignInNoticePolicy.accountKey("user-1"))
        val second = AutoSignInStateStore(prefs, AutoSignInNoticePolicy.accountKey("user-2"))
        val day = SignInDayState("20260912", report = report())
        day.forums[forum.key] = SignInForumState(forum, 4, false, failure)
        assertTrue(first.save(day))
        assertEquals(day, AutoSignInStateStore(prefs, AutoSignInNoticePolicy.accountKey("user-1")).load("20260912"))
        assertFalse(second.load("20260912").automaticDone)
        assertNull(second.latestReport())
        assertEquals(report(), first.latestReport())
        assertTrue(first.load("20260913").forums.isEmpty())
        assertFalse(first.load("20260913").automaticDone)
    }

    @Test fun identicalDailyFailuresDoNotNotifyAgainAfterManualRetry() {
        val original = report()
        val retried = original.copy(finishedAt = 9000L, signed = 0, alreadySigned = 3)
        assertFalse(AutoSignInNoticePolicy.shouldNotify(retried, AutoSignInNoticePolicy.fingerprint(original)))
        assertTrue(AutoSignInNoticePolicy.shouldNotify(report("20260913"), AutoSignInNoticePolicy.fingerprint(original)))
    }

    @Test fun successNotificationDeduplicationSurvivesPersistence() {
        val success = SignInReport("20260912", 1000L, 4, 2, 2, emptyList())
        val state = SignInDayState("20260912", automaticDone = true, report = success,
            notifiedFingerprint = AutoSignInNoticePolicy.fingerprint(success))
        val restored = AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state))
        assertEquals(state, restored)
        assertFalse(AutoSignInNoticePolicy.shouldNotify(success, restored.notifiedFingerprint))
    }

    @Test fun roundTripPreservesAttemptsResultsAndNotificationDeduplication() {
        val state = SignInDayState("20260912", report = report(),
            notifiedFingerprint = AutoSignInNoticePolicy.fingerprint(report()))
        state.forums[forum.key] = SignInForumState(forum, 4, false, failure)
        assertEquals(state, AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state)))
    }

    @Test fun legacyMixedAttemptBudgetsReopenOnlyUnsuccessfulForums() {
        val signed = SignInForum("1", "已成功吧")
        val state = SignInDayState("20260912", automaticDone = true, fetchAttempts = 3,
            report = report().copy(failures = listOf(SignInFailedForum(forum, failure, 3))))
        state.forums[signed.key] = SignInForumState(signed, 1, true)
        state.forums[forum.key] = SignInForumState(forum, 3, false, failure)
        val restored = AutoSignInStateCodec.decode(legacyJson(state))

        assertFalse(restored.automaticDone)
        assertTrue(restored.forums.getValue(signed.key).signed)
        assertEquals(1, restored.forums.getValue(signed.key).attempts)
        assertEquals(0, restored.forums.getValue(forum.key).attempts)
        assertEquals(2, JSONObject(AutoSignInStateCodec.encode(restored)).getInt("version"))
    }

    @Test fun legacyTaskErrorDoesNotKeepTheDayCompleted() {
        val state = SignInDayState("20260912", automaticDone = true, fetchAttempts = 3,
            nextAutomaticAt = 5000L, report = SignInReport("20260912", 1000L, 0, 0, 0,
                emptyList(), SignInFailure(SignInFailureKind.TIMEOUT)))
        val restored = AutoSignInStateCodec.decode(legacyJson(state))
        assertFalse(restored.automaticDone)
        assertEquals(0L, restored.nextAutomaticAt)
        assertFalse(AutoSignInNoticePolicy.shouldNotify(restored.report!!, null))
    }

    @Test fun corruptOrUnsupportedStateDoesNotResetRetryCounters() {
        for (raw in listOf("{}", """{"version":3}""", "not-json")) {
            assertThrows(Exception::class.java) { AutoSignInStateCodec.decode(raw) }
        }
    }

    @Test fun taskErrorsPendingForumsAndUnfinishedRetriesNeverNotify() {
        for (kind in SignInFailureKind.entries) {
            val paused = SignInReport("20260925", 1000L, 0, 0, 0, emptyList(), SignInFailure(kind))
            assertFalse(kind.name, AutoSignInNoticePolicy.shouldNotify(paused, null))
        }
        assertFalse(AutoSignInNoticePolicy.shouldNotify(report().copy(total = 5), null))
        val retrying = report().copy(failures = listOf(SignInFailedForum(forum, failure, 3)))
        assertFalse(AutoSignInNoticePolicy.shouldNotify(retrying, null))
        assertTrue(AutoSignInNoticePolicy.shouldNotify(report(), null))
    }

    private fun legacyJson(state: SignInDayState): String =
        JSONObject(AutoSignInStateCodec.encode(state)).apply {
            put("version", 1)
            remove("batchAttempted")
        }.toString()

    private fun preferences(): SharedPreferences {
        val values = hashMapOf<String, String?>()
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0]] ?: args[1]
                "edit" -> {
                    val pending = hashMapOf<String, String?>()
                    Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { editor, edit, params ->
                        when (edit.name) {
                            "putString" -> { pending[params!![0] as String] = params[1] as String?; editor }
                            "commit" -> { values.putAll(pending); true }
                            else -> error("Unexpected editor call: ${edit.name}")
                        }
                    }
                }
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
