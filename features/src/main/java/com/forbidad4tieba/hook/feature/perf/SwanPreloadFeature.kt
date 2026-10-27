package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.SwanPreloadContract
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

internal val SwanPreloadFeature = FeatureDefinition.observed(
    id = "SwanPreloadHook", phase = FeaturePhase.POST_ATTACH, process = FeatureProcess.MAIN,
    enabled = { it.isSwanPreloadDisabled },
) { cl, _ ->
    val targets = SwanPreloadContract.resolve(cl, symbols)
    val api = XposedCompat.module
    if (targets != null && api != null) SwanPreloadHook.install(api, targets) { ConfigManager.snapshot().isSwanPreloadDisabled }
}

internal object SwanPreloadHook {
    // Both producers can be suppressed independently; neither mutates an active launch or waits
    // for a callback. The shared client's pre-bind method is also used for explicit launches.
    fun install(api: XposedInterface, targets: List<Method>, enabled: () -> Boolean) {
        if (!enabled()) return
        for (method in targets) RuntimeHooks.builder(api, method, "SwanPreloadHook", "producer").intercept { chain ->
            if (enabled()) null else chain.proceed()
        }
    }
}
