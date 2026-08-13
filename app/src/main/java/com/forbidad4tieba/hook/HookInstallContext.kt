package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.status.HookFeatureStatusDeriver

internal class HookInstallContext(
    val processName: String,
    val symbols: HookSymbols,
) {
    val isMain: Boolean = HookProcess.isMain(processName)
    val isImageViewerRemote: Boolean = HookProcess.isImageViewerRemote(processName)
    val isImageViewerProcess: Boolean = HookProcess.isImageViewerProcess(processName)

    private val statusMap: Map<String, HookFeatureStatus> = HookFeatureStatusDeriver.derive(symbols)

    private fun available(featureKey: String): Boolean {
        return statusMap[featureKey]?.isSupported() == true
    }

    fun canInstallFreeCopyCommentInjection(settings: SettingsSnapshot): Boolean {
        return isMain &&
            settings.isFreeCopyEnabled &&
            settings.isFreeCopyCommentInjectionEnabled &&
            available(HookFeatureKey.FREE_COPY_COMMENT_INJECTION)
    }

    fun canInstallFreeCopyNative(settings: SettingsSnapshot): Boolean {
        if (!isMain || !settings.isFreeCopyEnabled) return false
        return (
            settings.isFreeCopyPostBodyEnabled &&
                available(HookFeatureKey.FREE_COPY_POST_BODY)
            ) ||
            (
                settings.isFreeCopyPostLongPressEnabled &&
                    available(HookFeatureKey.FREE_COPY_POST_LONG_PRESS)
                ) ||
            (
                settings.isFreeCopyCommentDialogEnabled &&
                    available(HookFeatureKey.FREE_COPY_COMMENT_DIALOG)
                )
    }

    fun canInstallImageViewerNativeShare(): Boolean {
        return isImageViewerProcess &&
            symbols.image.viewerShare.isNativeShareReady(symbols.imageViewerShareIconResId)
    }

    fun canInstallDefaultOriginalImage(settings: SettingsSnapshot): Boolean {
        return isImageViewerProcess &&
            settings.isDefaultOriginalImageEnabled &&
            available(HookFeatureKey.DEFAULT_ORIGINAL_IMAGE)
    }

    fun canInstallImageViewerAiJumpButton(settings: SettingsSnapshot): Boolean {
        return isImageViewerRemote &&
            settings.isAiComponentsDisabled &&
            symbols.ai.imageViewerJumpButton.isReady()
    }

    private fun canInstallAdBlockSubFeature(enabled: Boolean, featureKey: String): Boolean {
        return enabled && available(featureKey)
    }

    fun canInstallFeedAdBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isFeedAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_FEED,
        )
    }

    fun canInstallPostAdBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isPostPageAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_POST_PAGE,
        ) && hasPostAdDataPath()
    }

    fun canInstallForumPageAdBlock(settings: SettingsSnapshot): Boolean {
        return isMain &&
            canInstallAdBlockSubFeature(
                settings.isForumPageAdBlockEnabled,
                HookFeatureKey.BLOCK_AD_FORUM_PAGE,
            )
    }

    fun canInstallStrategyAdBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isStrategyAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_STRATEGY,
        )
    }

    fun canInstallPbEarlyAdBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isPostPageAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_POST_PAGE,
        ) && hasPbEarlyAdBlockPath()
    }

    fun canInstallPbFirstFloorRecommendBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isPostPageAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_POST_PAGE,
        ) && hasPbFirstFloorRecommendBlockPath()
    }

    fun canInstallPbAdRequestBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isPostPageAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_POST_PAGE,
        )
    }

    fun canInstallPbFallingAdBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isPostPageAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_POST_PAGE,
        ) && hasPbFallingAdBlockPath()
    }

    fun canInstallSearchBoxTextAdBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isSearchBoxTextAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT,
        )
    }

    fun canInstallHomeTopBarAdBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isHomeTopBarAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_HOME_TOP_BAR,
        )
    }

    private fun hasPostAdDataPath(): Boolean {
        val hasAdapterSetDataMethod =
            !symbols.typeAdapterSetDataMethod.isNullOrBlank() ||
                !symbols.recyclerViewTypeAdapterSetDataMethod.isNullOrBlank()
        return hasAdapterSetDataMethod &&
            !symbols.typeAdapterDataItemClass.isNullOrBlank() &&
            !symbols.typeAdapterDataGetTypeMethod.isNullOrBlank()
    }

    private fun hasPbEarlyAdBlockPath(): Boolean {
        return !symbols.pbEarlyAdInsertClass.isNullOrBlank() &&
            !symbols.pbEarlyAdInsertMethodSpecs.isNullOrEmpty()
    }

    private fun hasPbFirstFloorRecommendBlockPath(): Boolean {
        return !symbols.pbFirstFloorRecommendInsertClass.isNullOrBlank() &&
            !symbols.pbFirstFloorRecommendInsertMethod.isNullOrBlank()
    }

    private fun hasPbFallingAdBlockPath(): Boolean {
        return !symbols.pbFallingViewClass.isNullOrBlank() &&
            (
                !symbols.pbFallingInitMethod.isNullOrBlank() ||
                    !symbols.pbFallingShowMethod.isNullOrBlank() ||
                    !symbols.pbFallingClearMethod.isNullOrBlank()
                )
    }

    fun canInstallPbBottomEnterBarStable(): Boolean {
        return isMain &&
            available(HookFeatureKey.HIDE_PB_BOTTOM_BANNER) &&
            hasPbBottomEnterBarStablePath()
    }

    fun canInstallPbBottomEnterBarHotTopicGuide(): Boolean {
        return isMain &&
            available(HookFeatureKey.HIDE_PB_BOTTOM_BANNER) &&
            !symbols.pbHotTopicGuideTotalViewMethod.isNullOrBlank() &&
            !symbols.pbHotTopicGuideRefreshMethodSpecs.isNullOrEmpty()
    }

    private fun hasPbBottomEnterBarStablePath(): Boolean {
        val hasBottomEnterBarView =
            !symbols.pbBottomEnterBarViewClass.isNullOrBlank() &&
                (symbols.pbBottomEnterBarConstructorCount ?: 0) > 0
        val hasAnimationTip =
            !symbols.pbEnterFrsAnimationTipViewClass.isNullOrBlank() &&
                (symbols.pbEnterFrsAnimationTipConstructorCount ?: 0) > 0
        return hasBottomEnterBarView || hasAnimationTip
    }

    fun canInstallCustomPostFilter(settings: SettingsSnapshot): Boolean {
        return settings.isCustomPostFilterEnabled && available(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER)
    }

    fun canInstallHomeNativeGlass(settings: SettingsSnapshot): Boolean {
        return settings.isHomeNativeGlassEnabled &&
            settings.hasAnyHomeNativeGlassBackgroundImage() &&
            available(HookFeatureKey.HOME_NATIVE_GLASS)
    }

    fun canInstallHomeTopTabs(settings: SettingsSnapshot): Boolean {
        return settings.isHomeTopTabsCustomEnabled &&
            available(HookFeatureKey.SIMPLIFY_HOME_TOP_TABS)
    }

    fun canInstallFollowedTabWeb(settings: SettingsSnapshot): Boolean {
        return canInstallHomeTopTabs(settings) && settings.isHomeTopTabFollowedEnabled
    }

    fun canInstallBottomTabs(settings: SettingsSnapshot): Boolean {
        return settings.isBottomTabsCustomEnabled &&
            available(HookFeatureKey.SIMPLIFY_BOTTOM_TABS)
    }

    fun canInstallEnterForumWeb(settings: SettingsSnapshot): Boolean {
        return settings.isEnterForumWebFilterEnabled && available(HookFeatureKey.FILTER_ENTER_FORUM_WEB)
    }

    fun canInstallSystemBrowser(settings: SettingsSnapshot): Boolean {
        return settings.isOpenWebLinkInSystemBrowserEnabled &&
            available(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER)
    }

    fun canInstallMineTabWebBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isMineTabWebAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_MINE_TAB_WEB,
        )
    }

    fun canInstallHomeSideBarWebBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isHomeSideBarWebAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB,
        )
    }

    fun canInstallForumNativeTopShift(): Boolean = available(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT)

    fun canInstallAutoRefresh(settings: SettingsSnapshot): Boolean {
        return settings.isAutoRefreshDisabled && available(HookFeatureKey.DISABLE_AUTO_REFRESH)
    }

    fun canInstallAutoLoadMore(settings: SettingsSnapshot): Boolean {
        return settings.isAutoLoadMoreEnabled && available(HookFeatureKey.AUTO_LOAD_MORE)
    }

    fun canInstallPbScrollCoalesce(settings: SettingsSnapshot): Boolean {
        return settings.isPbScrollCoalesceEnabled && available(HookFeatureKey.ENABLE_PB_SCROLL_COALESCE)
    }

    fun canInstallPbGestureFontScale(settings: SettingsSnapshot): Boolean {
        return settings.isPbGestureFontScaleDisabled &&
            available(HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE)
    }

    fun canInstallPbLikeAutoReply(settings: SettingsSnapshot): Boolean {
        return settings.isPbLikeAutoReplyEnabled &&
            settings.pbLikeAutoReplyText.isNotBlank() &&
            available(HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY)
    }

    fun canInstallCommentAvatarDirectProfile(settings: SettingsSnapshot): Boolean {
        return isMain &&
            settings.isCommentAvatarDirectProfileEnabled &&
            available(HookFeatureKey.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE)
    }

    fun canInstallInputMemeBarBlock(settings: SettingsSnapshot): Boolean {
        return isMain &&
            settings.isInputMemeBarHidden &&
            available(HookFeatureKey.HIDE_INPUT_MEME_BAR)
    }

    fun canInstallMainAiComponents(settings: SettingsSnapshot): Boolean {
        return isMain &&
            settings.isAiComponentsDisabled &&
            available(HookFeatureKey.DISABLE_AI_COMPONENTS)
    }

    fun canInstallDefaultNotifyTab(settings: SettingsSnapshot): Boolean {
        return settings.isDefaultNotifyTabEnabled && available(HookFeatureKey.DEFAULT_NOTIFY_TAB)
    }

    fun canInstallPrivateReadReceipt(settings: SettingsSnapshot): Boolean {
        return settings.isPrivateReadReceiptInvisibleEnabled &&
            available(HookFeatureKey.PRIVATE_READ_RECEIPT_INVISIBLE)
    }

    fun canInstallCollectionSearch(): Boolean {
        return isMain && symbols.collectionHistory.collection.isSearchComplete()
    }

    fun canInstallHistorySearch(): Boolean {
        return isMain && symbols.collectionHistory.history.isSearchComplete()
    }

    fun canInstallShareTrackingCleaner(settings: SettingsSnapshot): Boolean {
        return settings.isCleanShareTrackingParamsEnabled &&
            available(HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS)
    }

    fun canInstallReplyVisibilityProbe(settings: SettingsSnapshot): Boolean {
        return isMain &&
            settings.isReplyVisibilityProbeEnabled &&
            available(HookFeatureKey.VERIFY_REPLY_AFTER_POST)
    }
}
