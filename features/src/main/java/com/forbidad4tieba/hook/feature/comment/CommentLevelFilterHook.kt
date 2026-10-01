package com.forbidad4tieba.hook.feature.comment

import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.CommentFilterTargets
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object CommentLevelFilterHook {
    private val hooks = OwnedHookSet<Method, XposedInterface.HookHandle> { it.unhook() }
    @Volatile private var activeInstallation: Any? = null
    @Volatile private var runtimeFailed = false
    fun isReady(): Boolean = activeInstallation != null && !runtimeFailed

    @Synchronized fun hook(targets: CommentFilterTargets): InstallOutcome {
        if (activeInstallation != null) return InstallOutcome(InstallState.ALREADY_INSTALLED, hooks.size())
        if (hooks.size() > 0) {
            val cleanup = rollback("retry incomplete comment filter installation")
            if (cleanup.installedCount > 0) return cleanup
        }
        val module = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        val installation = Any()
        val filter = CommentLevelFilter(targets.protocol)
        val failed = AtomicBoolean(false)
        runtimeFailed = false
        try {
            targets.parsers.forEach { (method, nested) ->
                hooks.install(method) {
                    RuntimeHooks.builder(module, method, "CommentLevelFilter", "response:$method").intercept { chain ->
                        if (activeInstallation !== installation || failed.get()) return@intercept chain.proceed()
                        val original = chain.args.firstOrNull() ?: return@intercept chain.proceed()
                        val snapshot = ConfigManager.snapshot()
                        val settings = CommentFilterOverrides.settings(original, snapshot.commentLevelFilter, snapshot.isCommentShortcutEnabled)
                        if (!settings.enabled) return@intercept chain.proceed()
                        val filtered = try {
                            filter.filter(original, nested, settings)
                        } catch (failure: Throwable) {
                            if (failed.compareAndSet(false, true)) {
                                runtimeFailed = true
                                XposedCompat.logW("[CommentLevelFilter] disabled for this process: ${failure.message}")
                            }
                            original
                        }
                        if (filtered === original) chain.proceed() else chain.proceed(arrayOf<Any?>(filtered))
                    }
                }
            }
            activeInstallation = installation
            XposedCompat.log("[CommentLevelFilter] INSTALLED handles=${hooks.size()}")
            return InstallOutcome(InstallState.INSTALLED, hooks.size())
        } catch (failure: Throwable) {
            return rollback(failure.message ?: "comment filter installation failed")
        }
    }

    private fun rollback(reason: String): InstallOutcome {
        activeInstallation = null
        val hadHooks = hooks.size() > 0
        val failures = hooks.rollback()
        failures.forEach { (method, failure) -> XposedCompat.logW("[CommentLevelFilter] rollback $method: ${failure.message}") }
        return InstallOutcome(when {
            failures.isNotEmpty() -> InstallState.ROLLBACK_FAILED
            hadHooks -> InstallState.ROLLED_BACK
            else -> InstallState.FAILED
        }, hooks.size(), reason)
    }
}
