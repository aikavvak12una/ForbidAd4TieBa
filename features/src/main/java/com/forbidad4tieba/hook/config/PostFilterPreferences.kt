package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

object PostFilterPreferences {
    internal fun publishThresholds(value: List<ModelScoreSettings.ModelScoreThreshold>) {
        ConfigManager.updateRuntimeSettings { it.copy(postModelScoreThresholds = value) }
    }

    const val KEY_ENABLE_CUSTOM_POST_FILTER = "enable_custom_post_filter"

    const val KEY_FILTER_POST_VOTE = "filter_post_vote"

    const val KEY_FILTER_POST_VIDEO = "filter_post_video"

    const val KEY_FILTER_POST_REPLY = "filter_post_reply"

    const val KEY_FILTER_POST_HOT = "filter_post_hot"

    const val KEY_FILTER_POST_GOODS = "filter_post_goods"

    const val KEY_FILTER_POST_GAME_BOOKING = "filter_post_game_booking"

    const val KEY_FILTER_POST_HELP = "filter_post_help"

    const val KEY_FILTER_POST_SCORE = "filter_post_score"

    const val KEY_FILTER_POST_LOTTERY = "filter_post_lottery"

    const val KEY_FILTER_POST_LIVE = "filter_post_live"

    const val KEY_FILTER_POST_RECOMMEND_FORUM = "filter_post_recommend_forum"

    const val KEY_FILTER_POST_UNFOLLOWED_FORUM = "filter_post_unfollowed_forum"

    const val KEY_FILTER_POST_FORUM_KEYWORD = "filter_post_forum_keyword"

    const val KEY_FILTER_POST_FORUM_KEYWORD_LIST = "filter_post_forum_keyword_list"

    const val KEY_FILTER_POST_MODEL_SCORE = "filter_post_model_score"

    const val KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS = "filter_post_model_score_thresholds"

    const val KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES = "filter_post_model_score_auto_percentiles"

    const val KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT = "filter_post_model_score_stats_post_limit"

    internal val ENABLE_CUSTOM_POST_FILTER = BooleanPreference(
        KEY_ENABLE_CUSTOM_POST_FILTER, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_LABEL, UiText.Settings.CUSTOM_POST_FILTER_DESC),
    )
    internal val FILTER_POST_VOTE = BooleanPreference(
        KEY_FILTER_POST_VOTE, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_VOTE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_VOTE_DESC),
    )
    internal val FILTER_POST_VIDEO = BooleanPreference(
        KEY_FILTER_POST_VIDEO, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_VIDEO_LABEL, UiText.Settings.CUSTOM_POST_FILTER_VIDEO_DESC),
    )
    internal val FILTER_POST_REPLY = BooleanPreference(
        KEY_FILTER_POST_REPLY, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_REPLY_LABEL, UiText.Settings.CUSTOM_POST_FILTER_REPLY_DESC),
    )
    internal val FILTER_POST_HOT = BooleanPreference(
        KEY_FILTER_POST_HOT, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_HOT_LABEL, UiText.Settings.CUSTOM_POST_FILTER_HOT_DESC),
    )
    internal val FILTER_POST_GOODS = BooleanPreference(
        KEY_FILTER_POST_GOODS, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_GOODS_LABEL, UiText.Settings.CUSTOM_POST_FILTER_GOODS_DESC),
    )
    internal val FILTER_POST_GAME_BOOKING = BooleanPreference(
        KEY_FILTER_POST_GAME_BOOKING, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_GAME_BOOKING_LABEL, UiText.Settings.CUSTOM_POST_FILTER_GAME_BOOKING_DESC),
    )
    internal val FILTER_POST_HELP = BooleanPreference(
        KEY_FILTER_POST_HELP, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_HELP_LABEL, UiText.Settings.CUSTOM_POST_FILTER_HELP_DESC),
    )
    internal val FILTER_POST_SCORE = BooleanPreference(
        KEY_FILTER_POST_SCORE, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_SCORE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_SCORE_DESC),
    )
    internal val FILTER_POST_LOTTERY = BooleanPreference(
        KEY_FILTER_POST_LOTTERY, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_LOTTERY_LABEL, UiText.Settings.CUSTOM_POST_FILTER_LOTTERY_DESC),
    )
    internal val FILTER_POST_LIVE = BooleanPreference(
        KEY_FILTER_POST_LIVE, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_LIVE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_LIVE_DESC),
    )
    internal val FILTER_POST_RECOMMEND_FORUM = BooleanPreference(
        KEY_FILTER_POST_RECOMMEND_FORUM, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_RECOMMEND_FORUM_LABEL, UiText.Settings.CUSTOM_POST_FILTER_RECOMMEND_FORUM_DESC),
    )
    internal val FILTER_POST_UNFOLLOWED_FORUM = BooleanPreference(
        KEY_FILTER_POST_UNFOLLOWED_FORUM, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_UNFOLLOWED_FORUM_LABEL, UiText.Settings.CUSTOM_POST_FILTER_UNFOLLOWED_FORUM_DESC),
    )
    internal val FILTER_POST_FORUM_KEYWORD = BooleanPreference(
        KEY_FILTER_POST_FORUM_KEYWORD, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_FORUM_KEYWORD_LABEL, UiText.Settings.CUSTOM_POST_FILTER_FORUM_KEYWORD_DESC),
    )
    internal val FILTER_POST_FORUM_KEYWORD_LIST = StringPreference(
        KEY_FILTER_POST_FORUM_KEYWORD_LIST, "", PreferenceUse.FORM,
    )
    internal val FILTER_POST_MODEL_SCORE = BooleanPreference(
        KEY_FILTER_POST_MODEL_SCORE, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
        SwitchPresentation(UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_DESC),
    )
    internal val FILTER_POST_MODEL_SCORE_THRESHOLDS = StringPreference(
        KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS, "", PreferenceUse.FORM,
    )
    internal val FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES = StringPreference(
        KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES, "", PreferenceUse.FORM,
    )
    internal val FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT = IntPreference(
        KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT, ModelScoreSettings.DEFAULT_MODEL_SCORE_STATS_POST_LIMIT, PreferenceUse.FORM,
    )

    internal val preferences: List<Preference<*>> = listOf(
        ENABLE_CUSTOM_POST_FILTER,
        FILTER_POST_VOTE,
        FILTER_POST_VIDEO,
        FILTER_POST_REPLY,
        FILTER_POST_HOT,
        FILTER_POST_GOODS,
        FILTER_POST_GAME_BOOKING,
        FILTER_POST_HELP,
        FILTER_POST_SCORE,
        FILTER_POST_LOTTERY,
        FILTER_POST_LIVE,
        FILTER_POST_RECOMMEND_FORUM,
        FILTER_POST_UNFOLLOWED_FORUM,
        FILTER_POST_FORUM_KEYWORD,
        FILTER_POST_FORUM_KEYWORD_LIST,
        FILTER_POST_MODEL_SCORE,
        FILTER_POST_MODEL_SCORE_THRESHOLDS,
        FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES,
        FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT,
    )

    internal fun parseKeywordList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n', ',', '，', ';', '；')
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}
