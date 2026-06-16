package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.feature.FeatureDescriptors
import com.forbidad4tieba.hook.symbol.model.ForumPageAdSymbolReadiness
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookFeatureSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.toFeatureSymbols
import com.forbidad4tieba.hook.symbol.status.HookFeatureStatusDeriver

internal class HookInstallContext(
    val processName: String,
    val symbols: HookSymbols,
) {
    val isMain: Boolean = HookProcess.isMain(processName)
    val isImageViewerRemote: Boolean = HookProcess.isImageViewerRemote(processName)
    val isImageViewerProcess: Boolean = HookProcess.isImageViewerProcess(processName)

    private val featureSymbols: HookFeatureSymbols = symbols.toFeatureSymbols()
    private val statusMap: Map<String, HookFeatureStatus> = HookFeatureStatusDeriver.deriveWithOverrides(symbols)

    private fun available(featureKey: String): Boolean {
        return statusMap[featureKey]?.isSupported() == true
    }

    fun canInstallFreeCopy(): Boolean {
        return isMain &&
            available(HookFeatureKey.FREE_COPY) &&
            featureSymbols.freeCopy.isComplete()
    }

    fun canInstallImageViewerNativeShare(): Boolean {
        return isImageViewerProcess && featureSymbols.share.isNativeShareComplete()
    }

    fun canInstallDefaultOriginalImage(settings: SettingsSnapshot): Boolean {
        return isImageViewerProcess &&
            settings.isDefaultOriginalImageEnabled &&
            available(FeatureDescriptors.DEFAULT_ORIGINAL_IMAGE.featureKey) &&
            featureSymbols.originalImage.isComplete()
    }

    fun canInstallImageViewerAiJumpButton(settings: SettingsSnapshot): Boolean {
        return isImageViewerRemote &&
            settings.isAiComponentsDisabled &&
            available(HookFeatureKey.DISABLE_AI_COMPONENTS) &&
            featureSymbols.performance.isImageViewerJumpButtonComplete()
    }

    private fun canInstallAdBlockSubFeature(enabled: Boolean, featureKey: String): Boolean {
        return enabled && available(featureKey)
    }

    fun canInstallFeedAdBlock(settings: SettingsSnapshot): Boolean {
        return canInstallAdBlockSubFeature(
            settings.isFeedAdBlockEnabled,
            HookFeatureKey.BLOCK_AD_FEED,
        ) && hasFeedAdPath()
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
            ) &&
            hasForumPageAdBlockPath()
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

    private fun hasForumPageAdBlockPath(): Boolean {
        return ForumPageAdSymbolReadiness.evaluate(symbols).any
    }

    private fun hasFeedAdPath(): Boolean {
        return !symbols.feedTemplateKeyMethod.isNullOrBlank()
    }

    private fun hasPostAdDataPath(): Boolean {
        return !symbols.typeAdapterSetDataMethod.isNullOrBlank() &&
            !symbols.typeAdapterDataItemClass.isNullOrBlank() &&
            !symbols.typeAdapterDataGetTypeMethod.isNullOrBlank()
    }

    private fun hasPbEarlyAdBlockPath(): Boolean {
        return !symbols.pbEarlyAdInsertClass.isNullOrBlank() &&
            !symbols.pbEarlyAdInsertMethodSpecs.isNullOrEmpty()
    }

    private fun hasPbFallingAdBlockPath(): Boolean {
        return !symbols.pbFallingViewClass.isNullOrBlank() &&
            (
                !symbols.pbFallingInitMethod.isNullOrBlank() ||
                    !symbols.pbFallingShowMethod.isNullOrBlank() ||
                    !symbols.pbFallingClearMethod.isNullOrBlank()
                )
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
            available(HookFeatureKey.SIMPLIFY_HOME_TOP_TABS) &&
            featureSymbols.homeTab.isComplete()
    }

    fun canInstallFollowedTabWeb(settings: SettingsSnapshot): Boolean {
        return canInstallHomeTopTabs(settings) && settings.isHomeTopTabFollowedEnabled
    }

    fun canInstallBottomTabs(settings: SettingsSnapshot): Boolean {
        return settings.isBottomTabsCustomEnabled &&
            available(HookFeatureKey.SIMPLIFY_BOTTOM_TABS) &&
            featureSymbols.mainTab.isComplete()
    }

    fun canInstallEnterForumWeb(settings: SettingsSnapshot): Boolean {
        return settings.isEnterForumWebFilterEnabled && available(HookFeatureKey.FILTER_ENTER_FORUM_WEB)
    }

    fun canInstallSystemBrowser(settings: SettingsSnapshot): Boolean {
        return settings.isOpenWebLinkInSystemBrowserEnabled &&
            available(FeatureDescriptors.OPEN_WEB_LINK_IN_SYSTEM_BROWSER.featureKey)
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
        return settings.isAutoRefreshDisabled && available(FeatureDescriptors.DISABLE_AUTO_REFRESH.featureKey)
    }

    fun canInstallAutoLoadMore(settings: SettingsSnapshot): Boolean {
        return settings.isAutoLoadMoreEnabled && available(FeatureDescriptors.AUTO_LOAD_MORE.featureKey)
    }

    fun canInstallPbScrollCoalesce(settings: SettingsSnapshot): Boolean {
        return settings.isPbScrollCoalesceEnabled && available(HookFeatureKey.ENABLE_PB_SCROLL_COALESCE)
    }

    fun canInstallPbGestureFontScale(): Boolean = available(HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE)

    fun canInstallPbLikeAutoReply(settings: SettingsSnapshot): Boolean {
        return settings.isPbLikeAutoReplyEnabled &&
            settings.pbLikeAutoReplyText.isNotBlank() &&
            available(FeatureDescriptors.ENABLE_PB_LIKE_AUTO_REPLY.featureKey)
    }

    fun canInstallMainAiComponents(settings: SettingsSnapshot): Boolean {
        return isMain &&
            settings.isAiComponentsDisabled &&
            available(HookFeatureKey.DISABLE_AI_COMPONENTS) &&
            featureSymbols.performance.isComplete()
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
