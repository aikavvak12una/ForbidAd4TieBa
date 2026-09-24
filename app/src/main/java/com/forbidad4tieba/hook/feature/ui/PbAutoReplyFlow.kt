package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PbAutoReplyFlowTargets
import io.github.libxposed.api.XposedInterface.HookHandle
import java.util.Collections
import java.util.WeakHashMap

/** Limit both changes to the exact request created by the preset send click. */
internal class PbAutoReplyFlow(private val targets: PbAutoReplyFlowTargets) {
    private class Dispatch {
        var model: Any? = null
        var write: Any? = null
        var uploadEntered = false
        var accepted = false
    }

    private enum class RequestStage { UPLOADING, VERIFYING }

    private val sending = ThreadLocal<Dispatch>()
    private val quietSuccess = ThreadLocal<Boolean>()
    private val requests = Collections.synchronizedMap(WeakHashMap<Any, RequestStage>())
    private val handles = ArrayList<HookHandle>(5)
    @Volatile private var disabled = false

    fun install() {
        val mod = checkNotNull(XposedCompat.module)
        try {
            handles += mod.hook(targets.send).intercept { chain ->
                val dispatch = sending.get()
                if (dispatch == null) {
                    // A new native send click takes ownership, even when reusing the same WriteData.
                    if (requests.isNotEmpty()) {
                        try {
                            val model = targets.writeModel.get(chain.thisObject)
                            if (model != null) requests.remove(targets.writeData.get(model))
                        } catch (t: Throwable) {
                            disable(t)
                        }
                    }
                    return@intercept chain.proceed()
                }
                val model = try {
                    val owner = chain.thisObject ?: error("reply editor missing")
                    val current = targets.writeModel.get(owner) ?: error("reply model missing")
                    // Existing drafts or an in-flight write belong to the native editor, not this click.
                    if (disabled || dispatch.model != null || dispatch.accepted || chain.args[1] != null ||
                        targets.writeData.get(current) != null) {
                        XposedCompat.logD { "[PbLikeAutoReplyHook] skipped: editor already owns a write" }
                        return@intercept null
                    }
                    current
                } catch (t: Throwable) {
                    disable(t)
                    return@intercept null
                }
                dispatch.model = model
                dispatch.uploadEntered = false
                try {
                    // A null parent lets the host construct a type-1 reply, including content and send_from.
                    chain.proceed(arrayOf<Any?>(null, null))
                } finally {
                    try {
                        val write = dispatch.write
                        // A rejected send has no callback. Reclaim only our still-owned, unsubmitted write.
                        if (!dispatch.uploadEntered && write != null &&
                            targets.writeModel.get(chain.thisObject) === model && targets.writeData.get(model) === write) {
                            targets.setWriteData.invoke(model, null)
                        }
                    } catch (t: Throwable) {
                        disable(t)
                    } finally {
                        dispatch.model = null
                        dispatch.write = null
                    }
                }
            }
            handles += mod.hook(targets.setWriteData).intercept { chain ->
                val dispatch = sending.get()
                if (dispatch != null && dispatch.model === chain.thisObject && dispatch.write == null) {
                    // Capture the first assignment; later replacements never become ours.
                    dispatch.write = chain.args[0]
                }
                chain.proceed()
            }
            handles += mod.hook(targets.upload).intercept { chain ->
                val dispatch = sending.get()
                if (dispatch == null || dispatch.model !== chain.thisObject) return@intercept chain.proceed()
                val write = try {
                    val current = targets.writeData.get(chain.thisObject) ?: error("ordinary reply was not constructed")
                    if (current !== dispatch.write) return@intercept false
                    check(targets.getType.invoke(current) == 1 && targets.getFloor.invoke(current) == null &&
                        targets.getSubPostId.invoke(current) == null) { "unexpected ordinary reply context" }
                    current
                } catch (t: Throwable) {
                    disable(t)
                    return@intercept false
                }
                // Mark before upload: a synchronous completion must already see the same request.
                requests[write] = RequestStage.UPLOADING
                // Once the host upload is entered, an exception cannot prove that nothing was submitted.
                dispatch.uploadEntered = true
                try {
                    val result = chain.proceed()
                    dispatch.accepted = result == true
                    if (!dispatch.accepted) requests.remove(write)
                    result
                } catch (t: Throwable) {
                    requests.remove(write)
                    throw t
                }
            }
            handles += mod.hook(targets.callback).intercept { chain ->
                val write = chain.args[3] ?: return@intercept chain.proceed()
                val success = chain.args[0] == true
                val automatic = synchronized(requests) {
                    val stage = requests[write] ?: return@synchronized false
                    val verification = !success && stage == RequestStage.UPLOADING && try {
                        chain.args[2] != null || chain.args[1]?.let { targets.getErrorCode.invoke(it) == 227001 } == true
                    } catch (t: Throwable) {
                        disable(t)
                        false
                    }
                    // Verification returns a second callback. Its failure/cancel is terminal, even if
                    // the activity returns the original account-verification error code again.
                    if (verification) requests[write] = RequestStage.VERIFYING else requests.remove(write)
                    success
                }
                if (!automatic) return@intercept chain.proceed()
                val previous = quietSuccess.get()
                quietSuccess.set(true)
                try {
                    // Keep editor cleanup, result handling and counts; only the navigation consumer is skipped.
                    chain.proceed()
                } finally {
                    if (previous == null) quietSuccess.remove() else quietSuccess.set(previous)
                }
            }
            handles += mod.hook(targets.transientPost).intercept { chain ->
                if (quietSuccess.get() == true) null else chain.proceed()
            }
        } catch (t: Throwable) {
            close()
            throw t
        }
    }

    fun send(view: View): Boolean {
        if (disabled || sending.get() != null) return false
        val dispatch = Dispatch()
        sending.set(dispatch)
        return try {
            view.performClick()
            dispatch.accepted
        } finally {
            sending.remove()
        }
    }

    fun close() {
        handles.asReversed().forEach { it.unhook() }
        handles.clear()
        requests.clear()
    }

    private fun disable(t: Throwable) {
        if (disabled) return
        disabled = true
        XposedCompat.log("[PbLikeAutoReplyHook] reply flow FAILED, disabled for this process: ${t.message}")
        XposedCompat.log(t)
    }
}
