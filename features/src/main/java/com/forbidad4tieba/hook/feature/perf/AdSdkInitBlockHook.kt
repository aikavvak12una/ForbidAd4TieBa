package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/** Uses the host's forbidden-channel path, preserving framework initialization and completion. */
object AdSdkInitBlockHook {
    private data class PreparedPoint(val method: Method, val handle: XposedInterface.HookHandle)

    private val channels = arrayOf(
        PerformanceRuntimeTarget.AD_CSJ,
        PerformanceRuntimeTarget.AD_GDT,
        PerformanceRuntimeTarget.AD_BAIDU,
    )
    private val installed = LinkedHashMap<PerformanceRuntimeTarget, PreparedPoint>()
    private var prepared: Map<PerformanceRuntimeTarget, PreparedPoint>? = null

    fun hook(targets: Map<PerformanceRuntimeTarget, Method>): InstallOutcome? {
        val settings = ConfigManager.snapshot()
        if (!settings.isAdSdkComponentsDisabled && !settings.isVideoComponentsDisabled) {
            return InstallOutcome.skipped("config disabled")
        }
        val api = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        return install(targets, api)
    }

    @Synchronized
    internal fun install(
        targets: Map<PerformanceRuntimeTarget, Method>,
        api: XposedInterface,
        requested: Boolean = true,
        enabled: () -> Boolean = ::isEnabled,
    ): InstallOutcome? {
        if (!requested) return InstallOutcome.skipped("config disabled")
        prepared = null
        val issues = ArrayList<String>()
        for (channel in channels) {
            val method = targets[channel]
            val previous = installed[channel]
            if (previous != null && (previous.method != method || !RuntimeHooks.ownsActiveHandle(previous.handle))) {
                try {
                    previous.handle.unhook()
                    installed.remove(channel)
                } catch (failure: Throwable) {
                    issues += "$channel release: ${failure.javaClass.simpleName}: ${failure.message}"
                    continue
                }
            }
            if (method == null) {
                issues += "$channel missing"
                continue
            }
            if (installed.containsKey(channel)) continue
            try {
                method.isAccessible = true
                val handle = RuntimeHooks.builder(api, method, "AdSdkInitBlockHook", channel.name).intercept { chain ->
                    if (enabled()) false else chain.proceed()
                }
                installed[channel] = PreparedPoint(method, handle)
            } catch (failure: Throwable) {
                issues += "$channel: ${failure.javaClass.simpleName}: ${failure.message}"
            }
        }
        if (channels.all { channel ->
                val point = installed[channel]
                point != null && point.method == targets[channel] && RuntimeHooks.ownsActiveHandle(point.handle)
            }) {
            prepared = installed.toMap()
        } else if (issues.isEmpty()) {
            issues += "ad channel handles are not fully prepared"
        }
        return if (issues.isEmpty()) null else InstallOutcome(InstallState.PARTIAL, reason = issues.joinToString("; "))
    }

    /** Installation-stage check only; callbacks never inspect ownership or the prepared map. */
    @Synchronized
    internal fun isPreparedFor(targets: Map<PerformanceRuntimeTarget, Method>): Boolean {
        val current = prepared ?: return false
        return channels.all { channel ->
            val point = current[channel]
            point != null && point.method == targets[channel] && RuntimeHooks.ownsActiveHandle(point.handle)
        }
    }

    private fun isEnabled(): Boolean {
        val settings = ConfigManager.snapshot()
        return settings.isAdSdkComponentsDisabled ||
            (settings.isVideoComponentsDisabled && VideoComponentsBlockHook.isInstallationActive())
    }
}
