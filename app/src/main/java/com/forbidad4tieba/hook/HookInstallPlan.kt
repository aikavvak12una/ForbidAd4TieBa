package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.FeedAdHook
import com.forbidad4tieba.hook.feature.ad.FeedInfoLogHook
import com.forbidad4tieba.hook.feature.ad.PbAdRequestBlockHook
import com.forbidad4tieba.hook.feature.ad.PbEarlyAdBlockHook
import com.forbidad4tieba.hook.feature.ad.PbFallingAdHook
import com.forbidad4tieba.hook.feature.ad.PostAdHook
import com.forbidad4tieba.hook.feature.ad.SearchBoxTextAdHook
import com.forbidad4tieba.hook.feature.ad.StrategyAdHook
import com.forbidad4tieba.hook.feature.diagnostic.AgreeServerResponseLogHook
import com.forbidad4tieba.hook.feature.diagnostic.ReplyServerResponseLogHook
import com.forbidad4tieba.hook.feature.diagnostic.ReplyVisibilityProbeHook
import com.forbidad4tieba.hook.feature.im.PrivateReadReceiptBlockHook
import com.forbidad4tieba.hook.feature.perf.AdSdkInitBlockHook
import com.forbidad4tieba.hook.feature.perf.AiComponentDisableHook
import com.forbidad4tieba.hook.feature.perf.ColdStartOptHook
import com.forbidad4tieba.hook.feature.perf.ForceFeedUiOptHook
import com.forbidad4tieba.hook.feature.perf.HostPerformanceConfigHook
import com.forbidad4tieba.hook.feature.perf.PbPerformanceModeHook
import com.forbidad4tieba.hook.feature.perf.TrackingBlockHook
import com.forbidad4tieba.hook.feature.perf.VideoPreloadBlockHook
import com.forbidad4tieba.hook.feature.share.ImageViewerNativeShareHook
import com.forbidad4tieba.hook.feature.share.ShareTrackingParamCleanerHook
import com.forbidad4tieba.hook.feature.ui.AutoLoadMoreHook
import com.forbidad4tieba.hook.feature.ui.AutoRefreshHook
import com.forbidad4tieba.hook.feature.ui.BottomTabTopLineHook
import com.forbidad4tieba.hook.feature.ui.CollectionSearchHook
import com.forbidad4tieba.hook.feature.ui.DefaultOriginalImageHook
import com.forbidad4tieba.hook.feature.ui.ForumNativeTopShiftBlockHook
import com.forbidad4tieba.hook.feature.ui.FreeCopyHook
import com.forbidad4tieba.hook.feature.ui.HistorySearchHook
import com.forbidad4tieba.hook.feature.ui.HomeBottomTabAutoHideHook
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassHook
import com.forbidad4tieba.hook.feature.ui.HomeSideBarSettingsEntryHook
import com.forbidad4tieba.hook.feature.ui.HomeTabHook
import com.forbidad4tieba.hook.feature.ui.HomeTopBarRightSlotHook
import com.forbidad4tieba.hook.feature.ui.HomeTopTabAutoHideHook
import com.forbidad4tieba.hook.feature.ui.ImageViewerSwipeEnterForumBlockHook
import com.forbidad4tieba.hook.feature.ui.MainTabBottomHook
import com.forbidad4tieba.hook.feature.ui.MsgTabDefaultNotifyHook
import com.forbidad4tieba.hook.feature.ui.PbBottomEnterBarHook
import com.forbidad4tieba.hook.feature.ui.PbCommentAutoLoadHook
import com.forbidad4tieba.hook.feature.ui.PbDisableGestureFontScaleHook
import com.forbidad4tieba.hook.feature.ui.PbLikeAutoReplyHook
import com.forbidad4tieba.hook.feature.ui.PbScrollCoalesceHook
import com.forbidad4tieba.hook.feature.ui.UpgradePopWindowBlockHook
import com.forbidad4tieba.hook.feature.web.EnterForumWebHook
import com.forbidad4tieba.hook.feature.web.FollowedTabWebHook
import com.forbidad4tieba.hook.feature.web.HomeSideBarWebBlockHook
import com.forbidad4tieba.hook.feature.web.MineTabWebBlockHook
import com.forbidad4tieba.hook.feature.web.PlainUrlDirectBrowserHook
import com.forbidad4tieba.hook.ui.SettingsMenuHook

