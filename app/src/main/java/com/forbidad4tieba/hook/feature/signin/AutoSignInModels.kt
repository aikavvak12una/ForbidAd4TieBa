package com.forbidad4tieba.hook.feature.signin

internal data class SignInForum(
    val id: String,
    val name: String,
    val level: Int = 0,
    val signed: Boolean = false,
) {
    val key: String get() = if (id.isNotEmpty()) "id:$id" else "name:$name"
}

internal enum class SignInFailureKind {
    API, NO_RESPONSE, INVALID_RESPONSE, REQUEST_FAILED, TIMEOUT, UNCONFIRMED, INVALID_FORUM,
    INTERRUPTED, SERVER_NOTICE,
}

internal data class SignInFailure(
    val kind: SignInFailureKind,
    val code: String? = null,
    val message: String = "",
)

internal data class SignInAttempt(val success: Boolean, val failure: SignInFailure? = null)

internal data class SignInSnapshot(
    val forums: List<SignInForum>,
    val batchAllowed: Boolean,
    val batchSize: Int,
    val batchMinLevel: Int,
    val allLevels: Boolean,
    val notice: SignInFailure? = null,
)

internal data class SignInSnapshotResult(
    val snapshot: SignInSnapshot? = null,
    val failure: SignInFailure? = null,
)

internal data class SignInBatchResult(
    val attempts: Map<String, SignInAttempt> = emptyMap(),
    val failure: SignInFailure? = null,
)

internal interface SignInGateway {
    fun fetchForums(): SignInSnapshotResult
    fun signBatch(forums: List<SignInForum>): SignInBatchResult
    fun signSingle(forum: SignInForum): SignInAttempt
}

internal data class SignInForumState(
    var forum: SignInForum,
    var attempts: Int = 0,
    var signed: Boolean = false,
    var failure: SignInFailure? = null,
)

internal data class SignInFailedForum(val forum: SignInForum, val failure: SignInFailure, val attempts: Int)

internal data class SignInReport(
    val day: String,
    val finishedAt: Long,
    val total: Int,
    val signed: Int,
    val alreadySigned: Int,
    val failures: List<SignInFailedForum>,
    val taskFailure: SignInFailure? = null,
) {
    val hasFailures: Boolean get() = failures.isNotEmpty() || taskFailure != null
    val isFinal: Boolean get() = taskFailure == null &&
        total == signed + alreadySigned + failures.size &&
        failures.all { it.attempts == AutoSignInTask.MAX_ATTEMPTS }
    val allSucceeded: Boolean get() = isFinal && !hasFailures
}

internal data class SignInDayState(
    val day: String,
    val forums: MutableMap<String, SignInForumState> = linkedMapOf(),
    var automaticDone: Boolean = false,
    var fetchAttempts: Int = 0,
    var nextAutomaticAt: Long = 0L,
    var report: SignInReport? = null,
    var notifiedFingerprint: String? = null,
    var batchAttempted: Boolean = false,
)
