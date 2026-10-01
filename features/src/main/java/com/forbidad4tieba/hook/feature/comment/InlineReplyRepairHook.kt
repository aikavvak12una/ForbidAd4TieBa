package com.forbidad4tieba.hook.feature.comment

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SimpleToggle
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.InlineReplyTargets
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Foreground rows own requests. Neither server counts nor shared PostData objects are edited. */
internal class InlineReplyRepairHook(private val t: InlineReplyTargets) {
    private class Binding(row: Any) {
        val row = WeakReference(row)
        var applied: InlineReplyAttempt? = null
        var needsRender = true
    }
    private class Pending(post: Any, val attempt: InlineReplyAttempt, val tag: Any, val pid: String, val tid: String) {
        val post = WeakReference(post)
    }
    private class Rendering(val post: Any, val children: ArrayList<Any>)
    private val handler = Handler(Looper.getMainLooper())
    private val bindings = WeakHashMap<View, Binding>()
    private val attempts = WeakHashMap<Any, InlineReplyAttempt>()
    // Weak membership also recognizes late/timeout responses without keeping their requests alive.
    private val owned = WeakHashMap<Any, Boolean>()
    private val pending = LinkedHashMap<Any, Pending>()
    private val rendering = ThreadLocal<Rendering?>()
    private var queued = false
    @Volatile private var active = true

    fun install() {
        val api = XposedCompat.module ?: return
        val handles = ArrayList<XposedInterface.HookHandle>()
        fun add(method: Method, name: String, hooker: XposedInterface.Hooker) {
            handles += RuntimeHooks.builder(api, method, "InlineReplyRepair", name).intercept(hooker)
        }
        try {
            add(t.bind, "bind") { chain ->
                val result = chain.proceed()
                if (enabled() && onMain()) guarded {
                    val view = chain.thisObject as View
                    val row = requireNotNull(chain.args[0])
                    val previous = bindings[view]?.takeIf { it.row.get() === row }
                    bindings[view] = (previous ?: Binding(row)).apply { needsRender = true }
                    schedule()
                }
                result
            }
            add(t.attach, "attach") { chain ->
                val result = chain.proceed()
                if (enabled() && onMain()) schedule()
                result
            }
            add(t.detach, "detach") { chain ->
                val result = chain.proceed()
                if (onMain()) schedule() // Cancel requests whose last visible owner left.
                result
            }
            add(t.windowFocus, "windowFocus") { chain ->
                val result = chain.proceed()
                if (onMain() && bindings.isNotEmpty()) schedule()
                result
            }
            add(t.children, "nativePreviewChildren") { chain ->
                val current = rendering.get()
                if (current != null && current.post === chain.thisObject) current.children else chain.proceed()
            }
            add(t.dispatch, "ownResponse") { chain ->
                val response = chain.args[0]
                // The native dispatcher posts worker callbacks to the UI thread itself.
                if (!onMain() || response == null || owned.isEmpty()) chain.proceed() else {
                    val request = guarded { t.original.invoke(response)?.let { t.extra.invoke(it) } }
                    if (request == null || !owned.containsKey(request)) chain.proceed() else {
                        guarded { receive(request, response) }
                        // These private tagged requests must never reach an open SubPbModel listener.
                        null
                    }
                }
            }
            XposedCompat.log("[InlineReplyRepair] INSTALLED: hooks=${handles.size}")
        } catch (failure: Throwable) {
            active = false
            handles.asReversed().forEach { handle ->
                try { handle.unhook() } catch (rollback: Throwable) {
                    XposedCompat.log("[InlineReplyRepair] rollback failed: ${rollback.message}")
                }
            }
            throw failure
        }
    }

    private fun schedule() {
        if (queued) return
        queued = true
        handler.post {
            queued = false
            guarded { pump() }
        }
    }

    private fun pump() {
        if (!enabled()) {
            pending.keys.toList().forEach(::cancel)
            bindings.forEach { (view, binding) ->
                if (binding.applied != null) binding.row.get()?.let { row ->
                    t.setComponents.invoke(t.rowAdapter.get(view), t.rowComponents.get(row))
                }
            }
            bindings.clear(); attempts.clear()
            return
        }
        val account = t.account.invoke(null) as? String ?: ""
        val sort = if (ConfigManager.snapshot()[SimpleToggle.DEFAULT_LZL_EARLIEST]) 0 else 2
        val visible = bindings.entries.toList().filter { (view, binding) -> visible(view) && binding.row.get() != null }
        val posts = visible.mapNotNull { t.rowPost.get(it.value.row.get()) }
        pending.entries.toList().forEach { (request, p) ->
            if (posts.none { it === p.post.get() } || p.attempt.account != account || p.attempt.sort != sort) cancel(request)
        }
        visible.forEach { (view, binding) ->
            val row = binding.row.get() ?: return@forEach
            val post = t.rowPost.get(row) ?: return@forEach
            val count = t.count.invoke(post) as Int
            if (count <= 0 || !(t.children.invoke(post) as? List<*>).isNullOrEmpty()) {
                restore(view, binding, row)
                return@forEach
            }
            val old = attempts[post]
            if (old != null && (!old.matches(account, count, sort) ||
                    SystemClock.uptimeMillis() - old.startedAt > 120_000L)) {
                pending.entries.toList().filter { it.value.attempt === old }.forEach { cancel(it.key) }
                attempts.remove(post)
            }
            val state = attempts[post]
            if (state?.state != InlineReplyAttempt.State.READY) restore(view, binding, row)
            if (state == null) {
                if (pending.size < 2) start(view, row, post, account, count, sort)
            } else if (state.state == InlineReplyAttempt.State.READY && (binding.applied !== state || binding.needsRender)) {
                apply(view, binding, row, post, state)
            }
        }
    }

