package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.feature.FeatureDescriptor
import com.forbidad4tieba.hook.feature.FeatureDescriptors

internal data class SettingsMenuGroupActions(
    val onAdBlock: (List<SwitchItem>) -> Unit,
    val onCustomPostFilter: (List<SwitchItem>) -> Unit,
    val onCustomPostModelScore: () -> Unit,
    val onCustomPostFilterKeyword: () -> Unit,
    val onPbLikeAutoReply: () -> Unit,
    val onPerformanceOptimization: (List<SettingGroup>) -> Unit,
    val onAutoSignIn: () -> Unit,
    val onReplyVisibilityProbe: () -> Unit,
    val onHomeTopTab: () -> Unit,
    val onHomeNativeGlass: () -> Unit,
    val onBottomTab: () -> Unit,
)

internal object SettingsMenuGroupBuilder {
    fun build(
        restrictedFeaturesUnlocked: Boolean,
        actions: SettingsMenuGroupActions,
    ): List<SettingGroup> {
        val adBlockItems = adBlockItems()
        val customPostFilterItems = customPostFilterItems(restrictedFeaturesUnlocked, actions)
        val contentBlockItems = contentBlockItems(restrictedFeaturesUnlocked, actions, adBlockItems, customPostFilterItems)
        val extensionItems = extensionItems(restrictedFeaturesUnlocked, actions)
        return listOf(
            SettingGroup(UiText.Settings.GROUP_CONTENT_BLOCK, contentBlockItems),
            SettingGroup(UiText.Settings.GROUP_UI_OPTIMIZE, uiOptimizeItems(actions)),
            SettingGroup(UiText.Settings.GROUP_EXTENSION, extensionItems),
        )
    }

    private fun adBlockItems(): List<SwitchItem> = listOf(
        SwitchItem(
            UiText.Settings.BLOCK_AD_FEED_LABEL,
            UiText.Settings.BLOCK_AD_FEED_DESC,
            ConfigManager.KEY_BLOCK_AD_FEED,
            true,
            true,
        ),
        SwitchItem(
            UiText.Settings.BLOCK_AD_POST_PAGE_LABEL,
            UiText.Settings.BLOCK_AD_POST_PAGE_DESC,
            ConfigManager.KEY_BLOCK_AD_POST_PAGE,
            true,
            true,
        ),
        SwitchItem(
            UiText.Settings.BLOCK_AD_FORUM_PAGE_LABEL,
            UiText.Settings.BLOCK_AD_FORUM_PAGE_DESC,
            ConfigManager.KEY_BLOCK_AD_FORUM_PAGE,
            true,
            true,
        ),
        SwitchItem(
            UiText.Settings.BLOCK_AD_STRATEGY_LABEL,
            UiText.Settings.BLOCK_AD_STRATEGY_DESC,
            ConfigManager.KEY_BLOCK_AD_STRATEGY,
            true,
            true,
        ),
        SwitchItem(
            UiText.Settings.BLOCK_AD_SEARCH_BOX_TEXT_LABEL,
            UiText.Settings.BLOCK_AD_SEARCH_BOX_TEXT_DESC,
            ConfigManager.KEY_BLOCK_AD_SEARCH_BOX_TEXT,
            true,
            true,
        ),
        SwitchItem(
            UiText.Settings.BLOCK_AD_HOME_TOP_BAR_LABEL,
            UiText.Settings.BLOCK_AD_HOME_TOP_BAR_DESC,
            ConfigManager.KEY_BLOCK_AD_HOME_TOP_BAR,
            true,
            true,
        ),
        SwitchItem(
            UiText.Settings.BLOCK_AD_MINE_TAB_WEB_LABEL,
            UiText.Settings.BLOCK_AD_MINE_TAB_WEB_DESC,
            ConfigManager.KEY_BLOCK_AD_MINE_TAB_WEB,
            true,
            true,
        ),
        SwitchItem(
            UiText.Settings.BLOCK_AD_HOME_SIDE_BAR_WEB_LABEL,
            UiText.Settings.BLOCK_AD_HOME_SIDE_BAR_WEB_DESC,
            ConfigManager.KEY_BLOCK_AD_HOME_SIDE_BAR_WEB,
            true,
            true,
        ),
        SwitchItem(
            UiText.Settings.FILTER_ENTER_FORUM_WEB_LABEL,
            UiText.Settings.FILTER_ENTER_FORUM_WEB_DESC,
            ConfigManager.KEY_FILTER_ENTER_FORUM_WEB,
            true,
            false,
        ),
    )

