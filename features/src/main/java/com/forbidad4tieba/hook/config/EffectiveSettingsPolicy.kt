package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.ConfigManager.BottomTabSelection
import com.forbidad4tieba.hook.config.ConfigManager.DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED
import com.forbidad4tieba.hook.config.ConfigManager.DEFAULT_MODEL_SCORE_STATS_POST_LIMIT
import com.forbidad4tieba.hook.config.ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS
import com.forbidad4tieba.hook.config.ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS
import com.forbidad4tieba.hook.config.ConfigManager.HOME_TOP_TAB_KEY_FOLLOWED
import com.forbidad4tieba.hook.config.ConfigManager.HOME_TOP_TAB_KEY_LIVE
import com.forbidad4tieba.hook.config.ConfigManager.HOME_TOP_TAB_KEY_MATERIAL
import com.forbidad4tieba.hook.config.ConfigManager.HOME_TOP_TAB_KEY_RECOMMEND
import com.forbidad4tieba.hook.config.ConfigManager.HOME_TOP_TAB_LEGACY_RECOMMEND_KEYS
import com.forbidad4tieba.hook.config.ConfigManager.HomeTopTabSelection
import com.forbidad4tieba.hook.config.ConfigManager.KEY_AUTO_HIDE_HOME_TAB
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD_FEED
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD_FORUM_PAGE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD_HOME_BOTTOM_EASTER_EGG
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD_HOME_SIDE_BAR_WEB
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD_HOME_TOP_BAR
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD_MINE_TAB_WEB
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD_POST_PAGE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD_SEARCH_BOX_TEXT
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_AD_STRATEGY
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BLOCK_TITAN_PATCH
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BOTTOM_TAB_ENTER_FORUM
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BOTTOM_TAB_HOME
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BOTTOM_TAB_LIQUID_GLASS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BOTTOM_TAB_MESSAGE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BOTTOM_TAB_MINE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_BOTTOM_TAB_RETAIL_STORE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_CLEAN_SHARE_TRACKING_PARAMS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_CUSTOM_BOTTOM_TABS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_CUSTOM_HOME_TOP_TABS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DEFAULT_NOTIFY_TAB
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DISABLE_AD_SDK_COMPONENTS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DISABLE_AI_COMPONENTS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DISABLE_APSARAS_SCHEDULE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DISABLE_AUTO_REFRESH
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DISABLE_FLUTTER_PREINIT
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DISABLE_HOST_SLIDE_ANIMATION
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DISABLE_MONITOR_SYNC_COMPONENTS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DISABLE_PB_GESTURE_FONT_SCALE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_DISABLE_VIDEO_COMPONENTS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_AUTO_LOAD_MORE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_AUTO_SIGN_IN
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_COMMENT_AVATAR_DIRECT_PROFILE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_CUSTOM_POST_FILTER
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_DETAILED_LOGGING
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_FREE_COPY
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_PB_LIKE_AUTO_REPLY
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_PB_PERFORMANCE_MODE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_PB_SCROLL_COALESCE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_ENTER_FORUM_WEB
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD_LIST
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_GAME_BOOKING
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_GOODS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_HELP
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_HOT
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_LIVE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_LOTTERY
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_MODEL_SCORE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_RECOMMEND_FORUM
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_REPLY
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_SCORE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_UNFOLLOWED_FORUM
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_VIDEO
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FILTER_POST_VOTE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FORCE_HOST_FEED_COLD_OPT
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FORCE_HOST_PERFORMANCE_FLAGS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FORCE_LOW_END_DEVICE_CONFIG
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FORCE_PB_PRELOAD
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FREE_COPY_COMMENT_DIALOG
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FREE_COPY_COMMENT_INJECTION
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FREE_COPY_POST_BODY
import com.forbidad4tieba.hook.config.ConfigManager.KEY_FREE_COPY_POST_LONG_PRESS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_HIDE_HOME_TAB_RED_DOT
import com.forbidad4tieba.hook.config.ConfigManager.KEY_HIDE_INPUT_MEME_BAR
import com.forbidad4tieba.hook.config.ConfigManager.KEY_HOME_TOP_TAB_DISABLED_KEYS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER
import com.forbidad4tieba.hook.config.ConfigManager.KEY_PB_LIKE_AUTO_REPLY_TEXT
import com.forbidad4tieba.hook.config.ConfigManager.KEY_PRIVATE_READ_RECEIPT_INVISIBLE
import com.forbidad4tieba.hook.config.ConfigManager.KEY_REPLY_VISIBILITY_PROBE_INTERVAL_MS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS
import com.forbidad4tieba.hook.config.ConfigManager.KEY_RESTRICTED_FEATURES_UNLOCKED
import com.forbidad4tieba.hook.config.ConfigManager.KEY_VERIFY_REPLY_AFTER_POST
import com.forbidad4tieba.hook.config.ConfigManager.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS
import com.forbidad4tieba.hook.config.ConfigManager.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS
import com.forbidad4tieba.hook.config.ConfigManager.MIN_MODEL_SCORE_STATS_POST_LIMIT
import com.forbidad4tieba.hook.config.ConfigManager.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS
import com.forbidad4tieba.hook.config.ConfigManager.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS
import com.forbidad4tieba.hook.config.ConfigManager.isLegacyHomeTopTabEnabled
import com.forbidad4tieba.hook.config.ConfigManager.normalizeBottomTabSelection
import com.forbidad4tieba.hook.config.ConfigManager.normalizeFreeCopyPostModes
import com.forbidad4tieba.hook.config.ConfigManager.parseModelScoreAutoPercentiles
import com.forbidad4tieba.hook.config.ConfigManager.parseModelScoreThresholds
import com.forbidad4tieba.hook.config.ConfigManager.readHomeTopTabDisabledKeys

