package com.forbidad4tieba.hook.feature.signin

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class AutoSignInReportingTest {
    private val forum = SignInForum("42", "失效吧")
    private val failure = SignInFailure(SignInFailureKind.API, "fixture_error", "贴吧不存在")
    private fun report(day: String = "20260912") = SignInReport(day, 1000L, 4, 1, 2,
        listOf(SignInFailedForum(forum, failure, 3)))

    @Test fun persistedBudgetsAndReportsAreIsolatedBetweenAccounts() {
        val prefs = preferences()
        val first = AutoSignInStateStore(prefs, AutoSignInNoticePolicy.accountKey("user-1"))
        val second = AutoSignInStateStore(prefs, AutoSignInNoticePolicy.accountKey("user-2"))
        val day = SignInDayState("20260912", automaticDone = true, report = report())
        day.forums[forum.key] = SignInForumState(forum, 3, false, failure)
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
        val retried = original.copy(finishedAt = 9000L, signed = 0, alreadySigned = 3,
            failures = listOf(original.failures.single().copy(attempts = 1)))
        assertFalse(AutoSignInNoticePolicy.shouldNotify(retried, AutoSignInNoticePolicy.fingerprint(original)))
        assertTrue(AutoSignInNoticePolicy.shouldNotify(report("20260913"), AutoSignInNoticePolicy.fingerprint(original)))
    }

    @Test fun changedFailureCodeProducesAnUpdatedSummary() {
        val original = report()
        val changed = original.copy(failures = listOf(original.failures.single()
            .copy(failure = failure.copy(code = "different"))))
        assertTrue(AutoSignInNoticePolicy.shouldNotify(changed, AutoSignInNoticePolicy.fingerprint(original)))
    }

    @Test fun successDoesNotGenerateNotification() {
        assertFalse(AutoSignInNoticePolicy.shouldNotify(report().copy(failures = emptyList()), null))
    }

    @Test fun roundTripPreservesAttemptsResultsAndNotificationDeduplication() {
        val state = SignInDayState("20260912", automaticDone = true, report = report(),
            notifiedFingerprint = AutoSignInNoticePolicy.fingerprint(report()))
        state.forums[forum.key] = SignInForumState(forum, 3, false, failure)
        assertEquals(state, AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state)))
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
        for (raw in listOf("{}", """{"version":2}""", "not-json")) {
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
