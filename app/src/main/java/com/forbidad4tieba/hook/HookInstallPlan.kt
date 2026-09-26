package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.feature.account.AccountListDedupHook
import com.forbidad4tieba.hook.feature.ad.FeedAdHook
import com.forbidad4tieba.hook.feature.ad.FeedInfoLogHook
import com.forbidad4tieba.hook.feature.ad.ForumPageAdBlockHook
import com.forbidad4tieba.hook.feature.ad.HomeBottomEasterEggAdHook
import com.forbidad4tieba.hook.feature.ad.PbAdRequestBlockHook
import com.forbidad4tieba.hook.feature.ad.PbEarlyAdBlockHook
import com.forbidad4tieba.hook.feature.ad.PbFallingAdHook
import com.forbidad4tieba.hook.feature.ad.PbFirstFloorRecommendBlockHook
import com.forbidad4tieba.hook.feature.ad.PostAdHook
import com.forbidad4tieba.hook.feature.ad.SearchBoxTextAdHook
import com.forbidad4tieba.hook.feature.ad.StrategyAdHook
import com.forbidad4tieba.hook.feature.diagnostic.AgreeServerResponseLogHook
import com.forbidad4tieba.hook.feature.diagnostic.TiebaHostLogHook
import com.forbidad4tieba.hook.feature.diagnostic.ReplyServerResponseLogHook
import com.forbidad4tieba.hook.feature.diagnostic.ReplyVisibilityProbeHook
import com.forbidad4tieba.hook.feature.im.PrivateReadReceiptBlockHook
import com.forbidad4tieba.hook.feature.perf.AdSdkInitBlockHook
import com.forbidad4tieba.hook.feature.perf.AiComponentDisableHook
import com.forbidad4tieba.hook.feature.perf.ColdStartOptHook
import com.forbidad4tieba.hook.feature.perf.HostPerformanceConfigHook
import com.forbidad4tieba.hook.feature.perf.HostSlideAnimationBlockHook
import com.forbidad4tieba.hook.feature.perf.PbForcePreloadHook
import com.forbidad4tieba.hook.feature.perf.PbPerformanceModeHook
import com.forbidad4tieba.hook.feature.perf.TrackingBlockHook
import com.forbidad4tieba.hook.feature.perf.VideoPreloadBlockHook
import com.forbidad4tieba.hook.feature.privacy.CrashReportBlockHook
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
import com.forbidad4tieba.hook.feature.ui.HomeFeedPromptBarBlockHook
import com.forbidad4tieba.hook.feature.ui.FirstLikePopupBlockHook
import com.forbidad4tieba.hook.feature.ui.NotificationGuideBlockHook
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassHook
import com.forbidad4tieba.hook.feature.ui.HomeSideBarSettingsEntryHook
import com.forbidad4tieba.hook.feature.ui.HomeTabHook
import com.forbidad4tieba.hook.feature.ui.HomeTabRedDotBlockHook
import com.forbidad4tieba.hook.feature.ui.HomeTopBarRightSlotHook
import com.forbidad4tieba.hook.feature.ui.HomeTopTabAutoHideHook
import com.forbidad4tieba.hook.feature.ui.ImageViewerSwipeEnterForumBlockHook
import com.forbidad4tieba.hook.feature.ui.InputMemeBarBlockHook
import com.forbidad4tieba.hook.feature.ui.MainTabBottomHook
import com.forbidad4tieba.hook.feature.ui.MsgTabDefaultNotifyHook
import com.forbidad4tieba.hook.feature.ui.PbBottomEnterBarHook
import com.forbidad4tieba.hook.feature.ui.PbCommentAutoLoadHook
import com.forbidad4tieba.hook.feature.ui.PbDisableGestureFontScaleHook
import com.forbidad4tieba.hook.feature.ui.PbLikeAutoReplyHook
import com.forbidad4tieba.hook.feature.ui.CommentAvatarDirectProfileHook
import com.forbidad4tieba.hook.feature.ui.PbScrollCoalesceHook
import com.forbidad4tieba.hook.feature.ui.UpgradePopWindowBlockHook
import com.forbidad4tieba.hook.feature.ui.liquidglass.BottomTabLiquidGlassHook
import com.forbidad4tieba.hook.feature.web.EnterForumWebHook
import com.forbidad4tieba.hook.feature.web.FollowedTabWebHook
import com.forbidad4tieba.hook.feature.web.HelpCenterFooterBlockHook
import com.forbidad4tieba.hook.feature.web.HomeSideBarWebBlockHook
import com.forbidad4tieba.hook.feature.web.MineTabWebBlockHook
import com.forbidad4tieba.hook.feature.web.PlainUrlDirectBrowserHook
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.ui.SettingsMenuHook
import java.lang.reflect.Method

