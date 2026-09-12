package com.forbidad4tieba.hook.symbol.model

/** Stable public AB entry points, shared by discovery, installation and status reporting. */
enum class PerformanceAbTarget(val methodName: String) {
    COLD_START_TTI("coldStartTTIOpt"),
    COLD_START_TTI_2("coldStartTTIOpt2"),
    IDLE_TASK("idleTaskOpt"),
    IDLE_TASK_2("idleTaskOpt2"),
    COOKIE_REPEATED("cookieRepeatedOpt"),
    FEED_ICON("isFeedIconNewOpt"),
    FRS_CHAT_ASYNC("frsChatAsync"),
    FRS_CHAT_PRELOAD("frsChatAsyncPre"),
    FEED_UI("isFeedUIOpt"),
    COLD_NET_DATA("isColdNetDataOpt"),
    APSARAS_SCHEDULE("isOpenApsarasSchedule"),
    FRS_AD_SDK("isFrsFunAdSdkTest"),
    DUPLICATE_AD("isDuplicateRemovalFunAdABTest"),
    AUTO_PLAY_NEXT_VIDEO("isAutoPlayNextVideo"),
    HYBRID_PB("hybridPbOpt"),
    IMAGE_PERF_LOG("imagePerfLog"),
    PB_COMMENT_AD("isPbCommentFunAdABTest"),
    PB_BANNER_AD("isPbPageBannerFunAdSdkTest"),
    PB_ARCH("isPbArchTest"),
}

internal object PerformanceAbTargets {
    val featureRequirements = mapOf(
        HookFeatureKey.FORCE_HOST_PERFORMANCE_FLAGS to listOf(
            PerformanceAbTarget.COLD_START_TTI, PerformanceAbTarget.COLD_START_TTI_2,
            PerformanceAbTarget.IDLE_TASK, PerformanceAbTarget.IDLE_TASK_2,
            PerformanceAbTarget.COOKIE_REPEATED, PerformanceAbTarget.FEED_ICON,
            PerformanceAbTarget.FRS_CHAT_ASYNC, PerformanceAbTarget.FRS_CHAT_PRELOAD,
        ),
        HookFeatureKey.ENABLE_PB_PERFORMANCE_MODE to listOf(
            PerformanceAbTarget.HYBRID_PB, PerformanceAbTarget.IMAGE_PERF_LOG,
            PerformanceAbTarget.PB_COMMENT_AD, PerformanceAbTarget.PB_BANNER_AD,
        ),
        HookFeatureKey.FORCE_PB_PRELOAD to listOf(PerformanceAbTarget.HYBRID_PB, PerformanceAbTarget.PB_ARCH),
        HookFeatureKey.FORCE_HOST_FEED_COLD_OPT to listOf(PerformanceAbTarget.FEED_UI, PerformanceAbTarget.COLD_NET_DATA),
        HookFeatureKey.DISABLE_APSARAS_SCHEDULE to listOf(PerformanceAbTarget.APSARAS_SCHEDULE),
    )
}
