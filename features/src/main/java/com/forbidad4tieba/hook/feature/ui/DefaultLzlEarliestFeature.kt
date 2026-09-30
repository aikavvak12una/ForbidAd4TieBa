package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SimpleToggle
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.LzlSortContract
import java.lang.reflect.Method

internal val DefaultLzlEarliestFeature = FeatureDefinition.observed(
    id = "DefaultLzlEarliestHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    toggle = SimpleToggle.DEFAULT_LZL_EARLIEST,
) { cl, _ ->
    LzlSortContract.resolve(cl, symbols)?.let(DefaultLzlEarliestHook::install)
}

internal object DefaultLzlEarliestHook {
    fun install(setter: Method) {
        val module = XposedCompat.module ?: return
        RuntimeHooks.builder(module, setter, "DefaultLzlEarliestHook", "defaultSort").intercept { chain ->
            val original = chain.args[0] as? String
            val replacement = defaultSort(original, ConfigManager.snapshot()[SimpleToggle.DEFAULT_LZL_EARLIEST])
            if (replacement == original) chain.proceed() else chain.proceed(arrayOf<Any?>(replacement))
        }
    }

    // Host protocol: 0 = earliest, 1 = latest, 2 = hot. The H5 manual sort path bypasses this setter.
    internal fun defaultSort(original: String?, enabled: Boolean): String? =
        if (enabled && (original == null || original == "2")) "0" else original
}
