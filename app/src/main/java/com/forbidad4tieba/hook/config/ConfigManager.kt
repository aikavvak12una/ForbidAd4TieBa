package com.forbidad4tieba.hook.config

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.forbidad4tieba.hook.BuildConfig
import com.forbidad4tieba.hook.HookFeatureState
import com.forbidad4tieba.hook.HookFeatureStatus
import com.forbidad4tieba.hook.core.XposedCompat
import java.math.BigDecimal
import java.math.RoundingMode

object ConfigManager {
    const val USER_SETTINGS_PREFS_NAME = "tbhook_user_settings"
    const val MODULE_STATE_PREFS_NAME = "tbhook_module_state"
    const val SYMBOL_CACHE_PREFS_NAME = "tbhook_symbol_cache"
    const val LEGACY_MIXED_PREFS_NAME = "tiebahook_settings"
    const val PREFS_NAME = USER_SETTINGS_PREFS_NAME
    const val MODEL_SCORE_STATS_FILE_NAME = "tbhook_model_score_stats.tsv"
    const val DEFAULT_MODEL_SCORE_STATS_POST_LIMIT = 5000
    const val MIN_MODEL_SCORE_STATS_POST_LIMIT = 1000
    const val MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT = 1000
    const val DEFAULT_MODEL_SCORE_AUTO_PERCENTILE = 5
    const val DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH = ""
    const val DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH = ""
    const val DEFAULT_HOME_NATIVE_GLASS_TEXT_PALETTE = ""
    const val DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR = 0
    const val DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 54
    const val DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 72
    const val DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 24
    const val DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED = true
    const val DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED = true
    const val DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED = true
    const val APPLE_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 54
    const val APPLE_HOME_NATIVE_GLASS_DARK_TINT_ALPHA_PERCENT = 78
    const val APPLE_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 72
    const val APPLE_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 24
    const val MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 0
    const val MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 100
    const val MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 0
    const val MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 100
    const val MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 0
    const val MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 32
    val SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES = intArrayOf(5, 10, 15, 20)
    private const val MODEL_SCORE_THRESHOLD_SCALE = 6
    private const val KEY_USER_SETTINGS_VERSION_CODE = "user_settings_version_code"

