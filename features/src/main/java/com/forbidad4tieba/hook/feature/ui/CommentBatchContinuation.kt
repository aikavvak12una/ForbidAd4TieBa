package com.forbidad4tieba.hook.feature.ui

/** One extra page requires both completion and a growing canonical commit for the same request. */
internal class CommentBatchContinuation(private val request: Any) {
    private var active = true
    private var finished = false
    private var committed = false
    private var queued = false

    fun matches(candidate: Any?): Boolean = active && candidate === request

    fun finish(candidate: Any?) {
        if (matches(candidate)) finished = true
    }

    fun commit(candidate: Any?, grew: Boolean) {
        if (!matches(candidate)) return
        if (grew) committed = true else cancel()
    }

    fun queue(): Boolean {
        if (!active || !finished || !committed || queued) return false
        queued = true
        return true
    }

    fun take(): Boolean {
        if (!active || !queued) return false
        active = false
        return true
    }

    fun cancel() { active = false }
}
