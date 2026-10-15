package com.forbidad4tieba.hook.feature.ad

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.forbidad4tieba.hook.config.BlockedPostUser
import com.forbidad4tieba.hook.config.PostFilterPreferences
import com.forbidad4tieba.hook.config.PostUserFilterSettings
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.PostUserLookupCall
import com.forbidad4tieba.hook.symbol.contract.PostUserLookupContract
import com.forbidad4tieba.hook.symbol.contract.PostUserProfileContract
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One dialog owns its requests and callbacks; the preference remains the authoritative saved list. */
internal class PostUserFilterController(context: Context, private val prefs: SharedPreferences) {
    enum class Status { IDLE, QUERYING, ADDED, REMOVED, INVALID_ID, DUPLICATE, FAILED, INVALID_RESPONSE,
        TIMEOUT, CANCELLED, UNAVAILABLE, SAVE_FAILED, PROFILE_FAILED }
    data class State(val users: List<BlockedPostUser>, val status: Status, val canAdd: Boolean)

    private val session = PostUserListSession()
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "tbhook-post-user-lookup").apply { isDaemon = true }
    }
    private val lookup = try {
        PostUserLookupContract.resolve(context.classLoader).also {
            XposedCompat.log("HookPoint[PostUserFilter.Lookup] state=FOUND missing=-")
        }
    } catch (t: Throwable) {
        XposedCompat.logW("HookPoint[PostUserFilter.Lookup] state=ERROR reason=${t.message}")
        null
    }
    private val profile = try {
        PostUserProfileContract.resolve(context.classLoader).also {
            XposedCompat.log("HookPoint[PostUserFilter.Profile] state=FOUND missing=-")
        }
    } catch (t: Throwable) {
        XposedCompat.logW("HookPoint[PostUserFilter.Profile] state=ERROR reason=${t.message}")
        null
    }
    private class Running(val request: PostUserListSession.Request) {
        val cancelled = AtomicBoolean(false)
        val call = AtomicReference<PostUserLookupCall?>()
        var future: Future<*>? = null
        var timeout: Runnable? = null
    }
    private var running: Running? = null
    private var closed = false
    private var status = if (lookup == null) Status.UNAVAILABLE else Status.IDLE
    private var listener: ((State) -> Unit)? = null

    fun observe(onChange: (State) -> Unit) { listener = onChange; publish() }

    fun add(raw: String) {
        if (closed) return
        val network = lookup ?: return publish(Status.UNAVAILABLE)
        when (val start = session.start(raw, users())) {
            PostUserListSession.Start.InvalidId -> publish(Status.INVALID_ID)
            PostUserListSession.Start.Duplicate -> publish(Status.DUPLICATE)
            PostUserListSession.Start.Busy, PostUserListSession.Start.Closed -> Unit
            is PostUserListSession.Start.Ready -> {
                val operation = Running(start.request)
                running = operation
                publish(Status.QUERYING)
                operation.timeout = Runnable {
                    if (session.isCurrent(operation.request)) {
                        cancelPending()
                        publish(Status.TIMEOUT)
                    }
                }.also { handler.postDelayed(it, REQUEST_TIMEOUT_MS) }
                operation.future = executor.submit {
                    val result = try {
                        val call = network.newCall(start.request.profileId)
                        operation.call.set(call)
                        if (operation.cancelled.get()) {
                            cancelCall(call)
                            return@submit
                        }
                        PostUserLookupResponse.parse(start.request.profileId, call.execute())
                    } catch (t: Throwable) {
                        if (!operation.cancelled.get()) {
                            XposedCompat.logW("[PostUserFilter] lookup failed: ${t.javaClass.simpleName}")
                        }
                        PostUserLookupResult.Failed
                    }
                    if (!operation.cancelled.get()) handler.post { complete(operation, result) }
                }
            }
        }
    }

    fun inputChanged() {
        if (session.pending != null) {
            cancelPending()
            publish(Status.CANCELLED)
        }
    }

    fun remove(user: BlockedPostUser) {
        if (closed) return
        session.removed(user.userId)
        if (save(users().filterNot { it.userId == user.userId })) {
            publish(if (session.pending != null) Status.QUERYING else Status.REMOVED)
        }
    }

    fun openProfile(context: Context, user: BlockedPostUser) {
        if (closed) return
        try {
            if (profile?.open(context, user.userId) != true) publish(Status.PROFILE_FAILED)
        } catch (t: Throwable) {
            XposedCompat.logW("[PostUserFilter] profile open failed: ${t.javaClass.simpleName}")
            publish(Status.PROFILE_FAILED)
        }
    }

    fun close() {
        closed = true
        cancelPending()
        session.close()
        listener = null
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    private fun complete(operation: Running, result: PostUserLookupResult) {
        if (!session.isCurrent(operation.request)) return
        operation.timeout?.let(handler::removeCallbacks)
        running = null
        when (result) {
            is PostUserLookupResult.Found -> {
                val updated = session.finish(operation.request, result.user, users())
                    ?: return publish(Status.CANCELLED)
                if (save(updated)) publish(Status.ADDED)
            }
            PostUserLookupResult.Failed -> { session.cancel(); publish(Status.FAILED) }
            PostUserLookupResult.InvalidResponse -> { session.cancel(); publish(Status.INVALID_RESPONSE) }
        }
    }

    private fun users(): List<BlockedPostUser> =
        PostUserFilterSettings.decode(PostFilterPreferences.FILTER_POST_USER_ID_LIST.read(prefs))

    private fun save(users: List<BlockedPostUser>): Boolean = try {
        prefs.edit().putString(PostFilterPreferences.KEY_FILTER_POST_USER_ID_LIST,
            PostUserFilterSettings.encode(users)).apply()
        true
    } catch (t: Throwable) {
        XposedCompat.logW("[PostUserFilter] save failed: ${t.javaClass.simpleName}")
        publish(Status.SAVE_FAILED)
        false
    }

    private fun cancelPending() {
        session.cancel()
        running?.let { operation ->
            operation.cancelled.set(true)
            operation.timeout?.let(handler::removeCallbacks)
            operation.call.get()?.let(::cancelCall)
            operation.future?.cancel(true)
        }
        running = null
    }

    private fun cancelCall(call: PostUserLookupCall) {
        try { call.cancel() } catch (t: Throwable) {
            XposedCompat.logW("[PostUserFilter] cancel failed: ${t.javaClass.simpleName}")
        }
    }

    private fun publish(next: Status = status) {
        if (closed) return
        status = next
        listener?.invoke(State(users(), status, lookup != null && session.pending == null))
    }

    private companion object { const val REQUEST_TIMEOUT_MS = 15_000L }
}