internal data class HookInstallEntry(
    val id: String,
    val install: (ClassLoader) -> Unit,
)

internal data class HookInstallPlan(
    val processName: String,
    val phase: String,
    val entries: List<HookInstallEntry>,
) {
    fun isEmpty(): Boolean = entries.isEmpty()
}

internal object HookInstaller {
    fun install(plan: HookInstallPlan, cl: ClassLoader) {
        if (plan.entries.isEmpty()) {
            XposedCompat.logD("[HookInstallPlan] ${plan.phase}: empty for process=${plan.processName}")
            return
        }
        for (entry in plan.entries) {
            try {
                XposedCompat.logD("[HookInstallPlan] Installing ${entry.id} (${plan.phase})...")
                entry.install(cl)
            } catch (t: Throwable) {
                XposedCompat.log(
                    "[HookInstallPlan] ${entry.id} install FAILED (${plan.phase}): ${t.message}",
                )
                XposedCompat.log(t)
            }
        }
    }
}

internal object HookInstallPlanner {
    private class PlanContext(
        val processName: String,
        val symbols: HookSymbols,
    ) {
        val isMain: Boolean = processName == Constants.TARGET_PACKAGE
        val isImageViewerRemote: Boolean = processName == "${Constants.TARGET_PACKAGE}:remote"
        val isImageViewerProcess: Boolean = isMain || isImageViewerRemote
        private val featureSymbols: HookFeatureSymbols = symbols.toFeatureSymbols()
        private val statusMap: Map<String, HookFeatureStatus> = HookSymbolResolver.featureStatusMap(symbols)

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
                available(HookFeatureKey.DEFAULT_ORIGINAL_IMAGE) &&
                featureSymbols.originalImage.isComplete()
        }

        fun canInstallImageViewerAiJumpButton(settings: SettingsSnapshot): Boolean {
            return isImageViewerRemote &&
                settings.isAiComponentsDisabled &&
                available(HookFeatureKey.DISABLE_AI_COMPONENTS) &&
                featureSymbols.performance.isImageViewerJumpButtonComplete()
        }

        fun canInstallAdBlockHooks(settings: SettingsSnapshot): Boolean {
            return settings.isAdBlockEnabled && available(HookFeatureKey.BLOCK_AD)
        }

        fun canInstallCustomPostFilter(settings: SettingsSnapshot): Boolean {
            return settings.isCustomPostFilterEnabled && available(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER)
        }

        fun canInstallHomeNativeGlass(settings: SettingsSnapshot): Boolean {
            return settings.isHomeNativeGlassEnabled &&
                settings.homeNativeGlassBackgroundImagePath.isNotBlank() &&
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
                available(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER)
        }

        fun canInstallMineTabWebBlock(settings: SettingsSnapshot): Boolean {
            return settings.isAdBlockEnabled && available(HookFeatureKey.BLOCK_AD)
        }

