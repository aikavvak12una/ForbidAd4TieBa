package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import android.content.Context

object AccountPreferences {
    const val KEY_ENABLE_AUTO_SIGN_IN = "enable_auto_sign_in"

    const val KEY_RESTRICTED_FEATURES_UNLOCKED = "restricted_features_unlocked"

    const val KEY_PRIVATE_READ_RECEIPT_INVISIBLE = "private_read_receipt_invisible"

    const val KEY_ENABLE_DETAILED_LOGGING = "enable_detailed_logging"

    fun isAutoSignInEnabled(context: Context): Boolean {
        ConfigManager.init(context)
        return ConfigManager.snapshot().isAutoSignInEnabled
    }

    internal val ENABLE_AUTO_SIGN_IN = BooleanPreference(
        KEY_ENABLE_AUTO_SIGN_IN, false, PreferenceUse.SWITCH, HookFeatureKey.AUTO_SIGN_IN,
        SwitchPresentation(UiText.Settings.AUTO_SIGN_IN_LABEL, UiText.Settings.AUTO_SIGN_IN_DESC),
    )
    internal val RESTRICTED_FEATURES_UNLOCKED = BooleanPreference(
        KEY_RESTRICTED_FEATURES_UNLOCKED, false, PreferenceUse.FORM, null,
    )
    internal val PRIVATE_READ_RECEIPT_INVISIBLE = BooleanPreference(
        KEY_PRIVATE_READ_RECEIPT_INVISIBLE, false, PreferenceUse.SWITCH, HookFeatureKey.PRIVATE_READ_RECEIPT_INVISIBLE,
        SwitchPresentation(UiText.Settings.PRIVATE_READ_RECEIPT_INVISIBLE_LABEL, UiText.Settings.PRIVATE_READ_RECEIPT_INVISIBLE_DESC),
    )
    internal val ENABLE_DETAILED_LOGGING = BooleanPreference(
        KEY_ENABLE_DETAILED_LOGGING, false, PreferenceUse.SWITCH, HookFeatureKey.DETAILED_LOGGING,
        SwitchPresentation(UiText.Settings.DETAILED_LOGGING_LABEL, UiText.Settings.DETAILED_LOGGING_DESC),
    )

    internal val preferences: List<Preference<*>> = listOf(
        ENABLE_AUTO_SIGN_IN,
        RESTRICTED_FEATURES_UNLOCKED,
        PRIVATE_READ_RECEIPT_INVISIBLE,
        ENABLE_DETAILED_LOGGING,
    )

}
