package com.forbidad4tieba.hook.feature.signin

/** Executes on the existing sign-in worker; the gateway is the only source of network effects. */
internal class AutoSignInTask(
    private val gateway: SignInGateway,
    private val save: (SignInDayState) -> Boolean,
    private val canContinue: () -> Boolean,
    private val pause: (Long) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun run(state: SignInDayState, force: Boolean): SignInReport? {
        if (!canContinue()) return null
        // A one-click notice must not block fetching the list or individual sign-in.
        if (state.report?.taskFailure?.kind == SignInFailureKind.SERVER_NOTICE) {
            state.fetchAttempts = 0
            state.nextAutomaticAt = 0L
            state.report = null
        }
        state.automaticDone = state.automaticDone && state.report?.allSucceeded == true &&
            state.forums.values.all { it.signed }
        if (!force && (state.automaticDone || now() < state.nextAutomaticAt)) return state.report
        // Only a finished run grants a new automatic retry budget. Interrupted runs
        // retain their reservations, while successful forums are never resent.
        if (force || state.report?.isFinal == true) {
            state.fetchAttempts = 0
            state.nextAutomaticAt = 0L
            state.batchAttempted = false
            state.forums.values.filterNot { it.signed }.forEach {
                it.attempts = 0
                it.failure = null
            }
        }
        state.automaticDone = false
        state.report = null
        // Reserve before the request so killing/restarting the host cannot reset the budget.
        state.fetchAttempts++
        checkpoint(state)
        val fetched = gateway.fetchForums()
        if (!canContinue()) return null
        val snapshot = fetched.snapshot
        if (snapshot == null) {
            state.nextAutomaticAt = now() + FETCH_RETRY_COOLDOWN_MS
            return finish(state, SignInReport(state.day, now(), 0, 0, 0, emptyList(),
                fetched.failure ?: SignInFailure(SignInFailureKind.NO_RESPONSE)))
        }
        for (forum in snapshot.forums) {
            state.forums.getOrPut(forum.key) { SignInForumState(forum) }.apply {
                this.forum = forum
                if (forum.signed) { signed = true; failure = null }
            }
        }
        // An absent forum is not evidence of success, including after a restart.
        val pending = state.forums.values.filterNot { it.signed }.map { it.forum }

        if (!state.batchAttempted) {
            // Persist the phase before sending: a restart falls back to singles even
            // if a batch reply was lost. Batches never consume individual attempts.
            state.batchAttempted = true
            checkpoint(state)
            // getforumlist's show_dialog/sign_notice applies to one-click sign-in only.
            if (snapshot.batchAllowed && snapshot.notice == null) {
                val batches = pending.filter { state.forums.getValue(it.key).attempts == 0 &&
                    it.id.isNotEmpty() && (snapshot.allLevels || it.level >= snapshot.batchMinLevel) }
                    .chunked(snapshot.batchSize.coerceAtLeast(1))
                for (batch in batches) {
                    if (!canContinue()) return null
                    val result = gateway.signBatch(batch)
                    for (forum in batch) {
                        result.attempts[forum.key]?.takeIf { it.success }?.let {
                            record(state, forum, it)
                        }
                    }
                    checkpoint(state)
                    if (!canContinue()) return null
                    if (result.failure != null) break
                }
            }
        }

        repeat(MAX_ATTEMPTS) { round ->
            // Exact saved counts also finish the first pass before retries on resume.
            val candidates = pending.filter {
                val entry = state.forums.getValue(it.key)
                !entry.signed && entry.attempts == round
            }
            if (candidates.isEmpty()) return@repeat
            if (round > 0 && (!canContinue() || !pause(RETRY_DELAY_MS * round))) return null
            for ((index, forum) in candidates.withIndex()) {
                if (forum.name.isEmpty()) {
                    state.forums.getValue(forum.key).apply {
                        attempts = MAX_ATTEMPTS
                        failure = SignInFailure(SignInFailureKind.INVALID_FORUM)
                    }
                    checkpoint(state)
                    continue
                }
                if (!reserve(state, forum)) return null
                record(state, forum, gateway.signSingle(forum))
                checkpoint(state)
                if (!canContinue()) return null
                if (index < candidates.lastIndex && !pause(SINGLE_DELAY_MS)) return null
            }
        }

        // A response may be lost after the server has signed. Only explicit signed flags
        // confirm this; a forum disappearing from the list does not count as success.
        if (pending.any { !state.forums.getValue(it.key).signed }) {
            if (!canContinue()) return null
            gateway.fetchForums().snapshot?.forums?.filter { it.signed }?.forEach { forum ->
                state.forums[forum.key]?.apply { signed = true; failure = null }
            }
        }
        if (!canContinue()) return null
        val failures = pending.mapNotNull { forum ->
            val entry = state.forums.getValue(forum.key)
            if (entry.signed) null else SignInFailedForum(forum,
                entry.failure ?: SignInFailure(SignInFailureKind.UNCONFIRMED), entry.attempts)
        }
        state.nextAutomaticAt = 0L
        val alreadySigned = snapshot.forums.count { it.signed }
        return finish(state, SignInReport(state.day, now(), state.forums.size,
            state.forums.size - failures.size - alreadySigned, alreadySigned, failures))
    }

    private fun reserve(state: SignInDayState, forum: SignInForum): Boolean {
        if (!canContinue()) return false
        state.forums.getValue(forum.key).apply {
            attempts++
            failure = SignInFailure(SignInFailureKind.INTERRUPTED)
        }
        checkpoint(state)
        return canContinue()
    }

    private fun record(state: SignInDayState, forum: SignInForum, result: SignInAttempt) {
        state.forums.getValue(forum.key).apply {
            signed = result.success
            failure = if (result.success) null else result.failure
                ?: SignInFailure(SignInFailureKind.UNCONFIRMED)
        }
    }

    private fun finish(state: SignInDayState, report: SignInReport): SignInReport {
        state.automaticDone = report.allSucceeded
        state.report = report
        checkpoint(state)
        return report
    }

    private fun checkpoint(state: SignInDayState) {
        check(save(state)) { "Unable to persist sign-in progress; stop before another request" }
    }

    companion object {
        const val MAX_ATTEMPTS = 4 // One initial individual request, then three retries.
        private const val RETRY_DELAY_MS = 2000L
        private const val SINGLE_DELAY_MS = 500L
        private const val FETCH_RETRY_COOLDOWN_MS = 30 * 60 * 1000L
    }
}
