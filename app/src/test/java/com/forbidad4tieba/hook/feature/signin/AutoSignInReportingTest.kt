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

    @Test fun changedFailureCodeProducesAnUpdatedSummary() {
        val original = report()
        val changed = original.copy(failures = listOf(original.failures.single()
            .copy(failure = failure.copy(code = "different"))))
        assertTrue(AutoSignInNoticePolicy.shouldNotify(changed, AutoSignInNoticePolicy.fingerprint(original)))
    }

    @Test fun successNotifiesOncePerDayIncludingWhenEverythingWasAlreadySigned() {
        val success = SignInReport("20260912", 1000L, 4, 4, 0, emptyList())
        assertTrue(AutoSignInNoticePolicy.shouldNotify(success, null))
        val fingerprint = AutoSignInNoticePolicy.fingerprint(success)
        val checkedAgain = success.copy(finishedAt = 9000L, signed = 0, alreadySigned = 4)
        assertFalse(AutoSignInNoticePolicy.shouldNotify(checkedAgain, fingerprint))
        assertTrue(AutoSignInNoticePolicy.shouldNotify(checkedAgain.copy(day = "20260913"), fingerprint))
        assertTrue(AutoSignInNoticePolicy.shouldNotify(checkedAgain, null))
    }

    @Test fun recoveryPublishesSuccessAfterThePreviousFailureNotification() {
        val failed = report()
        val success = failed.copy(signed = 2, failures = emptyList())
        assertTrue(AutoSignInNoticePolicy.shouldNotify(success, AutoSignInNoticePolicy.fingerprint(failed)))
        assertTrue(AutoSignInNoticePolicy.shouldNotify(failed, AutoSignInNoticePolicy.fingerprint(success)))
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

    @Test fun legacySuccessfulDayAndNotificationFingerprintRemainComplete() {
        val success = SignInReport("20260912", 1000L, 1, 1, 0, emptyList())
        val state = SignInDayState("20260912", automaticDone = true, report = success,
            notifiedFingerprint = AutoSignInNoticePolicy.fingerprint(success))
        state.forums[forum.key] = SignInForumState(forum, 1, true)
        val restored = AutoSignInStateCodec.decode(legacyJson(state))

        assertTrue(restored.automaticDone)
        assertEquals(state.forums, restored.forums)
        assertEquals(success, restored.report)
        assertFalse(AutoSignInNoticePolicy.shouldNotify(success, restored.notifiedFingerprint))
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

    @Test fun nullErrorCodeSurvivesPersistenceWithoutBecomingStringNull() {
        val state = SignInDayState("20260912", report = report().copy(failures = listOf(
            SignInFailedForum(forum, SignInFailure(SignInFailureKind.TIMEOUT), 3))))
        val restored = AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state))
        assertNull(restored.report!!.failures.single().failure.code)
        val detail = AutoSignInReportText.detail(restored.report!!)
        assertTrue(detail.contains("请求超时"))
        assertTrue(detail.contains("无业务错误码"))
        assertFalse(detail.contains("错误码：null"))
    }

    @Test fun corruptOrUnsupportedStateDoesNotResetRetryCounters() {
        for (raw in listOf("{}", """{"version":3}""", "not-json")) {
            try { AutoSignInStateCodec.decode(raw); fail("Invalid state must be rejected") }
            catch (_: Exception) { }
        }
    }

    @Test fun compactNotificationKeepsFullFailureListInSavedReport() {
        val entries = (1..12).map { SignInFailedForum(SignInForum("$it", "失败${it}吧"), failure, 3) }
        val report = report().copy(total = 12, signed = 0, alreadySigned = 0, failures = entries)
        val compact = AutoSignInReportText.detail(report, 8)
        val full = AutoSignInReportText.detail(report)
        assertTrue(compact.contains("另有 4 个失败项"))
        assertFalse(compact.contains("失败12吧"))
        assertTrue(full.contains("失败12吧"))
        assertTrue(full.contains("fixture_error"))
        assertEquals(12, report.failures.size)
    }

    @Test fun pausedTaskShowsUnprocessedForumsWithoutFalseSuccessCounts() {
        val stopped = SignInReport("20260912", 1000L, 10, 0, 2, emptyList(),
            SignInFailure(SignInFailureKind.SERVER_NOTICE))
        assertTrue(AutoSignInReportText.summary(stopped).contains("待处理 8 个"))
        assertEquals("自动签到暂未完成", AutoSignInReportText.summary(stopped.copy(total = 0, alreadySigned = 0)))
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
