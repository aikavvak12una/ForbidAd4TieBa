package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

object FreeCopyPreferences {
    const val KEY_ENABLE_FREE_COPY = "enable_free_copy"

    const val KEY_FREE_COPY_POST_BODY = "free_copy_post_body"

    const val KEY_FREE_COPY_POST_LONG_PRESS = "free_copy_post_long_press"

    const val KEY_FREE_COPY_COMMENT_INJECTION = "free_copy_comment_injection"

    const val KEY_FREE_COPY_COMMENT_DIALOG = "free_copy_comment_dialog"

    data class FreeCopyPostModes(
        val postButtonEnabled: Boolean,
        val longPressEnabled: Boolean,
    )

    fun normalizeFreeCopyPostModes(
        postButtonEnabled: Boolean,
        longPressEnabled: Boolean,
    ): FreeCopyPostModes {
        return if (longPressEnabled) {
            FreeCopyPostModes(postButtonEnabled = false, longPressEnabled = true)
        } else {
            FreeCopyPostModes(postButtonEnabled = postButtonEnabled, longPressEnabled = false)
        }
    }

    internal val ENABLE_FREE_COPY = BooleanPreference(
        KEY_ENABLE_FREE_COPY, true, PreferenceUse.SWITCH, HookFeatureKey.FREE_COPY,
        SwitchPresentation(UiText.Settings.FREE_COPY_LABEL, UiText.Settings.FREE_COPY_DESC),
    )
    internal val FREE_COPY_POST_BODY = BooleanPreference(
        KEY_FREE_COPY_POST_BODY, true, PreferenceUse.SWITCH, HookFeatureKey.FREE_COPY_POST_BODY,
        SwitchPresentation(UiText.Settings.FREE_COPY_POST_BODY_LABEL, UiText.Settings.FREE_COPY_POST_BODY_DESC),
    )
    internal val FREE_COPY_POST_LONG_PRESS = BooleanPreference(
        KEY_FREE_COPY_POST_LONG_PRESS, false, PreferenceUse.SWITCH, HookFeatureKey.FREE_COPY_POST_LONG_PRESS,
        SwitchPresentation(UiText.Settings.FREE_COPY_POST_LONG_PRESS_LABEL, UiText.Settings.FREE_COPY_POST_LONG_PRESS_DESC),
    )
    internal val FREE_COPY_COMMENT_INJECTION = BooleanPreference(
        KEY_FREE_COPY_COMMENT_INJECTION, true, PreferenceUse.SWITCH, HookFeatureKey.FREE_COPY_COMMENT_INJECTION,
        SwitchPresentation(UiText.Settings.FREE_COPY_COMMENT_INJECTION_LABEL, UiText.Settings.FREE_COPY_COMMENT_INJECTION_DESC),
    )
    internal val FREE_COPY_COMMENT_DIALOG = BooleanPreference(
        KEY_FREE_COPY_COMMENT_DIALOG, true, PreferenceUse.SWITCH, HookFeatureKey.FREE_COPY_COMMENT_DIALOG,
        SwitchPresentation(UiText.Settings.FREE_COPY_COMMENT_DIALOG_LABEL, UiText.Settings.FREE_COPY_COMMENT_DIALOG_DESC),
    )

    internal val preferences: List<Preference<*>> = listOf(
        ENABLE_FREE_COPY,
        FREE_COPY_POST_BODY,
        FREE_COPY_POST_LONG_PRESS,
        FREE_COPY_COMMENT_INJECTION,
        FREE_COPY_COMMENT_DIALOG,
    )

}
