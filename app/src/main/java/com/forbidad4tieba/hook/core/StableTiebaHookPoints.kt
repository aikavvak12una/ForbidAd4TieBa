package com.forbidad4tieba.hook.core

/**
 * 鐩爣搴旂敤閲屾寜绋冲畾涓旀湭娣锋穯澶勭悊鐨勭被鍚嶅拰鏂规硶鍚嶃€? * 娣锋穯绗﹀彿浠嶇劧瑕侀€氳繃 HookSymbolResolver 瑙ｆ瀽銆? */
object StableTiebaHookPoints {
    const val MAIN_TAB_ACTIVITY_CLASS = "com.baidu.tieba.tblauncher.MainTabActivity"
    const val FRAGMENT_TAB_HOST_CLASS = "com.baidu.tbadk.core.tabHost.FragmentTabHost"
    const val FRAGMENT_TAB_WIDGET_CLASS = "com.baidu.tbadk.core.tabHost.FragmentTabWidget"
    const val FEED_TEMPLATE_ADAPTER_CLASS = "com.baidu.tieba.feed.list.FeedTemplateAdapter"
    const val TEMPLATE_ADAPTER_CLASS = "com.baidu.tieba.feed.list.TemplateAdapter"
    const val FEED_CARD_VIEW_CLASS = "com.baidu.tieba.feed.card.FeedCardView"
    const val FEED_CARD_REPLY_VIEW_CLASS = "com.baidu.tieba.feed.component.CardReplyView"
    const val FEED_CARD_VOTE_VIEW_CLASS = "com.baidu.tieba.feed.component.CardVoteView"
    const val FEED_CARD_INPUT_GUIDE_VIEW_CLASS = "com.baidu.tieba.feed.component.CardInputGuideView"
    const val FEED_CARD_PIC_VIEW_CLASS = "com.baidu.tieba.feed.component.CardPicView"
    const val FEED_CARD_ORIGIN_MOUNT_VIEW_CLASS =
        "com.baidu.tieba.feed.component.mount.CardOriginMountView"
    const val FORUM_ACTIVITY_CLASS = "com.baidu.tieba.forum.ForumActivity"
    const val TYPE_ADAPTER_CLASS = "com.baidu.adp.widget.ListView.TypeAdapter"
    const val TYPE_ADAPTER_VIEW_HOLDER_CLASS = "$TYPE_ADAPTER_CLASS\$ViewHolder"
    const val BD_LIST_VIEW_CLASS = "com.baidu.adp.widget.ListView.BdListView"
    const val BD_RECYCLER_VIEW_CLASS = "com.baidu.adp.widget.ListView.BdRecyclerView"
    const val BD_TYPE_RECYCLER_VIEW_CLASS = "com.baidu.adp.widget.ListView.BdTypeRecyclerView"
    const val PB_FIRST_FLOOR_RECOMMEND_ADAPTER_CLASS =
        "com.baidu.tieba.pb.widget.adapter.PbFirstFloorRecommendAdapter"
    const val PB_FIRST_FLOOR_RECOMMEND_DATA_CLASS =
        "com.baidu.tieba.pb.data.PbFirstFloorRecommendData"
    const val PB_ACTIVITY_CLASS = "com.baidu.tieba.pb.pb.main.PbActivity"
    const val PB_ABS_ACTIVITY_CLASS = "com.baidu.tieba.pb.pb.main.AbsPbActivity"
    const val PB_FRAGMENT_CLASS = "com.baidu.tieba.pb.pb.main.PbFragment"
    const val PB_ITEM_FRAME_VIEW_CLASS = "com.baidu.tieba.pb.view.ItemFrameView"
    const val PB_ITEM_RELATIVE_VIEW_CLASS = "com.baidu.tieba.pb.view.ItemRelativeView"
    const val PB_EXTENSION_PB_VIEW_CLASS = "com.baidu.tieba.pb.widget.view.ExtensionPbView"
    const val PB_SUB_PB_LAYOUT_CLASS = "com.baidu.tieba.pb.sub.view.SubPbLayout"
    const val PB_PAGE_BROWSER_RECYCLER_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.ui.PageBrowserRecyclerView"
    const val PB_COMMENT_RECYCLER_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.ui.CommentRecyclerView"
    const val PB_COMMENT_FLOOR_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.CommentFloorView"
    const val PB_COMMENT_FLOOR_SUB_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.sub.CommentFloorSubView"
    const val PB_REPLY_TITLE_VIEW_HOLDER_CLASS =
        "com.baidu.tieba.pb.pb.main.PbReplyTitleViewHolder"
    const val PB_COMMON_LAYOUT_PRELOADER_CLASS =
        "com.baidu.tieba.pb.preload.CommonLayoutPreloader"
    const val AGREE_VIEW_CLASS = "com.baidu.tbadk.core.view.AgreeView"
    const val AGREE_DATA_CLASS = "com.baidu.tieba.tbadkcore.data.AgreeData"
    const val PB_NEW_INPUT_CONTAINER_CLASS = "com.baidu.tbadk.editortools.pb.PbNewInputContainer"
    const val SUB_PB_REPLY_ADAPTER_CLASS =
        "com.baidu.tieba.pb.pb.sub.adapter.SubPbReplyAdapter"
    const val NEW_SUB_PB_ACTIVITY_CLASS = "com.baidu.tieba.pb.pb.sub.NewSubPbActivity"
    const val SUB_PB_VIEW_CLASS = "com.baidu.tieba.pb.pb.sub.SubPbView"
    const val FOLD_COMMENT_ACTIVITY_CLASS = "com.baidu.tieba.pb.pb.foldcomment.FoldCommentActivity"
    const val SORT_SWITCH_BUTTON_CLASS = "com.baidu.tieba.view.SortSwitchButton"
    const val UBS_AB_TEST_HELPER_CLASS = "com.baidu.tbadk.abtest.UbsABTestHelper"
    const val SKIN_MANAGER_CLASS = "com.baidu.tbadk.core.util.SkinManager"
    const val EM_MANAGER_CLASS = "com.baidu.tbadk.core.elementsMaven.EMManager"
    const val CORE_DIALOG_ROUND_LINEAR_LAYOUT_CLASS = "com.baidu.tbadk.core.dialog.RoundLinearLayout"
    const val HOME_PERSONALIZE_PAGE_VIEW_CLASS =
        "com.baidu.tieba.homepage.personalize.PersonalizePageView"
    const val HOME_SCROLL_TAB_BAR_LAYOUT_CLASS =
        "com.baidu.tieba.homepage.framework.indicator.ScrollTabBarLayout"
    const val HOME_FIXED_APP_BAR_LAYOUT_CLASS =
        "com.baidu.tieba.homepage.framework.indicator.FixedAppBarLayout"
    const val TB_SEARCH_BOX_VIEW_CLASS = "com.baidu.tbadk.widget.TbSearchBoxView"
    const val CUSTOM_VIEW_PAGER_CLASS = "com.baidu.tbadk.widget.CustomViewPager"
    const val VIEW_PAGER_CLASS = "androidx.viewpager.widget.ViewPager"
    const val RECYCLER_VIEW_CLASS = "androidx.recyclerview.widget.RecyclerView"
    const val CONSTRAIN_IMAGE_GROUP_CLASS = "com.baidu.tbadk.widget.layout.ConstrainImageGroup"
    const val CONSTRAIN_IMAGE_LAYOUT_CLASS = "com.baidu.tbadk.widget.layout.ConstrainImageLayout"
    const val NESTED_SCROLLING_WEB_VIEW_CLASS =
        "com.baidu.tieba.browser.webview.scroll.NestedScrollingWebView"
    const val TB_SINGLETON_CLASS = "com.baidu.tbadk.TbSingleton"
    const val PB_BOTTOM_ENTER_BAR_VIEW_CLASS =
        "com.baidu.tieba.pb.pb.main.underlayer.PbBottomEnterBarView"
    const val PB_HOT_TOPIC_GUIDE_VIEW_CLASS =
        "com.baidu.tieba.pb.pb.main.underlayer.PbHotTopicGuideView"
    const val PB_VIEW_UTIL_KT_CLASS = "com.baidu.tieba.pb.pb.main.underlayer.PbViewUtilKt"
    const val CARD_FORUM_HEAD_LAYOUT_CLASS = "com.baidu.card.view.CardForumHeadLayout"
    const val TB_ANIMATION_TIP_VIEW_CLASS = "com.baidu.tieba.core.widget.TbAnimationTipView"
    const val SPRITE_ANIMATION_TIP_MANAGER_CLASS =
        "com.baidu.tieba.statemachine.animationtip.SpriteAnimationTipManager"
    const val GALLERY_SWIPE_LAYOUT_CLASS = "com.baidu.tbadk.coreExtra.view.GallerySwipeLayout"
    const val IMAGE_VIEWER_ACTIVITY_CLASS = "com.baidu.tieba.image.ImageViewerActivity"
    const val MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS =
        "com.baidu.tieba.immessagecenter.msgtab.ui.vm.MsgCenterContainerViewModel"
    const val COLLECT_TAB_ACTIVITY_CLASS = "com.baidu.tieba.myCollection.CollectTabActivity"
    const val COLLECTION_THREAD_FRAGMENT_CLASS = "com.baidu.tieba.myCollection.ThreadFragment"
    const val PB_HISTORY_ACTIVITY_CLASS = "com.baidu.tieba.myCollection.history.PbHistoryActivity"
    const val TBADK_CORE_APPLICATION_CLASS = "com.baidu.tbadk.core.TbadkCoreApplication"
    const val NAVIGATION_BAR_CLASS = "com.baidu.tbadk.core.view.NavigationBar"
    const val NAV_CONTROL_ALIGN_CLASS = "$NAVIGATION_BAR_CLASS\$ControlAlign"
    const val HOME_TAB_BAR_RIGHT_SLOT_CLASS =
        "com.baidu.tieba.homepage.personalize.view.HomeTabBarRightSlot"
    const val NETWORK_CLASS = "com.baidu.tbadk.core.util.NetWork"
    const val TB_CONFIG_CLASS = "com.baidu.tbadk.TbConfig"
    const val ACCOUNT_DATA_CLASS = "com.baidu.tbadk.core.data.AccountData"
    const val SWITCH_MANAGER_CLASS = "com.baidu.adp.lib.featureSwitch.SwitchManager"
    const val MESSAGE_MANAGER_CLASS = "com.baidu.adp.framework.MessageManager"
    const val MESSAGE_CLASS = "com.baidu.adp.framework.message.Message"
    const val CUSTOM_MESSAGE_CLASS = "com.baidu.adp.framework.message.CustomMessage"
    const val TB_WEB_VIEW_CLASS = "com.baidu.tieba.browser.TbWebView"
    const val HOME_SIDE_BAR_WEB_VIEW_CLASS = "com.baidu.tieba.sidebar.SideBarWebView"
    const val SIDEBAR_PERSON_INFO_BINDING_CLASS =
        "com.baidu.tieba.recommendfrs.databinding.SidePersonInfoViewBinding"
    const val TB_STACK_IMAGE_VIEW_CLASS = "com.baidu.tieba.feed.widget.TbStackImageView"
    const val AUTO_DEGRADE_TAG_VIEW_CLASS = "com.baidu.tieba.feed.widget.AutoDegradeTagView"
    const val CARD_SOCIAL_BAR_VIEW_CLASS = "com.baidu.tieba.feed.component.CardSocialBarView"
    const val SOCIAL_BAR_WRAPPER_CLASS = "com.baidu.tieba.compact.SocialBarWrapper"
    const val HEAD_PENDANT_CLICKABLE_VIEW_CLASS =
        "com.baidu.tbadk.core.view.HeadPendantClickableView"
    const val EM_TEXT_VIEW_CLASS = "com.baidu.tbadk.core.elementsMaven.view.EMTextView"
    const val TB_RICH_TEXT_VIEW_CLASS = "com.baidu.tbadk.widget.richText.TbRichTextView"
    const val TB_IMAGE_CLASS = "com.baidu.tbadk.widget.image.TbImage"

