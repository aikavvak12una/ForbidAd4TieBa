package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.AutoRefreshSymbols
import com.forbidad4tieba.hook.symbol.model.HomeRefreshField
import com.forbidad4tieba.hook.symbol.model.HomeRefreshMethod
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

object AutoRefreshHook {
    private var runtime: HomeRefreshRuntime? = null

    @Synchronized internal fun hook(targets: AutoRefreshSymbols): InstallOutcome {
        if (!ConfigManager.snapshot().isAutoRefreshDisabled) return InstallOutcome(InstallState.SKIPPED)
        val api = XposedCompat.module ?: return InstallOutcome(InstallState.SKIPPED)
        if (runtime?.active == true) return InstallOutcome(InstallState.ALREADY_INSTALLED)
        val next = HomeRefreshRuntime(api, targets) { ConfigManager.snapshot().isAutoRefreshDisabled }
        val result = next.install()
        if (next.active) runtime = next
        return result
    }
}

/** Suppresses only entry refreshes and the continuations of their cache reads. */
internal class HomeRefreshRuntime(
    private val api: XposedInterface,
    private val targets: AutoRefreshSymbols,
    private val enabled: () -> Boolean,
) {
    private enum class Phase { INITIALIZE, BLOCK_REFRESH }

    private val flow = targets.flow
    private val phase = ThreadLocal<Phase?>()
    private val bootstrapCaches = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val handles = ArrayList<XposedInterface.HookHandle>()
    @Volatile var active = false
        private set

    fun install(): InstallOutcome {
        if (active) return InstallOutcome(InstallState.ALREADY_INSTALLED, handles.size)
        try {
            intercept(targets.cacheRestoreMethod, "cache availability") { chain ->
                if (chain.args.firstOrNull() == "disable_home_cache") false else chain.proceed()
            }
            listOf(HomeRefreshMethod.PAGE_INITIALIZE, HomeRefreshMethod.CACHE_INITIALIZE).forEach { role ->
                methods(role) { _, chain -> inPhase(Phase.INITIALIZE) { chain.proceed() } }
            }
            listOf(HomeRefreshMethod.APP_INITIALIZE, HomeRefreshMethod.PAGE_RESUME, HomeRefreshMethod.PAGE_FOREGROUND).forEach { role ->
                methods(role) { _, chain -> inPhase(Phase.BLOCK_REFRESH) { chain.proceed() } }
            }
            methods(HomeRefreshMethod.INITIAL_PREFETCH) { _, chain ->
                if (phase.get() == null) chain.proceed() else null
            }
            methods(HomeRefreshMethod.PAGE_TRIGGER) { _, chain ->
                if (phase.get() == null) chain.proceed() else null
            }
            val oldCache = flow.methods.getValue(HomeRefreshMethod.CACHE_LOAD).single {
                it.declaringClass == flow.method(HomeRefreshMethod.REFRESH).declaringClass
            }
            val newCache = flow.methods.getValue(HomeRefreshMethod.CACHE_LOAD).single {
                it.declaringClass == flow.field(HomeRefreshField.NEW_FEED_CACHE).type
            }
            methods(HomeRefreshMethod.REFRESH) { _, chain ->
                when (phase.get()) {
                    null -> chain.proceed()
                    Phase.BLOCK_REFRESH -> null
                    Phase.INITIALIZE -> oldCache.invoke(chain.thisObject, false)
                }
            }
            methods(HomeRefreshMethod.NEW_REFRESH) { _, chain ->
                when (phase.get()) {
                    null -> chain.proceed()
                    Phase.BLOCK_REFRESH -> null
                    Phase.INITIALIZE -> {
                        val model = flow.field(HomeRefreshField.FEED_OWNER_MODEL).get(chain.thisObject) ?: return@methods null
                        val cache = flow.field(HomeRefreshField.NEW_FEED_CACHE).get(model)
                        val listener = flow.field(HomeRefreshField.NEW_FEED_CACHE_LISTENER).get(model)
                        if (cache == null || listener == null) null else newCache.invoke(cache, listener, false)
                    }
                }
            }
            listOf(HomeRefreshMethod.DISPATCH, HomeRefreshMethod.NEW_DISPATCH).forEach { role ->
                methods(role) { _, chain -> if (phase.get() == null) chain.proceed() else null }
            }
            methods(HomeRefreshMethod.REFRESH_INDICATOR) { _, chain ->
                if (phase.get() != null && chain.args.firstOrNull() == true) null else chain.proceed()
            }
            flow.constructors.values.flatten().forEach { constructor ->
                intercept(constructor, "initial cache " + constructor.declaringClass.simpleName) { chain ->
                    val result = chain.proceed()
                    if (phase.get() == Phase.INITIALIZE) chain.thisObject?.let { bootstrapCaches[it] = true }
                    result
                }
            }
            listOf(HomeRefreshMethod.CACHE_BACKGROUND, HomeRefreshMethod.CACHE_POST).forEach { role ->
                methods(role) { _, chain ->
                    if (bootstrapCaches.containsKey(chain.thisObject)) inPhase(Phase.BLOCK_REFRESH) { chain.proceed() }
                    else chain.proceed()
                }
            }
            listOf(HomeRefreshMethod.CACHE_CONTINUE to HomeRefreshField.CACHE_CONTINUATION_TASK,
                HomeRefreshMethod.IDLE_CONTINUE to HomeRefreshField.IDLE_CONTINUATION_TASK).forEach { (role, parent) ->
                methods(role) { _, chain ->
                    val task = flow.field(parent).get(chain.thisObject)
                    if (bootstrapCaches.containsKey(task)) inPhase(Phase.BLOCK_REFRESH) { chain.proceed() }
                    else chain.proceed()
                }
            }
            check(handles.all(RuntimeHooks::ownsActiveHandle)) { "An entry-refresh handle is inactive" }
            active = true
            XposedCompat.log("[AutoRefreshHook] installed: entry refresh suppression; handles=" + handles.size)
            return InstallOutcome(InstallState.INSTALLED, handles.size)
        } catch (failure: Throwable) {
            val cleanup = release()
            val state = if (cleanup.isEmpty()) InstallState.ROLLED_BACK else InstallState.ROLLBACK_FAILED
            XposedCompat.log("[AutoRefreshHook] install rolled back: " + failure.message)
            return InstallOutcome(state, reason = (listOf(failure.message.orEmpty()) + cleanup).joinToString("; "))
        }
    }

    fun release(): List<String> {
        active = false
        val failures = ArrayList<String>()
        handles.asReversed().forEach { handle ->
            try { handle.unhook() } catch (failure: Throwable) { failures += failure.message.orEmpty() }
        }
        bootstrapCaches.clear()
        phase.remove()
        return failures
    }

    internal fun retainedCount(): Int = bootstrapCaches.size

    private fun intercept(member: Executable, purpose: String, callback: (XposedInterface.Chain) -> Any?) {
        handles += RuntimeHooks.builder(api, member, "AutoRefreshHook", purpose).intercept { chain ->
            if (!active || !enabled()) chain.proceed() else callback(chain)
        }
    }

    private fun methods(role: HomeRefreshMethod, callback: (Method, XposedInterface.Chain) -> Any?) {
        flow.methods.getValue(role).forEach { method -> intercept(method, role.name) { callback(method, it) } }
    }

    private inline fun <T> inPhase(value: Phase, block: () -> T): T {
        val previous = phase.get()
        phase.set(value)
        try { return block() } finally { if (previous == null) phase.remove() else phase.set(previous) }
    }
}
