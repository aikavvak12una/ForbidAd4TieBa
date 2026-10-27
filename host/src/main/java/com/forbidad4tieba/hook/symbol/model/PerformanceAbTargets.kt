package com.forbidad4tieba.hook.symbol.model

/** Stable public AB entry points, shared by discovery, installation and status reporting. */
enum class PerformanceAbTarget(val methodName: String) {
    COOKIE_REPEATED("cookieRepeatedOpt"),
    PB_COMMENT_AD("isPbCommentFunAdABTest"),
    PB_BANNER_AD("isPbPageBannerFunAdSdkTest"),
}

internal object PerformanceAbTargets {
    val featureRequirements = mapOf(
        HookFeatureKey.COOKIE_SYNC_DEDUP to listOf(PerformanceAbTarget.COOKIE_REPEATED),
    )
}
