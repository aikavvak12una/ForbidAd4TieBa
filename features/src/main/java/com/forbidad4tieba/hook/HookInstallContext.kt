package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols

/** Immutable inputs for one installation phase. Never retained by hook callbacks. */
internal class HookInstallContext(
    val processName: String,
    private val resolvedSymbols: HookSymbols?,
) {
    val symbols: HookSymbols get() = checkNotNull(resolvedSymbols) { "Symbols unavailable during static installation" }
    val isMain: Boolean = HookProcess.isMain(processName)
    val isSystemUi: Boolean = HookProcess.isSystemUi(processName)
    val isImageViewerRemote: Boolean = HookProcess.isImageViewerRemote(processName)
    val isImageViewerProcess: Boolean = HookProcess.isImageViewerProcess(processName)

    private val statusMap: Map<String, HookFeatureStatus> by lazy(LazyThreadSafetyMode.NONE) {
        HookSymbolResolver.featureStatusMap(symbols)
    }

    fun available(featureKey: String): Boolean = statusMap[featureKey]?.isSupported() == true
}
