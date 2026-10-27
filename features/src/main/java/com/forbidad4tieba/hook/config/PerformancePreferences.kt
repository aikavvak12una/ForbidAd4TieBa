package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.ui.UiText

object PerformancePreferences {
    const val KEY_ENABLE_PERFORMANCE_OPTIMIZATION = "enable_performance_optimization"
    // Preserve the saved choice while narrowing the former AB bundle to Cookie deduplication.
    const val KEY_COOKIE_SYNC_DEDUP = "force_host_performance_flags"
    const val KEY_DISABLE_AD_SDK_COMPONENTS = "disable_ad_sdk_components"
    const val KEY_DISABLE_VIDEO_COMPONENTS = "disable_video_components"

    internal val ENABLE_PERFORMANCE_OPTIMIZATION = BooleanPreference(
        KEY_ENABLE_PERFORMANCE_OPTIMIZATION, false, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.GROUP_PERFORMANCE, UiText.Settings.PERFORMANCE_OPTIMIZATION_DESC),
    )
    internal val COOKIE_SYNC_DEDUP = BooleanPreference(
        KEY_COOKIE_SYNC_DEDUP, true, PreferenceUse.SWITCH, HookFeatureKey.COOKIE_SYNC_DEDUP,
        SwitchPresentation(UiText.Settings.COOKIE_SYNC_DEDUP_LABEL, UiText.Settings.COOKIE_SYNC_DEDUP_DESC),
    )
    internal val DISABLE_AD_SDK_COMPONENTS = BooleanPreference(
        KEY_DISABLE_AD_SDK_COMPONENTS, true, PreferenceUse.SWITCH, HookFeatureKey.DISABLE_AD_SDK_COMPONENTS,
        SwitchPresentation(UiText.Settings.DISABLE_AD_SDK_COMPONENTS_LABEL, UiText.Settings.DISABLE_AD_SDK_COMPONENTS_DESC),
    )
    internal val DISABLE_VIDEO_COMPONENTS = BooleanPreference(
        KEY_DISABLE_VIDEO_COMPONENTS, true, PreferenceUse.SWITCH, HookFeatureKey.DISABLE_VIDEO_COMPONENTS,
        SwitchPresentation(UiText.Settings.DISABLE_VIDEO_COMPONENTS_LABEL, UiText.Settings.DISABLE_VIDEO_COMPONENTS_DESC),
    )

    private val retiredKeys = listOf(
        "force_pb_preload",
        "disable_ai_components",
        "disable_monitor_sync_components",
        "enable_pb_performance_mode",
        "enable_pb_scroll_coalesce",
        "disable_host_slide_animation",
        "force_host_feed_cold_opt",
        "disable_apsaras_schedule",
        "disable_flutter_preinit",
        "force_low_end_device_config",
        "block_titan_patch",
    )

    /** Upgrade cleanup at the existing initialization boundary; retired keys have no consumers. */
    internal fun removeRetiredSettings(prefs: SharedPreferences) {
        val present = retiredKeys.filter(prefs::contains)
        if (present.isEmpty()) return
        val editor = prefs.edit()
        present.forEach { editor.remove(it) }
        editor.apply()
    }

    internal val preferences: List<Preference<*>> = listOf(
        ENABLE_PERFORMANCE_OPTIMIZATION,
        COOKIE_SYNC_DEDUP,
        DISABLE_AD_SDK_COMPONENTS,
        DISABLE_VIDEO_COMPONENTS,
    )
}
