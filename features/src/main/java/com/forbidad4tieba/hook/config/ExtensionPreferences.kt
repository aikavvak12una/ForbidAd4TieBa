package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

object ExtensionPreferences {
    const val KEY_HIDE_INPUT_MEME_BAR = "hide_input_meme_bar"

    const val KEY_FILTER_ENTER_FORUM_WEB = "filter_enter_forum_web"

    const val KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER = "open_web_link_in_system_browser"

    const val KEY_DISABLE_AUTO_REFRESH = "disable_auto_refresh"

    const val KEY_DISABLE_PB_GESTURE_FONT_SCALE = "disable_pb_gesture_font_scale"

    const val KEY_ENABLE_AUTO_LOAD_MORE = "enable_auto_load_more"

    const val KEY_DEFAULT_NOTIFY_TAB = "default_notify_tab"

    const val KEY_CLEAN_SHARE_TRACKING_PARAMS = "clean_share_tracking_params"

    internal val HIDE_INPUT_MEME_BAR = BooleanPreference(
        KEY_HIDE_INPUT_MEME_BAR, false, PreferenceUse.SWITCH, HookFeatureKey.HIDE_INPUT_MEME_BAR,
        SwitchPresentation(UiText.Settings.HIDE_INPUT_MEME_BAR_LABEL, UiText.Settings.HIDE_INPUT_MEME_BAR_DESC),
    )
    internal val FILTER_ENTER_FORUM_WEB = BooleanPreference(
        KEY_FILTER_ENTER_FORUM_WEB, false, PreferenceUse.SWITCH, HookFeatureKey.FILTER_ENTER_FORUM_WEB,
        SwitchPresentation(UiText.Settings.FILTER_ENTER_FORUM_WEB_LABEL, UiText.Settings.FILTER_ENTER_FORUM_WEB_DESC),
    )
    internal val OPEN_WEB_LINK_IN_SYSTEM_BROWSER = BooleanPreference(
        KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER, false, PreferenceUse.SWITCH, HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER,
        SwitchPresentation(UiText.Settings.OPEN_WEB_LINK_IN_SYSTEM_BROWSER_LABEL, UiText.Settings.OPEN_WEB_LINK_IN_SYSTEM_BROWSER_DESC),
    )
    internal val DISABLE_AUTO_REFRESH = BooleanPreference(
        KEY_DISABLE_AUTO_REFRESH, false, PreferenceUse.SWITCH, HookFeatureKey.DISABLE_AUTO_REFRESH,
        SwitchPresentation(UiText.Settings.DISABLE_AUTO_REFRESH_LABEL, UiText.Settings.DISABLE_AUTO_REFRESH_DESC),
    )
    internal val DISABLE_PB_GESTURE_FONT_SCALE = BooleanPreference(
        KEY_DISABLE_PB_GESTURE_FONT_SCALE, false, PreferenceUse.SWITCH, HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE,
        SwitchPresentation(UiText.Settings.DISABLE_PB_GESTURE_FONT_SCALE_LABEL, UiText.Settings.DISABLE_PB_GESTURE_FONT_SCALE_DESC),
    )
    internal val ENABLE_AUTO_LOAD_MORE = BooleanPreference(
        KEY_ENABLE_AUTO_LOAD_MORE, false, PreferenceUse.SWITCH, HookFeatureKey.AUTO_LOAD_MORE,
        SwitchPresentation(UiText.Settings.AUTO_LOAD_MORE_LABEL, UiText.Settings.AUTO_LOAD_MORE_DESC),
    )
    internal val DEFAULT_NOTIFY_TAB = BooleanPreference(
        KEY_DEFAULT_NOTIFY_TAB, true, PreferenceUse.AUTOMATIC, HookFeatureKey.DEFAULT_NOTIFY_TAB,
    )
    internal val CLEAN_SHARE_TRACKING_PARAMS = BooleanPreference(
        KEY_CLEAN_SHARE_TRACKING_PARAMS, true, PreferenceUse.AUTOMATIC, HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS,
    )

    internal val preferences: List<Preference<*>> = listOf(
        HIDE_INPUT_MEME_BAR,
        FILTER_ENTER_FORUM_WEB,
        OPEN_WEB_LINK_IN_SYSTEM_BROWSER,
        DISABLE_AUTO_REFRESH,
        DISABLE_PB_GESTURE_FONT_SCALE,
        ENABLE_AUTO_LOAD_MORE,
        DEFAULT_NOTIFY_TAB,
        CLEAN_SHARE_TRACKING_PARAMS,
    )

}
