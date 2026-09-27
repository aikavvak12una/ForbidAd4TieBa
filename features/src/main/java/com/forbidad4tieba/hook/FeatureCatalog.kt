package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.feature.account.AccountListDedupFeature
import com.forbidad4tieba.hook.feature.ad.FeedAdFeature
import com.forbidad4tieba.hook.feature.ad.FeedInfoLogFeature
import com.forbidad4tieba.hook.feature.ad.ForumPageAdBlockFeature
import com.forbidad4tieba.hook.feature.ad.HomeBottomEasterEggAdFeature
import com.forbidad4tieba.hook.feature.ad.PbAdRequestBlockFeature
import com.forbidad4tieba.hook.feature.ad.PbEarlyAdBlockFeature
import com.forbidad4tieba.hook.feature.ad.PbFallingAdFeature
import com.forbidad4tieba.hook.feature.ad.PbFirstFloorRecommendBlockFeature
import com.forbidad4tieba.hook.feature.ad.PostAdFeature
import com.forbidad4tieba.hook.feature.ad.SearchBoxTextAdFeature
import com.forbidad4tieba.hook.feature.ad.StrategyAdStaticFeature
import com.forbidad4tieba.hook.feature.ad.StrategyAdSymbolsFeature
import com.forbidad4tieba.hook.feature.diagnostic.AgreeServerResponseLogFeature
import com.forbidad4tieba.hook.feature.diagnostic.ReplyServerResponseLogFeature
import com.forbidad4tieba.hook.feature.diagnostic.ReplyVisibilityProbeFeature
import com.forbidad4tieba.hook.feature.diagnostic.TiebaHostLogFeature
import com.forbidad4tieba.hook.feature.im.PrivateReadReceiptBlockFeature
import com.forbidad4tieba.hook.feature.perf.AiComponentDisableFeature
import com.forbidad4tieba.hook.feature.perf.AiImageViewerJumpButtonFeature
import com.forbidad4tieba.hook.feature.perf.PerformanceFeature
import com.forbidad4tieba.hook.feature.privacy.CrashReportBlockFeature
import com.forbidad4tieba.hook.feature.share.ImageViewerNativeShareFeature
import com.forbidad4tieba.hook.feature.share.ShareTrackingParamCleanerFeature
import com.forbidad4tieba.hook.feature.ui.AutoLoadMoreFeature
import com.forbidad4tieba.hook.feature.ui.AutoRefreshFeature
import com.forbidad4tieba.hook.feature.ui.BottomTabTopLineFeature
import com.forbidad4tieba.hook.feature.ui.CollectionSearchFeature
import com.forbidad4tieba.hook.feature.ui.CommentAvatarDirectProfileFeature
import com.forbidad4tieba.hook.feature.ui.CommentAvatarDirectProfileHook
import com.forbidad4tieba.hook.feature.ui.DefaultOriginalImageFeature
import com.forbidad4tieba.hook.feature.ui.FirstLikePopupBlockFeature
import com.forbidad4tieba.hook.feature.ui.ForumNativeTopShiftBlockFeature
import com.forbidad4tieba.hook.feature.ui.FreeCopyCommentInjectionFeature
import com.forbidad4tieba.hook.feature.ui.FreeCopyNativeFeature
import com.forbidad4tieba.hook.feature.ui.HistorySearchFeature
import com.forbidad4tieba.hook.feature.ui.HomeBottomTabAutoHideFeature
import com.forbidad4tieba.hook.feature.ui.HomeFeedPromptBarBlockFeature
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassFeature
import com.forbidad4tieba.hook.feature.ui.HomeSideBarSettingsEntryFeature
import com.forbidad4tieba.hook.feature.ui.HomeTabFeature
import com.forbidad4tieba.hook.feature.ui.HomeTabRedDotBlockFeature
import com.forbidad4tieba.hook.feature.ui.HomeTopBarRightSlotFeature
import com.forbidad4tieba.hook.feature.ui.HomeTopTabAutoHideFeature
import com.forbidad4tieba.hook.feature.ui.ImageViewerSwipeEnterForumBlockFeature
import com.forbidad4tieba.hook.feature.ui.InputMemeBarBlockFeature
import com.forbidad4tieba.hook.feature.ui.MainTabBottomFeature
import com.forbidad4tieba.hook.feature.ui.MsgTabDefaultNotifyFeature
import com.forbidad4tieba.hook.feature.ui.NotificationGuideBlockFeature
import com.forbidad4tieba.hook.feature.ui.PbBottomEnterBarHotTopicGuideFeature
import com.forbidad4tieba.hook.feature.ui.PbBottomEnterBarStableFeature
import com.forbidad4tieba.hook.feature.ui.PbCommentAutoLoadFeature
import com.forbidad4tieba.hook.feature.ui.PbDisableGestureFontScaleFeature
import com.forbidad4tieba.hook.feature.ui.PbLikeAutoReplyFeature
import com.forbidad4tieba.hook.feature.ui.PbScrollCoalesceFeature
import com.forbidad4tieba.hook.feature.ui.UpgradePopWindowBlockFeature
import com.forbidad4tieba.hook.feature.ui.liquidglass.BottomTabLiquidGlassFeature
import com.forbidad4tieba.hook.feature.web.EnterForumWebFeature
import com.forbidad4tieba.hook.feature.web.FollowedTabWebFeature
import com.forbidad4tieba.hook.feature.web.HelpCenterFooterBlockFeature
import com.forbidad4tieba.hook.feature.web.HomeSideBarWebBlockFeature
import com.forbidad4tieba.hook.feature.web.MineTabWebBlockFeature
import com.forbidad4tieba.hook.feature.web.plainUrlDirectBrowserFeature
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.ui.SettingsMenuFeature