/** Derives runtime state without reading storage, publishing settings, or applying policy effects. */
internal object EffectiveSettingsPolicy {
    fun derive(p: UserSettings, capabilities: HostCapabilities, remote: RemoteSettingsPolicy): SettingsEvaluation {
        val restrictedUnlocked =
            p.getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false) && !remote.restrictedFeaturesLocked

        fun featureBoolean(key: String, defaultValue: Boolean = false): Boolean {
            return p.getBoolean(key, defaultValue) && capabilities.isAvailable(key)
        }

        fun restrictedBoolean(key: String): Boolean {
            return restrictedUnlocked && featureBoolean(key)
        }

        fun performanceChildBoolean(
            key: String,
            masterEnabled: Boolean,
            defaultValue: Boolean,
        ): Boolean {
            if (!restrictedUnlocked || !masterEnabled || !capabilities.isAvailable(key)) return false
            return if (p.contains(key)) {
                p.getBoolean(key, false)
            } else {
                defaultValue
            }
        }

        val adBlockEnabled = restrictedBoolean(KEY_BLOCK_AD)
        fun adBlockChildBoolean(key: String): Boolean {
            if (!adBlockEnabled || !capabilities.isAvailable(key)) return false
            return if (p.contains(key)) p.getBoolean(key, true) else true
        }

        val performanceOptimizationEnabled = restrictedBoolean(KEY_ENABLE_PERFORMANCE_OPTIMIZATION)
        val customPostFilterEnabled = featureBoolean(KEY_ENABLE_CUSTOM_POST_FILTER)
        val freeCopyEnabled = featureBoolean(KEY_ENABLE_FREE_COPY, defaultValue = true)
        fun freeCopyChildBoolean(key: String, defaultValue: Boolean): Boolean {
            return freeCopyEnabled &&
                capabilities.isAvailable(key) &&
                p.getBoolean(key, defaultValue)
        }
        val freeCopyPostModes = normalizeFreeCopyPostModes(
            postButtonEnabled = freeCopyChildBoolean(KEY_FREE_COPY_POST_BODY, true),
            longPressEnabled = freeCopyChildBoolean(KEY_FREE_COPY_POST_LONG_PRESS, false),
        )
        fun customPostFilterChildBoolean(key: String): Boolean {
            return customPostFilterEnabled &&
                capabilities.isAvailable(key) &&
                p.getBoolean(key, false)
        }

