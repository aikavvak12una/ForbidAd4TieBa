package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.TabPreferences.BottomTabSelection
import com.forbidad4tieba.hook.config.HomeGlassPreferences.DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED
import com.forbidad4tieba.hook.config.TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED
import com.forbidad4tieba.hook.config.TabPreferences.HOME_TOP_TAB_KEY_LIVE
import com.forbidad4tieba.hook.config.TabPreferences.HOME_TOP_TAB_KEY_MATERIAL
import com.forbidad4tieba.hook.config.TabPreferences.HOME_TOP_TAB_KEY_RECOMMEND
import com.forbidad4tieba.hook.config.TabPreferences.HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS
import com.forbidad4tieba.hook.config.TabPreferences.HomeTopTabSelection
import com.forbidad4tieba.hook.config.TabPreferences.KEY_HOME_TOP_TAB_DISABLED_KEYS
import com.forbidad4tieba.hook.config.ReplyPreferences.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS
import com.forbidad4tieba.hook.config.ReplyPreferences.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS
import com.forbidad4tieba.hook.config.ModelScoreSettings.MIN_MODEL_SCORE_STATS_POST_LIMIT
import com.forbidad4tieba.hook.config.ReplyPreferences.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS
import com.forbidad4tieba.hook.config.ReplyPreferences.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS
import com.forbidad4tieba.hook.config.TabPreferences.isLegacyHomeTopTabEnabled
import com.forbidad4tieba.hook.config.TabPreferences.normalizeBottomTabSelection
import com.forbidad4tieba.hook.config.FreeCopyPreferences.normalizeFreeCopyPostModes
import com.forbidad4tieba.hook.config.ModelScoreSettings.parseModelScoreAutoPercentiles
import com.forbidad4tieba.hook.config.ModelScoreSettings.parseModelScoreThresholds
import com.forbidad4tieba.hook.config.TabPreferences.readHomeTopTabDisabledKeys