    const val METHOD_GET_VIEW = "getView"
    const val METHOD_SET_NEXT_PAGE = "setNextPage"
    const val METHOD_SET_BACKGROUND_RESOURCE = "setBackgroundResource"
    const val METHOD_SET_LIST = "setList"
    const val METHOD_GET_ITEM_VIEW_TYPE = "getItemViewType"
    const val METHOD_ON_CREATE_VIEW_HOLDER = "onCreateViewHolder"
    const val METHOD_ON_FILL_VIEW_HOLDER = "onFillViewHolder"
    const val METHOD_GET_TAB_WRAPPER = "getTabWrapper"
    const val METHOD_GET_CURRENT_TAB_TYPE = "getCurrentTabType"
    const val METHOD_IS_HOME_PRE_LOAD_MORE_OPT = "isHomePreLoadMoreOpt"
    const val METHOD_SET_GUIDE_VISIBILITY = "setGuideVisibility"
    const val METHOD_FIND_TYPE = "findType"
    const val METHOD_SEND_MESSAGE = "sendMessage"
    const val METHOD_GET_DATA = "getData"
    const val METHOD_GET_INPUT_VIEW = "getInputView"
    const val METHOD_GET_SEND_VIEW = "getSendView"
    const val METHOD_GET_CURRENT_ACCOUNT = "getCurrentAccount"
    const val METHOD_ADD_POST_DATA = "addPostData"
    const val METHOD_POST_NET_DATA = "postNetData"
    const val METHOD_SET_NEED_TBS = "setNeedTbs"
    const val METHOD_SET_NEED_SIG = "setNeedSig"
    const val FIELD_SERVER_ADDRESS = "SERVER_ADDRESS"
    const val FIELD_MARK_GET_STORE = "MARK_GETSTORE"

    val FOLLOWED_TAB_WEB_VIEW_CLASS_NAMES = arrayOf(
        TB_WEB_VIEW_CLASS,
        "com.baidu.tieba.browser.core.webview.base.BaseWebView",
        "com.baidu.tieba.browser.webview.monitor.MonitorWebView",
        "com.baidu.tieba.browser.webview.scroll.NestedScrollingWebView",
    )

    val POST_AD_VIEW_CLASS_NAMES = arrayOf(
        "com.baidu.tieba.funad.view.AbsFeedAdxView",
        "com.baidu.tieba.recapp.lego.view.AdCardBaseView",
        "com.baidu.tieba.funad.view.TbAdVideoView",
        "com.baidu.tieba.feed.ad.compact.DelegateFunAdView",
        "com.baidu.tieba.pb.pb.main.view.PbImageAlaRecommendView",
        "com.baidu.tieba.core.widget.recommendcard.RecommendCardView",
    )
}
