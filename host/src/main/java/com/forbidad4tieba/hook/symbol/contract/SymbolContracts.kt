package com.forbidad4tieba.hook.symbol.contract

/** Explicit registration: a feature contributes its complete contract once. */
internal object SymbolContracts {
    val all: List<SymbolContract> = listOf(
        CommentFilterContract,
        CommentShortcutContract,
        PerformanceContract,
        VideoDisableContract,
        YyBackgroundContract,
        SwanPreloadContract,
        CrashReportContract,
        DefaultPopupsContract,
        ProfileContract,
        LoggingContract,
        PostPageAdContract,
        AdFamilyContract,
        HomeTabsContract,
        SettingsContract,
        FeedContract,
        StrategyAdContract,
        HomeBottomEasterEggContract,
        SearchBoxContract,
        HomeAnchorsContract,
        HomeRightSlotContract,
        PbFallingContract,
        PbBottomBannerContract,
        PbTopEnterBarContract,
        InputMemeBarContract,
        PbEarlyAdContract,
        PbFirstFloorRecommendContract,
        PbAdRequestContract,
        PostAdDataContract,
        EnterForumContract,
        PlainUrlContract,
        PrivateReadReceiptContract,
        MountCardContract,
        MineTabWebContract,
        HomeSidebarContract,
        AutoSignInContract,
        ForumTopShiftContract,
        AutoRefreshContract,
        AutoLoadMoreContract,
        PbGestureScaleContract,
        PbLikeAutoReplyContract,
        CollectionContract,
        HistoryContract,
        MessageTabContract,
        LzlSortContract,
        FreeCopyContract,
        MainTabsContract,
        OriginalImageContract,
        ImageSharingContract,
        NativeGlassContract,
        ForumPageAdContract,
        ReplyLogContract,
        AgreeLogContract,
        ReplyVisibilityContract,
    )

    init {
        val keys = all.flatMap { it.fields }.map { it.cacheKey }
        require(keys.size == keys.distinct().size) { "A cached symbol has multiple owners" }
    }

    fun featuresForPoint(name: String): List<String> = all.flatMap { it.pointOwners }
        .filter { it.matches(name) }.maxByOrNull { it.pattern.length }?.features.orEmpty()
}
