package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

object ReplyPreferences {
    const val DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS = 10

    const val MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS = 1

    const val MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS = 30

    const val DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS = 1000

    const val MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS = 500

    const val MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS = 10000

    const val KEY_ENABLE_PB_LIKE_AUTO_REPLY = "enable_pb_like_auto_reply"

    const val KEY_ENABLE_COMMENT_AVATAR_DIRECT_PROFILE = "enable_comment_avatar_direct_profile"

    const val KEY_PB_LIKE_AUTO_REPLY_TEXT = "pb_like_auto_reply_text"

    const val KEY_VERIFY_REPLY_AFTER_POST = "verify_reply_after_post"

    const val KEY_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS = "reply_visibility_probe_max_attempts"

    const val KEY_REPLY_VISIBILITY_PROBE_INTERVAL_MS = "reply_visibility_probe_interval_ms"

    internal val ENABLE_PB_LIKE_AUTO_REPLY = BooleanPreference(
        KEY_ENABLE_PB_LIKE_AUTO_REPLY, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY,
        SwitchPresentation(UiText.Settings.PB_LIKE_AUTO_REPLY_LABEL, UiText.Settings.PB_LIKE_AUTO_REPLY_DESC),
    )
    internal val ENABLE_COMMENT_AVATAR_DIRECT_PROFILE = BooleanPreference(
        KEY_ENABLE_COMMENT_AVATAR_DIRECT_PROFILE, false, PreferenceUse.SWITCH, HookFeatureKey.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE,
        SwitchPresentation(UiText.Settings.COMMENT_AVATAR_DIRECT_PROFILE_LABEL, UiText.Settings.COMMENT_AVATAR_DIRECT_PROFILE_DESC),
    )
    internal val PB_LIKE_AUTO_REPLY_TEXT = StringPreference(
        KEY_PB_LIKE_AUTO_REPLY_TEXT, "", PreferenceUse.FORM,
    )
    internal val VERIFY_REPLY_AFTER_POST = BooleanPreference(
        KEY_VERIFY_REPLY_AFTER_POST, false, PreferenceUse.SWITCH, HookFeatureKey.VERIFY_REPLY_AFTER_POST,
        SwitchPresentation(UiText.Settings.REPLY_VISIBILITY_PROBE_LABEL, UiText.Settings.REPLY_VISIBILITY_PROBE_DESC),
    )
    internal val REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS = IntPreference(
        KEY_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS, DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS, PreferenceUse.FORM,
    )
    internal val REPLY_VISIBILITY_PROBE_INTERVAL_MS = IntPreference(
        KEY_REPLY_VISIBILITY_PROBE_INTERVAL_MS, DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS, PreferenceUse.FORM,
    )

    internal val preferences: List<Preference<*>> = listOf(
        ENABLE_PB_LIKE_AUTO_REPLY,
        ENABLE_COMMENT_AVATAR_DIRECT_PROFILE,
        PB_LIKE_AUTO_REPLY_TEXT,
        VERIFY_REPLY_AFTER_POST,
        REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
        REPLY_VISIBILITY_PROBE_INTERVAL_MS,
    )

}
