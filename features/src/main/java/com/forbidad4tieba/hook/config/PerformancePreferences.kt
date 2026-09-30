package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

object PerformancePreferences {
    const val KEY_DISABLE_AI_COMPONENTS = "disable_ai_components"

    const val KEY_DISABLE_AD_SDK_COMPONENTS = "disable_ad_sdk_components"

    const val KEY_DISABLE_VIDEO_COMPONENTS = "disable_video_components"

    const val KEY_DISABLE_MONITOR_SYNC_COMPONENTS = "disable_monitor_sync_components"

    const val KEY_ENABLE_PB_PERFORMANCE_MODE = "enable_pb_performance_mode"

    const val KEY_ENABLE_PB_SCROLL_COALESCE = "enable_pb_scroll_coalesce"

    const val KEY_ENABLE_PERFORMANCE_OPTIMIZATION = "enable_performance_optimization"

    const val KEY_FORCE_HOST_PERFORMANCE_FLAGS = "force_host_performance_flags"

    const val KEY_FORCE_PB_PRELOAD = "force_pb_preload"

    const val KEY_DISABLE_HOST_SLIDE_ANIMATION = "disable_host_slide_animation"

    const val KEY_FORCE_HOST_FEED_COLD_OPT = "force_host_feed_cold_opt"

    const val KEY_DISABLE_APSARAS_SCHEDULE = "disable_apsaras_schedule"

    const val KEY_DISABLE_FLUTTER_PREINIT = "disable_flutter_preinit"

    const val KEY_FORCE_LOW_END_DEVICE_CONFIG = "force_low_end_device_config"

    const val KEY_BLOCK_TITAN_PATCH = "block_titan_patch"

