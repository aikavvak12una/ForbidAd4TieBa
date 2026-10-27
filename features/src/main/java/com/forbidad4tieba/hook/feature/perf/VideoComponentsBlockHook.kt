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
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/** Playback and initialization are one group: a partial installation must never block either. */
object VideoComponentsBlockHook {
    private const val OWNER = "VideoComponentsBlockHook"

    private class Installation(
        val members: Map<Method, VideoDisableResult>,
        val emptyKernelConstructor: Constructor<*>,
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
        return install(
            targets, api,
            dependenciesReady = targets != null && AdSdkInitBlockHook.isPreparedFor(targets.adTargets),
        )
    }

    @Synchronized
    internal fun install(
        targets: VideoDisableSymbols?,
        api: XposedInterface,
        dependenciesReady: Boolean,
        enabled: () -> Boolean = ::isEnabled,
    ): InstallOutcome {
        if (!enabled()) return InstallOutcome.skipped("config disabled")
        val missing = VideoDisableTarget.entries.filter { targets?.targets?.get(it).isNullOrEmpty() }
        if (targets == null || missing.isNotEmpty()) {
            val reason = if (targets == null) "complete video group unavailable" else "missing ${missing.joinToString()}"
            return invalidate(reason)
        }
        if (!dependenciesReady) return invalidate("ad channel dependency is not fully prepared")
        val members = LinkedHashMap<Method, VideoDisableResult>()
        for (point in VideoDisableTarget.entries) {
            for (method in targets.targets.getValue(point)) {
                val previousResult = members.putIfAbsent(method, point.blockedResult)
                if (previousResult != null && previousResult != point.blockedResult) {
                    return invalidate("conflicting video results for $method")
                }
            }
        }
        for (method in listOf(targets.pcdnStart, targets.playDispatch)) {
            val previousResult = members.putIfAbsent(method, VideoDisableResult.VOID)
            if (previousResult != null && previousResult != VideoDisableResult.VOID) {
                return invalidate("conflicting video results for $method")
            }
        }
        val previous = installation
        if (previous?.active == true && previous.members == members &&
            previous.emptyKernelConstructor == targets.emptyKernelConstructor
        ) {
            return InstallOutcome(InstallState.ALREADY_INSTALLED, previous.handles.size)
        }
        if (previous != null) {
            val issues = previous.rollback()
            if (issues.isNotEmpty()) return InstallOutcome(InstallState.ROLLBACK_FAILED, previous.handles.size, issues.joinToString("; "))
            installation = null
        }

        val emptyKernelConstructor = targets.emptyKernelConstructor
        val group = Installation(members, emptyKernelConstructor)
        installation = group
        for ((method, result) in members) {
            try {
                method.isAccessible = true
                val builder = RuntimeHooks.builder(api, method, OWNER, "block")
                if (result == VideoDisableResult.EMPTY_KERNEL) {
                    // A failed replacement must not silently fall through to a real decoder factory.
                    builder.setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                }
                group.handles[method] = builder.intercept { chain ->
                    if (!group.active || !enabled()) return@intercept chain.proceed()
                    when (result) {
                        VideoDisableResult.BOOLEAN_FALSE -> false
                        VideoDisableResult.VOID -> null
                        VideoDisableResult.EMPTY_KERNEL -> emptyKernelConstructor.newInstance()
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

    private fun isEnabled() = ConfigManager.snapshot().isVideoComponentsDisabled
}