internal data class HookInstallPlan(
    val processName: String,
    val phase: String,
    val entries: List<HookInstallEntry>,
) {
    fun isEmpty(): Boolean = entries.isEmpty()
}

internal object HookInstallPlanner {
    fun shouldHandleProcess(processName: String): Boolean {
        return shouldInstallAttachHook(processName) || !staticPlan(processName).isEmpty()
    }

    fun shouldInstallAttachHook(processName: String): Boolean {
        return HookProcess.isMain(processName) || HookProcess.isImageViewerRemote(processName)
    }

    fun staticPlan(processName: String): HookInstallPlan {
        val entries = ArrayList<HookInstallEntry>()
        val isMain = HookProcess.isMain(processName)
        if (isMain) {
            entries += HookInstallEntry.dispatched("UpgradePopWindowBlockHook") { cl -> UpgradePopWindowBlockHook.hook(cl) }
            entries += HookInstallEntry.dispatched("HomeFeedPromptBarBlockHook") { cl ->
                HomeFeedPromptBarBlockHook.hook(cl)
            }
        }
        return HookInstallPlan(processName, "static", entries)
    }

    fun postAttachPlan(
        processName: String,
        symbols: HookSymbols,
        settings: SettingsSnapshot,
    ): HookInstallPlan {
        val entries = ArrayList<HookInstallEntry>()
        val context = HookInstallContext(processName, symbols)

        entries += HookInstallEntry.dispatched("CrashReportBlockHook") { cl -> CrashReportBlockHook.hook(cl) }

        if (context.canInstallFreeCopyCommentInjection(settings)) {
            entries += HookInstallEntry.dispatched("FreeCopyHook.CommentInjection") { cl ->
                HookSymbolResolver.resolveFreeCopyPopupSymbols(cl, symbols)?.let { targets ->
                    FreeCopyHook.hookCommentInjection(targets)
                }
            }
        }
        if (context.canInstallFreeCopyNative(settings)) {
            entries += HookInstallEntry.dispatched("FreeCopyHook.Native") { cl ->
                HookSymbolResolver.resolveFreeCopyNativeSymbols(cl, symbols)?.let { targets ->
                    FreeCopyHook.hookNative(targets)
                }
            }
        }
        if (context.isMain) {
            entries += HookInstallEntry.dispatched("FirstLikePopupBlockHook") { cl ->
                HookSymbolResolver.resolveFirstLikePopupSymbols(cl, symbols)?.let(FirstLikePopupBlockHook::hook)
            }
            entries += HookInstallEntry.dispatched("NotificationGuideBlockHook") { cl ->
                HookSymbolResolver.resolveNotificationGuideSymbols(cl, symbols)?.let(NotificationGuideBlockHook::hook)
            }
            if (settings.isDetailedLoggingEnabled) {
                entries += HookInstallEntry("TiebaHostLogHook") { cl ->
                    TiebaHostLogHook.hook(cl)
                }
            }
            val enableSwitchManager =
                settings.isStrategyAdBlockEnabled ||
                    settings.isAdSdkComponentsDisabled ||
                    settings.isApsarasScheduleDisabled
            if (enableSwitchManager) {
                entries += HookInstallEntry.dispatched("StrategyAdHook.static") { cl ->
                    StrategyAdHook.hookStatic(
                        cl = cl,
                        enableAccountData = settings.isStrategyAdBlockEnabled,
                        enableSwitchManager = enableSwitchManager,
                    )
                }
            }
        }
        if (context.isMain && settings.isHomeTabAutoHideEnabled) {
            entries += HookInstallEntry.dispatched("HomeTopTabAutoHideHook") { cl -> HomeTopTabAutoHideHook.hook(cl) }
            entries += HookInstallEntry.dispatched("HomeBottomTabAutoHideHook") { cl -> HomeBottomTabAutoHideHook.hook(cl) }
        }
        if (context.isMain && settings.isHomeTabRedDotHidden) {
            entries += HookInstallEntry.dispatched("HomeTabRedDotBlockHook") { cl -> HomeTabRedDotBlockHook.hook(cl) }
        }
        if (context.isMain && settings.isBottomTabLiquidGlassEnabled) {
            entries += HookInstallEntry.dispatched("BottomTabLiquidGlassHook") { cl ->
                BottomTabLiquidGlassHook.hook(cl)
            }
        }
        // The liquid glass pill owns the bottom bar chrome; the line/shadow cleanup hook would
        // fight it over the same backgrounds.
        if (context.canInstallHomeNativeGlass(settings) && !settings.isBottomTabLiquidGlassEnabled) {
            entries += HookInstallEntry.dispatched("BottomTabTopLineHook") { cl -> BottomTabTopLineHook.hook(cl) }
        }
        if (context.isMain) {
            entries += performanceEntries(settings, symbols)
            entries += HookInstallEntry.dispatched("HelpCenterFooterBlockHook") { cl -> HelpCenterFooterBlockHook.hook(cl) }
            entries += HookInstallEntry.dispatched("AccountListDedupHook") { cl -> AccountListDedupHook.hook(cl) }
            if (context.canInstallMineTabWebBlock(settings)) {
                entries += HookInstallEntry.dispatched("MineTabWebBlockHook") { cl -> MineTabWebBlockHook.hook(cl, symbols) }
            }
            if (context.canInstallHomeSideBarWebBlock(settings)) {
                entries += HookInstallEntry.dispatched("HomeSideBarWebBlockHook") { cl ->
                    HomeSideBarWebBlockHook.hook(cl, symbols)
                }
            }
            if (context.canInstallFollowedTabWeb(settings)) {
                entries += HookInstallEntry.dispatched("FollowedTabWebHook") { cl -> FollowedTabWebHook.hook(cl) }
            }
        }
        if (context.canInstallImageViewerNativeShare()) {
            entries += HookInstallEntry.dispatched("ImageViewerNativeShareHook") { cl ->
                HookSymbolResolver.resolveImageViewerNativeShareSymbols(cl, symbols)?.let { targets ->
                    ImageViewerNativeShareHook.hook(targets)
                }
            }
        }
        if (context.canInstallDefaultOriginalImage(settings)) {
            entries += HookInstallEntry.dispatched("DefaultOriginalImageHook") { cl ->
                HookSymbolResolver.resolveDefaultOriginalImageSymbols(cl, symbols)?.let { targets ->
                    DefaultOriginalImageHook.hook(targets)
                }
            }
        }
        if (context.isImageViewerProcess) {
            entries += HookInstallEntry.dispatched("ImageViewerSwipeEnterForumBlockHook") { cl ->
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
        val context = HookInstallContext(processName, symbols)
        val entries = ArrayList<HookInstallEntry>()
        if (context.isImageViewerRemote) {
            if (context.canInstallImageViewerAiJumpButton(settings)) {
                entries += HookInstallEntry.dispatched("AiComponentDisableHook.imageViewerJumpButton") { cl ->
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

        val feedListAdBlockHook = context.canInstallFeedListAdBlock(settings)
        val postAdBlockHook = context.canInstallPostAdBlock(settings)
        val forumPageAdBlockHook = context.canInstallForumPageAdBlock(settings)
        val strategyAdBlockHook = context.canInstallStrategyAdBlock(settings)
        val homeBottomEasterEggAdBlockHook = context.canInstallHomeBottomEasterEggAdBlock(settings)
        val pbEarlyAdBlockHook = context.canInstallPbEarlyAdBlock(settings)
        val pbFirstFloorRecommendBlockHook =
            context.canInstallPbFirstFloorRecommendBlock(settings)
        val pbAdRequestBlockHook = context.canInstallPbAdRequestBlock(settings)
        val pbFallingAdBlockHook = context.canInstallPbFallingAdBlock(settings)
        val searchBoxTextAdBlockHook = context.canInstallSearchBoxTextAdBlock(settings)
        val homeTopBarAdBlockHook = context.canInstallHomeTopBarAdBlock(settings)
        val customPostFilterHook = context.canInstallCustomPostFilter(settings)
        val homeNativeGlassHook = context.canInstallHomeNativeGlass(settings)
        val feedListHook = feedListAdBlockHook || customPostFilterHook

        entries += HookInstallEntry.dispatched("SettingsMenuHook") { cl -> SettingsMenuHook.hook(cl, symbols) }
        entries += HookInstallEntry.dispatched("HomeSideBarSettingsEntryHook") { cl -> HomeSideBarSettingsEntryHook.hook(cl) }

        if (context.canInstallPbBottomEnterBarStable()) {
            entries += HookInstallEntry.dispatched("PbBottomEnterBarHook.Stable") { cl ->
                HookSymbolResolver.resolvePbBottomEnterBarStableSymbols(cl, symbols)?.let { targets ->
                    PbBottomEnterBarHook.hookStable(targets)
                }
            }
        }

        if (context.canInstallPbBottomEnterBarHotTopicGuide()) {
            entries += HookInstallEntry.dispatched("PbBottomEnterBarHook.HotTopicGuide") { cl ->
                HookSymbolResolver.resolvePbBottomEnterBarHotTopicGuideSymbols(cl, symbols)?.let { targets ->
                    PbBottomEnterBarHook.hookHotTopicGuide(targets)
                }
            }
        }

        if (feedListHook) {
            entries += HookInstallEntry("FeedAdHook") { cl ->
                HookSymbolResolver.resolveFeedAdSymbols(
                    cl = cl,
                    symbols = symbols,
                    includeCustomPostFilter = customPostFilterHook,
                )?.let { targets ->
                    FeedAdHook.hook(targets)
                } ?: InstallOutcome.skipped("feed list targets unavailable")
            }
        }
        if (postAdBlockHook) {
            entries += HookInstallEntry.dispatched("PostAdHook") { cl ->
                HookSymbolResolver.resolvePostAdDataFilterSymbols(cl, symbols)?.let { targets ->
                    PostAdHook.hook(targets)
                }
            }
        }
        if (forumPageAdBlockHook) {
            entries += HookInstallEntry("ForumPageAdBlockHook") { cl ->
                HookSymbolResolver.resolveForumPageAdBlockSymbols(cl, symbols)?.let(ForumPageAdBlockHook::hook)
                    ?: InstallOutcome.skipped("resolved targets unavailable")
            }
        }
        if (strategyAdBlockHook) {
            entries += HookInstallEntry.dispatched("StrategyAdHook.symbols") { cl ->
                HookSymbolResolver.resolveStrategyAdSymbols(cl, symbols)?.let { targets ->
                    StrategyAdHook.hookWithSymbols(targets)
                }
            }
        }
        if (homeBottomEasterEggAdBlockHook) {
            entries += HookInstallEntry.dispatched("HomeBottomEasterEggAdHook") { cl ->
                HookSymbolResolver.resolveHomeBottomEasterEggAdSymbols(cl, symbols)?.let { targets ->
                    HomeBottomEasterEggAdHook.hook(targets)
                }
            }
        }
        if (pbEarlyAdBlockHook) {
            entries += HookInstallEntry.dispatched("PbEarlyAdBlockHook") { cl ->
                HookSymbolResolver.resolvePbEarlyAdBlockSymbols(cl, symbols)?.let { targets ->
                    PbEarlyAdBlockHook.hook(targets)
                }
            }
        }
        if (pbFirstFloorRecommendBlockHook) {
            entries += HookInstallEntry.dispatched("PbFirstFloorRecommendBlockHook") { cl ->
                HookSymbolResolver.resolvePbFirstFloorRecommendInsertSymbols(cl, symbols)
                    ?.let { targets ->
                        PbFirstFloorRecommendBlockHook.hook(targets)
                    }
            }
        }
        if (pbAdRequestBlockHook) {
            entries += HookInstallEntry("PbAdRequestBlockHook") { cl ->
                HookSymbolResolver.resolvePbAdRequestBlockSymbols(cl, symbols)?.let { targets ->
                    PbAdRequestBlockHook.hook(targets)
                } ?: InstallOutcome.skipped("symbols unavailable")
            }
        }
        if (pbFallingAdBlockHook) {
            entries += HookInstallEntry.dispatched("PbFallingAdHook") { cl ->
                HookSymbolResolver.resolvePbFallingAdSymbols(cl, symbols)?.let { targets ->
                    PbFallingAdHook.hook(targets)
                }
            }
        }
        if (context.canInstallHomeTopTabs(settings)) {
            entries += HookInstallEntry.dispatched("HomeTabHook") { cl ->
                HookSymbolResolver.resolveHomeTabSymbols(cl, symbols)?.let { targets ->
                    HomeTabHook.hook(targets)
                }
            }
        }
        if (context.canInstallBottomTabs(settings)) {
            entries += HookInstallEntry.dispatched("MainTabBottomHook") { cl ->
                HookSymbolResolver.resolveMainTabBottomSymbols(cl, symbols)?.let { targets ->
                    MainTabBottomHook.hook(targets)
                }
            }
        }
        if (searchBoxTextAdBlockHook) {
            entries += HookInstallEntry.dispatched("SearchBoxTextAdHook") { cl ->
                HookSymbolResolver.resolveSearchBoxTextAdSymbols(cl, symbols)?.let { targets ->
                    SearchBoxTextAdHook.hook(targets)
                }
            }
        }
        if (homeTopBarAdBlockHook) {
            entries += HookInstallEntry.dispatched("HomeTopBarRightSlotHook") { cl ->
                HookSymbolResolver.resolveHomeTopBarRightSlotSymbols(cl, symbols)?.let { targets ->
                    HomeTopBarRightSlotHook.hook(targets)
                }
            }
        }
        if (context.canInstallEnterForumWeb(settings)) {
            entries += HookInstallEntry.dispatched("EnterForumWebHook") { cl ->
                HookSymbolResolver.resolveEnterForumWebSymbols(cl, symbols)?.let { targets ->
                    EnterForumWebHook.hook(targets)
                }
            }
        }
        val commentAvatarDirectProfile = context.canInstallCommentAvatarDirectProfile(settings)
        val systemBrowser = context.canInstallSystemBrowser(settings)
        if (systemBrowser || commentAvatarDirectProfile) {
            entries += HookInstallEntry.dispatched("PlainUrlDirectBrowserHook") { cl ->
                val spanTargets = HookSymbolResolver.resolvePlainUrlClickableSpanSymbols(cl, symbols)
                val messageTarget = HookSymbolResolver.resolvePlainUrlMessageDispatchSymbols(cl, symbols)
                val browserHelperTargets = if (systemBrowser) {
                    HookSymbolResolver.resolvePlainUrlBrowserHelperSymbols(cl, symbols)
                } else {
                    null
                }
                val mountCardTargets = if (systemBrowser) {
                    HookSymbolResolver.resolveMountCardLinkLayoutSymbols(cl, symbols)
                } else {
                    null
                }
                val clickSpanMarkerField = HookSymbolResolver.resolvePlainUrlClickSpanMarkerField(cl)
                val targets = PlainUrlDirectBrowserHook.RuntimeTargets(
                    spanTargets = spanTargets,
                    messageTarget = messageTarget,
                    browserHelperTargets = browserHelperTargets,
                    mountCardTargets = mountCardTargets,
                    clickSpanMarkerField = clickSpanMarkerField,
                    isClickMessageCmd = HookSymbolResolver::isPlainUrlClickMessageCmd,
                    resolveMessageDataSymbols = HookSymbolResolver::resolvePlainUrlMessageDataSymbols,
                )
                PlainUrlDirectBrowserHook.hook(targets)
            }
        }
        if (context.canInstallForumNativeTopShift()) {
            entries += HookInstallEntry.dispatched("ForumNativeTopShiftBlockHook") { cl ->
                HookSymbolResolver.resolveForumNativeTopShiftSymbols(cl, symbols)?.let { targets ->
                    ForumNativeTopShiftBlockHook.hook(targets)
                }
            }
        }
        if (homeNativeGlassHook) {
            entries += HookInstallEntry("HomeNativeGlassHook") { cl -> HomeNativeGlassHook.hook(cl, symbols) }
        }
        if (context.canInstallAutoRefresh(settings)) {
            entries += HookInstallEntry.dispatched("AutoRefreshHook") { cl ->
                HookSymbolResolver.resolveAutoRefreshSymbols(cl, symbols)?.let { targets ->
                    AutoRefreshHook.hook(targets)
                }
            }
        }
        if (context.canInstallAutoLoadMore(settings)) {
            entries += HookInstallEntry.dispatched("AutoLoadMoreHook") { cl ->
                HookSymbolResolver.resolveAutoLoadMoreSymbols(cl, symbols)?.let { targets ->
                    AutoLoadMoreHook.hook(targets)
                }
            }
            entries += HookInstallEntry.dispatched("PbCommentAutoLoadHook") { cl ->
                HookSymbolResolver.resolvePbCommentAutoLoadSymbols(cl, symbols)?.let { targets ->
                    PbCommentAutoLoadHook.hook(targets)
                }
            }
        }
        if (context.canInstallPbScrollCoalesce(settings)) {
            entries += HookInstallEntry.dispatched("PbScrollCoalesceHook") { cl ->
                HookSymbolResolver.resolvePbScrollCoalesceSymbols(cl, symbols)?.let { targets ->
                    PbScrollCoalesceHook.hook(targets)
                }
            }
        }
        if (context.canInstallPbGestureFontScale(settings)) {
            entries += HookInstallEntry.dispatched("PbDisableGestureFontScaleHook") { cl ->
                HookSymbolResolver.resolvePbGestureScaleSymbols(cl, symbols)?.let { targets ->
                    PbDisableGestureFontScaleHook.hook(targets)
                }
            }
        }
        if (context.canInstallPbLikeAutoReply(settings)) {
            entries += HookInstallEntry.dispatched("PbLikeAutoReplyHook") { cl ->
                HookSymbolResolver.resolvePbLikeAutoReplySymbols(cl, symbols)?.let { targets ->
                    PbLikeAutoReplyHook.hook(targets, settings.pbLikeAutoReplyText)
                }
            }
        }
        if (context.canInstallInputMemeBarBlock(settings)) {
            entries += HookInstallEntry.dispatched("InputMemeBarBlockHook") { cl ->
                HookSymbolResolver.resolveInputMemeBarSymbols(cl, symbols)?.let { targets ->
                    InputMemeBarBlockHook.hook(targets)
                }
            }
        }
        if (context.canInstallMainAiComponents(settings)) {
            entries += HookInstallEntry.dispatched("AiComponentDisableHook") { cl ->
                HookSymbolResolver.resolveAiComponentSymbols(cl, symbols)?.let { targets ->
                    AiComponentDisableHook.hook(targets)
                }
            }
        }
        if (context.canInstallDefaultNotifyTab(settings)) {
            entries += HookInstallEntry.dispatched("MsgTabDefaultNotifyHook") { cl ->
                HookSymbolResolver.resolveMsgTabDefaultNotifySymbols(cl, symbols)?.let { targets ->
                    MsgTabDefaultNotifyHook.hook(targets)
                }
            }
        }
        if (context.canInstallPrivateReadReceipt(settings)) {
            entries += HookInstallEntry("PrivateReadReceiptBlockHook") { cl ->
                HookSymbolResolver.resolvePrivateReadReceiptSymbols(cl, symbols)?.let { targets ->
                    PrivateReadReceiptBlockHook.hook(targets)
                } ?: InstallOutcome.skipped("private read receipt targets unavailable")
            }
        }

        if (context.canInstallCollectionSearch()) {
            entries += HookInstallEntry.dispatched("CollectionSearchHook") { cl ->
                HookSymbolResolver.resolveCollectionSearchSymbols(cl, symbols)?.let { targets ->
                    CollectionSearchHook.hook(targets)
                }
            }
        }
        if (context.canInstallHistorySearch()) {
            entries += HookInstallEntry.dispatched("HistorySearchHook") { cl ->
                HookSymbolResolver.resolveHistorySearchSymbols(cl, symbols)?.let { targets ->
                    HistorySearchHook.hook(targets)
                }
            }
        }

        if (context.canInstallShareTrackingCleaner(settings)) {
            entries += HookInstallEntry.dispatched("ShareTrackingParamCleanerHook") { cl ->
                HookSymbolResolver.resolveShareTrackingParamCleanerSymbols(cl, symbols)?.let { targets ->
                    ShareTrackingParamCleanerHook.hook(targets)
                }
            }
        }
        if (context.canInstallReplyVisibilityProbe(settings)) {
            entries += HookInstallEntry.dispatched("ReplyVisibilityProbeHook") { cl ->
                HookSymbolResolver.resolveReplyVisibilityProbeSymbols(cl, symbols)?.let { targets ->
                    ReplyVisibilityProbeHook.hook(targets)
                }
            }
        }
        if (settings.isDetailedLoggingEnabled) {
            entries += HookInstallEntry.dispatched("ReplyServerResponseLogHook") { cl ->
                HookSymbolResolver.resolveReplyServerResponseLogSymbols(cl, symbols)?.let { targets ->
                    ReplyServerResponseLogHook.hook(targets)
                }
            }
            entries += HookInstallEntry.dispatched("AgreeServerResponseLogHook") { cl ->
                HookSymbolResolver.resolveAgreeServerResponseLogSymbols(cl, symbols)?.let { targets ->
                    AgreeServerResponseLogHook.hook(targets)
                }
            }
            entries += HookInstallEntry.dispatched("FeedInfoLogHook") { cl ->
                HookSymbolResolver.resolveFeedInfoLogSymbols(cl, symbols)?.let { targets ->
                    FeedInfoLogHook.hook(targets)
                }
            }
        }

        if (commentAvatarDirectProfile) {
            entries += HookInstallEntry.dispatched("CommentAvatarDirectProfileHook") { cl ->
                HookSymbolResolver.resolveGlobalDirectProfileSymbols(cl)?.let { targets ->
                    CommentAvatarDirectProfileHook.hook(targets)
                }
            }
        }

        return HookInstallPlan(processName, "symbol", entries)
    }

    private fun performanceEntries(
        settings: SettingsSnapshot,
        symbols: HookSymbols,
    ): List<HookInstallEntry> {
        val entries = ArrayList<HookInstallEntry>()
        var abMethods: Map<String, Method>? = null
        fun abSymbols(cl: ClassLoader): Map<String, Method> = abMethods
            ?: HookSymbolResolver.resolvePerformanceAbSymbols(cl, symbols).also { abMethods = it }
        if (settings.isPbPerformanceModeEnabled || settings.isPostPageAdBlockEnabled) {
            entries += HookInstallEntry.dispatched("PbPerformanceModeHook") { cl -> PbPerformanceModeHook.hook(abSymbols(cl)) }
        }
        if (settings.isPbPreloadForced) {
            entries += HookInstallEntry.dispatched("PbForcePreloadHook") { cl ->
                HookSymbolResolver.resolvePbPreloadTargets(cl, symbols)?.let { targets ->
                    PbForcePreloadHook.hook(targets, abSymbols(cl))
                }
            }
        }
        if (settings.isAdSdkComponentsDisabled) {
            entries += HookInstallEntry.dispatched("AdSdkInitBlockHook") { cl -> AdSdkInitBlockHook.hook(cl) }
        }
        if (settings.isMonitorSyncComponentsDisabled) {
            entries += HookInstallEntry.dispatched("TrackingBlockHook") { cl ->
                TrackingBlockHook.hook(HookSymbolResolver.resolveTrackingSymbols(cl, symbols))
            }
        }
        if (settings.isVideoComponentsDisabled) {
            entries += HookInstallEntry.dispatched("VideoPreloadBlockHook") { cl -> VideoPreloadBlockHook.hook(cl) }
        }
        if (settings.isHostSlideAnimationDisabled) {
            entries += HookInstallEntry.dispatched("HostSlideAnimationBlockHook") { cl ->
                HostSlideAnimationBlockHook.hook(cl)
            }
        }
        if (
            settings.isHostPerformanceFlagsForced ||
            settings.isFlutterPreinitDisabled ||
            settings.isApsarasScheduleDisabled ||
            settings.isAdSdkComponentsDisabled ||
            settings.isVideoComponentsDisabled ||
            settings.isHostFeedColdOptEnabled
        ) {
            entries += HookInstallEntry.dispatched("ColdStartOptHook") { cl -> ColdStartOptHook.hook(abSymbols(cl)) }
        }
        if (
            settings.isAdSdkComponentsDisabled ||
            settings.isFlutterPreinitDisabled ||
            settings.isLowEndDeviceConfigForced
        ) {
            entries += HookInstallEntry("HostPerformanceConfigHook") { cl ->
                HostPerformanceConfigHook.hook(
                    cl,
                    if (settings.isLowEndDeviceConfigForced) symbols.lowEndConfig.restore(cl) else emptyMap(),
                )
            }
        }
        return entries
    }

}
