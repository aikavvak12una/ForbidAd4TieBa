package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Api102HookRegistry
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.FeedAdHook
import com.forbidad4tieba.hook.feature.ad.FeedInfoLogHook
import com.forbidad4tieba.hook.feature.ad.ForumPageAdBlockHook
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
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassHook
import com.forbidad4tieba.hook.feature.ui.HomeSideBarSettingsEntryHook
import com.forbidad4tieba.hook.feature.ui.HomeTabHook
import com.forbidad4tieba.hook.feature.ui.HomeTabRedDotBlockHook
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
import com.forbidad4tieba.hook.feature.web.HelpCenterFooterBlockHook
import com.forbidad4tieba.hook.feature.web.HomeSideBarWebBlockHook
import com.forbidad4tieba.hook.feature.web.MineTabWebBlockHook
import com.forbidad4tieba.hook.feature.web.PlainUrlDirectBrowserHook
import com.forbidad4tieba.hook.symbol.model.HookSymbols
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
        val registeredBefore = Api102HookRegistry.registeredCount()
        for (entry in plan.entries) {
            try {
                XposedCompat.logD("[HookInstallPlan] Installing ${entry.id} (${plan.phase})...")
                Api102HookRegistry.withInstallScope(
                    Api102HookRegistry.InstallScope(
                        processName = plan.processName,
                        phase = plan.phase,
                        entryId = entry.id,
                    ),
                ) {
                    entry.install(cl)
                }
            } catch (t: Throwable) {
                XposedCompat.log(
                    "[HookInstallPlan] ${entry.id} install FAILED (${plan.phase}): ${t.message}",
                )
                XposedCompat.log(t)
            }
        }
        val registeredAfter = Api102HookRegistry.registeredCount()
        XposedCompat.logD(
            "[HookInstallPlan] ${plan.phase}: entries=${plan.entries.size}, " +
                "registeredDelta=${registeredAfter - registeredBefore}, totalRegistered=$registeredAfter, " +
                "process=${plan.processName}"
        )
    }
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
            entries += HookInstallEntry("UpgradePopWindowBlockHook") { cl -> UpgradePopWindowBlockHook.hook(cl) }
            entries += HookInstallEntry("HomeFeedPromptBarBlockHook") { cl ->
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

        entries += HookInstallEntry("CrashReportBlockHook") { cl -> CrashReportBlockHook.hook(cl) }

        if (context.canInstallFreeCopy()) {
            entries += HookInstallEntry("FreeCopyHook") { cl ->
                HookSymbolResolver.resolveFreeCopyPopupSymbols(cl, symbols)?.let { targets ->
                    FreeCopyHook.hook(targets)
                }
            }
        }
        if (context.isMain) {
            val enableSwitchManager =
                settings.isStrategyAdBlockEnabled ||
                    settings.isAdSdkComponentsDisabled ||
                    settings.isApsarasScheduleDisabled
            if (enableSwitchManager) {
                entries += HookInstallEntry("StrategyAdHook.static") { cl ->
                    StrategyAdHook.hookStatic(
                        cl = cl,
                        enableAccountData = settings.isStrategyAdBlockEnabled,
                        enableSwitchManager = enableSwitchManager,
                    )
                }
            }
        }
        if (context.isMain && settings.isHomeTabAutoHideEnabled) {
            entries += HookInstallEntry("HomeTopTabAutoHideHook") { cl -> HomeTopTabAutoHideHook.hook(cl) }
            entries += HookInstallEntry("HomeBottomTabAutoHideHook") { cl -> HomeBottomTabAutoHideHook.hook(cl) }
        }
        if (context.isMain && settings.isHomeTabRedDotHidden) {
            entries += HookInstallEntry("HomeTabRedDotBlockHook") { cl -> HomeTabRedDotBlockHook.hook(cl) }
        }
        if (context.canInstallHomeNativeGlass(settings)) {
            entries += HookInstallEntry("BottomTabTopLineHook") { cl -> BottomTabTopLineHook.hook(cl) }
        }
        if (context.isMain) {
            entries += performanceEntries(settings)
            entries += HookInstallEntry("HelpCenterFooterBlockHook") { cl -> HelpCenterFooterBlockHook.hook(cl) }
            if (context.canInstallMineTabWebBlock(settings)) {
                entries += HookInstallEntry("MineTabWebBlockHook") { cl -> MineTabWebBlockHook.hook(cl, symbols) }
            }
            if (context.canInstallHomeSideBarWebBlock(settings)) {
                entries += HookInstallEntry("HomeSideBarWebBlockHook") { cl ->
                    HomeSideBarWebBlockHook.hook(cl, symbols)
                }
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
        val context = HookInstallContext(processName, symbols)
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

        val feedAdBlockHook = context.canInstallFeedAdBlock(settings)
        val postAdBlockHook = context.canInstallPostAdBlock(settings)
        val forumPageAdBlockHook = context.canInstallForumPageAdBlock(settings)
        val strategyAdBlockHook = context.canInstallStrategyAdBlock(settings)
        val pbEarlyAdBlockHook = context.canInstallPbEarlyAdBlock(settings)
        val pbAdRequestBlockHook = context.canInstallPbAdRequestBlock(settings)
        val pbFallingAdBlockHook = context.canInstallPbFallingAdBlock(settings)
        val searchBoxTextAdBlockHook = context.canInstallSearchBoxTextAdBlock(settings)
        val homeTopBarAdBlockHook = context.canInstallHomeTopBarAdBlock(settings)
        val customPostFilterHook = context.canInstallCustomPostFilter(settings)
        val homeNativeGlassHook = context.canInstallHomeNativeGlass(settings)
        val feedListHook = feedAdBlockHook || customPostFilterHook

        entries += HookInstallEntry("SettingsMenuHook") { cl -> SettingsMenuHook.hook(cl, symbols) }
        entries += HookInstallEntry("HomeSideBarSettingsEntryHook") { cl -> HomeSideBarSettingsEntryHook.hook(cl) }

        if (context.canInstallPbBottomEnterBarStable()) {
            entries += HookInstallEntry("PbBottomEnterBarHook.Stable") { cl ->
                HookSymbolResolver.resolvePbBottomEnterBarStableSymbols(cl, symbols)?.let { targets ->
                    PbBottomEnterBarHook.hookStable(targets)
                }
            }
        }

        if (context.canInstallPbBottomEnterBarHotTopicGuide()) {
            entries += HookInstallEntry("PbBottomEnterBarHook.HotTopicGuide") { cl ->
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
                }
            }
        }
        if (postAdBlockHook) {
            entries += HookInstallEntry("PostAdHook") { cl ->
                HookSymbolResolver.resolvePostAdDataFilterSymbols(cl, symbols)?.let { targets ->
                    PostAdHook.hook(targets)
                }
            }
        }
        if (forumPageAdBlockHook) {
            entries += HookInstallEntry("ForumPageAdBlockHook") { cl ->
                HookSymbolResolver.resolveForumPageAdBlockSymbols(cl, symbols)?.let { targets ->
                    ForumPageAdBlockHook.hook(targets)
                }
            }
        }
        if (strategyAdBlockHook) {
            entries += HookInstallEntry("StrategyAdHook.symbols") { cl ->
                HookSymbolResolver.resolveStrategyAdSymbols(cl, symbols)?.let { targets ->
                    StrategyAdHook.hookWithSymbols(targets)
                }
            }
        }
        if (pbEarlyAdBlockHook) {
            entries += HookInstallEntry("PbEarlyAdBlockHook") { cl ->
                HookSymbolResolver.resolvePbEarlyAdBlockSymbols(cl, symbols)?.let { targets ->
                    PbEarlyAdBlockHook.hook(targets)
                }
            }
        }
        if (pbAdRequestBlockHook) {
            entries += HookInstallEntry("PbAdRequestBlockHook") { cl ->
                HookSymbolResolver.resolvePbAdRequestBlockSymbols(cl, symbols)?.let { targets ->
                    PbAdRequestBlockHook.hook(targets)
                }
            }
        }
        if (pbFallingAdBlockHook) {
            entries += HookInstallEntry("PbFallingAdHook") { cl ->
                HookSymbolResolver.resolvePbFallingAdSymbols(cl, symbols)?.let { targets ->
                    PbFallingAdHook.hook(targets)
                }
            }
        }
        if (context.canInstallHomeTopTabs(settings)) {
            entries += HookInstallEntry("HomeTabHook") { cl ->
                HookSymbolResolver.resolveHomeTabSymbols(cl, symbols)?.let { targets ->
                    HomeTabHook.hook(targets)
                }
            }
        }
        if (context.canInstallBottomTabs(settings)) {
            entries += HookInstallEntry("MainTabBottomHook") { cl ->
                HookSymbolResolver.resolveMainTabBottomSymbols(cl, symbols)?.let { targets ->
                    MainTabBottomHook.hook(targets)
                }
            }
        }
        if (searchBoxTextAdBlockHook) {
            entries += HookInstallEntry("SearchBoxTextAdHook") { cl ->
                HookSymbolResolver.resolveSearchBoxTextAdSymbols(cl, symbols)?.let { targets ->
                    SearchBoxTextAdHook.hook(targets)
                }
            }
        }
        if (homeTopBarAdBlockHook) {
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
                    browserHelperTargets = HookSymbolResolver.resolvePlainUrlBrowserHelperSymbols(cl, symbols),
                    webContainerTargets = HookSymbolResolver.resolvePlainUrlWebContainerSymbols(cl, symbols),
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

        if (context.canInstallCollectionSearch()) {
            entries += HookInstallEntry("CollectionSearchHook") { cl ->
                HookSymbolResolver.resolveCollectionSearchSymbols(cl, symbols)?.let { targets ->
                    CollectionSearchHook.hook(targets)
                }
            }
        }
        if (context.canInstallHistorySearch()) {
            entries += HookInstallEntry("HistorySearchHook") { cl ->
                HookSymbolResolver.resolveHistorySearchSymbols(cl, symbols)?.let { targets ->
                    HistorySearchHook.hook(targets)
                }
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
        if (settings.isPbPerformanceModeEnabled || settings.isPostPageAdBlockEnabled) {
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

}
