package com.forbidad4tieba.hook.feature.comment

import org.json.JSONObject

/** Empty is evidence only for a successful first page with no remaining page. */
internal object InlineReplyResult {
    fun accepts(json: JSONObject, pid: String, tid: String, replyCount: Int): Boolean {
        if (json.optJSONObject("error")?.optInt("errorno", -1) != 0) return false
        val data = json.optJSONObject("data") ?: return false
        if (data.optJSONObject("post")?.optString("id") != pid ||
            data.optJSONObject("thread")?.optString("id") != tid) return false
        val page = data.optJSONObject("page") ?: return false
        if (page.optInt("current_page", -1) != 1) return false
        return replyCount > 0 || (page.optInt("has_more", -1) == 0 && page.optInt("total_page", -1) in 0..1)
    }
}

/** A request belongs to one model instance, account, count and sorting choice. */
internal class InlineReplyAttempt(val account: String, val count: Int, val sort: Int, val startedAt: Long) {
    enum class State { PENDING, READY, FAILED }
    var state = State.PENDING
        private set
    var replies: ArrayList<Any>? = null
        private set
    fun matches(account: String, count: Int, sort: Int) = this.account == account && this.count == count && this.sort == sort
    fun finish(value: ArrayList<Any>?) {
        if (state != State.PENDING) return
        replies = value
        state = if (value == null) State.FAILED else State.READY
    }
    fun cancel() { replies = null; state = State.FAILED }
}