/** Explicit registration order is the only composition policy here. */
internal object FeatureCatalog {
    val definitions: List<FeatureDefinition> = listOf(
        UpgradePopWindowBlockFeature,
        HomeFeedPromptBarBlockFeature,
        CrashReportBlockFeature,
        FreeCopyCommentInjectionFeature,
        FreeCopyNativeFeature,
        FirstLikePopupBlockFeature,
        NotificationGuideBlockFeature,
        TiebaHostLogFeature,
        StrategyAdStaticFeature,
        HomeTopTabAutoHideFeature,
        HomeBottomTabAutoHideFeature,
        HomeTabRedDotBlockFeature,
        BottomTabLiquidGlassFeature,
        BottomTabTopLineFeature,
        PerformanceFeature,
        HelpCenterFooterBlockFeature,
        AccountListDedupFeature,
        MineTabWebBlockFeature,
        HomeSideBarWebBlockFeature,
        FollowedTabWebFeature,
        ImageViewerNativeShareFeature,
        DefaultOriginalImageFeature,
        ImageViewerSwipeEnterForumBlockFeature,
        AiImageViewerJumpButtonFeature,
        SettingsMenuFeature,
        HomeSideBarSettingsEntryFeature,
        PbBottomEnterBarStableFeature,
        PbBottomEnterBarHotTopicGuideFeature,
        FeedAdFeature,
        PostAdFeature,
        ForumPageAdBlockFeature,
        StrategyAdSymbolsFeature,
        HomeBottomEasterEggAdFeature,
        PbEarlyAdBlockFeature,
        PbFirstFloorRecommendBlockFeature,
        PbAdRequestBlockFeature,
        PbFallingAdFeature,
        HomeTabFeature,
        MainTabBottomFeature,
        SearchBoxTextAdFeature,
        HomeTopBarRightSlotFeature,
        EnterForumWebFeature,
        plainUrlDirectBrowserFeature { context, userId -> CommentAvatarDirectProfileHook.openUserId(context, userId) },
        ForumNativeTopShiftBlockFeature,
        HomeNativeGlassFeature,
        AutoRefreshFeature,
        AutoLoadMoreFeature,
        PbCommentAutoLoadFeature,
        PbScrollCoalesceFeature,
        PbDisableGestureFontScaleFeature,
        PbLikeAutoReplyFeature,
        InputMemeBarBlockFeature,
        AiComponentDisableFeature,
        MsgTabDefaultNotifyFeature,
        PrivateReadReceiptBlockFeature,
        CollectionSearchFeature,
        HistorySearchFeature,
        ShareTrackingParamCleanerFeature,
        ReplyVisibilityProbeFeature,
        ReplyServerResponseLogFeature,
        AgreeServerResponseLogFeature,
        FeedInfoLogFeature,
        CommentAvatarDirectProfileFeature,
    )

    fun shouldHandleProcess(processName: String): Boolean =
        shouldInstallAttachHook(processName) || !staticPlan(processName).isEmpty()

    fun shouldInstallAttachHook(processName: String): Boolean =
        HookProcess.isMain(processName) || HookProcess.isImageViewerRemote(processName)

    fun staticPlan(processName: String): HookInstallPlan =
        plan(FeaturePhase.STATIC, processName, null, SettingsSnapshot())

    fun postAttachPlan(processName: String, symbols: HookSymbols, settings: SettingsSnapshot): HookInstallPlan =
        plan(FeaturePhase.POST_ATTACH, processName, symbols, settings)

    fun symbolPlan(processName: String, symbols: HookSymbols, settings: SettingsSnapshot): HookInstallPlan =
        plan(FeaturePhase.SYMBOL, processName, symbols, settings)

    private fun plan(
        phase: FeaturePhase,
        processName: String,
        symbols: HookSymbols?,
        settings: SettingsSnapshot,
    ): HookInstallPlan {
        val context = HookInstallContext(processName, symbols)
        return HookInstallPlan(processName, phase.diagnosticName,
            definitions.filter { it.phase == phase }.flatMap { it.entries(context, settings) })
    }
}
