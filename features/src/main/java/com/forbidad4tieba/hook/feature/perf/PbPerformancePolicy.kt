package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.SettingsSnapshot

/** Shared precedence for PB preload, hybrid rendering and the low-end WebView proxy block. */
internal enum class PbPerformancePolicy(
    val forcePreload: Boolean,
    val enableHybridOptimization: Boolean,
    val preserveWebViewProxy: Boolean,
) {
    HOST(false, false, false),
    PERFORMANCE(false, true, false),
    PRELOAD(true, false, true);

    companion object {
        /** Resolves one published snapshot without allocating state in a hook callback. */
        fun from(settings: SettingsSnapshot): PbPerformancePolicy = when {
            settings.isPbPreloadForced -> PRELOAD
            settings.isPbPerformanceModeEnabled -> PERFORMANCE
            else -> HOST
        }
    }
}
