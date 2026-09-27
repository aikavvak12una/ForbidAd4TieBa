package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

internal data class UbsAbTestBooleanOverride(
    val target: PerformanceAbTarget,
    val value: Boolean,
    val enabled: () -> Boolean,
) {
    val methodName: String get() = target.methodName
}

internal object UbsAbTestBooleanOverrideInstaller {
    fun hasEnabledOverride(overrides: Array<UbsAbTestBooleanOverride>): Boolean {
        return overrides.any { it.enabled() }
    }

    fun installEnabled(
        tag: String,
        module: XposedModule,
        methods: Map<String, Method>,
        overrides: Array<UbsAbTestBooleanOverride>,
    ): Int {
        val enabled = overrides.filter { it.enabled() }
        var installed = 0
        for (entry in enabled) {
            val method = methods[entry.methodName]
            if (method == null) {
                XposedCompat.log("$tag HookInstall[${entry.methodName}] state=MISSING methodOrConsumer")
                continue
            }
            try {
                install(tag, module, method, entry)
                installed++
                XposedCompat.logD("$tag HookInstall[${entry.methodName}] state=INSTALLED")
            } catch (t: Throwable) {
                XposedCompat.log("$tag HookInstall[${entry.methodName}] state=ERROR ${t.message}")
                XposedCompat.log(t)
            }
        }
        XposedCompat.log("$tag hooks INSTALLED: count=$installed/${enabled.size}")
        return installed
    }

    private fun install(owner: String, module: XposedModule, method: Method, override: UbsAbTestBooleanOverride) {
        method.isAccessible = true
        RuntimeHooks.builder(module, method, owner, "override").intercept { chain ->
            if (override.enabled()) {
                override.value
            } else {
                chain.proceed()
            }
        }
    }

}
