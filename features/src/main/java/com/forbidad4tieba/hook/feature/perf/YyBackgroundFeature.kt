package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.YyBackgroundContract
import com.forbidad4tieba.hook.symbol.model.YyBackgroundSymbols
import com.forbidad4tieba.hook.symbol.model.YyBackgroundTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

internal val YyBackgroundFeature = FeatureDefinition.single(
    id = "YyBackgroundHook", phase = FeaturePhase.POST_ATTACH, process = FeatureProcess.MAIN,
    enabled = { it.isYyBackgroundDisabled },
) { cl, _ -> YyBackgroundHook.hook(YyBackgroundContract.resolve(cl, symbols)) }

internal object YyBackgroundHook {
    private class Installation(val symbols: YyBackgroundSymbols) {
        @Volatile var active = false
        val constructing = ThreadLocal<Boolean>()
        val handles = LinkedHashMap<Executable, XposedInterface.HookHandle>()
        fun release(): List<String> {
            active = false
            val errors = ArrayList<String>()
            for ((member, handle) in handles.entries.toList().asReversed()) {
                try { handle.unhook(); handles.remove(member) }
                catch (failure: Throwable) { errors += "$member: ${failure.message}" }
            }
            return errors
        }
    }

    @Volatile private var installation: Installation? = null

    fun hook(symbols: YyBackgroundSymbols?): InstallOutcome {
        val api = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        return install(api, symbols) { ConfigManager.snapshot().isYyBackgroundDisabled }
    }

    @Synchronized internal fun install(api: XposedInterface, symbols: YyBackgroundSymbols?, enabled: () -> Boolean): InstallOutcome {
        if (!enabled()) return InstallOutcome.skipped("config disabled")
        val previous = installation
        if (symbols != null && previous?.active == true && previous.symbols == symbols) {
            return InstallOutcome(InstallState.ALREADY_INSTALLED, previous.handles.size)
        }
        val errors = previous?.release().orEmpty()
        if (errors.isNotEmpty()) return InstallOutcome(InstallState.ROLLBACK_FAILED, previous!!.handles.size, errors.joinToString())
        installation = null
        if (symbols == null || symbols.members.keys != YyBackgroundTarget.entries.toSet()) {
            return InstallOutcome.skipped("complete YY background group unavailable")
        }
        val group = Installation(symbols)
        installation = group
        val onResult = symbols.method(YyBackgroundTarget.CALLBACK_RESULT)
        val onEvent = symbols.method(YyBackgroundTarget.CALLBACK_EVENT)
        try {
            // The ready result is overridden only inside the original constructor. No public
            // readiness state is published; an explicit live request still checks/loads the real core.
            group.handles[symbols.readiness] = RuntimeHooks.builder(api, symbols.readiness, "YyBackgroundHook", "constructorReady")
                .intercept { chain ->
                    if (group.active && enabled() && group.constructing.get() == true) true else chain.proceed()
                }
            group.handles[symbols.constructor] = RuntimeHooks.builder(api, symbols.constructor, "YyBackgroundHook", "constructor")
                .intercept { chain ->
                    if (!group.active || !enabled()) return@intercept chain.proceed()
                    val prior = group.constructing.get()
                    group.constructing.set(true)
                    try { chain.proceed() }
                    finally { if (prior == null) group.constructing.remove() else group.constructing.set(prior) }
                }
            for (point in listOf(YyBackgroundTarget.PRELOAD, YyBackgroundTarget.SUBPLUGINS,
                YyBackgroundTarget.INSERT_SUPPORT, YyBackgroundTarget.PRELOAD_CALLBACK)) {
                val method = symbols.method(point)
                group.handles[method] = RuntimeHooks.builder(api, method, "YyBackgroundHook", point.name).intercept { chain ->
                    if (!group.active || !enabled()) return@intercept chain.proceed()
                    when (point) {
                        YyBackgroundTarget.INSERT_SUPPORT -> false
                        YyBackgroundTarget.PRELOAD_CALLBACK -> {
                            chain.args[0]?.let { callback ->
                                onResult.invoke(callback, false)
                                // NPS RET_BUNDLE_FORBIDDEN: rejected, never a synthetic success.
                                onEvent.invoke(callback, false, 42)
                            }
                            null
                        }
                        else -> null
                    }
                }
            }
        } catch (failure: Throwable) {
            val rollback = group.release()
            if (group.handles.isEmpty()) installation = null
            return InstallOutcome(if (rollback.isEmpty()) InstallState.ROLLED_BACK else InstallState.ROLLBACK_FAILED,
                group.handles.size, "${failure.message}; ${rollback.joinToString()}")
        }
        group.active = true
        return InstallOutcome(InstallState.INSTALLED, group.handles.size)
    }
}
