package com.forbidad4tieba.hook.feature.signin

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AutoSignInTaskTest {
    private val alive = SignInForum("1", "正常吧", 8)
    private val gone = SignInForum("2", "失效吧", 8)
    private val denied = SignInFailure(SignInFailureKind.API, "fixture_deleted", "贴吧不存在")
    private val peakNotice = "零点到一点为签到高峰期，一键签到失败机率较大，请错开高峰期再来签到！"

    @Test fun failedRunKeepsDayIncompleteAndNextStartupRetriesOnlyFailedForums() {
        val api = FakeGateway(listOf(alive, gone))
        val state = SignInDayState("20260912")
        val report = task(api).run(state, false)!!
        assertEquals(1, report.signed)
        assertEquals(listOf(gone), report.failures.map { it.forum })
        assertEquals(denied, report.failures.single().failure)
        assertFalse(state.automaticDone)
        assertEquals(4, api.singleCalls.count { it == gone.key })
        assertEquals(5, api.calls.count { it == gone.key })
        assertTrue(AutoSignInNoticePolicy.shouldNotify(report, null))
        api.recovered = true
        val restored = AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state))
        val recovered = task(api).run(restored, false)!!
        assertFalse(recovered.hasFailures)
        assertTrue(restored.automaticDone)
        assertEquals(1, api.calls.count { it == alive.key })
        assertEquals(6, api.calls.count { it == gone.key })
    }

    @Test fun failedBatchKeepsExplicitSuccessesAndFallsBackForTheRemainingForums() {
        val later = SignInForum("3", "后续吧", 8)
        val api = FakeGateway(listOf(alive, gone, later), batchSize = 2)
        api.batchReply = { SignInBatchResult(mapOf(alive.key to SignInAttempt(true)),
            SignInFailure(SignInFailureKind.API, "fixture_batch_unavailable")) }
        val report = task(api).run(SignInDayState("20260912"), false)!!

        assertEquals(1, api.batches.size)
        assertFalse(api.singleCalls.contains(alive.key))
        assertEquals(4, api.singleCalls.count { it == gone.key })
        assertEquals(1, api.singleCalls.count { it == later.key })
        assertEquals(2, report.signed)
        assertEquals(listOf(SignInFailedForum(gone, denied, 4)), report.failures)
    }

    @Test fun allOfficialBatchesPrecedeSinglesAndAlreadySignedSuccessSurvivesRestart() {
        val later = SignInForum("3", "后续吧", 8)
        val api = FakeGateway(listOf(alive, gone, later), batchSize = 2)
        api.singleReply = {
            assertEquals(listOf(listOf(alive.key, gone.key), listOf(later.key)), api.batches)
            AutoSignInResponses.single(JSONObject("""{"error_code":160002}"""))
        }
        var saved = ""
        val state = SignInDayState("20261025")
        val report = task(api, save = { saved = AutoSignInStateCodec.encode(it); true })
            .run(state, false)!!

        assertEquals(listOf(gone.key), api.singleCalls)
        assertEquals(3, report.signed)
        assertTrue(report.allSucceeded)
        val restored = AutoSignInStateCodec.decode(saved)
        assertTrue(restored.automaticDone)
        assertTrue(restored.forums.getValue(gone.key).signed)
        assertEquals(1, restored.forums.getValue(gone.key).attempts)
        assertEquals(report, task(api).run(restored, false))
        assertEquals(1, api.fetches)
        task(api).run(restored, true)
        assertEquals(listOf(gone.key), api.singleCalls)
        assertEquals(2, api.batches.size)
    }

    @Test fun cancellationDuringPacingDoesNotReserveOrSendTheNextRequest() {
        val forums = listOf(alive.copy(level = 6), gone.copy(level = 6))
        val api = FakeGateway(forums)
        var active = true
        var saved = ""
        assertNull(task(api, save = { saved = AutoSignInStateCodec.encode(it); true },
            allowed = { active }, pause = { active = false; true })
            .run(SignInDayState("20261025"), false))

        assertEquals(listOf(alive.key), api.singleCalls)
        val restored = AutoSignInStateCodec.decode(saved)
        assertTrue(restored.forums.getValue(alive.key).signed)
        assertEquals(0, restored.forums.getValue(gone.key).attempts)
        assertFalse(restored.automaticDone)
        api.recovered = true
        assertTrue(task(api).run(restored, false)!!.allSucceeded)
        assertEquals(listOf(alive.key, gone.key), api.singleCalls)
    }

    @Test fun failedBatchAndSingleSignProgressSurviveProcessDeath() {
        val api = FakeGateway(listOf(gone))
        api.batchReply = { SignInBatchResult(failure = SignInFailure(SignInFailureKind.NO_RESPONSE)) }
        api.crashOnSingle = true
        var saved = ""
        try {
            task(api, save = { saved = AutoSignInStateCodec.encode(it); true })
                .run(SignInDayState("20260912"), false)
            fail("Expected simulated process death during the first single sign")
        } catch (_: SimulatedDeath) { }

        val restored = AutoSignInStateCodec.decode(saved)
        assertEquals(1, restored.forums.getValue(gone.key).attempts)
        api.crashOnSingle = false
        val report = task(api).run(restored, false)!!
        assertEquals(1, api.batches.size)
        assertEquals(4, api.singleCalls.size)
        assertEquals(listOf(SignInFailedForum(gone, denied, 4)), report.failures)
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
        assertEquals(6, api.calls.count { it == gone.key })
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
        assertEquals(0, restored.forums.getValue(gone.key).attempts)
        task(api).run(restored, false)
        assertEquals(1, api.batches.size)
        assertEquals(4, api.singleCalls.size)
        assertEquals(5, api.calls.size)
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

    @Test fun finalSignedFlagRecoversLostResponse() {
        val api = FakeGateway(listOf(gone))
        api.finalForums = listOf(gone.copy(signed = true))
        val result = task(api).run(SignInDayState("20260912"), false)!!
        assertEquals(1, result.signed)
        assertFalse(result.hasFailures)
    }

    @Test fun listFetchFailuresKeepCooldownWithoutCompletingDayOrNotifying() {
        val api = FakeGateway(listOf(gone))
        api.fetchFailure = SignInFailure(SignInFailureKind.NO_RESPONSE)
        val state = SignInDayState("20260912")
        var time = 1L
        fun run() = AutoSignInTask(api, { true }, { true }, { true }, { time }).run(state, false)
        run(); run()
        assertEquals(1, api.fetches)
        repeat(2) { time += 31 * 60 * 1000; run() }
        time += 31 * 60 * 1000; run()
        assertEquals(4, api.fetches)
        assertFalse(state.automaticDone)
        assertTrue(state.report!!.hasFailures)
        assertFalse(AutoSignInNoticePolicy.shouldNotify(state.report!!, null))
    }

    @Test fun persistedBatchNoticeRecoveryKeepsSingleSignAttemptsAndSuccesses() {
        val api = FakeGateway(listOf(alive, gone))
        val state = blockedByBatchNotice()
        state.forums[alive.key] = SignInForumState(alive, attempts = 1, signed = true)
        state.forums[gone.key] = SignInForumState(gone, attempts = 2, failure = denied)
        api.snapshotReply = SignInSnapshotResult(SignInSnapshot(listOf(alive, gone), false, 50, 7, false))
        val restored = AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state))
        val report = task(api).run(restored, false)!!

        assertEquals(listOf(gone.key, gone.key), api.singleCalls)
        assertEquals(listOf(SignInFailedForum(gone, denied, 4)), report.failures)
        assertEquals(1, report.signed)
        assertNull(report.taskFailure)
    }

    @Test fun failedForumMissingFromNextStartupListIsStillRetriedAndReported() {
        val api = FakeGateway(listOf(alive, gone))
        val state = SignInDayState("20260925")
        task(api).run(state, false)
        api.finalForums = listOf(alive.copy(signed = true))
        val restored = AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state))
        val report = task(api).run(restored, false)!!

        assertFalse(restored.automaticDone)
        assertEquals(2, report.total)
        assertEquals(listOf(SignInFailedForum(gone, denied, 4)), report.failures)
        assertEquals(1, api.calls.count { it == alive.key })
        assertEquals(8, api.singleCalls.count { it == gone.key })
    }

    @Test fun staleCompletionFlagCannotStopAnUnfinishedTaskOrReachAnotherCheckpoint() {
        val api = FakeGateway(listOf(alive, gone))
        val state = SignInDayState("20260925", automaticDone = true,
            report = SignInReport("20260925", 1000L, 2, 0, 0, emptyList(),
                SignInFailure(SignInFailureKind.NO_RESPONSE)))
        var saves = 0
        task(api, save = { assertFalse(it.automaticDone); saves++; true }).run(state, false)
        assertTrue(saves > 0)
        assertEquals(4, api.singleCalls.count { it == gone.key })
        assertFalse(state.automaticDone)
    }

    private fun blockedByBatchNotice() = SignInDayState("20260925", automaticDone = true,

        fetchAttempts = 1, report = SignInReport("20260925", 1790269014104L, 8, 0, 0, emptyList(),

            SignInFailure(SignInFailureKind.SERVER_NOTICE, message = peakNotice)))



    private fun task(api: FakeGateway, save: (SignInDayState) -> Boolean = { true },
                     allowed: () -> Boolean = { true }, pause: (Long) -> Boolean = { true }) =
        AutoSignInTask(api, save, allowed, pause, { 1000L })

    private class SimulatedDeath : RuntimeException()

    private inner class FakeGateway(private val forums: List<SignInForum>, private val batchSize: Int = 50) : SignInGateway {
        val calls = mutableListOf<String>()
        val batches = mutableListOf<List<String>>()
        val singleCalls = mutableListOf<String>()
        var fetches = 0
        var recovered = false
        var crashOnBatch = false
        var crashOnSingle = false
        var batchReply: ((List<SignInForum>) -> SignInBatchResult)? = null
        var singleReply: ((SignInForum) -> SignInAttempt)? = null
        var finalForums: List<SignInForum>? = null
        var fetchFailure: SignInFailure? = null
        var snapshotReply: SignInSnapshotResult? = null
        override fun fetchForums(): SignInSnapshotResult {
            fetches++
            fetchFailure?.let { return SignInSnapshotResult(failure = it) }
            snapshotReply?.let { return it }
            return SignInSnapshotResult(SignInSnapshot(if (fetches > 1) finalForums ?: forums else forums,
                true, batchSize, 7, false))
        }
        override fun signBatch(forums: List<SignInForum>): SignInBatchResult {
            calls.addAll(forums.map { it.key })
            batches.add(forums.map { it.key })
            if (crashOnBatch) throw SimulatedDeath()
            batchReply?.let { return it(forums) }
            return SignInBatchResult(forums.associate { it.key to result(it) })
        }
        override fun signSingle(forum: SignInForum): SignInAttempt {
            calls.add(forum.key)
            singleCalls.add(forum.key)
            if (crashOnSingle) throw SimulatedDeath()
            return singleReply?.invoke(forum) ?: result(forum)
        }
        private fun result(forum: SignInForum) =
            if (forum.id != gone.id || recovered) SignInAttempt(true) else SignInAttempt(false, denied)
    }
}
