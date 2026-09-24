package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PbPreloadCardTargets
import com.forbidad4tieba.hook.symbol.model.PbPreloadProtoBuilder
import com.forbidad4tieba.hook.symbol.model.PbPreloadPageStateTargets
import com.forbidad4tieba.hook.symbol.model.PbPreloadTargets
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/** Supply the clicked ordinary card through the native PB provider, so PageBrowser receives it too. */
object PbForcePreloadHook {
    private const val TAG = "[PbForcePreloadHook]"
    private val hooked = AtomicBoolean(false)

    fun hook(targets: PbPreloadTargets, abMethods: Map<String, Method>) {
        if (!ConfigManager.isPbPreloadForced) return
        val mod = XposedCompat.module ?: return
        val hybrid = abMethods[PerformanceAbTarget.HYBRID_PB.methodName] ?: run {
            XposedCompat.log("$TAG hybrid gate unresolved, installation skipped")
            return
        }
        if (!hooked.compareAndSet(false, true)) return
        val handles = ArrayList<XposedInterface.HookHandle>(4)
        val disabled = AtomicBoolean(false)
        try {
            handles += mod.hook(targets.preloadSwitch).intercept { chain ->
                if (ConfigManager.isPbPreloadForced && !disabled.get()) true else chain.proceed()
            }
            handles += mod.hook(hybrid).intercept { chain ->
                if (ConfigManager.isPbPreloadForced && !disabled.get()) false else chain.proceed()
            }
            handles += mod.hook(targets.pageState.constructor).intercept { chain ->
                val original = chain.proceed()
                if (!disabled.get() && ConfigManager.isPbPreloadForced) {
                    try {
                        retainPageState(targets.pageState, requireNotNull(chain.thisObject))
                    } catch (failure: Throwable) {
                        if (disabled.compareAndSet(false, true)) {
                            XposedCompat.log("$TAG page state replay FAILED, disabled for this process: ${failure.message}")
                            XposedCompat.log(failure)
                        }
                    }
                }
                original
            }
            handles += mod.hook(targets.provider).intercept { chain ->
                val original = chain.proceed()
                if (original != null || disabled.get() || !ConfigManager.isPbPreloadForced) return@intercept original
                val tid = chain.args.firstOrNull() as? String ?: return@intercept original
                try {
                    val card = targets.cardGetter.invoke(null) ?: return@intercept original
                    buildCardResponse(targets.card, card, tid)
                } catch (failure: Throwable) {
                    if (disabled.compareAndSet(false, true)) {
                        XposedCompat.log("$TAG card preload FAILED, disabled for this process: ${failure.message}")
                        XposedCompat.log(failure)
                    }
                    original
                }
            }
            XposedCompat.log("$TAG hooks INSTALLED: count=${handles.size}/4")
        } catch (t: Throwable) {
            var removed = true
            for (handle in handles.asReversed()) {
                try {
                    handle.unhook()
                } catch (failure: Throwable) {
                    removed = false
                    XposedCompat.log("$TAG rollback failed: ${failure.message}")
                }
            }
            if (removed) hooked.set(false)
            XposedCompat.log("$TAG install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun retainPageState(t: PbPreloadPageStateTargets, viewModel: Any) {
        val original = requireNotNull(t.mutableField.get(viewModel))
        check(original === t.flowField.get(viewModel)) { "page state flow alias mismatch" }
        // The host already retains its latest page state. Replay it to a UI that subscribes after preload.
        val flow = t.createFlow.invoke(null, 1, 0, t.suspendOverflow)
        t.flowField.set(viewModel, flow)
        try {
            t.mutableField.set(viewModel, flow)
        } catch (failure: Throwable) {
            t.flowField.set(viewModel, original)
            throw failure
        }
    }

    private fun buildCardResponse(t: PbPreloadCardTargets, card: Any, tid: String): Any? {
        if (tid.isBlank() || t.getTid.invoke(card) != tid || t.isNormal.invoke(card) != true ||
            t.isPreloadType.invoke(null, card) != true) return null
        val thread = t.getThread.invoke(card) ?: return null
        if (t.threadId.get(thread)?.toString() != tid || (t.firstPostId.get(thread) as? Long ?: 0L) <= 0L) return null
        val content = t.content.get(thread) as? List<*> ?: return null
        if (content.isEmpty() || content.any { !t.contentType.isInstance(it) }) return null

        val post = t.postBuilder.constructor.newInstance()
        for ((from, to) in t.postCopies) to.set(post, from.get(thread))
        t.postFloor.set(post, 1)
        t.postContent.set(post, content)
        val first = t.postBuilder.finish(post)
        val page = t.pageBuilder.constructor.newInstance()
        t.pageNumber.set(page, 1)
        val forum = t.forum.get(thread) ?: run {
            val fid = t.getForumId.invoke(card) as Long
            val name = t.getForumName.invoke(card) as? String
            if (fid <= 0 || name.isNullOrBlank()) return null
            val builder = t.forumBuilder.constructor.newInstance()
            t.forumId.set(builder, fid)
            t.forumName.set(builder, name)
            t.forumBuilder.finish(builder)
        }
        val response = t.responseBuilder.constructor.newInstance()
        t.responseThread.set(response, thread)
        t.responseForum.set(response, forum)
        t.responsePage.set(response, t.pageBuilder.finish(page))
        t.responseFirstFloor.set(response, first)
        t.responseFirstFloorPost.set(response, first)
        t.responsePosts.set(response, listOf(first))
        t.author.get(thread)?.let { t.responseUsers.set(response, listOf(it)) }
        return t.responseBuilder.finish(response)
    }

    private fun PbPreloadProtoBuilder.finish(builder: Any): Any = build.invoke(builder, true)!!
}
