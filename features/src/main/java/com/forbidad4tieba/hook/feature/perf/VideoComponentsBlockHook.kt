package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.VideoDisableResult
import com.forbidad4tieba.hook.symbol.model.VideoDisableSymbols
import com.forbidad4tieba.hook.symbol.model.VideoDisableTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/** Blocks background producers without replacing kernels or suppressing active playback. */
object VideoComponentsBlockHook {
    private const val OWNER = "VideoComponentsBlockHook"

    private class Installation(
        val members: Map<Method, VideoDisableResult>,
    ) {
        @Volatile var active = false
        val handles = LinkedHashMap<Method, XposedInterface.HookHandle>()

        fun rollback(): List<String> {
            active = false
            val issues = ArrayList<String>()
            for ((point, handle) in handles.entries.toList().asReversed()) {
                try {
                    handle.unhook()
                    handles.remove(point)
                } catch (failure: Throwable) {
                    // Keep failed handles for a later release attempt; their callbacks remain inert.
                    issues += "$point rollback: ${failure.javaClass.simpleName}: ${failure.message}"
                }
            }
            return issues
        }
    }

    @Volatile private var installation: Installation? = null

    internal fun isInstallationActive(): Boolean = installation?.active == true

    fun hook(targets: VideoDisableSymbols?): InstallOutcome {
        if (!isEnabled()) return InstallOutcome.skipped("config disabled")
        val api = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        return install(targets, api)
    }

    @Synchronized
    internal fun install(
        targets: VideoDisableSymbols?,
        api: XposedInterface,
        enabled: () -> Boolean = ::isEnabled,
    ): InstallOutcome {
        if (!enabled()) return InstallOutcome.skipped("config disabled")
        val missing = VideoDisableTarget.entries.filter { targets?.targets?.get(it).isNullOrEmpty() }
        if (targets == null || missing.isNotEmpty()) {
            val reason = if (targets == null) "complete video group unavailable" else "missing ${missing.joinToString()}"
            return invalidate(reason)
        }
        val members = LinkedHashMap<Method, VideoDisableResult>()
        for (point in VideoDisableTarget.entries) {
            for (method in targets.targets.getValue(point)) {
                val previousResult = members.putIfAbsent(method, point.blockedResult)
                if (previousResult != null && previousResult != point.blockedResult) {
                    return invalidate("conflicting video results for $method")
                }
            }
        }
        for (method in listOf(targets.pcdnStart)) {
            val previousResult = members.putIfAbsent(method, VideoDisableResult.VOID)
            if (previousResult != null && previousResult != VideoDisableResult.VOID) {
                return invalidate("conflicting video results for $method")
            }
        }
        val previous = installation
        if (previous?.active == true && previous.members == members
        ) {
            return InstallOutcome(InstallState.ALREADY_INSTALLED, previous.handles.size)
        }
        if (previous != null) {
            val issues = previous.rollback()
            if (issues.isNotEmpty()) return InstallOutcome(InstallState.ROLLBACK_FAILED, previous.handles.size, issues.joinToString("; "))
            installation = null
        }

        val group = Installation(members)
        installation = group
        for ((method, result) in members) {
            try {
                method.isAccessible = true
                val builder = RuntimeHooks.builder(api, method, OWNER, "block")
                group.handles[method] = builder.intercept { chain ->
                    if (!group.active || !enabled()) return@intercept chain.proceed()
                    when (result) {
                        VideoDisableResult.BOOLEAN_FALSE -> false
                        VideoDisableResult.VOID -> null
                    }
                }
            } catch (failure: Throwable) {
                val issues = group.rollback()
                if (group.handles.isEmpty()) installation = null
                return InstallOutcome(
                    if (issues.isEmpty()) InstallState.ROLLED_BACK else InstallState.ROLLBACK_FAILED,
                    group.handles.size,
                    "$method install: ${failure.javaClass.simpleName}: ${failure.message}" +
                        if (issues.isEmpty()) "" else "; ${issues.joinToString("; ")}",
                )
            }
        }
        group.active = true
        return InstallOutcome(InstallState.INSTALLED, group.handles.size)
    }

    private fun invalidate(reason: String): InstallOutcome {
        val issues = installation?.rollback().orEmpty()
        if (issues.isNotEmpty()) return InstallOutcome(
            InstallState.ROLLBACK_FAILED, installation?.handles?.size ?: 0, "$reason; ${issues.joinToString("; ")}",
        )
        installation = null
        return InstallOutcome.skipped(reason)
    }

    private fun isEnabled() = ConfigManager.snapshot().isVideoPreloadDisabled
}
