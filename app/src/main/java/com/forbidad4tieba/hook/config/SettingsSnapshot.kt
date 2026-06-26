package com.forbidad4tieba.hook.config

data class SettingsSnapshot(
    val areRestrictedFeaturesUnlocked: Boolean = false,
    val isAdBlockEnabled: Boolean = false,
    val isFeedAdBlockEnabled: Boolean = false,
    val isPostPageAdBlockEnabled: Boolean = false,
    val isForumPageAdBlockEnabled: Boolean = false,
    val isStrategyAdBlockEnabled: Boolean = false,
    val isSearchBoxTextAdBlockEnabled: Boolean = false,
    val isHomeTopBarAdBlockEnabled: Boolean = false,
    val isMineTabWebAdBlockEnabled: Boolean = false,
    val isHomeSideBarWebAdBlockEnabled: Boolean = false,
    val isHomeTopTabsCustomEnabled: Boolean = false,
    val isHomeTopTabMaterialEnabled: Boolean = true,
    val isHomeTopTabRecommendEnabled: Boolean = true,
    val isHomeTopTabLiveEnabled: Boolean = true,
    val isHomeTopTabFollowedEnabled: Boolean = true,
    val homeTopTabDisabledKeys: Set<String> = emptySet(),
    val isHomeTabAutoHideEnabled: Boolean = false,
    val isBottomTabsCustomEnabled: Boolean = false,
    val isBottomTabHomeEnabled: Boolean = true,
    val isBottomTabEnterForumEnabled: Boolean = true,
    val isBottomTabRetailStoreEnabled: Boolean = true,
    val isBottomTabMessageEnabled: Boolean = true,
    val isBottomTabMineEnabled: Boolean = true,
    val isEnterForumWebFilterEnabled: Boolean = false,
    val isOpenWebLinkInSystemBrowserEnabled: Boolean = false,
    val isHomeNativeGlassEnabled: Boolean = false,
    val isHomeTabDynamicTintEnabled: Boolean = ConfigManager.DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED,
    val homeNativeGlassLightStyle: ConfigManager.HomeNativeGlassStyleConfig =
        ConfigManager.HomeNativeGlassStyleConfig(),
    val homeNativeGlassDarkStyle: ConfigManager.HomeNativeGlassStyleConfig =
        ConfigManager.HomeNativeGlassStyleConfig(),
    val isAutoRefreshDisabled: Boolean = false,
    val isAutoLoadMoreEnabled: Boolean = false,
    val isPbLikeAutoReplyEnabled: Boolean = false,
    val pbLikeAutoReplyText: String = "",
    val isReplyVisibilityProbeEnabled: Boolean = false,
    val replyVisibilityProbeMaxAttempts: Int =
        ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
    val replyVisibilityProbeIntervalMs: Int =
        ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
    val isPbScrollCoalesceEnabled: Boolean = false,
    val isDefaultNotifyTabEnabled: Boolean = true,
    val isDefaultOriginalImageEnabled: Boolean = false,
    val isAutoSignInEnabled: Boolean = false,
    val isCleanShareTrackingParamsEnabled: Boolean = true,
    val isAiComponentsDisabled: Boolean = false,
    val isCustomPostFilterEnabled: Boolean = false,
    val isAdSdkComponentsDisabled: Boolean = false,
    val isVideoComponentsDisabled: Boolean = false,
    val isMonitorSyncComponentsDisabled: Boolean = false,
    val isPbPerformanceModeEnabled: Boolean = false,
    val isFeedUiOptForced: Boolean = false,
    val isForceFeedUiOptRuntimeEnabled: Boolean = false,
    val isPerformanceOptimizationEnabled: Boolean = false,
    val isHostPerformanceFlagsForced: Boolean = false,
    val isApsarasScheduleDisabled: Boolean = false,
    val isFlutterPreinitDisabled: Boolean = false,
    val isLowEndDeviceConfigForced: Boolean = false,
    val isTitanPatchBlockEnabled: Boolean = false,
    val isPrivateReadReceiptInvisibleEnabled: Boolean = false,
    val isPostVoteFilterEnabled: Boolean = false,
    val isPostVideoFilterEnabled: Boolean = false,
    val isPostReplyFilterEnabled: Boolean = false,
    val isPostHotFilterEnabled: Boolean = false,
    val isPostGoodsFilterEnabled: Boolean = false,
    val isPostGameBookingFilterEnabled: Boolean = false,
    val isPostHelpFilterEnabled: Boolean = false,
    val isPostScoreFilterEnabled: Boolean = false,
    val isPostLotteryFilterEnabled: Boolean = false,
    val isPostLiveFilterEnabled: Boolean = false,
    val isPostRecommendForumFilterEnabled: Boolean = false,
    val isPostUnfollowedForumFilterEnabled: Boolean = false,
    val isPostForumKeywordFilterEnabled: Boolean = false,
    val postForumKeywordList: List<String> = emptyList(),
    val isPostModelScoreFilterEnabled: Boolean = false,
    val postModelScoreThresholds: List<ConfigManager.ModelScoreThreshold> = emptyList(),
    val postModelScoreAutoPercentiles: Map<String, Int> = emptyMap(),
    val postModelScoreStatsPostLimit: Int = ConfigManager.DEFAULT_MODEL_SCORE_STATS_POST_LIMIT,
    val isDetailedLoggingEnabled: Boolean = false,
) {
    fun isHomeNativeGlassRuntimeActive(): Boolean {
        return isHomeNativeGlassEnabled && hasAnyHomeNativeGlassBackgroundImage()
    }

    fun hasAnyHomeNativeGlassBackgroundImage(): Boolean {
        return homeNativeGlassLightStyle.hasBackgroundImage() ||
            homeNativeGlassDarkStyle.hasBackgroundImage()
    }

    fun homeNativeGlassStyleForDarkMode(darkMode: Boolean): ConfigManager.HomeNativeGlassStyleConfig {
        return if (darkMode) homeNativeGlassDarkStyle else homeNativeGlassLightStyle
    }

    fun shouldStabilizeHomeChrome(): Boolean {
        return isHomeTopBarAdBlockEnabled || isHomeNativeGlassRuntimeActive()
    }

    fun shouldForceFeedUiOpt(): Boolean = isForceFeedUiOptRuntimeEnabled
}
