package com.forbidad4tieba.hook.feature.signin

import org.junit.Assert.*
import org.junit.Test

class AutoSignInTaskTest {
    private val alive = SignInForum("1", "正常吧", 8)
    private val gone = SignInForum("2", "失效吧", 8)
    private val denied = SignInFailure(SignInFailureKind.API, "fixture_deleted", "贴吧不存在")

    @Test fun unavailableForumEndsTaskAndKeepsActualFailureWithoutRepeatedStartupRequests() {
        val api = FakeGateway(listOf(alive, gone))
        val state = SignInDayState("20260912")
        val report = task(api).run(state, false)!!
        assertEquals(1, report.signed)
        assertEquals(listOf(gone), report.failures.map { it.forum })
        assertEquals(denied, report.failures.single().failure)
        assertTrue(state.automaticDone)
        assertEquals(3, api.calls.count { it == gone.key })
        val savedCalls = api.calls.toList()
        val fetches = api.fetches
        assertEquals(report, task(api).run(AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state)), false))
        assertEquals(savedCalls, api.calls)
        assertEquals(fetches, api.fetches)
    }

    @Test fun badFirstBatchDoesNotPreventLaterBatchesFromSigning() {
        val api = FakeGateway(listOf(gone, alive), batchSize = 1)
        val report = task(api).run(SignInDayState("20260912"), false)!!
        assertEquals(1, report.signed)
        assertEquals(1, api.calls.count { it == alive.key })
        assertEquals(3, api.calls.count { it == gone.key })
    }

    @Test fun manualRetryResetsFailedBudgetButDoesNotResendSuccessfulForums() {
        val api = FakeGateway(listOf(alive, gone))
        val state = SignInDayState("20260912")
        task(api).run(state, false)
        api.recovered = true
        val report = task(api).run(state, true)!!
        assertFalse(report.hasFailures)
        assertEquals(2, report.signed)
        assertEquals(1, api.calls.count { it == alive.key })
        assertEquals(4, api.calls.count { it == gone.key })
    }

    @Test fun newDayAllowsRetryOfPreviouslyUnavailableForum() {
        val api = FakeGateway(listOf(gone))
        task(api).run(SignInDayState("20260912"), false)
        task(api).run(SignInDayState("20260913"), false)
        assertEquals(6, api.calls.size)
    }

    @Test fun attemptsSurviveProcessDeathDuringRequest() {
        val api = FakeGateway(listOf(gone))
        var saved = ""
        api.crashOnBatch = true
        try {
            task(api, save = { saved = AutoSignInStateCodec.encode(it); true })
                .run(SignInDayState("20260912"), false)
            fail("Expected simulated process death")
        } catch (_: SimulatedDeath) { }
        api.crashOnBatch = false
        val restored = AutoSignInStateCodec.decode(saved)
        assertEquals(1, restored.forums.getValue(gone.key).attempts)
        task(api).run(restored, false)
        assertEquals(3, api.calls.size)
    }

    @Test fun persistenceFailureStopsBeforeSendingSignRequests() {
        val api = FakeGateway(listOf(gone))
        var saves = 0
        try {
            task(api, save = { ++saves < 2 }).run(SignInDayState("20260912"), false)
            fail("Expected persistence failure")
        } catch (_: IllegalStateException) { }
        assertTrue(api.calls.isEmpty())
    }

    @Test fun disabledOrChangedAccountStopsWithoutNetworkOrFeedback() {
        val api = FakeGateway(listOf(gone))
        assertNull(task(api, allowed = { false }).run(SignInDayState("20260912"), false))
        assertEquals(0, api.fetches)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun switchOffBetweenAttemptsStopsRequests() {
        val api = FakeGateway(listOf(gone))
        assertNull(task(api, allowed = { api.calls.isEmpty() }).run(SignInDayState("20260912"), false))
        assertEquals(1, api.calls.size)
    }

    @Test fun disappearingForumIsNotFalselyCountedAsSigned() {
        val api = FakeGateway(listOf(gone))
        api.finalForums = emptyList()
        val result = task(api).run(SignInDayState("20260912"), false)!!
        assertEquals(0, result.signed)
        assertEquals(1, result.failures.size)
    }

    @Test fun finalSignedFlagRecoversLostResponse() {
        val api = FakeGateway(listOf(gone))
        api.finalForums = listOf(gone.copy(signed = true))
        val result = task(api).run(SignInDayState("20260912"), false)!!
        assertEquals(1, result.signed)
        assertFalse(result.hasFailures)
    }

    @Test fun alreadySignedForumsDoNotConsumeAttempts() {
        val api = FakeGateway(listOf(alive.copy(signed = true)))
        val result = task(api).run(SignInDayState("20260912"), false)!!
        assertTrue(api.calls.isEmpty())
        assertEquals(1, result.alreadySigned)
        assertEquals(0, result.signed)
    }

    @Test fun listFetchFailuresHavePersistedCooldownAndDailyLimit() {
        val api = FakeGateway(listOf(gone))
        api.fetchFailure = SignInFailure(SignInFailureKind.NO_RESPONSE)
        val state = SignInDayState("20260912")
        var time = 1L
        fun run() = AutoSignInTask(api, { true }, { true }, { true }, { time }).run(state, false)
        run(); run()
        assertEquals(1, api.fetches)
        repeat(2) { time += 31 * 60 * 1000; run() }
        time += 31 * 60 * 1000; run()
        assertEquals(3, api.fetches)
        assertTrue(state.automaticDone)
        assertTrue(state.report!!.hasFailures)
    }

    @Test fun serverNoticePausesWholeTaskWithoutBypassingItViaSingleSign() {
        val api = FakeGateway(listOf(gone))
        api.notice = SignInFailure(SignInFailureKind.SERVER_NOTICE, message = "请处理官方提示")
        val result = task(api).run(SignInDayState("20260912"), false)!!
        assertEquals(api.notice, result.taskFailure)
        assertTrue(api.calls.isEmpty())
    }

    private fun task(api: FakeGateway, save: (SignInDayState) -> Boolean = { true },
                     allowed: () -> Boolean = { true }) =
        AutoSignInTask(api, save, allowed, { true }, { 1000L })

    private class SimulatedDeath : RuntimeException()

    private inner class FakeGateway(private val forums: List<SignInForum>, private val batchSize: Int = 50) : SignInGateway {
        val calls = mutableListOf<String>()
        var fetches = 0
        var recovered = false
        var crashOnBatch = false
        var finalForums: List<SignInForum>? = null
        var fetchFailure: SignInFailure? = null
        var notice: SignInFailure? = null
        override fun fetchForums(): SignInSnapshotResult {
            fetches++
            fetchFailure?.let { return SignInSnapshotResult(failure = it) }
            return SignInSnapshotResult(SignInSnapshot(if (fetches > 1) finalForums ?: forums else forums,
                true, batchSize, 7, false, notice))
        }
        override fun signBatch(forums: List<SignInForum>): SignInBatchResult {
            calls.addAll(forums.map { it.key })
            if (crashOnBatch) throw SimulatedDeath()
            return SignInBatchResult(forums.associate { it.key to result(it) })
        }
        override fun signSingle(forum: SignInForum): SignInAttempt {
            calls.add(forum.key)
            return result(forum)
        }
        private fun result(forum: SignInForum) =
            if (forum.id != gone.id || recovered) SignInAttempt(true) else SignInAttempt(false, denied)
    }
}
