package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal object ScanMessages {
    fun versionWarning(symbols: HookSymbols?): String? {
        if (!HookSymbolResolver.isScanVersionCheckFailed(symbols)) return null
        return UiText.Settings.scanTiebaVersionNotAdapted(HookSymbolResolver.TARGET_TIEBA_VERSION_NAME)
    }

    fun featureWarning(): String {
        return UiText.Settings.scanFeatureAbnormal(HookSymbolResolver.TARGET_TIEBA_VERSION_NAME)
    }
}
