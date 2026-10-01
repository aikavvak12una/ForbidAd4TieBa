package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.config.AccountPreferences
import com.forbidad4tieba.hook.config.FreeCopyPreferences
import com.forbidad4tieba.hook.config.HomeGlassPreferences
import com.forbidad4tieba.hook.config.ReplyPreferences
import com.forbidad4tieba.hook.config.AdPreferences
import com.forbidad4tieba.hook.config.ExtensionPreferences
import com.forbidad4tieba.hook.config.PerformancePreferences
import com.forbidad4tieba.hook.config.TabPreferences
import com.forbidad4tieba.hook.config.PostFilterPreferences
import com.forbidad4tieba.hook.config.SimpleToggle
import com.forbidad4tieba.hook.config.CommentFilterPreferences

internal data class SettingsMenuGroupActions(
    val onAdBlock: (List<SwitchItem>) -> Unit,
    val onCustomPostFilter: (List<SwitchItem>) -> Unit,
    val onCommentLevelFilter: (List<SwitchItem>) -> Unit,
    val onCustomPostModelScore: () -> Unit,
    val onCustomPostFilterKeyword: () -> Unit,
    val onPbLikeAutoReply: () -> Unit,
    val onFreeCopy: (List<SwitchItem>) -> Unit,
    val onPerformanceOptimization: (List<SettingGroup>) -> Unit,
    val onAutoSignIn: () -> Unit,
    val onReplyVisibilityProbe: () -> Unit,
    val onDetailedLogSave: () -> Unit,
    val onTabCustomization: (List<SwitchItem>) -> Unit,
    val onHomeTopTab: () -> Unit,
    val onHomeNativeGlass: () -> Unit,
    val onBottomTab: () -> Unit,
    val onBottomTabLiquidGlass: () -> Unit,
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
        SwitchItem(AdPreferences.BLOCK_AD_FEED),
        SwitchItem(AdPreferences.BLOCK_AD_POST_PAGE),
        SwitchItem(AdPreferences.BLOCK_AD_FORUM_PAGE),
        SwitchItem(AdPreferences.BLOCK_AD_STRATEGY),
        SwitchItem(AdPreferences.BLOCK_AD_SEARCH_BOX_TEXT),
        SwitchItem(AdPreferences.BLOCK_AD_HOME_TOP_BAR),
        SwitchItem(AdPreferences.BLOCK_AD_MINE_TAB_WEB),
        SwitchItem(AdPreferences.BLOCK_AD_HOME_SIDE_BAR_WEB),
        SwitchItem(AdPreferences.BLOCK_AD_HOME_BOTTOM_EASTER_EGG),
        SwitchItem(ExtensionPreferences.FILTER_ENTER_FORUM_WEB),
    )

    private fun customPostFilterItems(
        restrictedFeaturesUnlocked: Boolean,
        actions: SettingsMenuGroupActions,
    ): List<SwitchItem> {
        val items = mutableListOf(
            SwitchItem(PostFilterPreferences.FILTER_POST_VOTE),
            SwitchItem(PostFilterPreferences.FILTER_POST_VIDEO),
            SwitchItem(PostFilterPreferences.FILTER_POST_LIVE),
            SwitchItem(PostFilterPreferences.FILTER_POST_REPLY),
            SwitchItem(PostFilterPreferences.FILTER_POST_HOT),
            SwitchItem(PostFilterPreferences.FILTER_POST_GOODS),
            SwitchItem(PostFilterPreferences.FILTER_POST_GAME_BOOKING),
            SwitchItem(PostFilterPreferences.FILTER_POST_HELP),
            SwitchItem(PostFilterPreferences.FILTER_POST_SCORE),
            SwitchItem(PostFilterPreferences.FILTER_POST_LOTTERY),
            SwitchItem(PostFilterPreferences.FILTER_POST_RECOMMEND_FORUM),
        )
        if (restrictedFeaturesUnlocked) {
            items.add(
                SwitchItem(
                    PostFilterPreferences.FILTER_POST_MODEL_SCORE,
                    actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
                    onActionClick = actions.onCustomPostModelScore,
                )
            )
        }
        items.add(SwitchItem(PostFilterPreferences.FILTER_POST_UNFOLLOWED_FORUM))
        items.add(
            SwitchItem(
                PostFilterPreferences.FILTER_POST_FORUM_KEYWORD,
                actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
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
        items.add(
            SwitchItem(CommentFilterPreferences.ENABLE, actionIcon = UiText.Settings.ACTION_ICON_SETTINGS) {
                actions.onCommentLevelFilter(listOf(
                    SwitchItem(CommentFilterPreferences.KEEP_WITH_REPLIES),
                    SwitchItem(CommentFilterPreferences.SKIP_NESTED),
                    SwitchItem(CommentFilterPreferences.SHORTCUT),
                ))
            },
        )
        if (restrictedFeaturesUnlocked) {
            items.add(
                SwitchItem(AdPreferences.BLOCK_AD, actionIcon = UiText.Settings.ACTION_ICON_SETTINGS) {
                    actions.onAdBlock(adBlockItems)
                }
            )
        }
        items.add(
            SwitchItem(
                PostFilterPreferences.ENABLE_CUSTOM_POST_FILTER,
                actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
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
        val freeCopyItems = freeCopyItems()
        val items = mutableListOf(
            SwitchItem(ExtensionPreferences.ENABLE_AUTO_LOAD_MORE),
            SwitchItem(
                FreeCopyPreferences.ENABLE_FREE_COPY,
                actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
                onActionClick = { actions.onFreeCopy(freeCopyItems) },
            ),
            SwitchItem(ExtensionPreferences.DISABLE_AUTO_REFRESH),
            SwitchItem(ExtensionPreferences.DISABLE_PB_GESTURE_FONT_SCALE),
            *SimpleToggle.entries.map(::SwitchItem).toTypedArray(),
            SwitchItem(ExtensionPreferences.OPEN_WEB_LINK_IN_SYSTEM_BROWSER),
        )
        if (restrictedFeaturesUnlocked) {
            items.add(
                1,
                SwitchItem(
                    ReplyPreferences.ENABLE_PB_LIKE_AUTO_REPLY,
                    actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
                    onActionClick = actions.onPbLikeAutoReply,
                )
            )
            items.add(
                SwitchItem(ReplyPreferences.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE)
            )
            val performanceGroups = performanceGroups()
            items.add(
                SwitchItem(
                    PerformancePreferences.ENABLE_PERFORMANCE_OPTIMIZATION,
                    actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
                ) {
                    actions.onPerformanceOptimization(performanceGroups)
                }
            )
            items.add(
                SwitchItem(
                    AccountPreferences.ENABLE_AUTO_SIGN_IN,
                    actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
                    actionContentDescription = UiText.AutoSignIn.RESULT_TITLE,
                    onActionClick = actions.onAutoSignIn,
                )
            )
            items.add(
                SwitchItem(AccountPreferences.PRIVATE_READ_RECEIPT_INVISIBLE)
            )
            items.add(
                SwitchItem(
                    ReplyPreferences.VERIFY_REPLY_AFTER_POST,
                    actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
                    onActionClick = actions.onReplyVisibilityProbe,
                )
            )
            items.add(
                SwitchItem(
                    AccountPreferences.ENABLE_DETAILED_LOGGING,
                    actionIcon = UiText.Settings.ACTION_ICON_SAVE,
                    actionContentDescription = UiText.Settings.DETAILED_LOG_SAVE_ACTION_DESC,
                    onActionClick = actions.onDetailedLogSave,
                )
            )
        }
        return items
    }

    private fun freeCopyItems(): List<SwitchItem> = listOf(
        SwitchItem(FreeCopyPreferences.FREE_COPY_POST_BODY),
        SwitchItem(FreeCopyPreferences.FREE_COPY_POST_LONG_PRESS),
        SwitchItem(FreeCopyPreferences.FREE_COPY_COMMENT_INJECTION),
        SwitchItem(FreeCopyPreferences.FREE_COPY_COMMENT_DIALOG),
    )

    private fun performanceGroups(): List<SettingGroup> = listOf(
        SettingGroup(
            UiText.Settings.PERFORMANCE_GROUP_HOST_RUNTIME,
            listOf(
                SwitchItem(PerformancePreferences.FORCE_HOST_PERFORMANCE_FLAGS),
                SwitchItem(PerformancePreferences.DISABLE_HOST_SLIDE_ANIMATION),
                SwitchItem(PerformancePreferences.FORCE_LOW_END_DEVICE_CONFIG),
                SwitchItem(PerformancePreferences.DISABLE_APSARAS_SCHEDULE),
                SwitchItem(PerformancePreferences.ENABLE_PB_PERFORMANCE_MODE),
                SwitchItem(PerformancePreferences.FORCE_PB_PRELOAD),
                SwitchItem(PerformancePreferences.ENABLE_PB_SCROLL_COALESCE),
            ),
        ),
        SettingGroup(
            UiText.Settings.PERFORMANCE_GROUP_STARTUP,
            listOf(
                SwitchItem(PerformancePreferences.DISABLE_AD_SDK_COMPONENTS),
                SwitchItem(PerformancePreferences.FORCE_HOST_FEED_COLD_OPT),
                SwitchItem(PerformancePreferences.DISABLE_FLUTTER_PREINIT),
                SwitchItem(PerformancePreferences.BLOCK_TITAN_PATCH),
            ),
        ),
        SettingGroup(
            UiText.Settings.PERFORMANCE_GROUP_COMPONENT,
            listOf(
                SwitchItem(PerformancePreferences.DISABLE_AI_COMPONENTS),
                SwitchItem(PerformancePreferences.DISABLE_VIDEO_COMPONENTS),
                SwitchItem(PerformancePreferences.DISABLE_MONITOR_SYNC_COMPONENTS),
            ),
        ),
    )

    private fun uiOptimizeItems(actions: SettingsMenuGroupActions): List<SwitchItem> = listOf(
        SwitchItem(
            TabPreferences.ENABLE_TAB_CUSTOMIZATION,
            actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
            actionContentDescription = UiText.Settings.TAB_CUSTOMIZATION_LABEL,
            onActionClick = { actions.onTabCustomization(tabCustomizationItems(actions)) },
        ),
        SwitchItem(TabPreferences.HIDE_HOME_TAB_RED_DOT),
        SwitchItem(ExtensionPreferences.HIDE_INPUT_MEME_BAR),
        SwitchItem(
            HomeGlassPreferences.ENABLE_HOME_NATIVE_GLASS,
            actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
            onActionClick = actions.onHomeNativeGlass,
        ),
    )

    private fun tabCustomizationItems(actions: SettingsMenuGroupActions): List<SwitchItem> = listOf(
        SwitchItem(
            TabPreferences.CUSTOM_HOME_TOP_TABS,
            actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
            actionContentDescription = UiText.Settings.SIMPLIFY_HOME_TAB_LABEL,
            onActionClick = actions.onHomeTopTab,
        ),
        SwitchItem(
            TabPreferences.CUSTOM_BOTTOM_TABS,
            actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
            actionContentDescription = UiText.Settings.SIMPLIFY_BOTTOM_TAB_LABEL,
            onActionClick = actions.onBottomTab,
        ),
        SwitchItem(TabPreferences.AUTO_HIDE_HOME_TAB),
        SwitchItem(
            TabPreferences.BOTTOM_TAB_LIQUID_GLASS,
            actionIcon = UiText.Settings.ACTION_ICON_SETTINGS,
            actionContentDescription = UiText.Settings.BOTTOM_TAB_LIQUID_GLASS_LABEL,
            onActionClick = actions.onBottomTabLiquidGlass,
        ),
    )

}
