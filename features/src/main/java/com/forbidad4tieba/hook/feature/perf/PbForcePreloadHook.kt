package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PbPreloadCardTargets
import com.forbidad4tieba.hook.symbol.model.PbPreloadProtoBuilder
import com.forbidad4tieba.hook.symbol.model.PbPreloadPageStateTargets
import com.forbidad4tieba.hook.symbol.model.PbPreloadTargets
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/** Present available card content early and keep its native body within the owning page. */
object PbForcePreloadHook {
    private const val TAG = "[PbForcePreloadHook]"
    private val hooked = AtomicBoolean(false)

    fun hook(targets: PbPreloadTargets, abMethods: Map<String, Method>) {
        if (!PbPerformancePolicy.from(ConfigManager.snapshot()).forcePreload) return
        val mod = XposedCompat.module ?: return
        val hybrid = abMethods[PerformanceAbTarget.HYBRID_PB.methodName] ?: run {
            XposedCompat.log("$TAG hybrid gate unresolved, installation skipped")
            return
        }
        if (!hooked.compareAndSet(false, true)) return
        val handles = ArrayList<XposedInterface.HookHandle>(6)
        val disabled = AtomicBoolean(false)
        val continuity = PbPreloadBodyContinuity()
        try {
            // Retain early cache results before enabling the cooperating preload gates.
            handles += RuntimeHooks.builder(mod, targets.pageState.constructor, "PbForcePreloadHook", "hook:targets.pageState.constructor").intercept { chain ->
                val original = chain.proceed()
                if (!disabled.get() && PbPerformancePolicy.from(ConfigManager.snapshot()).forcePreload) {
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
            handles += RuntimeHooks.builder(mod, targets.body.hybrid, "PbForcePreloadHook", "hook:targets.body.hybrid").intercept { chain ->
                val model = chain.args.firstOrNull()
                // Keep already accepted native models consistent if a later preload operation fails.
                if (PbPerformancePolicy.from(ConfigManager.snapshot()).forcePreload && model != null && continuity.isNative(model)) false else chain.proceed()
            }
            handles += RuntimeHooks.builder(mod, targets.body.commit, "PbForcePreloadHook", "hook:targets.body.commit").intercept { chain ->
                if (!disabled.get() && PbPerformancePolicy.from(ConfigManager.snapshot()).forcePreload) {
                    try {
                        retainNativeBody(targets, continuity, requireNotNull(chain.thisObject), requireNotNull(chain.args.firstOrNull()))
                    } catch (failure: Throwable) {
                        if (disabled.compareAndSet(false, true)) {
                            XposedCompat.log("$TAG body continuity FAILED, new previews disabled: ${failure.message}")
                            XposedCompat.log(failure)
                        }
                    }
                }
                chain.proceed()
            }
            handles += RuntimeHooks.builder(mod, targets.preloadSwitch, "PbForcePreloadHook", "hook:targets.preloadSwitch").intercept { chain ->
                if (!disabled.get() && PbPerformancePolicy.from(ConfigManager.snapshot()).forcePreload) true else chain.proceed()
            }
            handles += RuntimeHooks.builder(mod, hybrid, "PbForcePreloadHook", "hook:hybrid").intercept { chain ->
                val policy = PbPerformancePolicy.from(ConfigManager.snapshot())
                if (!disabled.get() && policy.forcePreload) policy.enableHybridOptimization else chain.proceed()
            }
            handles += RuntimeHooks.builder(mod, targets.provider, "PbForcePreloadHook", "hook:targets.provider").intercept { chain ->
                val original = chain.proceed()
                if (original != null || disabled.get() || !PbPerformancePolicy.from(ConfigManager.snapshot()).forcePreload) return@intercept original
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
            XposedCompat.log("$TAG hooks INSTALLED: count=${handles.size}/6")
        } catch (t: Throwable) {
            disabled.set(true)
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

    private fun retainNativeBody(t: PbPreloadTargets, continuity: PbPreloadBodyContinuity, page: Any, result: Any) {
        val b = t.body
        if (b.success.get(result) != true) return
        val model = requireNotNull(b.model.get(result))
        val thread = requireNotNull(b.thread.get(model))
        val tid = t.card.getTid.invoke(thread) as? String ?: return
        val response = b.response.get(result)
        val data = response?.let(b.responseData::get)
        val first = data?.let(b.firstFloor::get)
        val rawThread = data?.let(b.protocolThread::get)
        val content = first?.let(b.postContent::get) as? List<*>
        val postId = first?.let(b.postId::get) as? Long ?: 0L
        val nativeBody = data != null && first != null && rawThread != null &&
            t.card.isNormal.invoke(thread) == true && t.card.isPreloadType.invoke(null, thread) == true &&
            t.card.threadId.get(rawThread)?.toString() == tid &&
            b.postFloor.get(first) == 1 && !content.isNullOrEmpty() && content.all { t.card.contentType.isInstance(it) } &&
            b.forumBot.get(data) == null && b.shopGoods.get(data) == null
        continuity.accept(page, model, tid, postId, b.stage.get(result) == b.preloadStage,
            b.webView.get(model) != null, nativeBody) { b.webView.set(model, null) }
    }

    private fun retainPageState(t: PbPreloadPageStateTargets, viewModel: Any) {
        val original = requireNotNull(t.mutableField.get(viewModel))
        check(original === t.flowField.get(viewModel)) { "page state flow alias mismatch" }
        // A complete preload result may arrive before the UI starts collecting page state.
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