    private fun customPostFilterItems(
        restrictedFeaturesUnlocked: Boolean,
        actions: SettingsMenuGroupActions,
    ): List<SwitchItem> {
        val items = mutableListOf(
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_VOTE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_VOTE_DESC, ConfigManager.KEY_FILTER_POST_VOTE, true, false),
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_VIDEO_LABEL, UiText.Settings.CUSTOM_POST_FILTER_VIDEO_DESC, ConfigManager.KEY_FILTER_POST_VIDEO, true, false),
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_LIVE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_LIVE_DESC, ConfigManager.KEY_FILTER_POST_LIVE, true, false),
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_REPLY_LABEL, UiText.Settings.CUSTOM_POST_FILTER_REPLY_DESC, ConfigManager.KEY_FILTER_POST_REPLY, true, false),
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_HOT_LABEL, UiText.Settings.CUSTOM_POST_FILTER_HOT_DESC, ConfigManager.KEY_FILTER_POST_HOT, true, false),
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_GOODS_LABEL, UiText.Settings.CUSTOM_POST_FILTER_GOODS_DESC, ConfigManager.KEY_FILTER_POST_GOODS, true, false),
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_GAME_BOOKING_LABEL, UiText.Settings.CUSTOM_POST_FILTER_GAME_BOOKING_DESC, ConfigManager.KEY_FILTER_POST_GAME_BOOKING, true, false),
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_HELP_LABEL, UiText.Settings.CUSTOM_POST_FILTER_HELP_DESC, ConfigManager.KEY_FILTER_POST_HELP, true, false),
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_SCORE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_SCORE_DESC, ConfigManager.KEY_FILTER_POST_SCORE, true, false),
            SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_RECOMMEND_FORUM_LABEL, UiText.Settings.CUSTOM_POST_FILTER_RECOMMEND_FORUM_DESC, ConfigManager.KEY_FILTER_POST_RECOMMEND_FORUM, true, false),
        )
        if (restrictedFeaturesUnlocked) {
            items.add(
                SwitchItem(
                    UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_LABEL,
                    UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_DESC,
                    ConfigManager.KEY_FILTER_POST_MODEL_SCORE,
                    true,
                    false,
                    UiText.Settings.ACTION_ICON_SETTINGS,
                    onActionClick = actions.onCustomPostModelScore,
                )
            )
        }
        items.add(SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_UNFOLLOWED_FORUM_LABEL, UiText.Settings.CUSTOM_POST_FILTER_UNFOLLOWED_FORUM_DESC, ConfigManager.KEY_FILTER_POST_UNFOLLOWED_FORUM, true, false))
        items.add(
            SwitchItem(
                UiText.Settings.CUSTOM_POST_FILTER_FORUM_KEYWORD_LABEL,
                UiText.Settings.CUSTOM_POST_FILTER_FORUM_KEYWORD_DESC,
                ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD,
                true,
                false,
                UiText.Settings.ACTION_ICON_SETTINGS,
                onActionClick = actions.onCustomPostFilterKeyword,
            )
        )
        return items
    }

    private fun contentBlockItems(
        restrictedFeaturesUnlocked: Boolean,
        actions: SettingsMenuGroupActions,
        adBlockItems: List<SwitchItem>,
        customPostFilterItems: List<SwitchItem>,
    ): List<SwitchItem> {
        val items = mutableListOf<SwitchItem>()
        if (restrictedFeaturesUnlocked) {
            items.add(
                SwitchItem(
                    UiText.Settings.BLOCK_AD_LABEL,
                    UiText.Settings.BLOCK_AD_DESC,
                    ConfigManager.KEY_BLOCK_AD,
                    true,
                    false,
                    UiText.Settings.ACTION_ICON_SETTINGS,
                ) {
                    actions.onAdBlock(adBlockItems)
                }
            )
        }
        items.add(
            SwitchItem(
                UiText.Settings.CUSTOM_POST_FILTER_LABEL,
                UiText.Settings.CUSTOM_POST_FILTER_DESC,
                ConfigManager.KEY_ENABLE_CUSTOM_POST_FILTER,
                true,
                false,
                UiText.Settings.ACTION_ICON_SETTINGS,
            ) {
                actions.onCustomPostFilter(customPostFilterItems)
            }
        )
        return items
    }

    private fun extensionItems(
        restrictedFeaturesUnlocked: Boolean,
        actions: SettingsMenuGroupActions,
    ): List<SwitchItem> {
        val items = mutableListOf(
            FeatureDescriptors.AUTO_LOAD_MORE.toSwitchItem(),
            FeatureDescriptors.DISABLE_AUTO_REFRESH.toSwitchItem(),
            FeatureDescriptors.DEFAULT_ORIGINAL_IMAGE.toSwitchItem(),
            FeatureDescriptors.OPEN_WEB_LINK_IN_SYSTEM_BROWSER.toSwitchItem(),
        )
        if (restrictedFeaturesUnlocked) {
            items.add(
                1,
                FeatureDescriptors.ENABLE_PB_LIKE_AUTO_REPLY.toSwitchItem(
                    UiText.Settings.ACTION_ICON_SETTINGS,
                    onActionClick = actions.onPbLikeAutoReply,
                )
            )
            val performanceGroups = performanceGroups()
            items.add(
                SwitchItem(
                    UiText.Settings.GROUP_PERFORMANCE,
                    UiText.Settings.PERFORMANCE_OPTIMIZATION_DESC,
                    ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION,
                    true,
                    false,
                    UiText.Settings.ACTION_ICON_SETTINGS,
                ) {
                    actions.onPerformanceOptimization(performanceGroups)
                }
            )
            items.add(
                SwitchItem(
                    UiText.Settings.AUTO_SIGN_IN_LABEL,
                    UiText.Settings.AUTO_SIGN_IN_DESC,
                    ConfigManager.KEY_ENABLE_AUTO_SIGN_IN,
                    true,
                    false,
                    UiText.Settings.ACTION_ICON_PLAY,
                    onActionClick = actions.onAutoSignIn,
                )
            )
            items.add(
                SwitchItem(
                    UiText.Settings.PRIVATE_READ_RECEIPT_INVISIBLE_LABEL,
                    UiText.Settings.PRIVATE_READ_RECEIPT_INVISIBLE_DESC,
                    ConfigManager.KEY_PRIVATE_READ_RECEIPT_INVISIBLE,
                    true,
                    false,
                )
            )
            items.add(
                SwitchItem(
                    UiText.Settings.REPLY_VISIBILITY_PROBE_LABEL,
                    UiText.Settings.REPLY_VISIBILITY_PROBE_DESC,
                    ConfigManager.KEY_VERIFY_REPLY_AFTER_POST,
                    true,
                    false,
                    UiText.Settings.ACTION_ICON_SETTINGS,
                    onActionClick = actions.onReplyVisibilityProbe,
                )
            )
            items.add(
                SwitchItem(
                    UiText.Settings.DETAILED_LOGGING_LABEL,
                    UiText.Settings.DETAILED_LOGGING_DESC,
                    ConfigManager.KEY_ENABLE_DETAILED_LOGGING,
                    true,
                    false,
                )
            )
        }
        return items
    }

    private fun performanceGroups(): List<SettingGroup> = listOf(
        SettingGroup(
            UiText.Settings.PERFORMANCE_GROUP_HOST_RUNTIME,
            listOf(
                SwitchItem(UiText.Settings.FORCE_HOST_PERFORMANCE_FLAGS_LABEL, UiText.Settings.FORCE_HOST_PERFORMANCE_FLAGS_DESC, ConfigManager.KEY_FORCE_HOST_PERFORMANCE_FLAGS, true, true),
                SwitchItem(UiText.Settings.FORCE_LOW_END_DEVICE_CONFIG_LABEL, UiText.Settings.FORCE_LOW_END_DEVICE_CONFIG_DESC, ConfigManager.KEY_FORCE_LOW_END_DEVICE_CONFIG, true, true),
                SwitchItem(UiText.Settings.DISABLE_APSARAS_SCHEDULE_LABEL, UiText.Settings.DISABLE_APSARAS_SCHEDULE_DESC, ConfigManager.KEY_DISABLE_APSARAS_SCHEDULE, true, true),
                SwitchItem(UiText.Settings.PB_PERFORMANCE_MODE_LABEL, UiText.Settings.PB_PERFORMANCE_MODE_DESC, ConfigManager.KEY_ENABLE_PB_PERFORMANCE_MODE, true, true),
                SwitchItem(UiText.Settings.PB_SCROLL_COALESCE_LABEL, UiText.Settings.PB_SCROLL_COALESCE_DESC, ConfigManager.KEY_ENABLE_PB_SCROLL_COALESCE, true, true),
            ),
        ),
        SettingGroup(
            UiText.Settings.PERFORMANCE_GROUP_STARTUP,
            listOf(
                SwitchItem(UiText.Settings.DISABLE_AD_SDK_COMPONENTS_LABEL, UiText.Settings.DISABLE_AD_SDK_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_AD_SDK_COMPONENTS, true, true),
                SwitchItem(UiText.Settings.DISABLE_FLUTTER_PREINIT_LABEL, UiText.Settings.DISABLE_FLUTTER_PREINIT_DESC, ConfigManager.KEY_DISABLE_FLUTTER_PREINIT, true, true),
                SwitchItem(UiText.Settings.BLOCK_TITAN_PATCH_LABEL, UiText.Settings.BLOCK_TITAN_PATCH_DESC, ConfigManager.KEY_BLOCK_TITAN_PATCH, true, false),
            ),
        ),
        SettingGroup(
            UiText.Settings.PERFORMANCE_GROUP_COMPONENT,
            listOf(
                SwitchItem(UiText.Settings.DISABLE_AI_COMPONENTS_LABEL, UiText.Settings.DISABLE_AI_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_AI_COMPONENTS, true, true),
                SwitchItem(UiText.Settings.DISABLE_VIDEO_COMPONENTS_LABEL, UiText.Settings.DISABLE_VIDEO_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_VIDEO_COMPONENTS, true, true),
            ),
        ),
    )

    private fun uiOptimizeItems(actions: SettingsMenuGroupActions): List<SwitchItem> = listOf(
        SwitchItem(
            UiText.Settings.SIMPLIFY_HOME_TAB_LABEL,
            UiText.Settings.SIMPLIFY_HOME_TAB_DESC,
            ConfigManager.KEY_CUSTOM_HOME_TOP_TABS,
            true,
            false,
            UiText.Settings.ACTION_ICON_SETTINGS,
            onActionClick = actions.onHomeTopTab,
        ),
        SwitchItem(UiText.Settings.AUTO_HIDE_HOME_TAB_LABEL, UiText.Settings.AUTO_HIDE_HOME_TAB_DESC, ConfigManager.KEY_AUTO_HIDE_HOME_TAB, true, false),
        SwitchItem(
            UiText.Settings.HOME_NATIVE_GLASS_LABEL,
            UiText.Settings.HOME_NATIVE_GLASS_DESC,
            ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS,
            true,
            false,
            UiText.Settings.ACTION_ICON_SETTINGS,
            onActionClick = actions.onHomeNativeGlass,
        ),
        SwitchItem(
            UiText.Settings.FORCE_FEED_UI_OPT_LABEL,
            UiText.Settings.FORCE_FEED_UI_OPT_DESC,
            ConfigManager.KEY_FORCE_FEED_UI_OPT,
            true,
            false,
            linkedPrefKeys = listOf(ConfigManager.KEY_DISABLE_MONITOR_SYNC_COMPONENTS),
        ),
        SwitchItem(
            UiText.Settings.SIMPLIFY_BOTTOM_TAB_LABEL,
            UiText.Settings.SIMPLIFY_BOTTOM_TAB_DESC,
            ConfigManager.KEY_CUSTOM_BOTTOM_TABS,
            true,
            false,
            UiText.Settings.ACTION_ICON_SETTINGS,
            onActionClick = actions.onBottomTab,
        ),
    )

    private fun FeatureDescriptor.toSwitchItem(
        actionIcon: String? = null,
        onActionClick: (() -> Unit)? = null,
    ): SwitchItem {
        return SwitchItem(
            label = label,
            description = description,
            prefKey = prefKey,
            supported = true,
            defaultValue = defaultValue,
            actionIcon = actionIcon,
            onActionClick = onActionClick,
        )
    }
}
