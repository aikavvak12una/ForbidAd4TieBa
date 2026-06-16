package com.forbidad4tieba.hook.feature

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.ui.UiText

internal data class FeatureDescriptor(
    val prefKey: String,
    val featureKey: String,
    val label: String,
    val description: String,
    val defaultValue: Boolean = false,
)

internal object FeatureDescriptors {
    val AUTO_LOAD_MORE = FeatureDescriptor(
        prefKey = ConfigManager.KEY_ENABLE_AUTO_LOAD_MORE,
        featureKey = HookFeatureKey.AUTO_LOAD_MORE,
        label = UiText.Settings.AUTO_LOAD_MORE_LABEL,
        description = UiText.Settings.AUTO_LOAD_MORE_DESC,
    )

    val DISABLE_AUTO_REFRESH = FeatureDescriptor(
        prefKey = ConfigManager.KEY_DISABLE_AUTO_REFRESH,
        featureKey = HookFeatureKey.DISABLE_AUTO_REFRESH,
        label = UiText.Settings.DISABLE_AUTO_REFRESH_LABEL,
        description = UiText.Settings.DISABLE_AUTO_REFRESH_DESC,
    )

    val DEFAULT_ORIGINAL_IMAGE = FeatureDescriptor(
        prefKey = ConfigManager.KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE,
        featureKey = HookFeatureKey.DEFAULT_ORIGINAL_IMAGE,
        label = UiText.Settings.DEFAULT_ORIGINAL_IMAGE_LABEL,
        description = UiText.Settings.DEFAULT_ORIGINAL_IMAGE_DESC,
    )

    val OPEN_WEB_LINK_IN_SYSTEM_BROWSER = FeatureDescriptor(
        prefKey = ConfigManager.KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER,
        featureKey = HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER,
        label = UiText.Settings.OPEN_WEB_LINK_IN_SYSTEM_BROWSER_LABEL,
        description = UiText.Settings.OPEN_WEB_LINK_IN_SYSTEM_BROWSER_DESC,
    )

    val ENABLE_PB_LIKE_AUTO_REPLY = FeatureDescriptor(
        prefKey = ConfigManager.KEY_ENABLE_PB_LIKE_AUTO_REPLY,
        featureKey = HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY,
        label = UiText.Settings.PB_LIKE_AUTO_REPLY_LABEL,
        description = UiText.Settings.PB_LIKE_AUTO_REPLY_DESC,
    )

    private val all = listOf(
        AUTO_LOAD_MORE,
        DISABLE_AUTO_REFRESH,
        DEFAULT_ORIGINAL_IMAGE,
        OPEN_WEB_LINK_IN_SYSTEM_BROWSER,
        ENABLE_PB_LIKE_AUTO_REPLY,
    )

    private val byPrefKey = all.associateBy { it.prefKey }

    fun forPrefKey(prefKey: String): FeatureDescriptor? = byPrefKey[prefKey]
}
