package com.forbidad4tieba.hook.config

data class SettingsSnapshot(
    val areRestrictedFeaturesUnlocked: Boolean = false,
    val isAdBlockEnabled: Boolean = false,
    val isHomeTopTabsCustomEnabled: Boolean = false,
    val isHomeTopTabMaterialEnabled: Boolean = true,
    val isHomeTopTabRecommendEnabled: Boolean = true,
    val isHomeTopTabLiveEnabled: Boolean = true,
    val isHomeTopTabFollowedEnabled: Boolean = true,
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
    val homeNativeGlassBackgroundImagePath: String =
        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
    val homeNativeGlassBlurCacheImagePath: String =
        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
    val homeNativeGlassTextPaletteLight: String = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TEXT_PALETTE,
    val homeNativeGlassTextPaletteDark: String = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TEXT_PALETTE,
    val homeNativeGlassTintColor: Int = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR,
    val homeNativeGlassAutoTintColor: Int = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
    val homeNativeGlassTintAlphaPercent: Int =
        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
    val homeNativeGlassCardBlurPercent: Int =
        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
    val homeNativeGlassCardRadiusDp: Int =
        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
    val isHomeNativeGlassStrokeEnabled: Boolean =
        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED,
    val isHomeNativeGlassShadowEnabled: Boolean =
        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED,
    val isAutoRefreshDisabled: Boolean = false,
    val isAutoLoadMoreEnabled: Boolean = false,
    val isPbLikeAutoReplyEnabled: Boolean = false,
    val pbLikeAutoReplyText: String = "",
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
        return isHomeNativeGlassEnabled && homeNativeGlassBackgroundImagePath.isNotBlank()
    }

    fun shouldStabilizeHomeChrome(): Boolean {
        return isAdBlockEnabled || isHomeNativeGlassRuntimeActive()
    }

    fun shouldForceFeedUiOpt(): Boolean = isForceFeedUiOptRuntimeEnabled
}
