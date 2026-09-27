package com.forbidad4tieba.hook.symbol.cache

import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal object HookSymbolCacheKeys {
    const val PREFS_NAME = "tbhook_symbol_cache"
    const val SYMBOL_FP = "hook_symbol_fp_v23"
    const val SYMBOL_JSON = "hook_symbol_json_v75"
    const val MODULE_VERSION = "hook_symbol_cache_module_version"
    const val VERIFIED_FP = "hook_symbol_verified_fp_v1"
}

internal class HookSymbolMemoryCache {
    private class Entry(val fingerprint: String, val symbols: HookSymbols)
    @Volatile private var entry: Entry? = null

    fun currentSymbols(): HookSymbols? = entry?.symbols

    fun getIfFingerprint(currentFingerprint: String): HookSymbols? {
        val snapshot = entry ?: return null
        return snapshot.symbols.takeIf { snapshot.fingerprint == currentFingerprint }
    }

    fun put(currentFingerprint: String, currentSymbols: HookSymbols) {
        entry = Entry(currentFingerprint, currentSymbols)
    }

    fun clear() {
        entry = null
    }
}

internal object HookSymbolCachePolicy {
    /** The payload supplier is never touched when the host/module fingerprint has changed. */
    inline fun decodeIfFingerprint(stored: String?, current: String, readPayload: () -> String?): HookSymbols? {
        if (stored != current) return null
        return HookSymbols.fromJson(readPayload())
    }

    fun isUsable(symbols: HookSymbols): Boolean {
        if (symbols.cacheSchemaVersion != HookSymbols.CACHE_SCHEMA_VERSION ||
            symbols.dexKitRuleVersion != HookSymbols.DEXKIT_RULE_VERSION
        ) return false
        return when (symbols.source) {
            "unsupported" -> true
            "scan", "partial" -> symbols.createdAt > 0L
            else -> false
        }
    }
}
