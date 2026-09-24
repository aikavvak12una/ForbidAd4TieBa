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

    @Test fun badFirstBatchDoesNotPreventLaterBatchesFromSigning() {
        val api = FakeGateway(listOf(gone, alive), batchSize = 1)
        val report = task(api).run(SignInDayState("20260912"), false)!!
        assertEquals(1, report.signed)
        assertEquals(1, api.calls.count { it == alive.key })
        assertEquals(5, api.calls.count { it == gone.key })
    }

    @Test fun unavailableBatchStopsLaterBatchesAndKeepsTheSingleSignRetryBudget() {
        for (kind in listOf(SignInFailureKind.API, SignInFailureKind.TIMEOUT,
                SignInFailureKind.NO_RESPONSE, SignInFailureKind.INVALID_RESPONSE,
                SignInFailureKind.REQUEST_FAILED, SignInFailureKind.SERVER_NOTICE)) {
            val api = FakeGateway(listOf(gone, alive), batchSize = 1)
            api.batchReply = { SignInBatchResult(failure =
                SignInFailure(kind, "fixture_batch_unavailable", "批签不可用")) }
            val state = SignInDayState("20260912")
            val report = task(api).run(state, false)!!

            assertEquals(kind.name, listOf(listOf(gone.key)), api.batches)
            assertEquals(kind.name, 4, api.singleCalls.count { it == gone.key })
            assertEquals(kind.name, 1, api.singleCalls.count { it == alive.key })
            assertEquals(1, report.signed)
            assertEquals(listOf(SignInFailedForum(gone, denied, 4)), report.failures)
            assertNull(report.taskFailure)
            assertFalse(state.automaticDone)
            val detail = AutoSignInReportText.detail(report)
            assertTrue(detail.contains(denied.code!!))
            assertFalse(detail.contains("fixture_batch_unavailable"))
        }
    }

    @Test fun unavailableBatchIsQuietWhenEverySingleSignSucceeds() {
        val api = FakeGateway(listOf(gone, alive), batchSize = 1)
        api.recovered = true
        api.batchReply = { SignInBatchResult(failure =
            SignInFailure(SignInFailureKind.API, "fixture_batch_unavailable")) }
        val report = task(api).run(SignInDayState("20260912"), false)!!

        assertEquals(1, api.batches.size)
        assertEquals(listOf(gone.key, alive.key), api.singleCalls)
        assertEquals(2, report.signed)
        assertFalse(report.hasFailures)
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

    @Test fun newDayAllowsRetryOfPreviouslyUnavailableForum() {
        val api = FakeGateway(listOf(gone))
        task(api).run(SignInDayState("20260912"), false)
        task(api).run(SignInDayState("20260913"), false)
        assertEquals(10, api.calls.size)
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

    @Test fun peakHourBatchNoticeSkipsBatchesAndPreservesTheSingleSignBudget() {
        for (recovered in listOf(false, true)) {
            val alreadySigned = SignInForum("3", "已签到吧", 8, signed = true)
            val api = FakeGateway(listOf(gone, alive, alreadySigned), batchSize = 1)
            api.recovered = recovered
            api.snapshotReply = AutoSignInResponses.snapshot(JSONObject("""{
                "errno":0,"valid":1,"show_dialog":1,"sign_notice":"$peakNotice",
                "sign_max_num_new":1,"forum_info":[
                    {"forum_id":"2","forum_name":"失效吧","user_level":8,"is_sign_in":0},
                    {"forum_id":"1","forum_name":"正常吧","user_level":8,"is_sign_in":0},
                    {"forum_id":"3","forum_name":"已签到吧","user_level":8,"is_sign_in":1}
                ]
            }"""), 0)
            val state = SignInDayState("20260925")
            val report = task(api).run(state, false)!!

            assertTrue(api.batches.isEmpty())
            assertEquals(if (recovered) 1 else 4, api.singleCalls.count { it == gone.key })
            assertEquals(1, api.singleCalls.count { it == alive.key })
            assertFalse(api.singleCalls.contains(alreadySigned.key))
            assertEquals(if (recovered) 2 else 1, report.signed)
            assertEquals(1, report.alreadySigned)
            assertEquals(if (recovered) emptyList() else listOf(SignInFailedForum(gone, denied, 4)),
                report.failures)
            assertNull(report.taskFailure)
            assertEquals(recovered, state.automaticDone)
            assertFalse(AutoSignInReportText.detail(report).contains(peakNotice))
        }
    }

    @Test fun persistedBatchNoticeIsRetriedAutomaticallyAndCompletionIsRemembered() {
        val api = FakeGateway(listOf(alive))
        val state = AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(blockedByBatchNotice()))
        val report = task(api).run(state, false)!!

        assertEquals(1, api.fetches)
        assertEquals(listOf(alive.key), api.calls)
        assertEquals(1, report.signed)
        assertFalse(report.hasFailures)
        assertTrue(state.automaticDone)
        val restored = AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state))
        assertEquals(report, task(api).run(restored, false))
        assertEquals(1, api.fetches)
        assertEquals(listOf(alive.key), api.calls)
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

    @Test fun persistedBatchNoticeRecoveryStillRespectsFetchCooldownWithoutEndingDay() {
        val api = FakeGateway(listOf(alive))
        api.fetchFailure = SignInFailure(SignInFailureKind.NO_RESPONSE)
        var state = blockedByBatchNotice()
        var time = 1000L
        fun run() = AutoSignInTask(api, { true }, { true }, { true }, { time }).run(state, false)

        assertEquals(api.fetchFailure, run()!!.taskFailure)
        state = AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state))
        run()
        assertEquals(1, api.fetches)
        assertFalse(state.automaticDone)
        repeat(3) { time += 31 * 60 * 1000; run() }
        assertEquals(4, api.fetches)
        assertFalse(state.automaticDone)
        assertFalse(AutoSignInNoticePolicy.shouldNotify(state.report!!, null))
    }

    @Test fun remainingForumsGetThreeExtraRetriesAfterEveryInitialSingleSign() {
        val later = SignInForum("3", "后续吧", 8)
        val api = FakeGateway(listOf(gone, alive, later))
        api.snapshotReply = SignInSnapshotResult(SignInSnapshot(listOf(gone, alive, later), false, 50, 7, false))
        api.singleReply = { forum ->
            if (forum == gone && api.singleCalls.count { it == gone.key } < 4) SignInAttempt(false, denied)
            else SignInAttempt(true)
        }
        val saved = mutableListOf<SignInDayState>()
        val state = SignInDayState("20260925")
        val report = task(api, save = {
            saved.add(AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(it))); true
        }).run(state, false)!!

        assertEquals(listOf(gone.key, alive.key, later.key, gone.key, gone.key, gone.key), api.singleCalls)
        assertEquals(3, report.signed)
        assertFalse(report.hasFailures)
        assertTrue(state.automaticDone)
        assertTrue(saved.dropLast(1).all { !it.automaticDone && it.report == null })
        assertEquals(listOf(report), saved.mapNotNull { it.report }
            .filter { AutoSignInNoticePolicy.shouldNotify(it, null) })
    }

    @Test fun unfinishedListFetchCanRecoverOnALaterStartupWithoutManualRetry() {
        val api = FakeGateway(listOf(alive))
        api.fetchFailure = SignInFailure(SignInFailureKind.TIMEOUT)
        var state = SignInDayState("20260925")
        var time = 1000L
        fun run() = AutoSignInTask(api, { true }, { true }, { true }, { time }).run(state, false)
        repeat(4) {
            val report = run()!!
            assertFalse(state.automaticDone)
            assertFalse(AutoSignInNoticePolicy.shouldNotify(report, null))
            state = AutoSignInStateCodec.decode(AutoSignInStateCodec.encode(state))
            time += 31 * 60 * 1000
        }
        api.fetchFailure = null
        val report = run()!!
        assertTrue(state.automaticDone)
        assertEquals(1, report.signed)
        assertTrue(AutoSignInNoticePolicy.shouldNotify(report, null))
    }

    @Test fun restartingDuringANewRetryRunDoesNotResetItsBudgetAgain() {
        val api = FakeGateway(listOf(gone))
        api.batchReply = { SignInBatchResult(failure = SignInFailure(SignInFailureKind.NO_RESPONSE)) }
        val state = SignInDayState("20260925")
        task(api).run(state, false)
        var saved = ""
        api.crashOnSingle = true
        try {
            task(api, save = { saved = AutoSignInStateCodec.encode(it); true }).run(state, false)
            fail("The next startup must retry the failed forum")
        } catch (_: SimulatedDeath) { }

        val restored = AutoSignInStateCodec.decode(saved)
        assertFalse(restored.automaticDone)
        assertNull(restored.report)
        assertEquals(1, restored.forums.getValue(gone.key).attempts)
        api.crashOnSingle = false
        task(api).run(restored, false)
        assertEquals(2, api.batches.size)
        assertEquals(8, api.singleCalls.size)
        assertFalse(restored.automaticDone)
    }

    @Test fun restartAfterUnavailableBatchDoesNotRetryTheSkippedBatches() {
        val api = FakeGateway(listOf(gone, alive), batchSize = 1)
        api.batchReply = { SignInBatchResult(failure = SignInFailure(SignInFailureKind.TIMEOUT)) }
        api.crashOnSingle = true
        var saved = ""
        try {
            task(api, save = { saved = AutoSignInStateCodec.encode(it); true })
                .run(SignInDayState("20260925"), false)
            fail("Expected simulated process death")
        } catch (_: SimulatedDeath) { }

        api.crashOnSingle = false
        val restored = AutoSignInStateCodec.decode(saved)
        task(api).run(restored, false)
        assertEquals(listOf(listOf(gone.key)), api.batches)
        assertEquals(listOf(gone.key, alive.key, gone.key, gone.key, gone.key), api.singleCalls)
        assertFalse(restored.automaticDone)
    }

    @Test fun restartFinishesEveryInitialSingleSignBeforeRetryingFailures() {
        val api = FakeGateway(listOf(gone, alive))
        api.snapshotReply = SignInSnapshotResult(SignInSnapshot(listOf(gone, alive), false, 50, 7, false))
        api.crashOnSingle = true
        var saved = ""
        try {
            task(api, save = { saved = AutoSignInStateCodec.encode(it); true })
                .run(SignInDayState("20260925"), false)
            fail("Expected simulated process death")
        } catch (_: SimulatedDeath) { }

        api.crashOnSingle = false
        task(api).run(AutoSignInStateCodec.decode(saved), false)
        assertEquals(listOf(gone.key, alive.key, gone.key, gone.key, gone.key), api.singleCalls)
        assertTrue(api.batches.isEmpty())
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
                     allowed: () -> Boolean = { true }) =
        AutoSignInTask(api, save, allowed, { true }, { 1000L })

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