        val postForumKeywordFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_FORUM_KEYWORD)
        val postModelScoreFilterEnabled = customPostFilterEnabled &&
            restrictedBoolean(KEY_FILTER_POST_MODEL_SCORE)
        val homeNativeGlassLightStyle = p.lightGlassStyle
        val homeNativeGlassDarkStyle = p.darkGlassStyle
        val hasHomeNativeGlassStyle = homeNativeGlassLightStyle.hasBackgroundImage() ||
            homeNativeGlassDarkStyle.hasBackgroundImage()
        val homeNativeGlassEnabled = featureBoolean(KEY_ENABLE_HOME_NATIVE_GLASS)
        val tabCustomizationEnabled = TabCustomizationPreferences.isEnabled(p)
        val homeTopTabsCustomEnabled = tabCustomizationEnabled && featureBoolean(KEY_CUSTOM_HOME_TOP_TABS)
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
        val bottomTabsCustomEnabled = tabCustomizationEnabled && featureBoolean(KEY_CUSTOM_BOTTOM_TABS)
        val bottomTabLiquidGlassEnabled = tabCustomizationEnabled && featureBoolean(KEY_BOTTOM_TAB_LIQUID_GLASS)
        var normalizedBottomTabs: BottomTabSelection? = null
        val bottomTabSelection = if (bottomTabsCustomEnabled) {
            val raw = BottomTabSelection(
                homeEnabled = p.getBoolean(KEY_BOTTOM_TAB_HOME, true),
                enterForumEnabled = p.getBoolean(KEY_BOTTOM_TAB_ENTER_FORUM, true),
                retailStoreEnabled = p.getBoolean(KEY_BOTTOM_TAB_RETAIL_STORE, true),
                messageEnabled = p.getBoolean(KEY_BOTTOM_TAB_MESSAGE, true),
                mineEnabled = p.getBoolean(KEY_BOTTOM_TAB_MINE, true),
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
        val monitorSyncComponentsDisabled = performanceChildBoolean(
            KEY_DISABLE_MONITOR_SYNC_COMPONENTS,
            performanceOptimizationEnabled,
            true,
        )

        val snapshot = SettingsSnapshot(
            areRestrictedFeaturesUnlocked = restrictedUnlocked,
            isAdBlockEnabled = adBlockEnabled,
            isFeedAdBlockEnabled = adBlockChildBoolean(KEY_BLOCK_AD_FEED),
            isPostPageAdBlockEnabled = adBlockChildBoolean(KEY_BLOCK_AD_POST_PAGE),
            isForumPageAdBlockEnabled = adBlockChildBoolean(KEY_BLOCK_AD_FORUM_PAGE),
            isStrategyAdBlockEnabled = adBlockChildBoolean(KEY_BLOCK_AD_STRATEGY),
            isSearchBoxTextAdBlockEnabled = adBlockChildBoolean(KEY_BLOCK_AD_SEARCH_BOX_TEXT),
            isHomeTopBarAdBlockEnabled = adBlockChildBoolean(KEY_BLOCK_AD_HOME_TOP_BAR),
            isMineTabWebAdBlockEnabled = adBlockChildBoolean(KEY_BLOCK_AD_MINE_TAB_WEB),
            isHomeSideBarWebAdBlockEnabled = adBlockChildBoolean(KEY_BLOCK_AD_HOME_SIDE_BAR_WEB),
            isHomeBottomEasterEggAdBlockEnabled = adBlockChildBoolean(KEY_BLOCK_AD_HOME_BOTTOM_EASTER_EGG),
            isHomeTopTabsCustomEnabled = homeTopTabsCustomEnabled,
            isHomeTopTabMaterialEnabled = homeTopTabSelection.materialEnabled,
            isHomeTopTabRecommendEnabled = homeTopTabSelection.recommendEnabled,
            isHomeTopTabLiveEnabled = homeTopTabSelection.liveEnabled,
            isHomeTopTabFollowedEnabled = homeTopTabSelection.followedEnabled,
            homeTopTabDisabledKeys = homeTopTabSelection.disabledKeys,
            isHomeTabAutoHideEnabled = tabCustomizationEnabled && featureBoolean(KEY_AUTO_HIDE_HOME_TAB),
            isHomeTabRedDotHidden = featureBoolean(KEY_HIDE_HOME_TAB_RED_DOT),
            isInputMemeBarHidden = featureBoolean(KEY_HIDE_INPUT_MEME_BAR),
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
            isEnterForumWebFilterEnabled = featureBoolean(KEY_FILTER_ENTER_FORUM_WEB),
            isOpenWebLinkInSystemBrowserEnabled = featureBoolean(KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER),
            isHomeNativeGlassEnabled = homeNativeGlassEnabled,
            isHomeTabDynamicTintEnabled = DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED,
            homeNativeGlassLightStyle = homeNativeGlassLightStyle,
            homeNativeGlassDarkStyle = homeNativeGlassDarkStyle,
            isAutoRefreshDisabled = featureBoolean(KEY_DISABLE_AUTO_REFRESH),
            isPbGestureFontScaleDisabled = featureBoolean(KEY_DISABLE_PB_GESTURE_FONT_SCALE),
            isAutoLoadMoreEnabled = featureBoolean(KEY_ENABLE_AUTO_LOAD_MORE),
            isFreeCopyEnabled = freeCopyEnabled,
            isFreeCopyPostBodyEnabled = freeCopyPostModes.postButtonEnabled,
            isFreeCopyPostLongPressEnabled = freeCopyPostModes.longPressEnabled,
            isFreeCopyCommentInjectionEnabled = freeCopyChildBoolean(
                KEY_FREE_COPY_COMMENT_INJECTION,
                true,
            ),
            isFreeCopyCommentDialogEnabled = freeCopyChildBoolean(
                KEY_FREE_COPY_COMMENT_DIALOG,
                true,
            ),
            isPbLikeAutoReplyEnabled = restrictedBoolean(KEY_ENABLE_PB_LIKE_AUTO_REPLY),
            isCommentAvatarDirectProfileEnabled =
                restrictedBoolean(KEY_ENABLE_COMMENT_AVATAR_DIRECT_PROFILE),
            pbLikeAutoReplyText = if (restrictedBoolean(KEY_ENABLE_PB_LIKE_AUTO_REPLY)) {
                p.getString(KEY_PB_LIKE_AUTO_REPLY_TEXT, "")?.trim().orEmpty()
            } else {
                ""
            },
            isReplyVisibilityProbeEnabled = restrictedBoolean(KEY_VERIFY_REPLY_AFTER_POST),
            replyVisibilityProbeMaxAttempts = p.getInt(
                KEY_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
            ).coerceIn(
                MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
            ),
            replyVisibilityProbeIntervalMs = p.getInt(
                KEY_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
            ).coerceIn(
                MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
            ),
            isPbScrollCoalesceEnabled = performanceChildBoolean(
                KEY_ENABLE_PB_SCROLL_COALESCE,
                performanceOptimizationEnabled,
                true,
            ),
            isDefaultNotifyTabEnabled = featureBoolean(KEY_DEFAULT_NOTIFY_TAB, true),
            isDefaultOriginalImageEnabled = featureBoolean(KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE),
            isAutoSignInEnabled = restrictedBoolean(KEY_ENABLE_AUTO_SIGN_IN),
            isCleanShareTrackingParamsEnabled = featureBoolean(KEY_CLEAN_SHARE_TRACKING_PARAMS, true),
            isAiComponentsDisabled = performanceChildBoolean(
                KEY_DISABLE_AI_COMPONENTS,
                performanceOptimizationEnabled,
                true,
            ),
            isCustomPostFilterEnabled = customPostFilterEnabled,
            isAdSdkComponentsDisabled = performanceChildBoolean(
                KEY_DISABLE_AD_SDK_COMPONENTS,
                performanceOptimizationEnabled,
                true,
            ),
            isVideoComponentsDisabled = performanceChildBoolean(
                KEY_DISABLE_VIDEO_COMPONENTS,
                performanceOptimizationEnabled,
                true,
            ),
            isMonitorSyncComponentsDisabled = monitorSyncComponentsDisabled,
            isPbPerformanceModeEnabled = performanceChildBoolean(
                KEY_ENABLE_PB_PERFORMANCE_MODE,
                performanceOptimizationEnabled,
                true,
            ),
            isPbPreloadForced = performanceChildBoolean(
                KEY_FORCE_PB_PRELOAD,
                performanceOptimizationEnabled,
                true,
            ),
            isHostSlideAnimationDisabled = performanceChildBoolean(
                KEY_DISABLE_HOST_SLIDE_ANIMATION,
                performanceOptimizationEnabled,
                true,
            ),
            isHostFeedColdOptEnabled = performanceChildBoolean(
                KEY_FORCE_HOST_FEED_COLD_OPT,
                performanceOptimizationEnabled,
                true,
            ),
            isPerformanceOptimizationEnabled = performanceOptimizationEnabled,
            isHostPerformanceFlagsForced = performanceChildBoolean(
                KEY_FORCE_HOST_PERFORMANCE_FLAGS,
                performanceOptimizationEnabled,
                true,
            ),
            isApsarasScheduleDisabled = performanceChildBoolean(
                KEY_DISABLE_APSARAS_SCHEDULE,
                performanceOptimizationEnabled,
                true,
            ),
            isFlutterPreinitDisabled = performanceChildBoolean(
                KEY_DISABLE_FLUTTER_PREINIT,
                performanceOptimizationEnabled,
                true,
            ),
            isLowEndDeviceConfigForced = performanceChildBoolean(
                KEY_FORCE_LOW_END_DEVICE_CONFIG,
                performanceOptimizationEnabled,
                true,
            ),
            isTitanPatchBlockEnabled = performanceChildBoolean(
                KEY_BLOCK_TITAN_PATCH,
                performanceOptimizationEnabled,
                false,
            ),
            isPrivateReadReceiptInvisibleEnabled = restrictedBoolean(KEY_PRIVATE_READ_RECEIPT_INVISIBLE),
            isPostVoteFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_VOTE),
            isPostVideoFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_VIDEO),
            isPostReplyFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_REPLY),
            isPostHotFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_HOT),
            isPostGoodsFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_GOODS),
            isPostGameBookingFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_GAME_BOOKING),
            isPostHelpFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_HELP),
            isPostScoreFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_SCORE),
            isPostLotteryFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_LOTTERY),
            isPostLiveFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_LIVE),
            isPostRecommendForumFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_RECOMMEND_FORUM),
            isPostUnfollowedForumFilterEnabled = customPostFilterChildBoolean(KEY_FILTER_POST_UNFOLLOWED_FORUM),
            isPostForumKeywordFilterEnabled = postForumKeywordFilterEnabled,
            postForumKeywordList = if (postForumKeywordFilterEnabled) {
                parseKeywordList(p.getString(KEY_FILTER_POST_FORUM_KEYWORD_LIST, ""))
            } else {
                emptyList()
            },
            isPostModelScoreFilterEnabled = postModelScoreFilterEnabled,
            postModelScoreThresholds = if (postModelScoreFilterEnabled) {
                parseModelScoreThresholds(p.getString(KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS, ""))
            } else {
                emptyList()
            },
            postModelScoreAutoPercentiles = if (postModelScoreFilterEnabled) {
                parseModelScoreAutoPercentiles(p.getString(KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES, ""))
            } else {
                emptyMap()
            },
            postModelScoreStatsPostLimit = p.getInt(
                KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT,
                DEFAULT_MODEL_SCORE_STATS_POST_LIMIT
            ).coerceAtLeast(MIN_MODEL_SCORE_STATS_POST_LIMIT),
            isDetailedLoggingEnabled = restrictedBoolean(KEY_ENABLE_DETAILED_LOGGING),
        )
        return SettingsEvaluation(snapshot, normalizedBottomTabs)
    }

    private fun parseKeywordList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n', ',', '，', ';', '；')
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}
