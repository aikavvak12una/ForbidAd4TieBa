package com.forbidad4tieba.hook.feature.ui

import android.os.Looper
import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PbCommentBatchSymbols
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Binds one continuation to a visible page and its accepted native append request. */
internal class PbCommentBatchLoader(private val targets: PbCommentBatchSymbols) {
    private class Scroll(val view: View, val fragment: Any)
    private class Attempt(val vm: Any, val scroll: Scroll, val first: Int, val last: Int) {
        var pending: Pending? = null
    }
    private class Pending(request: Any, attempt: Attempt) {
        val gate = CommentBatchContinuation(request)
        val view = WeakReference(attempt.scroll.view)
        val fragment = WeakReference(attempt.scroll.fragment)
        var first = attempt.first
        var last = attempt.last
    }

    private val pending = WeakHashMap<Any, Pending>()
    private var scroll: Scroll? = null
    private var attempt: Attempt? = null
    private var requestAttempt: Attempt? = null
    private var continuing = false
    @Volatile private var active = true

    fun install(api: XposedInterface) {
        val handles = ArrayList<XposedInterface.HookHandle>()
        fun add(method: Method, purpose: String, hooker: XposedInterface.Hooker) {
            handles += RuntimeHooks.builder(api, method, "PbCommentAutoLoadHook", "batch:$purpose").intercept { chain ->
                if (!enabled()) {
                    if (Looper.myLooper() == Looper.getMainLooper()) cancelAll()
                    chain.proceed()
                } else hooker.intercept(chain)
            }
        }
        try {
            add(targets.scroll, "scroll") { chain ->
                val previous = scroll
                scroll = guarded {
                    val view = chain.args[0] as View
                    if ((chain.args[2] as Int) > 0 && view.isShown && view.isAttachedToWindow)
                        Scroll(view, requireNotNull(targets.fragment.get(chain.thisObject))) else null
                }
                try { chain.proceed() } finally { scroll = previous }
            }
            add(targets.prefetch, "prefetch") { chain ->
                val previous = attempt
                val vm = requireNotNull(chain.thisObject)
                val current = scroll
                attempt = if (!continuing && current != null) {
                    val first = chain.args[0] as Int
                    val last = chain.args[1] as Int
                    pending[vm]?.takeIf { it.view.get() === current.view }?.let {
                        it.first = first
                        it.last = last
                    }
                    Attempt(vm, current, first, last)
                } else null
                try { chain.proceed() } finally { attempt = previous }
            }
            add(targets.request, "request") { chain ->
                val vm = requireNotNull(chain.thisObject)
                cancel(vm) // Includes refresh, sorting and host-only changes, even if rejected as busy.
                val previous = requestAttempt
                val current = attempt?.takeIf { it.vm === vm && chain.args[1] === targets.appendRequestKind }
                requestAttempt = current
                var accepted = false
                try {
                    chain.proceed().also { accepted = it == true }
                } finally {
                    if (!accepted && current?.pending != null) cancel(vm)
                    requestAttempt = previous
                }
            }
            add(targets.loading, "loading") { chain ->
                val result = chain.proceed()
                guarded {
                    val vm = requireNotNull(chain.thisObject)
                    val request = requireNotNull(chain.args[0])
                    if (chain.args[1] == true) {
                        val current = requestAttempt?.takeIf { it.vm === vm }
                        if (current != null) {
                            val state = Pending(request, current)
                            current.pending = state
                            pending[vm] = state
                        }
                    } else pending[vm]?.let { state ->
                        state.gate.finish(request)
                        schedule(vm, state)
                    }
                }
                result
            }
            add(targets.commit, "commit") { chain ->
                val vm = requireNotNull(chain.thisObject)
                val state = pending[vm]?.takeIf { it.gate.matches(chain.args[0]) &&
                    chain.args[2] === targets.appendMode && chain.args[3] === targets.networkType }
                val before = if (state != null) guarded { size(vm) } else null
                val result = chain.proceed()
                if (state != null && before != null) guarded {
                    state.gate.commit(chain.args[0], result != null && size(vm) > before)
                    schedule(vm, state)
                }
                result
            }
            listOf(targets.pause, targets.destroyView).forEach { lifecycle ->
                add(lifecycle, lifecycle.name) { chain ->
                    val iterator = pending.entries.iterator()
                    while (iterator.hasNext()) {
                        val state = iterator.next().value
                        if (state.fragment.get() === chain.thisObject) {
                            state.gate.cancel()
                            iterator.remove()
                        }
                    }
                    chain.proceed()
                }
            }
            XposedCompat.log("[PbCommentAutoLoadHook] batch INSTALLED: hooks=${handles.size}, pages=2")
        } catch (failure: Throwable) {
            disable(failure)
            handles.asReversed().forEach { handle ->
                try { handle.unhook() } catch (rollback: Throwable) {
                    XposedCompat.log("[PbCommentAutoLoadHook] batch rollback FAILED: ${rollback.message}")
                }
            }
            throw failure
        }
    }

    private fun schedule(vm: Any, state: Pending) {
        if (!state.gate.queue()) return
        val view = state.view.get() ?: return cancel(vm)
        val weakVm = WeakReference(vm)
        if (!view.post {
            val owner = weakVm.get() ?: return@post
            if (pending[owner] !== state) return@post
            val currentView = state.view.get()
            if (!enabled() || currentView == null || !currentView.isAttachedToWindow ||
                !currentView.isShown || !currentView.hasWindowFocus() || state.fragment.get() == null) {
                cancel(owner)
                return@post
            }
            if (!state.gate.take()) return@post
            pending.remove(owner)
            guarded {
                // Only the distance check is widened for page two. Native end/loading/session guards stay active.
                val count = size(owner)
                if (state.first < 0 || state.last < state.first || state.last >= count) return@guarded
                continuing = true
                try { targets.prefetch.invoke(owner, state.first, state.last, count) }
                finally { continuing = false }
            }
        }) cancel(vm)
    }

    private fun size(vm: Any): Int = (targets.items.invoke(vm) as List<*>).size
    private fun enabled() = active && Looper.myLooper() == Looper.getMainLooper() &&
        ConfigManager.snapshot().isAutoLoadMoreEnabled
    private fun cancel(vm: Any) { pending.remove(vm)?.gate?.cancel() }
    private fun cancelAll() { pending.values.forEach { it.gate.cancel() }; pending.clear() }
    private fun disable(failure: Throwable) {
        if (!active) return
        active = false
        cancelAll()
        XposedCompat.log("[PbCommentAutoLoadHook] batch disabled: ${failure.message}")
        XposedCompat.log(failure)
    }
    private inline fun <T> guarded(block: () -> T): T? = try { block() } catch (failure: Throwable) {
        disable(failure)
        null
    }
}
