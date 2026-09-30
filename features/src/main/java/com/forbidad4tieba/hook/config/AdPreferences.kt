package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

object AdPreferences {
    const val KEY_BLOCK_AD = "block_ad"

    const val KEY_BLOCK_AD_FEED = "block_ad_feed"

    const val KEY_BLOCK_AD_POST_PAGE = "block_ad_post_page"

    const val KEY_BLOCK_AD_FORUM_PAGE = "block_ad_forum_page"

    const val KEY_BLOCK_AD_STRATEGY = "block_ad_strategy"

    const val KEY_BLOCK_AD_SEARCH_BOX_TEXT = "block_ad_search_box_text"

    const val KEY_BLOCK_AD_HOME_TOP_BAR = "block_ad_home_top_bar"

    const val KEY_BLOCK_AD_MINE_TAB_WEB = "block_ad_mine_tab_web"

    const val KEY_BLOCK_AD_HOME_SIDE_BAR_WEB = "block_ad_home_side_bar_web"

    const val KEY_BLOCK_AD_HOME_BOTTOM_EASTER_EGG = "block_ad_home_bottom_easter_egg"

    internal val BLOCK_AD = BooleanPreference(
        KEY_BLOCK_AD, false, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD,
        SwitchPresentation(UiText.Settings.BLOCK_AD_LABEL, UiText.Settings.BLOCK_AD_DESC),
    )
    internal val BLOCK_AD_FEED = BooleanPreference(
        KEY_BLOCK_AD_FEED, true, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD_FEED,
        SwitchPresentation(UiText.Settings.BLOCK_AD_FEED_LABEL, UiText.Settings.BLOCK_AD_FEED_DESC),
    )
    internal val BLOCK_AD_POST_PAGE = BooleanPreference(
        KEY_BLOCK_AD_POST_PAGE, true, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD_POST_PAGE,
        SwitchPresentation(UiText.Settings.BLOCK_AD_POST_PAGE_LABEL, UiText.Settings.BLOCK_AD_POST_PAGE_DESC),
    )
    internal val BLOCK_AD_FORUM_PAGE = BooleanPreference(
        KEY_BLOCK_AD_FORUM_PAGE, true, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD_FORUM_PAGE,
        SwitchPresentation(UiText.Settings.BLOCK_AD_FORUM_PAGE_LABEL, UiText.Settings.BLOCK_AD_FORUM_PAGE_DESC),
    )
    internal val BLOCK_AD_STRATEGY = BooleanPreference(
        KEY_BLOCK_AD_STRATEGY, true, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD_STRATEGY,
        SwitchPresentation(UiText.Settings.BLOCK_AD_STRATEGY_LABEL, UiText.Settings.BLOCK_AD_STRATEGY_DESC),
    )
    internal val BLOCK_AD_SEARCH_BOX_TEXT = BooleanPreference(
        KEY_BLOCK_AD_SEARCH_BOX_TEXT, true, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT,
        SwitchPresentation(UiText.Settings.BLOCK_AD_SEARCH_BOX_TEXT_LABEL, UiText.Settings.BLOCK_AD_SEARCH_BOX_TEXT_DESC),
    )
    internal val BLOCK_AD_HOME_TOP_BAR = BooleanPreference(
        KEY_BLOCK_AD_HOME_TOP_BAR, true, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD_HOME_TOP_BAR,
        SwitchPresentation(UiText.Settings.BLOCK_AD_HOME_TOP_BAR_LABEL, UiText.Settings.BLOCK_AD_HOME_TOP_BAR_DESC),
    )
    internal val BLOCK_AD_MINE_TAB_WEB = BooleanPreference(
        KEY_BLOCK_AD_MINE_TAB_WEB, true, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD_MINE_TAB_WEB,
        SwitchPresentation(UiText.Settings.BLOCK_AD_MINE_TAB_WEB_LABEL, UiText.Settings.BLOCK_AD_MINE_TAB_WEB_DESC),
    )
    internal val BLOCK_AD_HOME_SIDE_BAR_WEB = BooleanPreference(
        KEY_BLOCK_AD_HOME_SIDE_BAR_WEB, true, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB,
        SwitchPresentation(UiText.Settings.BLOCK_AD_HOME_SIDE_BAR_WEB_LABEL, UiText.Settings.BLOCK_AD_HOME_SIDE_BAR_WEB_DESC),
    )
    internal val BLOCK_AD_HOME_BOTTOM_EASTER_EGG = BooleanPreference(
        KEY_BLOCK_AD_HOME_BOTTOM_EASTER_EGG, true, PreferenceUse.SWITCH, HookFeatureKey.BLOCK_AD_HOME_BOTTOM_EASTER_EGG,
        SwitchPresentation(UiText.Settings.BLOCK_AD_HOME_BOTTOM_EASTER_EGG_LABEL, UiText.Settings.BLOCK_AD_HOME_BOTTOM_EASTER_EGG_DESC),
    )

    internal val preferences: List<Preference<*>> = listOf(
        BLOCK_AD,
        BLOCK_AD_FEED,
        BLOCK_AD_POST_PAGE,
        BLOCK_AD_FORUM_PAGE,
        BLOCK_AD_STRATEGY,
        BLOCK_AD_SEARCH_BOX_TEXT,
        BLOCK_AD_HOME_TOP_BAR,
        BLOCK_AD_MINE_TAB_WEB,
        BLOCK_AD_HOME_SIDE_BAR_WEB,
        BLOCK_AD_HOME_BOTTOM_EASTER_EGG,
    )

}
