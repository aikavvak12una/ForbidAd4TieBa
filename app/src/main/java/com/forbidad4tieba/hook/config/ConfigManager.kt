package com.forbidad4tieba.hook.config

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.forbidad4tieba.hook.BuildConfig
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
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
    const val DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR = 0
    const val DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR = 0
    const val DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE = ""
    const val DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 0
    const val DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 72
    const val DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 24
    const val DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED = true
    const val DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED = true
    const val DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED = true
    const val DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT = 100
    const val DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS = 10
    const val MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS = 1
    const val MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS = 30
    const val DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS = 1000
    const val MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS = 500
    const val MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS = 10000
    const val APPLE_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 0
    const val APPLE_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 72
    const val APPLE_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 24
    const val MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = -50
    const val MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = 50
    const val MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 10
    const val MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = 100
    const val MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 0
    const val MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP = 32
    const val MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT = 0
    const val MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT = 100
    val SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES = intArrayOf(5, 10, 15, 20)
    private const val MODEL_SCORE_THRESHOLD_SCALE = 6
    private const val KEY_USER_SETTINGS_VERSION_CODE = "user_settings_version_code"
    private const val KEY_REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE =
        "remote_environment_warning_dialog_active"
    private const val KEY_REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE = "remote_restricted_features_lock_active"
    private const val KEY_PENDING_POST_SCAN_ENVIRONMENT_WARNING =
        "pending_post_scan_environment_warning"

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
    const val KEY_HOME_NATIVE_GLASS_TINT_COLOR = "home_native_glass_tint_color"
    const val KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR = "home_native_glass_auto_tint_color"
    const val KEY_HOME_NATIVE_GLASS_TINT_PALETTE = "home_native_glass_tint_palette"
    const val KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT = "home_native_glass_tint_alpha_percent"
    const val KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED =
        "home_native_glass_tint_alpha_offset_migrated"
    const val KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT = "home_native_glass_card_blur_percent"
    const val KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP = "home_native_glass_card_radius_dp"
    const val KEY_HOME_NATIVE_GLASS_STROKE_ENABLED = "home_native_glass_stroke_enabled"
    const val KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED = "home_native_glass_shadow_enabled"
    const val KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT =
        "home_native_glass_shadow_strength_percent"
    const val KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT =
        "home_native_glass_background_image_path_light"
    const val KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_LIGHT =
        "home_native_glass_blur_cache_image_path_light"
    const val KEY_HOME_NATIVE_GLASS_TINT_COLOR_LIGHT = "home_native_glass_tint_color_light"
    const val KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR_LIGHT = "home_native_glass_auto_tint_color_light"
    const val KEY_HOME_NATIVE_GLASS_TINT_PALETTE_LIGHT = "home_native_glass_tint_palette_light"
    const val KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_LIGHT =
        "home_native_glass_tint_alpha_percent_light"
    const val KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_LIGHT =
        "home_native_glass_card_blur_percent_light"
    const val KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_LIGHT =
        "home_native_glass_card_radius_dp_light"
    const val KEY_HOME_NATIVE_GLASS_STROKE_ENABLED_LIGHT = "home_native_glass_stroke_enabled_light"
    const val KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED_LIGHT = "home_native_glass_shadow_enabled_light"
    const val KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_LIGHT =
        "home_native_glass_shadow_strength_percent_light"
    const val KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_DARK =
        "home_native_glass_background_image_path_dark"
    const val KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_DARK =
        "home_native_glass_blur_cache_image_path_dark"
    const val KEY_HOME_NATIVE_GLASS_TINT_COLOR_DARK = "home_native_glass_tint_color_dark"
    const val KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR_DARK = "home_native_glass_auto_tint_color_dark"
    const val KEY_HOME_NATIVE_GLASS_TINT_PALETTE_DARK = "home_native_glass_tint_palette_dark"
    const val KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_DARK =
        "home_native_glass_tint_alpha_percent_dark"
    const val KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_DARK =
        "home_native_glass_card_blur_percent_dark"
    const val KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_DARK =
        "home_native_glass_card_radius_dp_dark"
    const val KEY_HOME_NATIVE_GLASS_STROKE_ENABLED_DARK = "home_native_glass_stroke_enabled_dark"
    const val KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED_DARK = "home_native_glass_shadow_enabled_dark"
    const val KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_DARK =
        "home_native_glass_shadow_strength_percent_dark"
    const val KEY_DISABLE_AUTO_REFRESH = "disable_auto_refresh"
    const val KEY_ENABLE_AUTO_LOAD_MORE = "enable_auto_load_more"
    const val KEY_ENABLE_PB_LIKE_AUTO_REPLY = "enable_pb_like_auto_reply"
    const val KEY_PB_LIKE_AUTO_REPLY_TEXT = "pb_like_auto_reply_text"
    const val KEY_VERIFY_REPLY_AFTER_POST = "verify_reply_after_post"
    const val KEY_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS = "reply_visibility_probe_max_attempts"
    const val KEY_REPLY_VISIBILITY_PROBE_INTERVAL_MS = "reply_visibility_probe_interval_ms"
    const val KEY_DEFAULT_NOTIFY_TAB = "default_notify_tab"
    const val KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE = "enable_default_original_image"
    const val KEY_ENABLE_AUTO_SIGN_IN = "enable_auto_sign_in"
    const val KEY_RESTRICTED_FEATURES_UNLOCKED = "restricted_features_unlocked"
    const val KEY_CLEAN_SHARE_TRACKING_PARAMS = "clean_share_tracking_params"
    const val KEY_DISABLE_AI_COMPONENTS = "disable_ai_components"
    const val KEY_ENABLE_CUSTOM_POST_FILTER = "enable_custom_post_filter"
    const val KEY_DISABLE_AD_SDK_COMPONENTS = "disable_ad_sdk_components"
    const val KEY_DISABLE_VIDEO_COMPONENTS = "disable_video_components"
    const val KEY_DISABLE_MONITOR_SYNC_COMPONENTS = "disable_monitor_sync_components"
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
    @Volatile private var settingsSnapshot: SettingsSnapshot = SettingsSnapshot()
    @Volatile private var settingsSnapshotVersion: Long = 0L
    @Volatile private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile private var homeNativeGlassDarkModeActive: Boolean = false

    @Volatile private var restrictedFeatureUnlockBlockedByRemote: Boolean = false
    @Volatile private var environmentWarningDialogActive: Boolean = false

    data class HomeNativeGlassStyleConfig(
        val backgroundImagePath: String = DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
        val blurCacheImagePath: String = DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
        val tintColor: Int = DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR,
        val autoTintColor: Int = DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
        val tintPalette: String = DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE,
        val tintAlphaPercent: Int = DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        val cardBlurPercent: Int = DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
        val cardRadiusDp: Int = DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
        val strokeEnabled: Boolean = DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED,
        val shadowStrengthPercent: Int = DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
    ) {
        fun hasBackgroundImage(): Boolean = backgroundImagePath.isNotBlank()
    }

    data class HomeNativeGlassStyleKeys(
        val backgroundImagePath: String,
        val blurCacheImagePath: String,
        val tintColor: String,
        val autoTintColor: String,
        val tintPalette: String,
        val tintAlphaPercent: String,
        val cardBlurPercent: String,
        val cardRadiusDp: String,
        val strokeEnabled: String,
        val shadowStrengthPercent: String,
        val legacyShadowEnabled: String,
    ) {
        fun all(): Array<String> = arrayOf(
            backgroundImagePath,
            blurCacheImagePath,
            tintColor,
            autoTintColor,
            tintPalette,
            tintAlphaPercent,
            cardBlurPercent,
            cardRadiusDp,
            strokeEnabled,
            shadowStrengthPercent,
            legacyShadowEnabled,
        )
    }

    val HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS = HomeNativeGlassStyleKeys(
        backgroundImagePath = KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT,
        blurCacheImagePath = KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_LIGHT,
        tintColor = KEY_HOME_NATIVE_GLASS_TINT_COLOR_LIGHT,
        autoTintColor = KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR_LIGHT,
        tintPalette = KEY_HOME_NATIVE_GLASS_TINT_PALETTE_LIGHT,
        tintAlphaPercent = KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_LIGHT,
        cardBlurPercent = KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_LIGHT,
        cardRadiusDp = KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_LIGHT,
        strokeEnabled = KEY_HOME_NATIVE_GLASS_STROKE_ENABLED_LIGHT,
        shadowStrengthPercent = KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_LIGHT,
        legacyShadowEnabled = KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED_LIGHT,
    )

    val HOME_NATIVE_GLASS_DARK_STYLE_KEYS = HomeNativeGlassStyleKeys(
        backgroundImagePath = KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_DARK,
        blurCacheImagePath = KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH_DARK,
        tintColor = KEY_HOME_NATIVE_GLASS_TINT_COLOR_DARK,
        autoTintColor = KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR_DARK,
        tintPalette = KEY_HOME_NATIVE_GLASS_TINT_PALETTE_DARK,
        tintAlphaPercent = KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT_DARK,
        cardBlurPercent = KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT_DARK,
        cardRadiusDp = KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP_DARK,
        strokeEnabled = KEY_HOME_NATIVE_GLASS_STROKE_ENABLED_DARK,
        shadowStrengthPercent = KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT_DARK,
        legacyShadowEnabled = KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED_DARK,
    )
    val areRestrictedFeaturesUnlocked: Boolean get() = settingsSnapshot.areRestrictedFeaturesUnlocked
    val isAdBlockEnabled: Boolean get() = settingsSnapshot.isAdBlockEnabled
    val isHomeTopTabsCustomEnabled: Boolean get() = settingsSnapshot.isHomeTopTabsCustomEnabled
    val isHomeTopTabMaterialEnabled: Boolean get() = settingsSnapshot.isHomeTopTabMaterialEnabled
    val isHomeTopTabRecommendEnabled: Boolean get() = settingsSnapshot.isHomeTopTabRecommendEnabled
    val isHomeTopTabLiveEnabled: Boolean get() = settingsSnapshot.isHomeTopTabLiveEnabled
    val isHomeTopTabFollowedEnabled: Boolean get() = settingsSnapshot.isHomeTopTabFollowedEnabled
    val isHomeTabAutoHideEnabled: Boolean get() = settingsSnapshot.isHomeTabAutoHideEnabled
    val isBottomTabsCustomEnabled: Boolean get() = settingsSnapshot.isBottomTabsCustomEnabled
    val isBottomTabHomeEnabled: Boolean get() = settingsSnapshot.isBottomTabHomeEnabled
    val isBottomTabEnterForumEnabled: Boolean get() = settingsSnapshot.isBottomTabEnterForumEnabled
    val isBottomTabRetailStoreEnabled: Boolean get() = settingsSnapshot.isBottomTabRetailStoreEnabled
    val isBottomTabMessageEnabled: Boolean get() = settingsSnapshot.isBottomTabMessageEnabled
    val isBottomTabMineEnabled: Boolean get() = settingsSnapshot.isBottomTabMineEnabled
    val isEnterForumWebFilterEnabled: Boolean get() = settingsSnapshot.isEnterForumWebFilterEnabled
    val isOpenWebLinkInSystemBrowserEnabled: Boolean
        get() = settingsSnapshot.isOpenWebLinkInSystemBrowserEnabled
    val isHomeNativeGlassEnabled: Boolean get() = settingsSnapshot.isHomeNativeGlassEnabled
    val isHomeTabDynamicTintEnabled: Boolean get() = settingsSnapshot.isHomeTabDynamicTintEnabled
    val hasAnyHomeNativeGlassBackgroundImage: Boolean
        get() = settingsSnapshot.hasAnyHomeNativeGlassBackgroundImage()
    val homeNativeGlassTintColor: Int get() = activeHomeNativeGlassStyle().tintColor
    val homeNativeGlassAutoTintColor: Int get() = activeHomeNativeGlassStyle().autoTintColor
    val homeNativeGlassCardRadiusDp: Int get() = activeHomeNativeGlassStyle().cardRadiusDp
    val isHomeNativeGlassStrokeEnabled: Boolean get() = activeHomeNativeGlassStyle().strokeEnabled
    val isAutoRefreshDisabled: Boolean get() = settingsSnapshot.isAutoRefreshDisabled
    val isAutoLoadMoreEnabled: Boolean get() = settingsSnapshot.isAutoLoadMoreEnabled
    val isPbLikeAutoReplyEnabled: Boolean get() = settingsSnapshot.isPbLikeAutoReplyEnabled
    val pbLikeAutoReplyText: String get() = settingsSnapshot.pbLikeAutoReplyText
    val isReplyVisibilityProbeEnabled: Boolean get() = settingsSnapshot.isReplyVisibilityProbeEnabled
    val replyVisibilityProbeMaxAttempts: Int get() = settingsSnapshot.replyVisibilityProbeMaxAttempts
    val replyVisibilityProbeIntervalMs: Int get() = settingsSnapshot.replyVisibilityProbeIntervalMs
    val isPbScrollCoalesceEnabled: Boolean get() = settingsSnapshot.isPbScrollCoalesceEnabled
    val isDefaultNotifyTabEnabled: Boolean get() = settingsSnapshot.isDefaultNotifyTabEnabled
    val isDefaultOriginalImageEnabled: Boolean get() = settingsSnapshot.isDefaultOriginalImageEnabled
    val isCleanShareTrackingParamsEnabled: Boolean get() = settingsSnapshot.isCleanShareTrackingParamsEnabled
    val isAiComponentsDisabled: Boolean get() = settingsSnapshot.isAiComponentsDisabled
    val isCustomPostFilterEnabled: Boolean get() = settingsSnapshot.isCustomPostFilterEnabled
    val isAdSdkComponentsDisabled: Boolean get() = settingsSnapshot.isAdSdkComponentsDisabled
    val isVideoComponentsDisabled: Boolean get() = settingsSnapshot.isVideoComponentsDisabled
    val isMonitorSyncComponentsDisabled: Boolean get() = settingsSnapshot.isMonitorSyncComponentsDisabled
    val isPbPerformanceModeEnabled: Boolean get() = settingsSnapshot.isPbPerformanceModeEnabled
    val isFeedUiOptForced: Boolean get() = settingsSnapshot.isFeedUiOptForced
    val isHostPerformanceFlagsForced: Boolean get() = settingsSnapshot.isHostPerformanceFlagsForced
    val isApsarasScheduleDisabled: Boolean get() = settingsSnapshot.isApsarasScheduleDisabled
    val isFlutterPreinitDisabled: Boolean get() = settingsSnapshot.isFlutterPreinitDisabled
    val isLowEndDeviceConfigForced: Boolean get() = settingsSnapshot.isLowEndDeviceConfigForced
    val isTitanPatchBlockEnabled: Boolean get() = settingsSnapshot.isTitanPatchBlockEnabled
    val isPrivateReadReceiptInvisibleEnabled: Boolean
        get() = settingsSnapshot.isPrivateReadReceiptInvisibleEnabled
    val isPostVoteFilterEnabled: Boolean get() = settingsSnapshot.isPostVoteFilterEnabled
    val isPostVideoFilterEnabled: Boolean get() = settingsSnapshot.isPostVideoFilterEnabled
    val isPostReplyFilterEnabled: Boolean get() = settingsSnapshot.isPostReplyFilterEnabled
    val isPostHotFilterEnabled: Boolean get() = settingsSnapshot.isPostHotFilterEnabled
    val isPostGoodsFilterEnabled: Boolean get() = settingsSnapshot.isPostGoodsFilterEnabled
    val isPostGameBookingFilterEnabled: Boolean get() = settingsSnapshot.isPostGameBookingFilterEnabled
    val isPostHelpFilterEnabled: Boolean get() = settingsSnapshot.isPostHelpFilterEnabled
    val isPostScoreFilterEnabled: Boolean get() = settingsSnapshot.isPostScoreFilterEnabled
    val isPostLiveFilterEnabled: Boolean get() = settingsSnapshot.isPostLiveFilterEnabled
    val isPostRecommendForumFilterEnabled: Boolean
        get() = settingsSnapshot.isPostRecommendForumFilterEnabled
    val isPostUnfollowedForumFilterEnabled: Boolean
        get() = settingsSnapshot.isPostUnfollowedForumFilterEnabled
    val isPostForumKeywordFilterEnabled: Boolean get() = settingsSnapshot.isPostForumKeywordFilterEnabled
    val postForumKeywordList: List<String> get() = settingsSnapshot.postForumKeywordList
    val isPostModelScoreFilterEnabled: Boolean get() = settingsSnapshot.isPostModelScoreFilterEnabled
    var postModelScoreThresholds: List<ModelScoreThreshold>
        get() = settingsSnapshot.postModelScoreThresholds
        set(value) {
            replaceSettingsSnapshot(settingsSnapshot.copy(postModelScoreThresholds = value))
        }
    val postModelScoreAutoPercentiles: Map<String, Int> get() = settingsSnapshot.postModelScoreAutoPercentiles
    val postModelScoreStatsPostLimit: Int get() = settingsSnapshot.postModelScoreStatsPostLimit
    val isDetailedLoggingEnabled: Boolean get() = settingsSnapshot.isDetailedLoggingEnabled


    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val appCtx = context.applicationContext ?: context
            val p = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            appContext = appCtx
            prefs = p
            ensureUserSettingsVersion(p)

            restrictedFeatureUnlockBlockedByRemote = getModuleStatePrefs(appCtx)
                .getBoolean(KEY_REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE, false)
            environmentWarningDialogActive = getModuleStatePrefs(appCtx)
                .getBoolean(KEY_REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE, false)
            if (restrictedFeatureUnlockBlockedByRemote && p.getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false)) {
                p.edit().putBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false).apply()
            }
            refreshUserSettingsSnapshot(p)
            ensurePrefsListener(p)
        }
    }

    fun snapshot(): SettingsSnapshot = settingsSnapshot
    fun snapshotVersion(): Long = settingsSnapshotVersion

    fun refreshRuntimeSettings(context: Context? = null) {
        val p = prefs ?: context?.let { getPrefs(it) } ?: return
        synchronized(this) {
            refreshUserSettingsSnapshot(p)
        }
    }

    val isHomeNativeGlassDarkModeActive: Boolean
        get() = homeNativeGlassDarkModeActive

    fun setHomeNativeGlassDarkModeActive(enabled: Boolean?): Boolean {
        val next = enabled ?: return false
        if (homeNativeGlassDarkModeActive == next) return false
        homeNativeGlassDarkModeActive = next
        return true
    }

    fun activeHomeNativeGlassStyle(): HomeNativeGlassStyleConfig {
        return settingsSnapshot.homeNativeGlassStyleForDarkMode(homeNativeGlassDarkModeActive)
    }

    private fun replaceSettingsSnapshot(snapshot: SettingsSnapshot) {
        settingsSnapshot = snapshot
        settingsSnapshotVersion++
    }

    private fun ensurePrefsListener(p: SharedPreferences) {
        if (prefsListener != null) return
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, _ ->
            synchronized(this@ConfigManager) {
                if (prefs !== sharedPrefs) return@OnSharedPreferenceChangeListener
                replaceSettingsSnapshot(buildSettingsSnapshot(sharedPrefs))
            }
        }
        prefsListener = listener
        p.registerOnSharedPreferenceChangeListener(listener)
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
            prefsListener?.let { listener ->
                prefs?.unregisterOnSharedPreferenceChangeListener(listener)
            }
            prefsListener = null
            prefs = null
            appContext = null
            settingsSnapshot = SettingsSnapshot()
            settingsSnapshotVersion++
            homeNativeGlassDarkModeActive = false
        }
        init(context.applicationContext ?: context)
    }

    fun shouldOutputDetailedLogs(): Boolean {
        return settingsSnapshot.isDetailedLoggingEnabled
    }

    fun shouldStabilizeHomeChrome(): Boolean {
        return settingsSnapshot.shouldStabilizeHomeChrome()
    }

    fun shouldForceFeedUiOpt(): Boolean {
        return settingsSnapshot.shouldForceFeedUiOpt()
    }

    fun isAutoSignInEnabled(context: Context): Boolean {
        if (prefs == null) init(context)
        return settingsSnapshot.isAutoSignInEnabled
    }

    fun isRestrictedFeaturesUnlocked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false) &&
            !isRestrictedFeatureUnlockBlocked(context)
    }

    fun setRestrictedFeaturesUnlocked(context: Context, unlocked: Boolean) {
        val p = getPrefs(context)
        val finalUnlocked = unlocked && !isRestrictedFeatureUnlockBlocked(context)
        p.edit()
            .putBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, finalUnlocked)
            .apply()
    }

    fun isRestrictedFeatureUnlockBlocked(context: Context): Boolean {
        if (restrictedFeatureUnlockBlockedByRemote) return true
        return getModuleStatePrefs(context).getBoolean(KEY_REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE, false)
    }

    fun shouldShowEnvironmentWarningDialog(context: Context): Boolean {
        if (environmentWarningDialogActive) return true
        return getModuleStatePrefs(context).getBoolean(KEY_REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE, false)
    }

    fun markPostScanEnvironmentWarningPending(context: Context) {
        getModuleStatePrefs(context).edit()
            .putBoolean(KEY_PENDING_POST_SCAN_ENVIRONMENT_WARNING, true)
            .apply()
    }

    fun hasPendingPostScanEnvironmentWarning(context: Context): Boolean {
        return getModuleStatePrefs(context).getBoolean(KEY_PENDING_POST_SCAN_ENVIRONMENT_WARNING, false)
    }

    fun consumePendingPostScanEnvironmentWarning(context: Context): Boolean {
        val statePrefs = getModuleStatePrefs(context)
        if (!statePrefs.getBoolean(KEY_PENDING_POST_SCAN_ENVIRONMENT_WARNING, false)) return false
        statePrefs.edit()
            .putBoolean(KEY_PENDING_POST_SCAN_ENVIRONMENT_WARNING, false)
            .apply()
        return shouldShowEnvironmentWarningDialog(context)
    }

    fun applyRemoteEnvironmentControls(
        context: Context,
        showWarningDialog: Boolean,
        lockHiddenFeatures: Boolean,
    ) {
        val appCtx = context.applicationContext ?: context
        getModuleStatePrefs(appCtx).edit()
            .putBoolean(KEY_REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE, showWarningDialog)
            .putBoolean(KEY_REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE, lockHiddenFeatures)
            .apply()

        val p = getPrefs(appCtx)
        environmentWarningDialogActive = showWarningDialog
        restrictedFeatureUnlockBlockedByRemote = lockHiddenFeatures
        if (lockHiddenFeatures && p.getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false)) {
            p.edit().putBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false).apply()
        }
        refreshUserSettingsSnapshot(p)
    }

    private fun refreshUserSettingsSnapshot(p: SharedPreferences) {
        replaceSettingsSnapshot(buildSettingsSnapshot(p))
    }

    private fun buildSettingsSnapshot(p: SharedPreferences): SettingsSnapshot {
        val restrictedUnlocked =
            p.getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false) && !restrictedFeatureUnlockBlockedByRemote

        fun featureBoolean(key: String, defaultValue: Boolean = false): Boolean {
            return p.getBoolean(key, defaultValue) && isScanFeatureAvailable(key)
        }

        fun restrictedBoolean(key: String): Boolean {
            return restrictedUnlocked && featureBoolean(key)
        }

        fun performanceChildBoolean(
            key: String,
            masterEnabled: Boolean,
            defaultValue: Boolean,
        ): Boolean {
            if (!restrictedUnlocked || !masterEnabled || !isScanFeatureAvailable(key)) return false
            return if (p.contains(key)) {
                p.getBoolean(key, false)
            } else {
                defaultValue
            }
        }

        val performanceOptimizationEnabled = restrictedBoolean(KEY_ENABLE_PERFORMANCE_OPTIMIZATION)
        val homeNativeGlassLightStyle = readHomeNativeGlassStyle(p, HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS)
        val homeNativeGlassDarkStyle = readHomeNativeGlassStyle(p, HOME_NATIVE_GLASS_DARK_STYLE_KEYS)
        val hasHomeNativeGlassStyle = homeNativeGlassLightStyle.hasBackgroundImage() ||
            homeNativeGlassDarkStyle.hasBackgroundImage()
        val homeNativeGlassEnabled = featureBoolean(KEY_ENABLE_HOME_NATIVE_GLASS)
        val bottomTabsCustomEnabled = featureBoolean(KEY_CUSTOM_BOTTOM_TABS)
        val bottomTabSelection = loadBottomTabSelectionFromPrefs(
            p,
            persistNormalized = bottomTabsCustomEnabled,
        )
        val homeFeedUiOptEnabled = featureBoolean(KEY_FORCE_FEED_UI_OPT) ||
            featureBoolean(KEY_DISABLE_MONITOR_SYNC_COMPONENTS)
        val forceFeedUiOptRuntimeEnabled = homeFeedUiOptEnabled ||
            (
                homeNativeGlassEnabled &&
                    hasHomeNativeGlassStyle &&
                    isScanFeatureAvailable(KEY_FORCE_FEED_UI_OPT)
                )

        return SettingsSnapshot(
            areRestrictedFeaturesUnlocked = restrictedUnlocked,
            isAdBlockEnabled = restrictedBoolean(KEY_BLOCK_AD),
            isHomeTopTabsCustomEnabled = featureBoolean(KEY_CUSTOM_HOME_TOP_TABS),
            isHomeTopTabMaterialEnabled = p.getBoolean(KEY_HOME_TOP_TAB_MATERIAL, true),
            isHomeTopTabRecommendEnabled = p.getBoolean(KEY_HOME_TOP_TAB_RECOMMEND, true),
            isHomeTopTabLiveEnabled = p.getBoolean(KEY_HOME_TOP_TAB_LIVE, true),
            isHomeTopTabFollowedEnabled = p.getBoolean(KEY_HOME_TOP_TAB_FOLLOWED, true),
            isHomeTabAutoHideEnabled = featureBoolean(KEY_AUTO_HIDE_HOME_TAB),
            isBottomTabsCustomEnabled = bottomTabsCustomEnabled,
            isBottomTabHomeEnabled = bottomTabSelection.homeEnabled,
            isBottomTabEnterForumEnabled = bottomTabSelection.enterForumEnabled,
            isBottomTabRetailStoreEnabled = bottomTabSelection.retailStoreEnabled,
            isBottomTabMessageEnabled = bottomTabSelection.messageEnabled,
            isBottomTabMineEnabled = bottomTabSelection.mineEnabled,
            isEnterForumWebFilterEnabled = featureBoolean(KEY_FILTER_ENTER_FORUM_WEB),
            isOpenWebLinkInSystemBrowserEnabled = featureBoolean(KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER),
            isHomeNativeGlassEnabled = homeNativeGlassEnabled,
            isHomeTabDynamicTintEnabled = featureBoolean(
                KEY_ENABLE_HOME_TAB_DYNAMIC_TINT,
                DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED,
            ),
            homeNativeGlassLightStyle = homeNativeGlassLightStyle,
            homeNativeGlassDarkStyle = homeNativeGlassDarkStyle,
            isAutoRefreshDisabled = featureBoolean(KEY_DISABLE_AUTO_REFRESH),
            isAutoLoadMoreEnabled = featureBoolean(KEY_ENABLE_AUTO_LOAD_MORE),
            isPbLikeAutoReplyEnabled = restrictedBoolean(KEY_ENABLE_PB_LIKE_AUTO_REPLY),
            pbLikeAutoReplyText = p.getString(KEY_PB_LIKE_AUTO_REPLY_TEXT, "")?.trim().orEmpty(),
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
            isCustomPostFilterEnabled = featureBoolean(KEY_ENABLE_CUSTOM_POST_FILTER),
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
            isMonitorSyncComponentsDisabled = homeFeedUiOptEnabled,
            isPbPerformanceModeEnabled = performanceChildBoolean(
                KEY_ENABLE_PB_PERFORMANCE_MODE,
                performanceOptimizationEnabled,
                true,
            ),
            isFeedUiOptForced = homeFeedUiOptEnabled,
            isForceFeedUiOptRuntimeEnabled = forceFeedUiOptRuntimeEnabled,
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
            isPostVoteFilterEnabled = p.getBoolean(KEY_FILTER_POST_VOTE, false),
            isPostVideoFilterEnabled = p.getBoolean(KEY_FILTER_POST_VIDEO, false),
            isPostReplyFilterEnabled = p.getBoolean(KEY_FILTER_POST_REPLY, false),
            isPostHotFilterEnabled = p.getBoolean(KEY_FILTER_POST_HOT, false),
            isPostGoodsFilterEnabled = p.getBoolean(KEY_FILTER_POST_GOODS, false),
            isPostGameBookingFilterEnabled = p.getBoolean(KEY_FILTER_POST_GAME_BOOKING, false),
            isPostHelpFilterEnabled = p.getBoolean(KEY_FILTER_POST_HELP, false),
            isPostScoreFilterEnabled = p.getBoolean(KEY_FILTER_POST_SCORE, false),
            isPostLiveFilterEnabled = p.getBoolean(KEY_FILTER_POST_LIVE, false),
            isPostRecommendForumFilterEnabled = p.getBoolean(KEY_FILTER_POST_RECOMMEND_FORUM, false),
            isPostUnfollowedForumFilterEnabled = p.getBoolean(KEY_FILTER_POST_UNFOLLOWED_FORUM, false),
            isPostForumKeywordFilterEnabled = p.getBoolean(KEY_FILTER_POST_FORUM_KEYWORD, false),
            postForumKeywordList = parseKeywordList(p.getString(KEY_FILTER_POST_FORUM_KEYWORD_LIST, "")),
            isPostModelScoreFilterEnabled = restrictedBoolean(KEY_FILTER_POST_MODEL_SCORE),
            postModelScoreThresholds = parseModelScoreThresholds(
                p.getString(KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS, "")
            ),
            postModelScoreAutoPercentiles = parseModelScoreAutoPercentiles(
                p.getString(KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES, "")
            ),
            postModelScoreStatsPostLimit = p.getInt(
                KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT,
                DEFAULT_MODEL_SCORE_STATS_POST_LIMIT
            ).coerceAtLeast(MIN_MODEL_SCORE_STATS_POST_LIMIT),
            isDetailedLoggingEnabled = restrictedBoolean(KEY_ENABLE_DETAILED_LOGGING),
        )
    }

    fun readHomeNativeGlassStyle(
        p: SharedPreferences,
        keys: HomeNativeGlassStyleKeys,
    ): HomeNativeGlassStyleConfig {
        val hasModeStyle = keys.all().any { p.contains(it) }
        val allowLegacyFallback = !hasModeStyle &&
            keys.backgroundImagePath == KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH_LIGHT

        fun readString(key: String, legacyKey: String, defaultValue: String): String {
            return when {
                p.contains(key) -> p.getString(key, defaultValue)
                allowLegacyFallback -> p.getString(legacyKey, defaultValue)
                else -> defaultValue
            }?.trim().orEmpty()
        }

        fun readInt(key: String, legacyKey: String, defaultValue: Int): Int {
            return when {
                p.contains(key) -> p.getInt(key, defaultValue)
                allowLegacyFallback -> p.getInt(legacyKey, defaultValue)
                else -> defaultValue
            }
        }

        fun readBoolean(key: String, legacyKey: String, defaultValue: Boolean): Boolean {
            return when {
                p.contains(key) -> p.getBoolean(key, defaultValue)
                allowLegacyFallback -> p.getBoolean(legacyKey, defaultValue)
                else -> defaultValue
            }
        }

        fun readShadowStrengthPercent(): Int {
            return when {
                p.contains(keys.shadowStrengthPercent) -> p.getInt(
                    keys.shadowStrengthPercent,
                    DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                )
                p.contains(keys.legacyShadowEnabled) -> {
                    if (p.getBoolean(keys.legacyShadowEnabled, DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED)) {
                        DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    } else {
                        MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    }
                }
                allowLegacyFallback && p.contains(KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT) -> p.getInt(
                    KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                    DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                )
                allowLegacyFallback && p.contains(KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED) -> {
                    if (p.getBoolean(KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED, DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED)) {
                        DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    } else {
                        MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    }
                }
                else -> DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
            }.coerceIn(
                MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            )
        }

        val tintAlphaPercent = when {
            p.contains(keys.tintAlphaPercent) -> p.getInt(
                keys.tintAlphaPercent,
                DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ).coerceIn(
                MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            )
            allowLegacyFallback -> readHomeNativeGlassTintAlphaPercent(p)
            else -> DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
        }

        return HomeNativeGlassStyleConfig(
            backgroundImagePath = readString(
                keys.backgroundImagePath,
                KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
                DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
            ),
            blurCacheImagePath = readString(
                keys.blurCacheImagePath,
                KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
                DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
            ),
            tintColor = normalizeHomeNativeGlassTintColor(
                readInt(
                    keys.tintColor,
                    KEY_HOME_NATIVE_GLASS_TINT_COLOR,
                    DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR,
                )
            ),
            autoTintColor = normalizeHomeNativeGlassTintColor(
                readInt(
                    keys.autoTintColor,
                    KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
                    DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
                )
            ),
            tintPalette = readString(
                keys.tintPalette,
                KEY_HOME_NATIVE_GLASS_TINT_PALETTE,
                DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE,
            ),
            tintAlphaPercent = tintAlphaPercent,
            cardBlurPercent = readInt(
                keys.cardBlurPercent,
                KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ).coerceIn(
                MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            cardRadiusDp = readInt(
                keys.cardRadiusDp,
                KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ).coerceIn(
                MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ),
            strokeEnabled = readBoolean(
                keys.strokeEnabled,
                KEY_HOME_NATIVE_GLASS_STROKE_ENABLED,
                DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED,
            ),
            shadowStrengthPercent = readShadowStrengthPercent(),
        )
    }

    private fun readHomeNativeGlassTintAlphaPercent(p: SharedPreferences): Int {
        val rawValue = p.getInt(
            KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
        val migrated = p.getBoolean(KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED, false)
        val normalized = if (!migrated && p.contains(KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT)) {
            (rawValue - 50).coerceIn(
                MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            )
        } else {
            rawValue.coerceIn(
                MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            )
        }
        if (!migrated && p.contains(KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT)) {
            runCatching {
                p.edit()
                    .putInt(KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT, normalized)
                    .putBoolean(KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED, true)
                    .apply()
            }
        }
        return normalized
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

    fun applyScanAvailability(
        context: Context,
        featureStatusMap: Map<String, HookFeatureStatus>,
        refreshRuntime: Boolean = false,
    ) {
        if (featureStatusMap.isEmpty()) return
        scanFeatureAvailability = featureStatusMap.mapValues { (_, status) ->
            status.state != HookFeatureState.DISABLED
        }

        if (refreshRuntime) {
            refreshUserSettingsSnapshot(getPrefs(context))
        }
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
        // Keep a valid fallback when external preferences become inconsistent.
        return selection.copy(
            materialEnabled = false,
            recommendEnabled = true,
            liveEnabled = false,
            followedEnabled = false,
        )
    }

    fun resolveHomeTopTabSelection(): HomeTopTabSelection {
        val settings = settingsSnapshot
        val current = HomeTopTabSelection(
            materialEnabled = settings.isHomeTopTabMaterialEnabled,
            recommendEnabled = settings.isHomeTopTabRecommendEnabled,
            liveEnabled = settings.isHomeTopTabLiveEnabled,
            followedEnabled = settings.isHomeTopTabFollowedEnabled,
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
        // Keep a valid fallback when external preferences become inconsistent.
        return selection.copy(
            homeEnabled = true,
            enterForumEnabled = false,
            retailStoreEnabled = false,
            messageEnabled = false,
            mineEnabled = false,
        )
    }

    fun resolveBottomTabSelection(): BottomTabSelection {
        val settings = settingsSnapshot
        val current = BottomTabSelection(
            homeEnabled = settings.isBottomTabHomeEnabled,
            enterForumEnabled = settings.isBottomTabEnterForumEnabled,
            retailStoreEnabled = settings.isBottomTabRetailStoreEnabled,
            messageEnabled = settings.isBottomTabMessageEnabled,
            mineEnabled = settings.isBottomTabMineEnabled,
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