    const val KEY_BLOCK_AD = "block_ad"
    const val KEY_SIMPLIFY_HOME_TABS = "simplify_home_tabs"
    const val KEY_CUSTOM_HOME_TOP_TABS = KEY_SIMPLIFY_HOME_TABS
    const val KEY_HOME_TOP_TAB_MATERIAL = "home_top_tab_material"
    const val KEY_HOME_TOP_TAB_RECOMMEND = "home_top_tab_recommend"
    const val KEY_HOME_TOP_TAB_LIVE = "home_top_tab_live"
    const val KEY_HOME_TOP_TAB_FOLLOWED = "home_top_tab_followed"
    const val KEY_AUTO_HIDE_HOME_TAB = "auto_hide_home_tab"
    const val KEY_SIMPLIFY_BOTTOM_TABS = "simplify_bottom_tabs"
    const val KEY_CUSTOM_BOTTOM_TABS = KEY_SIMPLIFY_BOTTOM_TABS
    const val KEY_BOTTOM_TAB_HOME = "bottom_tab_home"
    const val KEY_BOTTOM_TAB_ENTER_FORUM = "bottom_tab_enter_forum"
    const val KEY_BOTTOM_TAB_RETAIL_STORE = "bottom_tab_retail_store"
    const val KEY_BOTTOM_TAB_MESSAGE = "bottom_tab_message"
    const val KEY_BOTTOM_TAB_MINE = "bottom_tab_mine"
    const val KEY_FILTER_ENTER_FORUM_WEB = "filter_enter_forum_web"
    const val KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER = "open_web_link_in_system_browser"
    const val KEY_ENABLE_HOME_NATIVE_GLASS = "enable_home_native_glass"
    const val KEY_ENABLE_HOME_TAB_DYNAMIC_TINT = "enable_home_tab_dynamic_tint"
    const val KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH = "home_native_glass_background_image_path"
    const val KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH = "home_native_glass_blur_cache_image_path"
    const val KEY_HOME_NATIVE_GLASS_TEXT_PALETTE_LIGHT = "home_native_glass_text_palette_light"
    const val KEY_HOME_NATIVE_GLASS_TEXT_PALETTE_DARK = "home_native_glass_text_palette_dark"
    const val KEY_HOME_NATIVE_GLASS_TINT_COLOR = "home_native_glass_tint_color"
    const val KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = "home_native_glass_tint_alpha_percent"
    const val KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = "home_native_glass_card_blur_percent"
    const val KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP = "home_native_glass_card_radius_dp"
    const val KEY_HOME_NATIVE_GLASS_STROKE_ENABLED = "home_native_glass_stroke_enabled"
    const val KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED = "home_native_glass_shadow_enabled"
    const val KEY_DISABLE_AUTO_REFRESH = "disable_auto_refresh"
    const val KEY_ENABLE_AUTO_LOAD_MORE = "enable_auto_load_more"
    const val KEY_DEFAULT_NOTIFY_TAB = "default_notify_tab"
    const val KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE = "enable_default_original_image"
    const val KEY_ENABLE_AUTO_SIGN_IN = "enable_auto_sign_in"
    const val KEY_RESTRICTED_FEATURES_UNLOCKED = "restricted_features_unlocked"
    const val KEY_CLEAN_SHARE_TRACKING_PARAMS = "clean_share_tracking_params"
    const val KEY_DISABLE_AI_COMPONENTS = "disable_ai_components"
    const val KEY_ENABLE_CUSTOM_POST_FILTER = "enable_custom_post_filter"
    const val KEY_DISABLE_LOCATION_COMPONENTS = "disable_location_components"
    const val KEY_DISABLE_AD_SDK_COMPONENTS = "disable_ad_sdk_components"
    const val KEY_DISABLE_HEAVY_FEATURE_COMPONENTS = "disable_heavy_feature_components"
    const val KEY_DISABLE_VIDEO_COMPONENTS = "disable_video_components"
    const val KEY_DISABLE_MONITOR_SYNC_COMPONENTS = "disable_monitor_sync_components"
    const val KEY_DISABLE_UPDATE_DOWNLOAD_COMPONENTS = "disable_update_download_components"
    const val KEY_ENABLE_PB_PERFORMANCE_MODE = "enable_pb_performance_mode"
    const val KEY_ENABLE_PB_SCROLL_COALESCE = "enable_pb_scroll_coalesce"
    const val KEY_FORCE_FEED_UI_OPT = "force_feed_ui_opt"
    const val KEY_ENABLE_PERFORMANCE_OPTIMIZATION = "enable_performance_optimization"
    const val KEY_FORCE_HOST_PERFORMANCE_FLAGS = "force_host_performance_flags"
    const val KEY_DISABLE_APSARAS_SCHEDULE = "disable_apsaras_schedule"
    const val KEY_DISABLE_FLUTTER_PREINIT = "disable_flutter_preinit"
    const val KEY_FORCE_LOW_END_DEVICE_CONFIG = "force_low_end_device_config"
    const val KEY_BLOCK_TITAN_PATCH = "block_titan_patch"
    const val KEY_PRIVATE_READ_RECEIPT_INVISIBLE = "private_read_receipt_invisible"
    const val KEY_FILTER_POST_VOTE = "filter_post_vote"
    const val KEY_FILTER_POST_VIDEO = "filter_post_video"
    const val KEY_FILTER_POST_REPLY = "filter_post_reply"
    const val KEY_FILTER_POST_HOT = "filter_post_hot"
    const val KEY_FILTER_POST_GOODS = "filter_post_goods"
    const val KEY_FILTER_POST_GAME_BOOKING = "filter_post_game_booking"
    const val KEY_FILTER_POST_HELP = "filter_post_help"
    const val KEY_FILTER_POST_SCORE = "filter_post_score"
    const val KEY_FILTER_POST_LIVE = "filter_post_live"
    const val KEY_FILTER_POST_RECOMMEND_FORUM = "filter_post_recommend_forum"
    const val KEY_FILTER_POST_UNFOLLOWED_FORUM = "filter_post_unfollowed_forum"
    const val KEY_FILTER_POST_FORUM_KEYWORD = "filter_post_forum_keyword"
    const val KEY_FILTER_POST_FORUM_KEYWORD_LIST = "filter_post_forum_keyword_list"
    const val KEY_FILTER_POST_MODEL_SCORE = "filter_post_model_score"
    const val KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS = "filter_post_model_score_thresholds"
    const val KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES = "filter_post_model_score_auto_percentiles"
    const val KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT = "filter_post_model_score_stats_post_limit"
    const val KEY_ENABLE_DETAILED_LOGGING = "enable_detailed_logging"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var scanFeatureAvailability: Map<String, Boolean> = emptyMap()

