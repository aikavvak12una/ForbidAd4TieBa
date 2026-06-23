package com.forbidad4tieba.hook.symbol.model

data class ResourceSymbols(
    val imageViewerShareIconResId: Int? = null,
    val homeNativeGlassSubPbNextPageMoreViewId: Int? = null,
    val homeNativeGlassPbReplyTitleDividerViewId: Int? = null,
    val homeNativeGlassDynamicBackgroundColorIds: List<Int> = emptyList(),
)

data class ScanMeta(
    val featureStatusMap: Map<String, HookFeatureStatus> = emptyMap(),
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