/** Derives runtime state without reading storage, publishing settings, or applying policy effects. */
internal object EffectiveSettingsPolicy {
    fun derive(p: UserSettings, capabilities: HostCapabilities, remote: RemoteSettingsPolicy): SettingsEvaluation {
        val restrictedUnlocked =
            AccountPreferences.RESTRICTED_FEATURES_UNLOCKED.read(p) && !remote.restrictedFeaturesLocked

        fun featureBoolean(setting: BooleanPreference): Boolean =
            setting.read(p) && capabilities.isAvailable(setting.key)

        fun restrictedBoolean(setting: BooleanPreference): Boolean =
            restrictedUnlocked && featureBoolean(setting)

        val adBlockEnabled = restrictedBoolean(AdPreferences.BLOCK_AD)
        fun adBlockChildBoolean(setting: BooleanPreference): Boolean =
            adBlockEnabled && featureBoolean(setting)

        val performanceOptimizationEnabled = restrictedBoolean(PerformancePreferences.ENABLE_PERFORMANCE_OPTIMIZATION)
        fun performanceChildBoolean(setting: BooleanPreference): Boolean =
            restrictedUnlocked && performanceOptimizationEnabled && featureBoolean(setting)

        val customPostFilterEnabled = featureBoolean(PostFilterPreferences.ENABLE_CUSTOM_POST_FILTER)
        fun customPostFilterChildBoolean(setting: BooleanPreference): Boolean =
            customPostFilterEnabled && featureBoolean(setting)

        val freeCopyEnabled = featureBoolean(FreeCopyPreferences.ENABLE_FREE_COPY)
        fun freeCopyChildBoolean(setting: BooleanPreference): Boolean =
            freeCopyEnabled && featureBoolean(setting)
        val freeCopyPostModes = normalizeFreeCopyPostModes(
            postButtonEnabled = freeCopyChildBoolean(FreeCopyPreferences.FREE_COPY_POST_BODY),
            longPressEnabled = freeCopyChildBoolean(FreeCopyPreferences.FREE_COPY_POST_LONG_PRESS),
        )

        val postForumKeywordFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_FORUM_KEYWORD)
        val postModelScoreFilterEnabled = customPostFilterEnabled &&
            restrictedBoolean(PostFilterPreferences.FILTER_POST_MODEL_SCORE)
        val homeNativeGlassLightStyle = p.lightGlassStyle
        val homeNativeGlassDarkStyle = p.darkGlassStyle
        val homeNativeGlassEnabled = featureBoolean(HomeGlassPreferences.ENABLE_HOME_NATIVE_GLASS)
        val tabCustomizationEnabled = TabCustomizationPreferences.isEnabled(p)
        val homeTopTabsCustomEnabled = tabCustomizationEnabled && featureBoolean(TabPreferences.CUSTOM_HOME_TOP_TABS)
        val homeTopTabSelection = if (homeTopTabsCustomEnabled) {
            val disabledKeys = readHomeTopTabDisabledKeys(p)
            val rawSelection = HomeTopTabSelection(
                materialEnabled = isLegacyHomeTopTabEnabled(disabledKeys, HOME_TOP_TAB_KEY_MATERIAL),
                recommendEnabled = isLegacyHomeTopTabEnabled(disabledKeys, HOME_TOP_TAB_KEY_RECOMMEND),
                liveEnabled = isLegacyHomeTopTabEnabled(disabledKeys, HOME_TOP_TAB_KEY_LIVE),
                followedEnabled = isLegacyHomeTopTabEnabled(disabledKeys, HOME_TOP_TAB_KEY_FOLLOWED),
                disabledKeys = disabledKeys,
            )
            if (p.contains(KEY_HOME_TOP_TAB_DISABLED_KEYS) || rawSelection.hasEnabledTab()) {
                rawSelection
            } else {
                rawSelection.copy(
                    recommendEnabled = true,
                    disabledKeys = rawSelection.disabledKeys - HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS,
                )
            }
        } else {
            HomeTopTabSelection(
                materialEnabled = false,
                recommendEnabled = false,
                liveEnabled = false,
                followedEnabled = false,
                disabledKeys = emptySet(),
            )
        }
        val bottomTabsCustomEnabled = tabCustomizationEnabled && featureBoolean(TabPreferences.CUSTOM_BOTTOM_TABS)
        val bottomTabLiquidGlassEnabled = tabCustomizationEnabled && featureBoolean(TabPreferences.BOTTOM_TAB_LIQUID_GLASS)
        var normalizedBottomTabs: BottomTabSelection? = null
        val bottomTabSelection = if (bottomTabsCustomEnabled) {
            val raw = BottomTabSelection(
                homeEnabled = TabPreferences.BOTTOM_TAB_HOME.read(p),
                enterForumEnabled = TabPreferences.BOTTOM_TAB_ENTER_FORUM.read(p),
                retailStoreEnabled = TabPreferences.BOTTOM_TAB_RETAIL_STORE.read(p),
                messageEnabled = TabPreferences.BOTTOM_TAB_MESSAGE.read(p),
                mineEnabled = TabPreferences.BOTTOM_TAB_MINE.read(p),
            )
            normalizeBottomTabSelection(raw).also { normalized ->
                if (normalized != raw) normalizedBottomTabs = normalized
            }
        } else {
            BottomTabSelection(
                homeEnabled = false,
                enterForumEnabled = false,
                retailStoreEnabled = false,
                messageEnabled = false,
                mineEnabled = false,
            )
        }
        val monitorSyncComponentsDisabled = performanceChildBoolean(PerformancePreferences.DISABLE_MONITOR_SYNC_COMPONENTS)