    @Volatile var areRestrictedFeaturesUnlocked: Boolean = false
    @Volatile var isAdBlockEnabled: Boolean = false
    @Volatile var isHomeTopTabsCustomEnabled: Boolean = false
    @Volatile var isHomeTopTabMaterialEnabled: Boolean = true
    @Volatile var isHomeTopTabRecommendEnabled: Boolean = true
    @Volatile var isHomeTopTabLiveEnabled: Boolean = true
    @Volatile var isHomeTopTabFollowedEnabled: Boolean = true
    @Volatile var isHomeTabAutoHideEnabled: Boolean = false
    @Volatile var isBottomTabsCustomEnabled: Boolean = false
    @Volatile var isBottomTabHomeEnabled: Boolean = true
    @Volatile var isBottomTabEnterForumEnabled: Boolean = true
    @Volatile var isBottomTabRetailStoreEnabled: Boolean = true
    @Volatile var isBottomTabMessageEnabled: Boolean = true
    @Volatile var isBottomTabMineEnabled: Boolean = true
    @Volatile var isEnterForumWebFilterEnabled: Boolean = false
    @Volatile var isOpenWebLinkInSystemBrowserEnabled: Boolean = false
    @Volatile var isHomeNativeGlassEnabled: Boolean = false
    @Volatile var isHomeTabDynamicTintEnabled: Boolean = false
    @Volatile var homeNativeGlassBackgroundImagePath: String = DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH
    @Volatile var homeNativeGlassBlurCacheImagePath: String = DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH
    @Volatile var homeNativeGlassTextPaletteLight: String = DEFAULT_HOME_NATIVE_GLASS_TEXT_PALETTE
    @Volatile var homeNativeGlassTextPaletteDark: String = DEFAULT_HOME_NATIVE_GLASS_TEXT_PALETTE
    @Volatile var homeNativeGlassTintColor: Int = DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
    @Volatile var homeNativeGlassTintAlphaPercent: Int = DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
    @Volatile var homeNativeGlassCardBlurPercent: Int = DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
    @Volatile var homeNativeGlassCardRadiusDp: Int = DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP
    @Volatile var isHomeNativeGlassStrokeEnabled: Boolean = DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED
    @Volatile var isHomeNativeGlassShadowEnabled: Boolean = DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED
    @Volatile var isAutoRefreshDisabled: Boolean = false
    @Volatile var isAutoLoadMoreEnabled: Boolean = false
    @Volatile var isPbScrollCoalesceEnabled: Boolean = false
    @Volatile var isDefaultNotifyTabEnabled: Boolean = true
    @Volatile var isDefaultOriginalImageEnabled: Boolean = false
    @Volatile var isCleanShareTrackingParamsEnabled: Boolean = true
    @Volatile var isAiComponentsDisabled: Boolean = false
    @Volatile var isCustomPostFilterEnabled: Boolean = false
    @Volatile var isLocationComponentsDisabled: Boolean = false
    @Volatile var isAdSdkComponentsDisabled: Boolean = false
    @Volatile var isHeavyFeatureComponentsDisabled: Boolean = false
    @Volatile var isVideoComponentsDisabled: Boolean = false
    @Volatile var isMonitorSyncComponentsDisabled: Boolean = false
    @Volatile var isUpdateDownloadComponentsDisabled: Boolean = false
    @Volatile var isPbPerformanceModeEnabled: Boolean = false
    @Volatile var isFeedUiOptForced: Boolean = false
    @Volatile var isHostPerformanceFlagsForced: Boolean = false
    @Volatile var isApsarasScheduleDisabled: Boolean = false
    @Volatile var isFlutterPreinitDisabled: Boolean = false
    @Volatile var isLowEndDeviceConfigForced: Boolean = false
    @Volatile var isTitanPatchBlockEnabled: Boolean = false
    @Volatile var isPrivateReadReceiptInvisibleEnabled: Boolean = false
    @Volatile var isPostVoteFilterEnabled: Boolean = false
    @Volatile var isPostVideoFilterEnabled: Boolean = false
    @Volatile var isPostReplyFilterEnabled: Boolean = false
    @Volatile var isPostHotFilterEnabled: Boolean = false
    @Volatile var isPostGoodsFilterEnabled: Boolean = false
    @Volatile var isPostGameBookingFilterEnabled: Boolean = false
    @Volatile var isPostHelpFilterEnabled: Boolean = false
    @Volatile var isPostScoreFilterEnabled: Boolean = false
    @Volatile var isPostLiveFilterEnabled: Boolean = false
    @Volatile var isPostRecommendForumFilterEnabled: Boolean = false
    @Volatile var isPostUnfollowedForumFilterEnabled: Boolean = false
    @Volatile var isPostForumKeywordFilterEnabled: Boolean = false
    @Volatile var postForumKeywordList: List<String> = emptyList()
    @Volatile var isPostModelScoreFilterEnabled: Boolean = false
    @Volatile var postModelScoreThresholds: List<ModelScoreThreshold> = emptyList()
    @Volatile var postModelScoreAutoPercentiles: Map<String, Int> = emptyMap()
    @Volatile var postModelScoreStatsPostLimit: Int = DEFAULT_MODEL_SCORE_STATS_POST_LIMIT
    @Volatile var isDetailedLoggingEnabled: Boolean = false


    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        updateMemory(sharedPreferences, key)
    }

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val appCtx = context.applicationContext ?: context
            val p = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            appContext = appCtx
            prefs = p
            ensureUserSettingsVersion(p)

            areRestrictedFeaturesUnlocked = p.getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false)
            refreshRestrictedRuntimeFlags(p)
            isHomeTopTabsCustomEnabled = featureBoolean(p, KEY_CUSTOM_HOME_TOP_TABS)
            isHomeTopTabMaterialEnabled = p.getBoolean(KEY_HOME_TOP_TAB_MATERIAL, true)
            isHomeTopTabRecommendEnabled = p.getBoolean(KEY_HOME_TOP_TAB_RECOMMEND, true)
            isHomeTopTabLiveEnabled = p.getBoolean(KEY_HOME_TOP_TAB_LIVE, true)
            isHomeTopTabFollowedEnabled = p.getBoolean(KEY_HOME_TOP_TAB_FOLLOWED, true)
            isHomeTabAutoHideEnabled = featureBoolean(p, KEY_AUTO_HIDE_HOME_TAB)
            isBottomTabsCustomEnabled = featureBoolean(p, KEY_CUSTOM_BOTTOM_TABS)
            applyBottomTabSelection(loadBottomTabSelectionFromPrefs(p, persistNormalized = isBottomTabsCustomEnabled))
            isEnterForumWebFilterEnabled = featureBoolean(p, KEY_FILTER_ENTER_FORUM_WEB)
            isOpenWebLinkInSystemBrowserEnabled = featureBoolean(p, KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER, false)
            isHomeNativeGlassEnabled = featureBoolean(p, KEY_ENABLE_HOME_NATIVE_GLASS)
            isHomeTabDynamicTintEnabled = featureBoolean(p, KEY_ENABLE_HOME_TAB_DYNAMIC_TINT, DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED)
            refreshHomeNativeGlassStyle(p)
            isAutoRefreshDisabled = featureBoolean(p, KEY_DISABLE_AUTO_REFRESH)
            isAutoLoadMoreEnabled = featureBoolean(p, KEY_ENABLE_AUTO_LOAD_MORE)
            isDefaultNotifyTabEnabled = isScanFeatureAvailable(KEY_DEFAULT_NOTIFY_TAB)
            isDefaultOriginalImageEnabled = featureBoolean(p, KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE)
            isCleanShareTrackingParamsEnabled = isScanFeatureAvailable(KEY_CLEAN_SHARE_TRACKING_PARAMS)
            isCustomPostFilterEnabled = featureBoolean(p, KEY_ENABLE_CUSTOM_POST_FILTER)
            isPostVoteFilterEnabled = p.getBoolean(KEY_FILTER_POST_VOTE, false)
            isPostVideoFilterEnabled = p.getBoolean(KEY_FILTER_POST_VIDEO, false)
            isPostReplyFilterEnabled = p.getBoolean(KEY_FILTER_POST_REPLY, false)
            isPostHotFilterEnabled = p.getBoolean(KEY_FILTER_POST_HOT, false)
            isPostGoodsFilterEnabled = p.getBoolean(KEY_FILTER_POST_GOODS, false)
            isPostGameBookingFilterEnabled = p.getBoolean(KEY_FILTER_POST_GAME_BOOKING, false)
            isPostHelpFilterEnabled = p.getBoolean(KEY_FILTER_POST_HELP, false)
            isPostScoreFilterEnabled = p.getBoolean(KEY_FILTER_POST_SCORE, false)
            isPostLiveFilterEnabled = p.getBoolean(KEY_FILTER_POST_LIVE, false)
            isPostRecommendForumFilterEnabled = p.getBoolean(KEY_FILTER_POST_RECOMMEND_FORUM, false)
            isPostUnfollowedForumFilterEnabled = p.getBoolean(KEY_FILTER_POST_UNFOLLOWED_FORUM, false)
            isPostForumKeywordFilterEnabled = p.getBoolean(KEY_FILTER_POST_FORUM_KEYWORD, false)
            postForumKeywordList = parseKeywordList(p.getString(KEY_FILTER_POST_FORUM_KEYWORD_LIST, ""))
            isPostModelScoreFilterEnabled = p.getBoolean(KEY_FILTER_POST_MODEL_SCORE, false)
            postModelScoreThresholds = parseModelScoreThresholds(p.getString(KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS, ""))
            postModelScoreAutoPercentiles = parseModelScoreAutoPercentiles(
                p.getString(KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES, "")
            )
            postModelScoreStatsPostLimit = p.getInt(
                KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT,
                DEFAULT_MODEL_SCORE_STATS_POST_LIMIT
            ).coerceAtLeast(MIN_MODEL_SCORE_STATS_POST_LIMIT)
            isDetailedLoggingEnabled = p.getBoolean(KEY_ENABLE_DETAILED_LOGGING, false)


            p.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    private fun ensureUserSettingsVersion(p: SharedPreferences) {
        val currentVersion = BuildConfig.VERSION_CODE
        val minSupportedVersion = BuildConfig.MIN_SUPPORTED_USER_SETTINGS_VERSION_CODE
            .coerceAtMost(currentVersion)
        val storedVersion = p.getInt(KEY_USER_SETTINGS_VERSION_CODE, 0)

        if (storedVersion < minSupportedVersion) {
            p.edit()
                .clear()
                .putInt(KEY_USER_SETTINGS_VERSION_CODE, currentVersion)
                .apply()
            XposedCompat.log(
                "[ConfigManager] user settings reset: " +
                    "storedVersion=$storedVersion, minSupportedVersion=$minSupportedVersion, " +
                    "currentVersion=$currentVersion"
            )
            return
        }

        if (storedVersion != currentVersion) {
            p.edit()
                .putInt(KEY_USER_SETTINGS_VERSION_CODE, currentVersion)
                .apply()
        }
    }

    private fun updateMemory(p: SharedPreferences, key: String?) {
        when (key) {
            KEY_RESTRICTED_FEATURES_UNLOCKED -> {
                areRestrictedFeaturesUnlocked = p.getBoolean(key, false)
                refreshRestrictedRuntimeFlags(p)
            }
            KEY_BLOCK_AD -> refreshRestrictedRuntimeFlags(p)
            KEY_SIMPLIFY_HOME_TABS -> {
                isHomeTopTabsCustomEnabled = featureBoolean(p, key)
            }
            KEY_HOME_TOP_TAB_MATERIAL -> {
                isHomeTopTabMaterialEnabled = p.getBoolean(key, true)
            }
            KEY_HOME_TOP_TAB_RECOMMEND -> {
                isHomeTopTabRecommendEnabled = p.getBoolean(key, true)
            }
            KEY_HOME_TOP_TAB_LIVE -> {
                isHomeTopTabLiveEnabled = p.getBoolean(key, true)
            }
            KEY_HOME_TOP_TAB_FOLLOWED -> {
                isHomeTopTabFollowedEnabled = p.getBoolean(key, true)
            }
            KEY_AUTO_HIDE_HOME_TAB -> isHomeTabAutoHideEnabled = featureBoolean(p, key)
            KEY_SIMPLIFY_BOTTOM_TABS -> {
                isBottomTabsCustomEnabled = featureBoolean(p, key)
            }
            KEY_BOTTOM_TAB_HOME,
            KEY_BOTTOM_TAB_ENTER_FORUM,
            KEY_BOTTOM_TAB_RETAIL_STORE,
            KEY_BOTTOM_TAB_MESSAGE,
            KEY_BOTTOM_TAB_MINE -> {
                applyBottomTabSelection(loadBottomTabSelectionFromPrefs(p, persistNormalized = true))
            }
            KEY_FILTER_ENTER_FORUM_WEB -> isEnterForumWebFilterEnabled = featureBoolean(p, key)
            KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER -> isOpenWebLinkInSystemBrowserEnabled = featureBoolean(p, key, false)
            KEY_ENABLE_HOME_NATIVE_GLASS -> isHomeNativeGlassEnabled = featureBoolean(p, key)
            KEY_ENABLE_HOME_TAB_DYNAMIC_TINT -> isHomeTabDynamicTintEnabled = featureBoolean(p, key, DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED)
            KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
            KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
            KEY_HOME_NATIVE_GLASS_TEXT_PALETTE_LIGHT,
            KEY_HOME_NATIVE_GLASS_TEXT_PALETTE_DARK,
            KEY_HOME_NATIVE_GLASS_TINT_COLOR,
            KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            KEY_HOME_NATIVE_GLASS_STROKE_ENABLED,
            KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED -> refreshHomeNativeGlassStyle(p)
            KEY_DISABLE_AUTO_REFRESH -> isAutoRefreshDisabled = featureBoolean(p, key)
            KEY_ENABLE_AUTO_LOAD_MORE -> isAutoLoadMoreEnabled = featureBoolean(p, key)
            KEY_DEFAULT_NOTIFY_TAB -> isDefaultNotifyTabEnabled = isScanFeatureAvailable(KEY_DEFAULT_NOTIFY_TAB)
            KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE -> isDefaultOriginalImageEnabled = featureBoolean(p, key)
            KEY_CLEAN_SHARE_TRACKING_PARAMS -> isCleanShareTrackingParamsEnabled = isScanFeatureAvailable(KEY_CLEAN_SHARE_TRACKING_PARAMS)
            KEY_ENABLE_CUSTOM_POST_FILTER -> isCustomPostFilterEnabled = featureBoolean(p, key)
            KEY_DISABLE_AI_COMPONENTS,
            KEY_DISABLE_LOCATION_COMPONENTS,
            KEY_DISABLE_AD_SDK_COMPONENTS,
            KEY_DISABLE_HEAVY_FEATURE_COMPONENTS,
            KEY_DISABLE_VIDEO_COMPONENTS,
            KEY_DISABLE_MONITOR_SYNC_COMPONENTS,
            KEY_DISABLE_UPDATE_DOWNLOAD_COMPONENTS,
            KEY_ENABLE_PB_PERFORMANCE_MODE,
            KEY_ENABLE_PB_SCROLL_COALESCE,
            KEY_FORCE_FEED_UI_OPT,
            KEY_ENABLE_PERFORMANCE_OPTIMIZATION,
            KEY_FORCE_HOST_PERFORMANCE_FLAGS,
            KEY_DISABLE_APSARAS_SCHEDULE,
            KEY_DISABLE_FLUTTER_PREINIT,
            KEY_FORCE_LOW_END_DEVICE_CONFIG,
            KEY_BLOCK_TITAN_PATCH,
            KEY_PRIVATE_READ_RECEIPT_INVISIBLE -> refreshRestrictedRuntimeFlags(p)
            KEY_FILTER_POST_VOTE -> isPostVoteFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_VIDEO -> isPostVideoFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_REPLY -> isPostReplyFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_HOT -> isPostHotFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_GOODS -> isPostGoodsFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_GAME_BOOKING -> isPostGameBookingFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_HELP -> isPostHelpFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_SCORE -> isPostScoreFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_LIVE -> isPostLiveFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_RECOMMEND_FORUM -> isPostRecommendForumFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_UNFOLLOWED_FORUM -> isPostUnfollowedForumFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_FORUM_KEYWORD -> isPostForumKeywordFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_FORUM_KEYWORD_LIST -> postForumKeywordList = parseKeywordList(p.getString(key, ""))
            KEY_FILTER_POST_MODEL_SCORE -> isPostModelScoreFilterEnabled = p.getBoolean(key, false)
            KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS -> postModelScoreThresholds = parseModelScoreThresholds(p.getString(key, ""))
            KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES -> postModelScoreAutoPercentiles = parseModelScoreAutoPercentiles(
                p.getString(key, "")
            )
            KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT -> postModelScoreStatsPostLimit = p.getInt(
                key,
                DEFAULT_MODEL_SCORE_STATS_POST_LIMIT
            ).coerceAtLeast(MIN_MODEL_SCORE_STATS_POST_LIMIT)
            KEY_ENABLE_DETAILED_LOGGING -> isDetailedLoggingEnabled = p.getBoolean(key, false)

        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
            init(context)
        }
    }

    fun getModuleStatePrefs(context: Context): SharedPreferences {
        val appCtx = context.applicationContext ?: context
        return appCtx.getSharedPreferences(MODULE_STATE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAppContext(): Context? = appContext

    fun resetRuntimeAfterUserDataClear(context: Context) {
        synchronized(this) {
            prefs?.unregisterOnSharedPreferenceChangeListener(listener)
            prefs = null
            appContext = null
        }
        init(context.applicationContext ?: context)
    }

    fun shouldOutputDetailedLogs(): Boolean {
        return isDetailedLoggingEnabled
    }

    fun isAutoSignInEnabled(context: Context): Boolean {
        val p = getPrefs(context)
        return p.getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false) &&
            p.getBoolean(KEY_ENABLE_AUTO_SIGN_IN, false) &&
            isScanFeatureAvailable(KEY_ENABLE_AUTO_SIGN_IN)
    }

    fun isRestrictedFeaturesUnlocked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false)
    }

    fun setRestrictedFeaturesUnlocked(context: Context, unlocked: Boolean) {
        val p = getPrefs(context)
        p.edit()
            .putBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, unlocked)
            .apply()
        areRestrictedFeaturesUnlocked = unlocked
        refreshRestrictedRuntimeFlags(p)
    }

    private fun refreshRestrictedRuntimeFlags(p: SharedPreferences) {
        val performanceOptimizationEnabled = restrictedBoolean(p, KEY_ENABLE_PERFORMANCE_OPTIMIZATION)
        isAdBlockEnabled = restrictedBoolean(p, KEY_BLOCK_AD)
        isHostPerformanceFlagsForced = performanceChildBoolean(p, KEY_FORCE_HOST_PERFORMANCE_FLAGS, performanceOptimizationEnabled, true)
        isApsarasScheduleDisabled = performanceChildBoolean(p, KEY_DISABLE_APSARAS_SCHEDULE, performanceOptimizationEnabled, true)
        isFlutterPreinitDisabled = performanceChildBoolean(p, KEY_DISABLE_FLUTTER_PREINIT, performanceOptimizationEnabled, true)
        isLowEndDeviceConfigForced = performanceChildBoolean(p, KEY_FORCE_LOW_END_DEVICE_CONFIG, performanceOptimizationEnabled, true)
        isAiComponentsDisabled = performanceChildBoolean(p, KEY_DISABLE_AI_COMPONENTS, performanceOptimizationEnabled, true)
        isLocationComponentsDisabled = performanceChildBoolean(p, KEY_DISABLE_LOCATION_COMPONENTS, performanceOptimizationEnabled, true)
        isAdSdkComponentsDisabled = performanceChildBoolean(p, KEY_DISABLE_AD_SDK_COMPONENTS, performanceOptimizationEnabled, true)
        isHeavyFeatureComponentsDisabled = performanceChildBoolean(p, KEY_DISABLE_HEAVY_FEATURE_COMPONENTS, performanceOptimizationEnabled, true)
        isVideoComponentsDisabled = performanceChildBoolean(p, KEY_DISABLE_VIDEO_COMPONENTS, performanceOptimizationEnabled, true)
        isMonitorSyncComponentsDisabled = performanceChildBoolean(p, KEY_DISABLE_MONITOR_SYNC_COMPONENTS, performanceOptimizationEnabled, true)
        isUpdateDownloadComponentsDisabled = performanceChildBoolean(p, KEY_DISABLE_UPDATE_DOWNLOAD_COMPONENTS, performanceOptimizationEnabled, true)
        isPbPerformanceModeEnabled = performanceChildBoolean(p, KEY_ENABLE_PB_PERFORMANCE_MODE, performanceOptimizationEnabled, true)
        isPbScrollCoalesceEnabled = performanceChildBoolean(p, KEY_ENABLE_PB_SCROLL_COALESCE, performanceOptimizationEnabled, true)
        isFeedUiOptForced = performanceChildBoolean(p, KEY_FORCE_FEED_UI_OPT, performanceOptimizationEnabled, true)
        isTitanPatchBlockEnabled = performanceChildBoolean(p, KEY_BLOCK_TITAN_PATCH, performanceOptimizationEnabled, false)
        isPrivateReadReceiptInvisibleEnabled = restrictedBoolean(p, KEY_PRIVATE_READ_RECEIPT_INVISIBLE)
    }

    private fun restrictedBoolean(p: SharedPreferences, key: String): Boolean {
        return areRestrictedFeaturesUnlocked && featureBoolean(p, key)
    }

    private fun performanceChildBoolean(
        p: SharedPreferences,
        key: String,
        masterEnabled: Boolean,
        defaultValue: Boolean,
    ): Boolean {
        if (!areRestrictedFeaturesUnlocked || !masterEnabled || !isScanFeatureAvailable(key)) return false
        return if (p.contains(key)) {
            p.getBoolean(key, false)
        } else {
            defaultValue
        }
    }

    private fun featureBoolean(p: SharedPreferences, key: String, defaultValue: Boolean = false): Boolean {
        return p.getBoolean(key, defaultValue) && isScanFeatureAvailable(key)
    }

    private fun refreshHomeNativeGlassStyle(p: SharedPreferences) {
        homeNativeGlassBackgroundImagePath = p.getString(
            KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
            DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
        )?.trim().orEmpty()
        homeNativeGlassBlurCacheImagePath = p.getString(
            KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
            DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
        )?.trim().orEmpty()
        homeNativeGlassTextPaletteLight = p.getString(
            KEY_HOME_NATIVE_GLASS_TEXT_PALETTE_LIGHT,
            DEFAULT_HOME_NATIVE_GLASS_TEXT_PALETTE,
        )?.trim().orEmpty()
        homeNativeGlassTextPaletteDark = p.getString(
            KEY_HOME_NATIVE_GLASS_TEXT_PALETTE_DARK,
            DEFAULT_HOME_NATIVE_GLASS_TEXT_PALETTE,
        )?.trim().orEmpty()
        homeNativeGlassTintColor = normalizeHomeNativeGlassTintColor(
            p.getInt(
                KEY_HOME_NATIVE_GLASS_TINT_COLOR,
                DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR,
            )
        )
        homeNativeGlassTintAlphaPercent = p.getInt(
            KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        ).coerceIn(
            MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
        homeNativeGlassCardBlurPercent = p.getInt(
            KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
        ).coerceIn(
            MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
        )
        homeNativeGlassCardRadiusDp = p.getInt(
            KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
        ).coerceIn(
            MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
        )
        isHomeNativeGlassStrokeEnabled = p.getBoolean(
            KEY_HOME_NATIVE_GLASS_STROKE_ENABLED,
            DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED,
        )
        isHomeNativeGlassShadowEnabled = p.getBoolean(
            KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED,
            DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED,
        )
    }

    fun normalizeHomeNativeGlassTintColor(color: Int): Int {
        if (color == DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR || Color.alpha(color) == 0) {
            return DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
        }
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }

    fun isScanFeatureAvailable(featureKey: String): Boolean {
        return scanFeatureAvailability[featureKey] != false
    }

    fun applyScanAvailability(context: Context, featureStatusMap: Map<String, HookFeatureStatus>) {
        if (featureStatusMap.isEmpty()) return
        scanFeatureAvailability = featureStatusMap.mapValues { (_, status) ->
            status.state != HookFeatureState.DISABLED
        }

        val p = getPrefs(context)
        refreshRestrictedRuntimeFlags(p)

        isHomeTopTabsCustomEnabled = featureBoolean(p, KEY_CUSTOM_HOME_TOP_TABS)
        isHomeTabAutoHideEnabled = featureBoolean(p, KEY_AUTO_HIDE_HOME_TAB)
        isBottomTabsCustomEnabled = featureBoolean(p, KEY_CUSTOM_BOTTOM_TABS)
        applyBottomTabSelection(loadBottomTabSelectionFromPrefs(p, persistNormalized = isBottomTabsCustomEnabled))

        isEnterForumWebFilterEnabled = featureBoolean(p, KEY_FILTER_ENTER_FORUM_WEB)
        isOpenWebLinkInSystemBrowserEnabled = featureBoolean(p, KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER, false)
        isHomeNativeGlassEnabled = featureBoolean(p, KEY_ENABLE_HOME_NATIVE_GLASS)
        isHomeTabDynamicTintEnabled = featureBoolean(p, KEY_ENABLE_HOME_TAB_DYNAMIC_TINT, DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED)
        refreshHomeNativeGlassStyle(p)
        isAutoRefreshDisabled = featureBoolean(p, KEY_DISABLE_AUTO_REFRESH)
        isAutoLoadMoreEnabled = featureBoolean(p, KEY_ENABLE_AUTO_LOAD_MORE)
        isDefaultNotifyTabEnabled = isScanFeatureAvailable(KEY_DEFAULT_NOTIFY_TAB)
        isDefaultOriginalImageEnabled = featureBoolean(p, KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE)
        isCleanShareTrackingParamsEnabled = isScanFeatureAvailable(KEY_CLEAN_SHARE_TRACKING_PARAMS)
        isCustomPostFilterEnabled = featureBoolean(p, KEY_ENABLE_CUSTOM_POST_FILTER)
    }

    data class ModelScoreThreshold(
        val key: String,
        val threshold: Double,
    )

    fun parseModelScoreThresholds(raw: String?): List<ModelScoreThreshold> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = LinkedHashMap<String, Double>()
        for (token in raw.split('\n', ';')) {
            val line = token.trim()
            if (line.isEmpty()) continue
            val separator = line.indexOf('=').takeIf { it > 0 } ?: line.indexOf(':').takeIf { it > 0 } ?: continue
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim().toDoubleOrNull()
            if (key.isNotEmpty() && value != null && value >= 0.0 && !value.isNaN() && !value.isInfinite()) {
                result[key] = value
            }
        }
        return result.map { (key, threshold) -> ModelScoreThreshold(key, threshold) }
    }

    fun serializeModelScoreThresholds(thresholds: List<ModelScoreThreshold>): String {
        return thresholds.joinToString("\n") { "${it.key}=${formatModelScoreThresholdValue(it.threshold)}" }
    }

    fun roundModelScoreThreshold(value: Double): Double {
        return BigDecimal.valueOf(value)
            .setScale(MODEL_SCORE_THRESHOLD_SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun formatModelScoreThresholdValue(value: Double?): String {
        value ?: return ""
        val decimal = BigDecimal.valueOf(value)
            .setScale(MODEL_SCORE_THRESHOLD_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return decimal.toPlainString().ifEmpty { "0" }
    }

    fun normalizeModelScoreAutoPercentile(percentile: Int): Int {
        return if (percentile in SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES) {
            percentile
        } else {
            DEFAULT_MODEL_SCORE_AUTO_PERCENTILE
        }
    }

    fun parseModelScoreAutoPercentiles(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, Int>()
        for (token in raw.split('\n', ';')) {
            val line = token.trim()
            if (line.isEmpty()) continue
            val separator = line.indexOf('=').takeIf { it > 0 } ?: line.indexOf(':').takeIf { it > 0 } ?: continue
            val key = line.substring(0, separator).trim()
            val valueText = line.substring(separator + 1).trim().removePrefix("P").removePrefix("p")
            val percentile = valueText.toIntOrNull() ?: continue
            if (key.isNotEmpty() && percentile in SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES) {
                result[key] = percentile
            }
        }
        return result
    }

    fun serializeModelScoreAutoPercentiles(percentiles: Map<String, Int>): String {
        return percentiles.asSequence()
            .filter { (key, percentile) -> key.isNotBlank() && percentile in SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES }
            .joinToString("\n") { (key, percentile) -> "${key.trim()}=$percentile" }
    }

    data class HomeTopTabSelection(
        val materialEnabled: Boolean,
        val recommendEnabled: Boolean,
        val liveEnabled: Boolean,
        val followedEnabled: Boolean,
    ) {
        fun hasEnabledTab(): Boolean = materialEnabled || recommendEnabled || liveEnabled || followedEnabled
    }

    fun normalizeHomeTopTabSelection(selection: HomeTopTabSelection): HomeTopTabSelection {
        if (selection.hasEnabledTab()) return selection
        // 外部改偏好导致状态无效时，保留 "推荐" 作为兜底值。
        return selection.copy(
            materialEnabled = false,
            recommendEnabled = true,
            liveEnabled = false,
            followedEnabled = false,
        )
    }

    fun resolveHomeTopTabSelection(): HomeTopTabSelection {
        val current = HomeTopTabSelection(
            materialEnabled = isHomeTopTabMaterialEnabled,
            recommendEnabled = isHomeTopTabRecommendEnabled,
            liveEnabled = isHomeTopTabLiveEnabled,
            followedEnabled = isHomeTopTabFollowedEnabled,
        )
        return normalizeHomeTopTabSelection(current)
    }

    data class BottomTabSelection(
        val homeEnabled: Boolean,
        val enterForumEnabled: Boolean,
        val retailStoreEnabled: Boolean,
        val messageEnabled: Boolean,
        val mineEnabled: Boolean,
    ) {
        fun hasEnabledTab(): Boolean {
            return homeEnabled || enterForumEnabled || retailStoreEnabled || messageEnabled || mineEnabled
        }
    }

    fun normalizeBottomTabSelection(selection: BottomTabSelection): BottomTabSelection {
        if (selection.hasEnabledTab()) return selection
        // 外部改偏好导致状态无效时，保留 "首页" 作为兜底值。
        return selection.copy(
            homeEnabled = true,
            enterForumEnabled = false,
            retailStoreEnabled = false,
            messageEnabled = false,
            mineEnabled = false,
        )
    }

    fun resolveBottomTabSelection(): BottomTabSelection {
        val current = BottomTabSelection(
            homeEnabled = isBottomTabHomeEnabled,
            enterForumEnabled = isBottomTabEnterForumEnabled,
            retailStoreEnabled = isBottomTabRetailStoreEnabled,
            messageEnabled = isBottomTabMessageEnabled,
            mineEnabled = isBottomTabMineEnabled,
        )
        return normalizeBottomTabSelection(current)
    }



    private fun loadBottomTabSelectionFromPrefs(
        p: SharedPreferences,
        persistNormalized: Boolean,
    ): BottomTabSelection {
        val raw = BottomTabSelection(
            homeEnabled = p.getBoolean(KEY_BOTTOM_TAB_HOME, true),
            enterForumEnabled = p.getBoolean(KEY_BOTTOM_TAB_ENTER_FORUM, true),
            retailStoreEnabled = p.getBoolean(KEY_BOTTOM_TAB_RETAIL_STORE, true),
            messageEnabled = p.getBoolean(KEY_BOTTOM_TAB_MESSAGE, true),
            mineEnabled = p.getBoolean(KEY_BOTTOM_TAB_MINE, true),
        )
        val normalized = normalizeBottomTabSelection(raw)
        if (persistNormalized && normalized != raw) {
            p.edit()
                .putBoolean(KEY_BOTTOM_TAB_HOME, normalized.homeEnabled)
                .putBoolean(KEY_BOTTOM_TAB_ENTER_FORUM, normalized.enterForumEnabled)
                .putBoolean(KEY_BOTTOM_TAB_RETAIL_STORE, normalized.retailStoreEnabled)
                .putBoolean(KEY_BOTTOM_TAB_MESSAGE, normalized.messageEnabled)
                .putBoolean(KEY_BOTTOM_TAB_MINE, normalized.mineEnabled)
                .apply()
        }
        return normalized
    }

    private fun applyBottomTabSelection(selection: BottomTabSelection) {
        isBottomTabHomeEnabled = selection.homeEnabled
        isBottomTabEnterForumEnabled = selection.enterForumEnabled
        isBottomTabRetailStoreEnabled = selection.retailStoreEnabled
        isBottomTabMessageEnabled = selection.messageEnabled
        isBottomTabMineEnabled = selection.mineEnabled
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