        fun canInstallHomeSideBarWebBlock(settings: SettingsSnapshot): Boolean {
            return settings.isAdBlockEnabled && available(HookFeatureKey.BLOCK_AD)
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

        fun canInstallPbGestureFontScale(): Boolean = available(HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE)

        fun canInstallPbLikeAutoReply(settings: SettingsSnapshot): Boolean {
            return settings.isPbLikeAutoReplyEnabled &&
                settings.pbLikeAutoReplyText.isNotBlank() &&
                available(HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY)
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

    fun shouldHandleProcess(processName: String): Boolean {
        return shouldInstallAttachHook(processName) || !staticPlan(processName).isEmpty()
    }

    fun shouldInstallAttachHook(processName: String): Boolean {
        return processName == Constants.TARGET_PACKAGE || isImageViewerRemoteProcess(processName)
    }

    fun staticPlan(processName: String): HookInstallPlan {
        val entries = ArrayList<HookInstallEntry>()
        val isMain = isMainProcess(processName)
        if (isMain) {
            entries += HookInstallEntry("PbBottomEnterBarHook") { cl -> PbBottomEnterBarHook.hook(cl) }
            entries += HookInstallEntry("UpgradePopWindowBlockHook") { cl -> UpgradePopWindowBlockHook.hook(cl) }
        }
        return HookInstallPlan(processName, "static", entries)
    }

    fun postAttachPlan(
        processName: String,
        symbols: HookSymbols,
        settings: SettingsSnapshot,
    ): HookInstallPlan {
        val entries = ArrayList<HookInstallEntry>()
        val context = PlanContext(processName, symbols)

        if (context.canInstallFreeCopy()) {
            entries += HookInstallEntry("FreeCopyHook") { cl ->
                HookSymbolResolver.resolveFreeCopyPopupSymbols(cl, symbols)?.let { targets ->
                    FreeCopyHook.hook(targets)
                }
            }
        }
        if (context.isMain) {
            val enableSwitchManager =
                settings.isAdBlockEnabled ||
                    settings.isAdSdkComponentsDisabled ||
                    settings.isApsarasScheduleDisabled
            if (enableSwitchManager) {
                entries += HookInstallEntry("StrategyAdHook.static") { cl ->
                    StrategyAdHook.hookStatic(
                        cl = cl,
                        enableAccountData = settings.isAdBlockEnabled,
                        enableSwitchManager = enableSwitchManager,
                    )
                }
            }
        }
        if (context.isMain && settings.isHomeTabAutoHideEnabled) {
            entries += HookInstallEntry("HomeTopTabAutoHideHook") { cl -> HomeTopTabAutoHideHook.hook(cl) }
            entries += HookInstallEntry("HomeBottomTabAutoHideHook") { cl -> HomeBottomTabAutoHideHook.hook(cl) }
        }
        if (context.canInstallHomeNativeGlass(settings)) {
            entries += HookInstallEntry("BottomTabTopLineHook") { cl -> BottomTabTopLineHook.hook(cl) }
        }
        if (context.isMain) {
            entries += performanceEntries(settings)
            if (context.canInstallMineTabWebBlock(settings)) {
                entries += HookInstallEntry("MineTabWebBlockHook") { cl -> MineTabWebBlockHook.hook(cl) }
            }
            if (context.canInstallHomeSideBarWebBlock(settings)) {
                entries += HookInstallEntry("HomeSideBarWebBlockHook") { cl -> HomeSideBarWebBlockHook.hook(cl) }
            }
            if (context.canInstallFollowedTabWeb(settings)) {
                entries += HookInstallEntry("FollowedTabWebHook") { cl -> FollowedTabWebHook.hook(cl) }
            }
        }
        if (context.canInstallImageViewerNativeShare()) {
            entries += HookInstallEntry("ImageViewerNativeShareHook") { cl ->
                HookSymbolResolver.resolveImageViewerNativeShareSymbols(cl, symbols)?.let { targets ->
                    ImageViewerNativeShareHook.hook(targets)
                }
            }
        }
        if (context.canInstallDefaultOriginalImage(settings)) {
            entries += HookInstallEntry("DefaultOriginalImageHook") { cl ->
                HookSymbolResolver.resolveDefaultOriginalImageSymbols(cl, symbols)?.let { targets ->
                    DefaultOriginalImageHook.hook(targets)
                }
            }
        }
        if (context.isImageViewerProcess) {
            entries += HookInstallEntry("ImageViewerSwipeEnterForumBlockHook") { cl ->
                ImageViewerSwipeEnterForumBlockHook.hook(cl)
            }
        }
        return HookInstallPlan(processName, "postAttach", entries)
    }

    fun symbolPlan(
        processName: String,
        symbols: HookSymbols,
        settings: SettingsSnapshot,
    ): HookInstallPlan {
        val context = PlanContext(processName, symbols)
        val entries = ArrayList<HookInstallEntry>()
        if (context.isImageViewerRemote) {
            if (context.canInstallImageViewerAiJumpButton(settings)) {
                entries += HookInstallEntry("AiComponentDisableHook.imageViewerJumpButton") { cl ->
                    HookSymbolResolver.resolveAiImageViewerJumpButtonSymbols(cl, symbols)?.let { targets ->
                        AiComponentDisableHook.hookImageViewerJumpButton(targets)
                    }
                }
            }
            return HookInstallPlan(processName, "symbol", entries)
        }
        if (!context.isMain) {
            return HookInstallPlan(processName, "symbol", emptyList())
        }

        val adBlockHooks = context.canInstallAdBlockHooks(settings)
        val customPostFilterHook = context.canInstallCustomPostFilter(settings)
        val homeNativeGlassHook = context.canInstallHomeNativeGlass(settings)
        val feedListHook = adBlockHooks || customPostFilterHook

        entries += HookInstallEntry("SettingsMenuHook") { cl -> SettingsMenuHook.hook(cl, symbols) }
        entries += HookInstallEntry("HomeSideBarSettingsEntryHook") { cl -> HomeSideBarSettingsEntryHook.hook(cl) }

        if (feedListHook) {
            entries += HookInstallEntry("FeedAdHook") { cl ->
                HookSymbolResolver.resolveFeedAdSymbols(
                    cl = cl,
                    symbols = symbols,
                    includeCustomPostFilter = customPostFilterHook,
                )?.let { targets ->
                    FeedAdHook.hook(targets)
                }
            }
        }
        if (adBlockHooks) {
            entries += HookInstallEntry("PostAdHook") { cl ->
                HookSymbolResolver.resolvePostAdDataFilterSymbols(cl, symbols)?.let { targets ->
                    PostAdHook.hook(targets)
                }
            }
            entries += HookInstallEntry("StrategyAdHook.symbols") { cl ->
                HookSymbolResolver.resolveStrategyAdSymbols(cl, symbols)?.let { targets ->
                    StrategyAdHook.hookWithSymbols(targets)
                }
            }
            entries += HookInstallEntry("PbEarlyAdBlockHook") { cl ->
                HookSymbolResolver.resolvePbEarlyAdBlockSymbols(cl, symbols)?.let { targets ->
                    PbEarlyAdBlockHook.hook(targets)
                }
            }
            entries += HookInstallEntry("PbAdRequestBlockHook") { cl ->
                HookSymbolResolver.resolvePbAdRequestBlockSymbols(cl, symbols)?.let { targets ->
                    PbAdRequestBlockHook.hook(targets)
                }
            }
            entries += HookInstallEntry("PbFallingAdHook") { cl ->
                HookSymbolResolver.resolvePbFallingAdSymbols(cl, symbols)?.let { targets ->
                    PbFallingAdHook.hook(targets)
                }
            }
        }
        if (context.canInstallHomeTopTabs(settings)) {
            val selection = ConfigManager.normalizeHomeTopTabSelection(
                ConfigManager.HomeTopTabSelection(
                    materialEnabled = settings.isHomeTopTabMaterialEnabled,
                    recommendEnabled = settings.isHomeTopTabRecommendEnabled,
                    liveEnabled = settings.isHomeTopTabLiveEnabled,
                    followedEnabled = settings.isHomeTopTabFollowedEnabled,
                ),
            )
            entries += HookInstallEntry("HomeTabHook") { cl ->
                HookSymbolResolver.resolveHomeTabSymbols(cl, symbols)?.let { targets ->
                    HomeTabHook.hook(targets, selection)
                }
            }
        }
        if (context.canInstallBottomTabs(settings)) {
            val selection = ConfigManager.normalizeBottomTabSelection(
                ConfigManager.BottomTabSelection(
                    homeEnabled = settings.isBottomTabHomeEnabled,
                    enterForumEnabled = settings.isBottomTabEnterForumEnabled,
                    retailStoreEnabled = settings.isBottomTabRetailStoreEnabled,
                    messageEnabled = settings.isBottomTabMessageEnabled,
                    mineEnabled = settings.isBottomTabMineEnabled,
                ),
            )
            entries += HookInstallEntry("MainTabBottomHook") { cl ->
                HookSymbolResolver.resolveMainTabBottomSymbols(cl, symbols)?.let { targets ->
                    MainTabBottomHook.hook(targets, selection)
                }
            }
        }
        if (adBlockHooks) {
            entries += HookInstallEntry("SearchBoxTextAdHook") { cl ->
                HookSymbolResolver.resolveSearchBoxTextAdSymbols(cl, symbols)?.let { targets ->
                    SearchBoxTextAdHook.hook(targets)
                }
            }
            entries += HookInstallEntry("HomeTopBarRightSlotHook") { cl ->
                HookSymbolResolver.resolveHomeTopBarRightSlotSymbols(cl, symbols)?.let { targets ->
                    HomeTopBarRightSlotHook.hook(targets)
                }
            }
        }
        if (context.canInstallEnterForumWeb(settings)) {
            entries += HookInstallEntry("EnterForumWebHook") { cl ->
                HookSymbolResolver.resolveEnterForumWebSymbols(cl, symbols)?.let { targets ->
                    EnterForumWebHook.hook(targets)
                }
            }
        }
        if (context.canInstallSystemBrowser(settings)) {
            entries += HookInstallEntry("PlainUrlDirectBrowserHook") { cl ->
                val targets = PlainUrlDirectBrowserHook.RuntimeTargets(
                    spanTargets = HookSymbolResolver.resolvePlainUrlClickableSpanSymbols(cl, symbols),
                    messageTarget = HookSymbolResolver.resolvePlainUrlMessageDispatchSymbols(cl, symbols),
                    mountCardTargets = HookSymbolResolver.resolveMountCardLinkLayoutSymbols(cl, symbols),
                    clickSpanMarkerField = HookSymbolResolver.resolvePlainUrlClickSpanMarkerField(cl),
                    isClickMessageCmd = HookSymbolResolver::isPlainUrlClickMessageCmd,
                    resolveMessageDataSymbols = HookSymbolResolver::resolvePlainUrlMessageDataSymbols,
                )
                PlainUrlDirectBrowserHook.hook(targets)
            }
        }
        if (context.canInstallForumNativeTopShift()) {
            entries += HookInstallEntry("ForumNativeTopShiftBlockHook") { cl ->
                HookSymbolResolver.resolveForumNativeTopShiftSymbols(cl, symbols)?.let { targets ->
                    ForumNativeTopShiftBlockHook.hook(targets)
                }
            }
        }
        if (homeNativeGlassHook) {
            entries += HookInstallEntry("HomeNativeGlassHook") { cl -> HomeNativeGlassHook.hook(cl, symbols) }
        }
        if (context.canInstallAutoRefresh(settings)) {
            entries += HookInstallEntry("AutoRefreshHook") { cl ->
                HookSymbolResolver.resolveAutoRefreshSymbols(cl, symbols)?.let { targets ->
                    AutoRefreshHook.hook(targets)
                }
            }
        }
        if (context.canInstallAutoLoadMore(settings)) {
            entries += HookInstallEntry("AutoLoadMoreHook") { cl ->
                HookSymbolResolver.resolveAutoLoadMoreSymbols(cl, symbols)?.let { targets ->
                    AutoLoadMoreHook.hook(targets)
                }
            }
            entries += HookInstallEntry("PbCommentAutoLoadHook") { cl ->
                HookSymbolResolver.resolvePbCommentAutoLoadSymbols(cl, symbols)?.let { targets ->
                    PbCommentAutoLoadHook.hook(targets)
                }
            }
        }
        if (context.canInstallPbScrollCoalesce(settings)) {
            entries += HookInstallEntry("PbScrollCoalesceHook") { cl ->
                HookSymbolResolver.resolvePbScrollCoalesceSymbols(cl, symbols)?.let { targets ->
                    PbScrollCoalesceHook.hook(targets)
                }
            }
        }
        if (context.canInstallPbGestureFontScale()) {
            entries += HookInstallEntry("PbDisableGestureFontScaleHook") { cl ->
                HookSymbolResolver.resolvePbGestureScaleSymbols(cl, symbols)?.let { targets ->
                    PbDisableGestureFontScaleHook.hook(targets)
                }
            }
        }
        if (context.canInstallPbLikeAutoReply(settings)) {
            entries += HookInstallEntry("PbLikeAutoReplyHook") { cl ->
                HookSymbolResolver.resolvePbLikeAutoReplySymbols(cl, symbols)?.let { targets ->
                    PbLikeAutoReplyHook.hook(targets, settings.pbLikeAutoReplyText)
                }
            }
        }
        if (context.canInstallMainAiComponents(settings)) {
            entries += HookInstallEntry("AiComponentDisableHook") { cl ->
                HookSymbolResolver.resolveAiComponentSymbols(cl, symbols)?.let { targets ->
                    AiComponentDisableHook.hook(targets)
                }
            }
        }
        if (context.canInstallDefaultNotifyTab(settings)) {
            entries += HookInstallEntry("MsgTabDefaultNotifyHook") { cl ->
                HookSymbolResolver.resolveMsgTabDefaultNotifySymbols(cl, symbols)?.let { targets ->
                    MsgTabDefaultNotifyHook.hook(targets)
                }
            }
        }
        if (context.canInstallPrivateReadReceipt(settings)) {
            entries += HookInstallEntry("PrivateReadReceiptBlockHook") { cl ->
                HookSymbolResolver.resolvePrivateReadReceiptSymbols(cl, symbols)?.let { targets ->
                    PrivateReadReceiptBlockHook.hook(targets)
                }
            }
        }

        entries += HookInstallEntry("CollectionSearchHook") { cl ->
            HookSymbolResolver.resolveCollectionSearchSymbols(cl, symbols)?.let { targets ->
                CollectionSearchHook.hook(targets)
            }
        }
        entries += HookInstallEntry("HistorySearchHook") { cl ->
            HookSymbolResolver.resolveHistorySearchSymbols(cl, symbols)?.let { targets ->
                HistorySearchHook.hook(targets)
            }
        }

        if (context.canInstallShareTrackingCleaner(settings)) {
            entries += HookInstallEntry("ShareTrackingParamCleanerHook") { cl ->
                HookSymbolResolver.resolveShareTrackingParamCleanerSymbols(cl, symbols)?.let { targets ->
                    ShareTrackingParamCleanerHook.hook(targets)
                }
            }
        }
        if (context.canInstallReplyVisibilityProbe(settings)) {
            entries += HookInstallEntry("ReplyVisibilityProbeHook") { cl ->
                HookSymbolResolver.resolveReplyVisibilityProbeSymbols(cl, symbols)?.let { targets ->
                    ReplyVisibilityProbeHook.hook(targets)
                }
            }
        }
        if (settings.isDetailedLoggingEnabled) {
            entries += HookInstallEntry("ReplyServerResponseLogHook") { cl ->
                HookSymbolResolver.resolveReplyServerResponseLogSymbols(cl, symbols)?.let { targets ->
                    ReplyServerResponseLogHook.hook(targets)
                }
            }
            entries += HookInstallEntry("AgreeServerResponseLogHook") { cl ->
                HookSymbolResolver.resolveAgreeServerResponseLogSymbols(cl, symbols)?.let { targets ->
                    AgreeServerResponseLogHook.hook(targets)
                }
            }
            entries += HookInstallEntry("FeedInfoLogHook") { cl ->
                HookSymbolResolver.resolveFeedInfoLogSymbols(cl, symbols)?.let { targets ->
                    FeedInfoLogHook.hook(targets)
                }
            }
        }

        return HookInstallPlan(processName, "symbol", entries)
    }

    private fun performanceEntries(settings: SettingsSnapshot): List<HookInstallEntry> {
        val entries = ArrayList<HookInstallEntry>()
        if (settings.isPbPerformanceModeEnabled || settings.isAdBlockEnabled) {
            entries += HookInstallEntry("PbPerformanceModeHook") { cl -> PbPerformanceModeHook.hook(cl) }
        }
        if (settings.shouldForceFeedUiOpt()) {
            entries += HookInstallEntry("ForceFeedUiOptHook") { cl -> ForceFeedUiOptHook.hook(cl) }
        }
        if (settings.isAdSdkComponentsDisabled) {
            entries += HookInstallEntry("AdSdkInitBlockHook") { cl -> AdSdkInitBlockHook.hook(cl) }
        }
        if (settings.isMonitorSyncComponentsDisabled) {
            entries += HookInstallEntry("TrackingBlockHook") { cl -> TrackingBlockHook.hook(cl) }
        }
        if (settings.isVideoComponentsDisabled) {
            entries += HookInstallEntry("VideoPreloadBlockHook") { cl -> VideoPreloadBlockHook.hook(cl) }
        }
        if (
            settings.isHostPerformanceFlagsForced ||
            settings.isFlutterPreinitDisabled ||
            settings.isLowEndDeviceConfigForced ||
            settings.isApsarasScheduleDisabled ||
            settings.isAdSdkComponentsDisabled ||
            settings.isVideoComponentsDisabled
        ) {
            entries += HookInstallEntry("ColdStartOptHook") { cl -> ColdStartOptHook.hook(cl) }
        }
        if (
            settings.isAdSdkComponentsDisabled ||
            settings.isFlutterPreinitDisabled ||
            settings.isLowEndDeviceConfigForced
        ) {
            entries += HookInstallEntry("HostPerformanceConfigHook") { cl -> HostPerformanceConfigHook.hook(cl) }
        }
        return entries
    }

    private fun isMainProcess(processName: String): Boolean {
        return processName == Constants.TARGET_PACKAGE
    }

    fun isImageViewerRemoteProcess(processName: String): Boolean {
        return processName == "${Constants.TARGET_PACKAGE}:remote"
    }
}