        val snapshot = SettingsSnapshot(
            areRestrictedFeaturesUnlocked = restrictedUnlocked,
            isAdBlockEnabled = adBlockEnabled,
            isFeedAdBlockEnabled = adBlockChildBoolean(AdPreferences.BLOCK_AD_FEED),
            isPostPageAdBlockEnabled = adBlockChildBoolean(AdPreferences.BLOCK_AD_POST_PAGE),
            isForumPageAdBlockEnabled = adBlockChildBoolean(AdPreferences.BLOCK_AD_FORUM_PAGE),
            isStrategyAdBlockEnabled = adBlockChildBoolean(AdPreferences.BLOCK_AD_STRATEGY),
            isSearchBoxTextAdBlockEnabled = adBlockChildBoolean(AdPreferences.BLOCK_AD_SEARCH_BOX_TEXT),
            isHomeTopBarAdBlockEnabled = adBlockChildBoolean(AdPreferences.BLOCK_AD_HOME_TOP_BAR),
            isMineTabWebAdBlockEnabled = adBlockChildBoolean(AdPreferences.BLOCK_AD_MINE_TAB_WEB),
            isHomeSideBarWebAdBlockEnabled = adBlockChildBoolean(AdPreferences.BLOCK_AD_HOME_SIDE_BAR_WEB),
            isHomeBottomEasterEggAdBlockEnabled = adBlockChildBoolean(AdPreferences.BLOCK_AD_HOME_BOTTOM_EASTER_EGG),
            isHomeTopTabsCustomEnabled = homeTopTabsCustomEnabled,
            isHomeTopTabMaterialEnabled = homeTopTabSelection.materialEnabled,
            isHomeTopTabRecommendEnabled = homeTopTabSelection.recommendEnabled,
            isHomeTopTabLiveEnabled = homeTopTabSelection.liveEnabled,
            isHomeTopTabFollowedEnabled = homeTopTabSelection.followedEnabled,
            homeTopTabDisabledKeys = homeTopTabSelection.disabledKeys,
            isHomeTabAutoHideEnabled = tabCustomizationEnabled && featureBoolean(TabPreferences.AUTO_HIDE_HOME_TAB),
            isHomeTabRedDotHidden = featureBoolean(TabPreferences.HIDE_HOME_TAB_RED_DOT),
            isInputMemeBarHidden = featureBoolean(ExtensionPreferences.HIDE_INPUT_MEME_BAR),
            isBottomTabsCustomEnabled = bottomTabsCustomEnabled,
            isBottomTabHomeEnabled = bottomTabSelection.homeEnabled,
            isBottomTabEnterForumEnabled = bottomTabSelection.enterForumEnabled,
            isBottomTabRetailStoreEnabled = bottomTabSelection.retailStoreEnabled,
            isBottomTabMessageEnabled = bottomTabSelection.messageEnabled,
            isBottomTabMineEnabled = bottomTabSelection.mineEnabled,
            isBottomTabLiquidGlassEnabled = bottomTabLiquidGlassEnabled,
            bottomTabLiquidGlass = if (bottomTabLiquidGlassEnabled) {
                BottomTabLiquidGlassPreferences.read(p)
            } else {
                BottomTabLiquidGlassConfig.DEFAULT
            },
            isEnterForumWebFilterEnabled = featureBoolean(ExtensionPreferences.FILTER_ENTER_FORUM_WEB),
            isOpenWebLinkInSystemBrowserEnabled = featureBoolean(ExtensionPreferences.OPEN_WEB_LINK_IN_SYSTEM_BROWSER),
            isHomeNativeGlassEnabled = homeNativeGlassEnabled,
            isHomeTabDynamicTintEnabled = DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED,
            homeNativeGlassLightStyle = homeNativeGlassLightStyle,
            homeNativeGlassDarkStyle = homeNativeGlassDarkStyle,
            isAutoRefreshDisabled = featureBoolean(ExtensionPreferences.DISABLE_AUTO_REFRESH),
            isPbGestureFontScaleDisabled = featureBoolean(ExtensionPreferences.DISABLE_PB_GESTURE_FONT_SCALE),
            isAutoLoadMoreEnabled = featureBoolean(ExtensionPreferences.ENABLE_AUTO_LOAD_MORE),
            isFreeCopyEnabled = freeCopyEnabled,
            isFreeCopyPostBodyEnabled = freeCopyPostModes.postButtonEnabled,
            isFreeCopyPostLongPressEnabled = freeCopyPostModes.longPressEnabled,
            isFreeCopyCommentInjectionEnabled = freeCopyChildBoolean(FreeCopyPreferences.FREE_COPY_COMMENT_INJECTION),
            isFreeCopyCommentDialogEnabled = freeCopyChildBoolean(FreeCopyPreferences.FREE_COPY_COMMENT_DIALOG),
            isPbLikeAutoReplyEnabled = restrictedBoolean(ReplyPreferences.ENABLE_PB_LIKE_AUTO_REPLY),
            isCommentAvatarDirectProfileEnabled =
                restrictedBoolean(ReplyPreferences.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE),
            pbLikeAutoReplyText = if (restrictedBoolean(ReplyPreferences.ENABLE_PB_LIKE_AUTO_REPLY)) {
                ReplyPreferences.PB_LIKE_AUTO_REPLY_TEXT.read(p)?.trim().orEmpty()
            } else {
                ""
            },
            isReplyVisibilityProbeEnabled = restrictedBoolean(ReplyPreferences.VERIFY_REPLY_AFTER_POST),
            replyVisibilityProbeMaxAttempts = ReplyPreferences.REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS.read(p).coerceIn(
                MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
            ),
            replyVisibilityProbeIntervalMs = ReplyPreferences.REPLY_VISIBILITY_PROBE_INTERVAL_MS.read(p).coerceIn(
                MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
            ),
            isPbScrollCoalesceEnabled = performanceChildBoolean(PerformancePreferences.ENABLE_PB_SCROLL_COALESCE),
            isDefaultNotifyTabEnabled = featureBoolean(ExtensionPreferences.DEFAULT_NOTIFY_TAB),
            simpleToggles = SimpleToggle.enabledBy(p, capabilities),
            isAutoSignInEnabled = restrictedBoolean(AccountPreferences.ENABLE_AUTO_SIGN_IN),
            isCleanShareTrackingParamsEnabled = featureBoolean(ExtensionPreferences.CLEAN_SHARE_TRACKING_PARAMS),
            isAiComponentsDisabled = performanceChildBoolean(PerformancePreferences.DISABLE_AI_COMPONENTS),
            isCustomPostFilterEnabled = customPostFilterEnabled,
            isAdSdkComponentsDisabled = performanceChildBoolean(PerformancePreferences.DISABLE_AD_SDK_COMPONENTS),
            isVideoComponentsDisabled = performanceChildBoolean(PerformancePreferences.DISABLE_VIDEO_COMPONENTS),
            isMonitorSyncComponentsDisabled = monitorSyncComponentsDisabled,
            isPbPerformanceModeEnabled = performanceChildBoolean(PerformancePreferences.ENABLE_PB_PERFORMANCE_MODE),
            isPbPreloadForced = performanceChildBoolean(PerformancePreferences.FORCE_PB_PRELOAD),
            isHostSlideAnimationDisabled = performanceChildBoolean(PerformancePreferences.DISABLE_HOST_SLIDE_ANIMATION),
            isHostFeedColdOptEnabled = performanceChildBoolean(PerformancePreferences.FORCE_HOST_FEED_COLD_OPT),
            isPerformanceOptimizationEnabled = performanceOptimizationEnabled,
            isHostPerformanceFlagsForced = performanceChildBoolean(PerformancePreferences.FORCE_HOST_PERFORMANCE_FLAGS),
            isApsarasScheduleDisabled = performanceChildBoolean(PerformancePreferences.DISABLE_APSARAS_SCHEDULE),
            isFlutterPreinitDisabled = performanceChildBoolean(PerformancePreferences.DISABLE_FLUTTER_PREINIT),
            isLowEndDeviceConfigForced = performanceChildBoolean(PerformancePreferences.FORCE_LOW_END_DEVICE_CONFIG),
            isTitanPatchBlockEnabled = performanceChildBoolean(PerformancePreferences.BLOCK_TITAN_PATCH),
            isPrivateReadReceiptInvisibleEnabled = restrictedBoolean(AccountPreferences.PRIVATE_READ_RECEIPT_INVISIBLE),
            isPostVoteFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_VOTE),
            isPostVideoFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_VIDEO),
            isPostReplyFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_REPLY),
            isPostHotFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_HOT),
            isPostGoodsFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_GOODS),
            isPostGameBookingFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_GAME_BOOKING),
            isPostHelpFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_HELP),
            isPostScoreFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_SCORE),
            isPostLotteryFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_LOTTERY),
            isPostLiveFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_LIVE),
            isPostRecommendForumFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_RECOMMEND_FORUM),
            isPostUnfollowedForumFilterEnabled = customPostFilterChildBoolean(PostFilterPreferences.FILTER_POST_UNFOLLOWED_FORUM),
            isPostForumKeywordFilterEnabled = postForumKeywordFilterEnabled,
            postForumKeywordList = if (postForumKeywordFilterEnabled) {
                PostFilterPreferences.parseKeywordList(PostFilterPreferences.FILTER_POST_FORUM_KEYWORD_LIST.read(p))
            } else {
                emptyList()
            },
            isPostModelScoreFilterEnabled = postModelScoreFilterEnabled,
            postModelScoreThresholds = if (postModelScoreFilterEnabled) {
                parseModelScoreThresholds(PostFilterPreferences.FILTER_POST_MODEL_SCORE_THRESHOLDS.read(p))
            } else {
                emptyList()
            },
            postModelScoreAutoPercentiles = if (postModelScoreFilterEnabled) {
                parseModelScoreAutoPercentiles(PostFilterPreferences.FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES.read(p))
            } else {
                emptyMap()
            },
            postModelScoreStatsPostLimit = PostFilterPreferences.FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT.read(p).coerceAtLeast(MIN_MODEL_SCORE_STATS_POST_LIMIT),
            isDetailedLoggingEnabled = restrictedBoolean(AccountPreferences.ENABLE_DETAILED_LOGGING),
            commentLevelFilter = CommentLevelFilterSettings(
                enabled = featureBoolean(CommentFilterPreferences.ENABLE),
                minimumLevel = CommentFilterPreferences.MINIMUM_LEVEL.read(p).coerceIn(
                    CommentFilterPreferences.MIN_LEVEL, CommentFilterPreferences.MAX_LEVEL,
                ),
                keepWithReplies = CommentFilterPreferences.KEEP_WITH_REPLIES.read(p),
                skipNested = CommentFilterPreferences.SKIP_NESTED.read(p),
            ),
        )
        return SettingsEvaluation(snapshot, normalizedBottomTabs)
    }


}
