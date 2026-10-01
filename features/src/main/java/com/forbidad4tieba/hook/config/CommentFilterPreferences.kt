package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.ui.UiText

object CommentFilterPreferences {
    const val KEY_ENABLE = "enable_comment_level_filter"
    const val KEY_MINIMUM_LEVEL = "comment_filter_minimum_level"
    const val KEY_KEEP_WITH_REPLIES = "comment_filter_keep_with_replies"
    const val KEY_SKIP_NESTED = "comment_filter_skip_nested"
    const val KEY_SHORTCUT = "comment_filter_shortcut"
    const val DEFAULT_MINIMUM_LEVEL = 5
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 18

    internal val ENABLE = BooleanPreference(
        KEY_ENABLE, false, PreferenceUse.SWITCH, HookFeatureKey.COMMENT_LEVEL_FILTER,
        SwitchPresentation(UiText.Settings.COMMENT_LEVEL_FILTER_LABEL, UiText.Settings.COMMENT_LEVEL_FILTER_DESC),
    )
    internal val MINIMUM_LEVEL = IntPreference(
        KEY_MINIMUM_LEVEL, DEFAULT_MINIMUM_LEVEL, PreferenceUse.FORM,
    )
    internal val KEEP_WITH_REPLIES = BooleanPreference(
        KEY_KEEP_WITH_REPLIES, false, PreferenceUse.SWITCH, HookFeatureKey.COMMENT_LEVEL_FILTER,
        SwitchPresentation(UiText.Settings.COMMENT_KEEP_REPLIES_LABEL, UiText.Settings.COMMENT_KEEP_REPLIES_DESC),
    )
    internal val SKIP_NESTED = BooleanPreference(
        KEY_SKIP_NESTED, false, PreferenceUse.SWITCH, HookFeatureKey.COMMENT_LEVEL_FILTER,
        SwitchPresentation(UiText.Settings.COMMENT_SKIP_NESTED_LABEL, UiText.Settings.COMMENT_SKIP_NESTED_DESC),
    )
    internal val SHORTCUT = BooleanPreference(
        KEY_SHORTCUT, true, PreferenceUse.SWITCH, HookFeatureKey.COMMENT_SHORTCUT,
        SwitchPresentation(UiText.Settings.COMMENT_SHORTCUT_LABEL, UiText.Settings.COMMENT_SHORTCUT_DESC),
    )
    internal val preferences: List<Preference<*>> = listOf(ENABLE, MINIMUM_LEVEL, KEEP_WITH_REPLIES, SKIP_NESTED, SHORTCUT)
}

data class CommentLevelFilterSettings(
    val enabled: Boolean,
    val minimumLevel: Int,
    val keepWithReplies: Boolean,
    val skipNested: Boolean,
) {
    fun hides(level: Int?, isNested: Boolean, hasReplies: Boolean): Boolean =
        enabled && !(isNested && skipNested) && (level == null || level < minimumLevel) &&
            (isNested || !keepWithReplies || !hasReplies)
}