    private fun start(view: View, row: Any, post: Any, account: String, count: Int, sort: Int) {
        val thread = t.rowThread.get(row) ?: return
        val forum = t.rowForum.get(row) ?: return
        val pid = t.postId.invoke(post) as String
        val tid = t.threadId.invoke(thread) as String
        val fid = (t.forumId.invoke(forum) as String).toLongOrNull() ?: return
        val pidNumber = pid.toLongOrNull()?.takeIf { it > 0 } ?: return
        val tidNumber = tid.toLongOrNull()?.takeIf { it > 0 } ?: return
        val state = InlineReplyAttempt(account, count, sort, SystemClock.uptimeMillis())
        attempts[post] = state
        val metrics = view.resources.displayMetrics
        val request = t.request.newInstance(view.context.applicationContext, tidNumber, pidNumber, 0L,
            1, metrics.widthPixels, metrics.heightPixels, metrics.density.toDouble(), "", 0, sort, "")
        t.setForum.invoke(request, fid)
        val tag = requireNotNull(t.newTag.invoke(null))
        t.setTag.invoke(request, tag)
        owned[request] = true
        pending[request] = Pending(post, state, tag, pid, tid)
        val manager = requireNotNull(t.managerInstance.invoke(null))
        if (t.send.invoke(manager, request) != true) {
            cancel(request)
            schedule()
            return
        }
        XposedCompat.logD { "[InlineReplyRepair] request pid=$pid sort=$sort" }
        handler.postDelayed({
            if (pending[request]?.attempt === state) guarded { cancel(request); schedule() }
        }, 10_000L)
    }

    private fun receive(request: Any, response: Any) {
        val p = pending.remove(request) ?: return
        val post = p.post.get()
        var accepted: ArrayList<Any>? = null
        if (enabled() && post != null && attempts[post] === p.attempt &&
            p.attempt.matches(t.account.invoke(null) as? String ?: "", t.count.invoke(post) as Int,
                if (ConfigManager.snapshot()[SimpleToggle.DEFAULT_LZL_EARLIEST]) 0 else 2) &&
            t.transportError.invoke(response) == 0) {
            t.result(response)?.let { (data, json) ->
                val children = t.replies.get(data) as? List<*>
                if (children != null && children.none { it == null } && InlineReplyResult.accepts(json, p.pid, p.tid, children.size)) {
                    // Native preview consumes at most four replies plus a fifth-item More trigger.
                    accepted = ArrayList(children.filterNotNull().take(5))
                }
            }
        }
        p.attempt.finish(accepted)
        XposedCompat.logD { "[InlineReplyRepair] result pid=${p.pid} preview=${accepted?.size ?: "failed"}" }
        schedule()
    }

    private fun apply(view: View, binding: Binding, row: Any, post: Any, state: InlineReplyAttempt) {
        val children = state.replies ?: return
        val original = t.rowComponents.get(row) as List<*>
        val index = original.indexOfFirst(t.wrapper::isInstance)
        if (index < 0 || original.count(t.wrapper::isInstance) != 1) return
        val replacement = ArrayList<Any>()
        if (children.isNotEmpty()) {
            val thread = t.rowThread.get(row) ?: return
            val forum = t.rowForum.get(row) ?: return
            rendering.set(Rendering(post, children))
            try { t.build.invoke(null, post, replacement, thread, forum) } finally { rendering.remove() }
            check(replacement.size == 1 && t.wrapper.isInstance(replacement.single())) { "Unexpected native preview section" }
        }
        val updated = original.toMutableList()
        updated.removeAt(index)
        updated.addAll(index, replacement)
        t.setComponents.invoke(t.rowAdapter.get(view), updated)
        binding.applied = state
        binding.needsRender = false
    }

    private fun restore(view: View, binding: Binding, row: Any) {
        if (binding.applied == null) return
        t.setComponents.invoke(t.rowAdapter.get(view), t.rowComponents.get(row))
        binding.applied = null
        binding.needsRender = false
    }

    private fun cancel(request: Any) {
        val p = pending.remove(request) ?: return
        p.attempt.cancel()
        t.cancel.invoke(t.managerInstance.invoke(null), p.tag)
    }
    private fun visible(view: View) = view.isAttachedToWindow && view.isShown && view.hasWindowFocus()
    private fun onMain() = Looper.myLooper() == Looper.getMainLooper()
    private fun enabled() = active
    private inline fun <T> guarded(block: () -> T): T? = try { block() } catch (failure: Throwable) {
        if (active) {
            active = false
            XposedCompat.log("[InlineReplyRepair] disabled: ${failure.message}")
            XposedCompat.log(failure)
            schedule()
        }
        null
    }
}
