package com.forbidad4tieba.hook.symbol.model

data class ScanMeta(
    val availability: ScanAvailabilityMeta = ScanAvailabilityMeta(),
    val scanErrors: List<String> = emptyList(),
    val source: String = "unsupported",
    val createdAt: Long = 0L,
    val cacheSchemaVersion: Int = HookSymbols.CACHE_SCHEMA_VERSION,
    val dexKitRuleVersion: Int = HookSymbols.DEXKIT_RULE_VERSION,
)

data class ScanAvailabilityMeta(
    val scanSupportState: String = ScanSupportState.UNKNOWN,
    val scanTargetVersionCode: Long? = null,
    val scanTargetVersionName: String? = null,
    val scanTargetVersionType: String? = null,
)
