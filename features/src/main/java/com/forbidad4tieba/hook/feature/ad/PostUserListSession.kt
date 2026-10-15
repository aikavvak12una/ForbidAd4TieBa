package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.BlockedPostUser
import com.forbidad4tieba.hook.config.PostUserFilterSettings

/** Owns one editor's pending addition; late results cannot revive deleted or abandoned users. */
internal class PostUserListSession {
    class Request internal constructor(val profileId: String) {
        internal val removedUserIds = mutableSetOf<String>()
    }

    sealed interface Start {
        data class Ready(val request: Request) : Start
        data object InvalidId : Start
        data object Duplicate : Start
        data object Busy : Start
        data object Closed : Start
    }

    var pending: Request? = null
        private set
    private var closed = false

    fun start(raw: String, users: List<BlockedPostUser>): Start {
        if (closed) return Start.Closed
        if (pending != null) return Start.Busy
        val id = PostUserFilterSettings.parseProfileIdInput(raw) ?: return Start.InvalidId
        if (users.any { it.profileId == id }) return Start.Duplicate
        return Start.Ready(Request(id).also { pending = it })
    }

    fun isCurrent(request: Request): Boolean = !closed && pending === request

    fun finish(
        request: Request,
        user: BlockedPostUser,
        currentUsers: List<BlockedPostUser>,
    ): List<BlockedPostUser>? {
        if (!isCurrent(request)) return null
        pending = null
        if (user.profileId != request.profileId ||
            user.userId in request.removedUserIds
        ) return null
        if (currentUsers.any { it.profileId == user.profileId && it.userId != user.userId }) return null
        if (currentUsers.any { it.userId == user.userId && it.profileId != user.profileId }) return null
        val previous = currentUsers.indexOfFirst { it.userId == user.userId }
        return if (previous < 0) currentUsers + user else currentUsers.toMutableList().apply { set(previous, user) }
    }

    fun removed(userId: String) { pending?.removedUserIds?.add(userId) }
    fun cancel() { pending = null }
    fun close() { closed = true; cancel() }
}
