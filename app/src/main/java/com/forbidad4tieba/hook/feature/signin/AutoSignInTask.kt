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
        if (!force && (state.automaticDone || now() < state.nextAutomaticAt)) return state.report
        if (force) {
            state.automaticDone = false
            state.fetchAttempts = 0
            state.nextAutomaticAt = 0L
            state.forums.values.filterNot { it.signed }.forEach { it.attempts = 0 }
        }
        // Reserve before the request so killing/restarting the host cannot reset the budget.
        state.fetchAttempts++
        checkpoint(state)
        val fetched = gateway.fetchForums()
        if (!canContinue()) return null
        val snapshot = fetched.snapshot
        if (snapshot == null) {
            state.nextAutomaticAt = now() + FETCH_RETRY_COOLDOWN_MS
            state.automaticDone = state.fetchAttempts >= MAX_ATTEMPTS
            return finish(state, SignInReport(state.day, now(), 0, 0, 0, emptyList(),
                fetched.failure ?: SignInFailure(SignInFailureKind.NO_RESPONSE)))
        }
        if (snapshot.notice != null) {
            state.automaticDone = true
            return finish(state, SignInReport(state.day, now(), snapshot.forums.size, 0,
                snapshot.forums.count { it.signed }, emptyList(), snapshot.notice))
        }
        val pending = snapshot.forums.filterNot { it.signed }.distinctBy { it.key }
        for (forum in pending) {
            state.forums.getOrPut(forum.key) { SignInForumState(forum) }.forum = forum
        }
        snapshot.forums.filter { it.signed }.forEach { forum ->
            state.forums[forum.key]?.apply { signed = true; failure = null }
        }

        // Each eligible forum participates in at most one batch in this run. One unavailable
        // forum must not stall later batches, or be re-added by a refreshed server list.
        if (snapshot.batchAllowed) {
            val batches = pending.filter { eligible(state, it) && it.id.isNotEmpty() &&
                (snapshot.allLevels || it.level >= snapshot.batchMinLevel) }
                .chunked(snapshot.batchSize.coerceAtLeast(1))
            for (batch in batches) {
                if (!reserve(state, batch)) return null
                val result = gateway.signBatch(batch)
                val batchUnavailable = result.failure != null
                for (forum in batch) {
                    val attempt = result.attempts[forum.key]
                    if (batchUnavailable && attempt?.success != true) {
                        // A failed batch request does not diagnose an individual forum or
                        // consume its single-sign retries. Persist the refund before fallback.
                        state.forums.getValue(forum.key).apply {
                            attempts--
                            failure = null
                        }
                    } else {
                        record(state, forum, attempt ?: SignInAttempt(false,
                            SignInFailure(SignInFailureKind.UNCONFIRMED)))
                    }
                }
                checkpoint(state)
                if (!canContinue()) return null
                if (batchUnavailable) break
            }
        }

        repeat(MAX_ATTEMPTS) { round ->
            val candidates = pending.filter { eligible(state, it) }
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
                if (!reserve(state, listOf(forum))) return null
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
        state.automaticDone = true
        state.nextAutomaticAt = 0L
        return finish(state, SignInReport(state.day, now(), snapshot.forums.size,
            pending.size - failures.size, snapshot.forums.count { it.signed }, failures))
    }

    private fun eligible(state: SignInDayState, forum: SignInForum): Boolean {
        val entry = state.forums.getValue(forum.key)
        return !entry.signed && entry.attempts < MAX_ATTEMPTS
    }

    private fun reserve(state: SignInDayState, forums: List<SignInForum>): Boolean {
        if (!canContinue()) return false
        for (forum in forums) {
            state.forums.getValue(forum.key).apply {
                attempts++
                failure = SignInFailure(SignInFailureKind.INTERRUPTED)
            }
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
        state.report = report
        checkpoint(state)
        return report
    }

    private fun checkpoint(state: SignInDayState) {
        check(save(state)) { "Unable to persist sign-in progress; stop before another request" }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val SINGLE_DELAY_MS = 500L
        private const val FETCH_RETRY_COOLDOWN_MS = 30 * 60 * 1000L
    }
}