    internal val DISABLE_AI_COMPONENTS = BooleanPreference(
        KEY_DISABLE_AI_COMPONENTS, true, PreferenceUse.SWITCH, HookFeatureKey.DISABLE_AI_COMPONENTS,
        SwitchPresentation(UiText.Settings.DISABLE_AI_COMPONENTS_LABEL, UiText.Settings.DISABLE_AI_COMPONENTS_DESC),
    )
    internal val DISABLE_AD_SDK_COMPONENTS = BooleanPreference(
        KEY_DISABLE_AD_SDK_COMPONENTS, true, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.DISABLE_AD_SDK_COMPONENTS_LABEL, UiText.Settings.DISABLE_AD_SDK_COMPONENTS_DESC),
    )
    internal val DISABLE_VIDEO_COMPONENTS = BooleanPreference(
        KEY_DISABLE_VIDEO_COMPONENTS, true, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.DISABLE_VIDEO_COMPONENTS_LABEL, UiText.Settings.DISABLE_VIDEO_COMPONENTS_DESC),
    )
    internal val DISABLE_MONITOR_SYNC_COMPONENTS = BooleanPreference(
        KEY_DISABLE_MONITOR_SYNC_COMPONENTS, true, PreferenceUse.SWITCH, HookFeatureKey.DISABLE_MONITOR_SYNC_COMPONENTS,
        SwitchPresentation(UiText.Settings.MONITOR_SYNC_BLOCK_LABEL, UiText.Settings.MONITOR_SYNC_BLOCK_DESC),
    )
    internal val ENABLE_PB_PERFORMANCE_MODE = BooleanPreference(
        KEY_ENABLE_PB_PERFORMANCE_MODE, true, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_PB_PERFORMANCE_MODE,
        SwitchPresentation(UiText.Settings.PB_PERFORMANCE_MODE_LABEL, UiText.Settings.PB_PERFORMANCE_MODE_DESC),
    )
    internal val ENABLE_PB_SCROLL_COALESCE = BooleanPreference(
        KEY_ENABLE_PB_SCROLL_COALESCE, true, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_PB_SCROLL_COALESCE,
        SwitchPresentation(UiText.Settings.PB_SCROLL_COALESCE_LABEL, UiText.Settings.PB_SCROLL_COALESCE_DESC),
    )
    internal val ENABLE_PERFORMANCE_OPTIMIZATION = BooleanPreference(
        KEY_ENABLE_PERFORMANCE_OPTIMIZATION, false, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.GROUP_PERFORMANCE, UiText.Settings.PERFORMANCE_OPTIMIZATION_DESC),
    )
    internal val FORCE_HOST_PERFORMANCE_FLAGS = BooleanPreference(
        KEY_FORCE_HOST_PERFORMANCE_FLAGS, true, PreferenceUse.SWITCH, HookFeatureKey.FORCE_HOST_PERFORMANCE_FLAGS,
        SwitchPresentation(UiText.Settings.FORCE_HOST_PERFORMANCE_FLAGS_LABEL, UiText.Settings.FORCE_HOST_PERFORMANCE_FLAGS_DESC),
    )
    internal val FORCE_PB_PRELOAD = BooleanPreference(
        KEY_FORCE_PB_PRELOAD, true, PreferenceUse.SWITCH, HookFeatureKey.FORCE_PB_PRELOAD,
        SwitchPresentation(UiText.Settings.PB_FORCE_PRELOAD_LABEL, UiText.Settings.PB_FORCE_PRELOAD_DESC),
    )
    internal val DISABLE_HOST_SLIDE_ANIMATION = BooleanPreference(
        KEY_DISABLE_HOST_SLIDE_ANIMATION, true, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.DISABLE_HOST_SLIDE_ANIMATION_LABEL, UiText.Settings.DISABLE_HOST_SLIDE_ANIMATION_DESC),
    )
    internal val FORCE_HOST_FEED_COLD_OPT = BooleanPreference(
        KEY_FORCE_HOST_FEED_COLD_OPT, true, PreferenceUse.SWITCH, HookFeatureKey.FORCE_HOST_FEED_COLD_OPT,
        SwitchPresentation(UiText.Settings.FORCE_HOST_FEED_COLD_OPT_LABEL, UiText.Settings.FORCE_HOST_FEED_COLD_OPT_DESC),
    )
    internal val DISABLE_APSARAS_SCHEDULE = BooleanPreference(
        KEY_DISABLE_APSARAS_SCHEDULE, true, PreferenceUse.SWITCH, HookFeatureKey.DISABLE_APSARAS_SCHEDULE,
        SwitchPresentation(UiText.Settings.DISABLE_APSARAS_SCHEDULE_LABEL, UiText.Settings.DISABLE_APSARAS_SCHEDULE_DESC),
    )
    internal val DISABLE_FLUTTER_PREINIT = BooleanPreference(
        KEY_DISABLE_FLUTTER_PREINIT, true, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.DISABLE_FLUTTER_PREINIT_LABEL, UiText.Settings.DISABLE_FLUTTER_PREINIT_DESC),
    )
    internal val FORCE_LOW_END_DEVICE_CONFIG = BooleanPreference(
        KEY_FORCE_LOW_END_DEVICE_CONFIG, true, PreferenceUse.SWITCH, HookFeatureKey.FORCE_LOW_END_DEVICE_CONFIG,
        SwitchPresentation(UiText.Settings.FORCE_LOW_END_DEVICE_CONFIG_LABEL, UiText.Settings.FORCE_LOW_END_DEVICE_CONFIG_DESC),
    )
    internal val BLOCK_TITAN_PATCH = BooleanPreference(
        KEY_BLOCK_TITAN_PATCH, false, PreferenceUse.SWITCH, null,
        SwitchPresentation(UiText.Settings.BLOCK_TITAN_PATCH_LABEL, UiText.Settings.BLOCK_TITAN_PATCH_DESC),
    )

    internal val preferences: List<Preference<*>> = listOf(
        DISABLE_AI_COMPONENTS,
        DISABLE_AD_SDK_COMPONENTS,
        DISABLE_VIDEO_COMPONENTS,
        DISABLE_MONITOR_SYNC_COMPONENTS,
        ENABLE_PB_PERFORMANCE_MODE,
        ENABLE_PB_SCROLL_COALESCE,
        ENABLE_PERFORMANCE_OPTIMIZATION,
        FORCE_HOST_PERFORMANCE_FLAGS,
        FORCE_PB_PRELOAD,
        DISABLE_HOST_SLIDE_ANIMATION,
        FORCE_HOST_FEED_COLD_OPT,
        DISABLE_APSARAS_SCHEDULE,
        DISABLE_FLUTTER_PREINIT,
        FORCE_LOW_END_DEVICE_CONFIG,
        BLOCK_TITAN_PATCH,
    )

}
