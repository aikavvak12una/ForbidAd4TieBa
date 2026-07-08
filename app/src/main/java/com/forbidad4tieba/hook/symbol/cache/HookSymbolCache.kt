package com.forbidad4tieba.hook.symbol.cache

import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal object HookSymbolCacheKeys {
    const val SYMBOL_FP = "hook_symbol_fp_v23"
    const val SYMBOL_JSON = "hook_symbol_json_v71"
    const val MODULE_VERSION = "hook_symbol_cache_module_version"
    const val VERIFIED_FP = "hook_symbol_verified_fp_v1"
}

internal class HookSymbolMemoryCache {
    @Volatile private var fingerprint: String? = null
    @Volatile private var symbols: HookSymbols? = null

    fun currentSymbols(): HookSymbols? = symbols

    fun getIfFingerprint(currentFingerprint: String): HookSymbols? {
        return symbols?.takeIf { fingerprint == currentFingerprint }
    }

    fun put(currentFingerprint: String, currentSymbols: HookSymbols) {
        fingerprint = currentFingerprint
        symbols = currentSymbols
    }

    fun clear() {
        fingerprint = null
        symbols = null
    }
}
