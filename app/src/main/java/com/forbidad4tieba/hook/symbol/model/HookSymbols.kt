package com.forbidad4tieba.hook.symbol.model

import org.json.JSONObject

data class HookSymbols(
    val hookPoints: HookPointSymbols = HookPointSymbols(),
    val resources: ResourceSymbols = ResourceSymbols(),
    val meta: ScanMeta = ScanMeta(),
) {
    val settings: SettingsSymbols
        get() = hookPoints.primary.settings
    val home: HomeSymbols
        get() = hookPoints.primary.home
    val ad: AdSymbols
        get() = hookPoints.primary.ad
    val pb: PbSymbols
        get() = hookPoints.primary.pb
    val web: WebSymbols
        get() = hookPoints.web
    val privateMessage: PrivateMessageSymbols
        get() = hookPoints.privateMessage
    val collectionHistory: CollectionHistorySymbols
        get() = hookPoints.collectionHistory
    val image: ImageSymbols
        get() = hookPoints.media.image
    val ai: AiSymbols
        get() = hookPoints.media.ai

    val homeTabClass: String?
        get() = hookPoints.primary.home.tab.homeTabClass
    val homeTabRebuildMethod: String?
        get() = hookPoints.primary.home.tab.homeTabRebuildMethod
    val homeTabListField: String?
        get() = hookPoints.primary.home.tab.homeTabListField
    val homeTabItemTypeField: String?
        get() = hookPoints.primary.home.tab.item.homeTabItemTypeField
    val homeTabItemCodeField: String?
        get() = hookPoints.primary.home.tab.item.homeTabItemCodeField
    val homeTabItemNameField: String?
        get() = hookPoints.primary.home.tab.item.homeTabItemNameField
    val homeTabItemUrlField: String?
        get() = hookPoints.primary.home.tab.item.homeTabItemUrlField
    val homeTabItemMainSetterMethod: String?
        get() = hookPoints.primary.home.tab.itemMain.homeTabItemMainSetterMethod
    val homeTabItemMainIntField: String?
        get() = hookPoints.primary.home.tab.itemMain.homeTabItemMainIntField
    val homeTabItemMainBooleanField: String?
        get() = hookPoints.primary.home.tab.itemMain.homeTabItemMainBooleanField
    val settingsClass: String?
        get() = hookPoints.primary.settings.settingsClass
    val settingsInitMethod: String?
        get() = hookPoints.primary.settings.settingsInitMethod
    val settingsContainerField: String?
        get() = hookPoints.primary.settings.settingsContainerField
    val feedTemplateKeyMethod: String?
        get() = hookPoints.primary.ad.feedTemplate.feedTemplateKeyMethod
    val feedTemplatePayloadMethod: String?
        get() = hookPoints.primary.ad.feedTemplate.feedTemplatePayloadMethod
    val feedTemplateLoadMoreMethod: String?
        get() = hookPoints.primary.ad.feedTemplate.feedTemplateLoadMoreMethod
    val splashAdHelperClass: String?
        get() = hookPoints.primary.ad.splash.splashAdHelperClass
    val splashAdHelperMethod: String?
        get() = hookPoints.primary.ad.splash.splashAdHelperMethod
    val closeAdDataClass: String?
        get() = hookPoints.primary.ad.closeAd.closeAdDataClass
    val closeAdDataMethodG1: String?
        get() = hookPoints.primary.ad.closeAd.closeAdDataMethodG1
    val closeAdDataMethodJ1: String?
        get() = hookPoints.primary.ad.closeAd.closeAdDataMethodJ1
    val zgaClass: String?
        get() = hookPoints.primary.ad.zga.zgaClass
    val zgaMethods: List<String>?
        get() = hookPoints.primary.ad.zga.zgaMethods
    val searchBoxViewClass: String?
        get() = hookPoints.primary.home.search.searchBoxViewClass
    val searchBoxSetHintMethod: String?
        get() = hookPoints.primary.home.search.searchBoxSetHintMethod
    val homeSearchBoxOwnerClass: String?
        get() = hookPoints.primary.home.search.homeSearchBoxOwnerClass
    val homeSearchBoxInitMethod: String?
        get() = hookPoints.primary.home.search.homeSearchBoxInitMethod
    val homeSearchBoxGetterMethod: String?
        get() = hookPoints.primary.home.search.homeSearchBoxGetterMethod
    val homePersonalizeAnchorClasses: List<String>?
        get() = hookPoints.primary.home.personalization.homePersonalizeAnchorClasses
    val homeRightSlotClass: String?
        get() = hookPoints.primary.home.rightSlot.homeRightSlotClass
    val homeRightSlotStateMethods: List<String>?
        get() = hookPoints.primary.home.rightSlot.homeRightSlotStateMethods
    val pbFallingViewClass: String?
        get() = hookPoints.primary.pb.falling.pbFallingViewClass
    val pbFallingInitMethod: String?
        get() = hookPoints.primary.pb.falling.pbFallingInitMethod
    val pbFallingShowMethod: String?
        get() = hookPoints.primary.pb.falling.pbFallingShowMethod
    val pbFallingClearMethod: String?
        get() = hookPoints.primary.pb.falling.pbFallingClearMethod
    val pbEarlyAdInsertClass: String?
        get() = hookPoints.primary.pb.ad.earlyInsert.pbEarlyAdInsertClass
    val pbEarlyAdInsertMethodSpecs: List<String>?
        get() = hookPoints.primary.pb.ad.earlyInsert.pbEarlyAdInsertMethodSpecs
    val pbAdBidCommonRequestModelClass: String?
        get() = hookPoints.primary.pb.ad.bid.pbAdBidCommonRequestModelClass
    val pbAdBidCommonRequestStartMethods: List<String>?
        get() = hookPoints.primary.pb.ad.bid.pbAdBidCommonRequestStartMethods
    val pbAdBidCommonRequestNotifyMethod: String?
        get() = hookPoints.primary.pb.ad.bid.pbAdBidCommonRequestNotifyMethod
    val pbAdBidPageBrowserRequestModelClass: String?
        get() = hookPoints.primary.pb.ad.bid.pbAdBidPageBrowserRequestModelClass
    val pbAdBidPageBrowserRequestDataMethod: String?
        get() = hookPoints.primary.pb.ad.bid.pbAdBidPageBrowserRequestDataMethod
    val typeAdapterSetDataMethod: String?
        get() = hookPoints.primary.ad.typeAdapter.typeAdapterSetDataMethod
    val typeAdapterDataItemClass: String?
        get() = hookPoints.primary.ad.typeAdapter.typeAdapterDataItemClass
    val typeAdapterDataGetTypeMethod: String?
        get() = hookPoints.primary.ad.typeAdapter.typeAdapterDataGetTypeMethod
    val enterForumWebControllerClass: String?
        get() = hookPoints.web.enterForum.enterForumWebControllerClass
    val enterForumWebLoadMethod: String?
        get() = hookPoints.web.enterForum.enterForumWebLoadMethod
    val enterForumInitInfoDataClass: String?
        get() = hookPoints.web.enterForum.enterForumInitInfoDataClass
    val enterForumInitInfoGetUrlMethod: String?
        get() = hookPoints.web.enterForum.enterForumInitInfoGetUrlMethod
    val plainUrlClickableSpanClass: String?
        get() = hookPoints.web.plainUrl.clickableSpan.plainUrlClickableSpanClass
    val plainUrlClickableSpanOnClickMethod: String?
        get() = hookPoints.web.plainUrl.clickableSpan.plainUrlClickableSpanOnClickMethod
    val plainUrlClickableSpanOnClickOwnerClasses: List<String>?
        get() = hookPoints.web.plainUrl.clickableSpan.plainUrlClickableSpanOnClickOwnerClasses
    val plainUrlClickableSpanTypeField: String?
        get() = hookPoints.web.plainUrl.clickableSpan.plainUrlClickableSpanTypeField
    val plainUrlClickableSpanUrlField: String?
        get() = hookPoints.web.plainUrl.clickableSpan.plainUrlClickableSpanUrlField
    val plainUrlClickableSpanTextField: String?
        get() = hookPoints.web.plainUrl.clickableSpan.plainUrlClickableSpanTextField
    val plainUrlMessageManagerClass: String?
        get() = hookPoints.web.plainUrl.message.manager.plainUrlMessageManagerClass
    val plainUrlMessageDispatchMethod: String?
        get() = hookPoints.web.plainUrl.message.manager.plainUrlMessageDispatchMethod
    val plainUrlResponsedMessageClass: String?
        get() = hookPoints.web.plainUrl.message.response.plainUrlResponsedMessageClass
    val plainUrlResponsedMessageGetCmdMethod: String?
        get() = hookPoints.web.plainUrl.message.response.plainUrlResponsedMessageGetCmdMethod
    val plainUrlCustomResponsedMessageClass: String?
        get() = hookPoints.web.plainUrl.message.response.plainUrlCustomResponsedMessageClass
    val plainUrlCustomResponsedMessageGetDataMethod: String?
        get() = hookPoints.web.plainUrl.message.response.plainUrlCustomResponsedMessageGetDataMethod
    val plainUrlApplicationClass: String?
        get() = hookPoints.web.plainUrl.message.application.plainUrlApplicationClass
    val plainUrlApplicationGetInstMethod: String?
        get() = hookPoints.web.plainUrl.message.application.plainUrlApplicationGetInstMethod
    val privateReadReceiptModelClass: String?
        get() = hookPoints.privateMessage.readReceipt.model.privateReadReceiptModelClass
    val privateReadReceiptModelReadDispatchMethod: String?
        get() = hookPoints.privateMessage.readReceipt.model.privateReadReceiptModelReadDispatchMethod
    val privateReadReceiptMessageManagerClass: String?
        get() = hookPoints.privateMessage.readReceipt.message.privateReadReceiptMessageManagerClass
    val privateReadReceiptMessageManagerGetInstanceMethod: String?
        get() = hookPoints.privateMessage.readReceipt.message.privateReadReceiptMessageManagerGetInstanceMethod
    val privateReadReceiptMessageSendMethod: String?
        get() = hookPoints.privateMessage.readReceipt.message.privateReadReceiptMessageSendMethod
    val privateReadReceiptMessageBaseClass: String?
        get() = hookPoints.privateMessage.readReceipt.message.privateReadReceiptMessageBaseClass
    val privateReadReceiptRequestClass: String?
        get() = hookPoints.privateMessage.readReceipt.request.privateReadReceiptRequestClass
    val privateReadReceiptModelBaseClass: String?
        get() = hookPoints.privateMessage.readReceipt.model.privateReadReceiptModelBaseClass
    val privateReadReceiptCommitResponseClass: String?
        get() = hookPoints.privateMessage.readReceipt.response.privateReadReceiptCommitResponseClass
    val privateReadReceiptProcessAckMethod: String?
        get() = hookPoints.privateMessage.readReceipt.response.privateReadReceiptProcessAckMethod
    val privateReadReceiptResponseErrorMethod: String?
        get() = hookPoints.privateMessage.readReceipt.response.privateReadReceiptResponseErrorMethod
    val privateReadReceiptRequestMsgIdField: String?
        get() = hookPoints.privateMessage.readReceipt.request.privateReadReceiptRequestMsgIdField
    val privateReadReceiptRequestToUidField: String?
        get() = hookPoints.privateMessage.readReceipt.request.privateReadReceiptRequestToUidField
    val privateReadReceiptModelDataField: String?
        get() = hookPoints.privateMessage.readReceipt.model.privateReadReceiptModelDataField
    val privateReadReceiptPageDataClass: String?
        get() = hookPoints.privateMessage.readReceipt.page.privateReadReceiptPageDataClass
    val privateReadReceiptPageDataChatListMethod: String?
        get() = hookPoints.privateMessage.readReceipt.page.privateReadReceiptPageDataChatListMethod
    val privateReadReceiptChatMessageClass: String?
        get() = hookPoints.privateMessage.readReceipt.page.privateReadReceiptChatMessageClass
    val privateReadReceiptChatMessageMsgIdMethod: String?
        get() = hookPoints.privateMessage.readReceipt.page.privateReadReceiptChatMessageMsgIdMethod
    val privateReadReceiptChatMessageUserIdMethod: String?
        get() = hookPoints.privateMessage.readReceipt.page.privateReadReceiptChatMessageUserIdMethod
    val privateReadReceiptChatMessageLocalDataMethod: String?
        get() = hookPoints.privateMessage.readReceipt.page.privateReadReceiptChatMessageLocalDataMethod
    val privateReadReceiptLocalDataClass: String?
        get() = hookPoints.privateMessage.readReceipt.localAccount.privateReadReceiptLocalDataClass
    val privateReadReceiptLocalDataStatusMethod: String?
        get() = hookPoints.privateMessage.readReceipt.localAccount.privateReadReceiptLocalDataStatusMethod
    val privateReadReceiptAccountClass: String?
        get() = hookPoints.privateMessage.readReceipt.localAccount.privateReadReceiptAccountClass
    val privateReadReceiptCurrentAccountMethod: String?
        get() = hookPoints.privateMessage.readReceipt.localAccount.privateReadReceiptCurrentAccountMethod
    val mountCardLinkLayoutClass: String?
        get() = hookPoints.web.mountCard.mountCardLinkLayoutClass
    val mountCardLinkLayoutOnClickMethod: String?
        get() = hookPoints.web.mountCard.mountCardLinkLayoutOnClickMethod
    val mountCardLinkLayoutDataField: String?
        get() = hookPoints.web.mountCard.mountCardLinkLayoutDataField
    val mountCardLinkInfoDataClass: String?
        get() = hookPoints.web.mountCard.mountCardLinkInfoDataClass
    val mountCardLinkInfoGetUrlMethod: String?
        get() = hookPoints.web.mountCard.mountCardLinkInfoGetUrlMethod
    val forumBottomSheetViewClass: String?
        get() = hookPoints.primary.pb.comment.bottomSheet.forumBottomSheetViewClass
    val forumBottomSheetInitScrollMethod: String?
        get() = hookPoints.primary.pb.comment.bottomSheet.forumBottomSheetInitScrollMethod
    val autoRefreshTriggerMethod: String?
        get() = hookPoints.primary.pb.comment.autoLoad.autoRefreshTriggerMethod
    val autoLoadMoreConfigClass: String?
        get() = hookPoints.primary.pb.comment.autoLoad.autoLoadMoreConfigClass
    val autoLoadMoreConfigMethod: String?
        get() = hookPoints.primary.pb.comment.autoLoad.autoLoadMoreConfigMethod
    val pbCommentScrollListenerClass: String?
        get() = hookPoints.primary.pb.comment.scroll.pbCommentScrollListenerClass
    val pbCommentScrollMethod: String?
        get() = hookPoints.primary.pb.comment.scroll.pbCommentScrollMethod
    val pbCommentScrollFragmentField: String?
        get() = hookPoints.primary.pb.comment.scroll.pbCommentScrollFragmentField
    val pbCommentScrollBottomListenerField: String?
        get() = hookPoints.primary.pb.comment.scroll.pbCommentScrollBottomListenerField
    val pbCommentScrollBottomMethod: String?
        get() = hookPoints.primary.pb.comment.scroll.pbCommentScrollBottomMethod
    val pbCommentBottomListScrollClass: String?
        get() = hookPoints.primary.pb.comment.bottomList.pbCommentBottomListScrollClass
    val pbCommentBottomListScrollMethod: String?
        get() = hookPoints.primary.pb.comment.bottomList.pbCommentBottomListScrollMethod
    val pbCommentBottomListOwnerField: String?
        get() = hookPoints.primary.pb.comment.bottomList.pbCommentBottomListOwnerField
    val pbCommentBottomRecyclerScrollClass: String?
        get() = hookPoints.primary.pb.comment.bottomRecycler.pbCommentBottomRecyclerScrollClass
    val pbCommentBottomRecyclerScrollMethod: String?
        get() = hookPoints.primary.pb.comment.bottomRecycler.pbCommentBottomRecyclerScrollMethod
    val pbCommentBottomRecyclerOwnerField: String?
        get() = hookPoints.primary.pb.comment.bottomRecycler.pbCommentBottomRecyclerOwnerField
    val pbGestureScaleManagerClass: String?
        get() = hookPoints.primary.pb.gestureScale.pbGestureScaleManagerClass
    val pbGestureScaleDispatchMethod: String?
        get() = hookPoints.primary.pb.gestureScale.pbGestureScaleDispatchMethod
    val pbGestureScaleListenerSetterMethod: String?
        get() = hookPoints.primary.pb.gestureScale.pbGestureScaleListenerSetterMethod
    val pbGestureScaleListenerClass: String?
        get() = hookPoints.primary.pb.gestureScale.pbGestureScaleListenerClass
    val pbGestureScaleListenerOnScaleMethod: String?
        get() = hookPoints.primary.pb.gestureScale.pbGestureScaleListenerOnScaleMethod
    val pbLikeAutoReplyAgreeViewClass: String?
        get() = hookPoints.primary.pb.likeAutoReply.agreeView.pbLikeAutoReplyAgreeViewClass
    val pbLikeAutoReplyAgreeClickMethod: String?
        get() = hookPoints.primary.pb.likeAutoReply.agreeView.pbLikeAutoReplyAgreeClickMethod
    val pbLikeAutoReplyAgreeViewGetDataMethod: String?
        get() = hookPoints.primary.pb.likeAutoReply.agreeView.pbLikeAutoReplyAgreeViewGetDataMethod
    val pbLikeAutoReplyAgreeDataClass: String?
        get() = hookPoints.primary.pb.likeAutoReply.agreeData.pbLikeAutoReplyAgreeDataClass
    val pbLikeAutoReplyAgreeDataHasAgreeField: String?
        get() = hookPoints.primary.pb.likeAutoReply.agreeData.pbLikeAutoReplyAgreeDataHasAgreeField
    val pbLikeAutoReplyAgreeDataAgreeTypeField: String?
        get() = hookPoints.primary.pb.likeAutoReply.agreeData.pbLikeAutoReplyAgreeDataAgreeTypeField
    val pbLikeAutoReplyAgreeDataIsInThreadField: String?
        get() = hookPoints.primary.pb.likeAutoReply.agreeData.pbLikeAutoReplyAgreeDataIsInThreadField
    val pbLikeAutoReplyInputContainerClass: String?
        get() = hookPoints.primary.pb.likeAutoReply.inputContainer.pbLikeAutoReplyInputContainerClass
    val pbLikeAutoReplyInputContainerGetInputViewMethod: String?
        get() = hookPoints.primary.pb.likeAutoReply.inputContainer.pbLikeAutoReplyInputContainerGetInputViewMethod
    val pbLikeAutoReplyInputContainerGetSendViewMethod: String?
        get() = hookPoints.primary.pb.likeAutoReply.inputContainer.pbLikeAutoReplyInputContainerGetSendViewMethod
    val collectionPresenterField: String?
        get() = hookPoints.collectionHistory.collection.presenter.collectionPresenterField
    val collectionPresenterListSetterMethod: String?
        get() = hookPoints.collectionHistory.collection.presenter.collectionPresenterListSetterMethod
    val collectionPresenterAdapterField: String?
        get() = hookPoints.collectionHistory.collection.presenter.collectionPresenterAdapterField
    val collectionModelField: String?
        get() = hookPoints.collectionHistory.collection.model.collectionModelField
    val collectionModelListGetterMethod: String?
        get() = hookPoints.collectionHistory.collection.model.collectionModelListGetterMethod
    val collectionModelParseMethod: String?
        get() = hookPoints.collectionHistory.collection.model.collectionModelParseMethod
    val collectionModelListField: String?
        get() = hookPoints.collectionHistory.collection.model.collectionModelListField
    val collectionFragmentDisplayListField: String?
        get() = hookPoints.collectionHistory.collection.fragment.collectionFragmentDisplayListField
    val collectionActivityNavControllerField: String?
        get() = hookPoints.collectionHistory.collection.fragment.collectionActivityNavControllerField
    val collectionNavBarField: String?
        get() = hookPoints.collectionHistory.collection.fragment.collectionNavBarField
    val collectionAdapterShowFooterMethod: String?
        get() = hookPoints.collectionHistory.collection.adapter.collectionAdapterShowFooterMethod
    val collectionAdapterLoadingMethod: String?
        get() = hookPoints.collectionHistory.collection.adapter.collectionAdapterLoadingMethod
    val collectionAdapterHasMoreMethod: String?
        get() = hookPoints.collectionHistory.collection.adapter.collectionAdapterHasMoreMethod
    val collectionEditModeMethod: String?
        get() = hookPoints.collectionHistory.collection.adapter.collectionEditModeMethod
    val historyAdapterField: String?
        get() = hookPoints.collectionHistory.history.activity.historyAdapterField
    val historyAdapterSetListMethod: String?
        get() = hookPoints.collectionHistory.history.activity.historyAdapterSetListMethod
    val historyListField: String?
        get() = hookPoints.collectionHistory.history.activity.historyListField
    val historyActivityListUpdateMethod: String?
        get() = hookPoints.collectionHistory.history.activity.historyActivityListUpdateMethod
    val historyActivityNavBarField: String?
        get() = hookPoints.collectionHistory.history.activity.historyActivityNavBarField
    val historyThreadNameMethod: String?
        get() = hookPoints.collectionHistory.history.threadData.historyThreadNameMethod
    val historyForumNameMethod: String?
        get() = hookPoints.collectionHistory.history.threadData.historyForumNameMethod
    val historyUserNameMethod: String?
        get() = hookPoints.collectionHistory.history.threadData.historyUserNameMethod
    val historyDescriptionMethod: String?
        get() = hookPoints.collectionHistory.history.threadData.historyDescriptionMethod
    val historyThreadIdMethod: String?
        get() = hookPoints.collectionHistory.history.threadData.historyThreadIdMethod
    val historyPostIdMethod: String?
        get() = hookPoints.collectionHistory.history.threadData.historyPostIdMethod
    val historyLiveIdMethod: String?
        get() = hookPoints.collectionHistory.history.threadData.historyLiveIdMethod
    val msgTabLocateToTabMethod: String?
        get() = hookPoints.privateMessage.tab.msgTabLocateToTabMethod
    val msgTabContainerSelectMethod: String?
        get() = hookPoints.privateMessage.tab.msgTabContainerSelectMethod
    val msgTabContainerExtDataField: String?
        get() = hookPoints.privateMessage.tab.msgTabContainerExtDataField
    val freeCopyPopupMenuClass: String?
        get() = hookPoints.collectionHistory.freeCopy.freeCopyPopupMenuClass
    val freeCopyPopupContentViewMethod: String?
        get() = hookPoints.collectionHistory.freeCopy.freeCopyPopupContentViewMethod
    val freeCopyPopupTextField: String?
        get() = hookPoints.collectionHistory.freeCopy.freeCopyPopupTextField
    val mainTabDataClass: String?
        get() = hookPoints.primary.home.mainTab.mainTabDataClass
    val mainTabAddMethod: String?
        get() = hookPoints.primary.home.mainTab.mainTabAddMethod
    val mainTabGetListMethod: String?
        get() = hookPoints.primary.home.mainTab.mainTabGetListMethod
    val mainTabDelegateGetStructureMethod: String?
        get() = hookPoints.primary.home.mainTab.mainTabDelegateGetStructureMethod
    val mainTabStructureTypeField: String?
        get() = hookPoints.primary.home.mainTab.structure.mainTabStructureTypeField
    val mainTabStructureDynamicIconField: String?
        get() = hookPoints.primary.home.mainTab.structure.mainTabStructureDynamicIconField
    val mainTabStructureFragmentField: String?
        get() = hookPoints.primary.home.mainTab.structure.mainTabStructureFragmentField
    val origImagePagerAdapterClass: String?
        get() = hookPoints.media.image.original.component.origImagePagerAdapterClass
    val origImageUrlDragImageViewClass: String?
        get() = hookPoints.media.image.original.component.origImageUrlDragImageViewClass
    val origImageDataClass: String?
        get() = hookPoints.media.image.original.component.origImageDataClass
    val origImageSetPrimaryItemMethod: String?
        get() = hookPoints.media.image.original.component.origImageSetPrimaryItemMethod
    val origImageSetAssistUrlMethod: String?
        get() = hookPoints.media.image.original.component.origImageSetAssistUrlMethod
    val origImageAssistDataMethod: String?
        get() = hookPoints.media.image.original.data.origImageAssistDataMethod
    val origImageOriginTextMethod: String?
        get() = hookPoints.media.image.original.data.origImageOriginTextMethod
    val origImageShowButtonField: String?
        get() = hookPoints.media.image.original.data.origImageShowButtonField
    val origImageBlockedField: String?
        get() = hookPoints.media.image.original.data.origImageBlockedField
    val origImageOriginalProcessField: String?
        get() = hookPoints.media.image.original.data.origImageOriginalProcessField
    val origImageOriginalUrlField: String?
        get() = hookPoints.media.image.original.data.origImageOriginalUrlField
    val origImageSharedPrefHelperClass: String?
        get() = hookPoints.media.image.original.sharedPref.origImageSharedPrefHelperClass
    val origImageSharedPrefGetInstanceMethod: String?
        get() = hookPoints.media.image.original.sharedPref.origImageSharedPrefGetInstanceMethod
    val origImageSharedPrefPutBooleanMethod: String?
        get() = hookPoints.media.image.original.sharedPref.origImageSharedPrefPutBooleanMethod
    val origImageMd5Class: String?
        get() = hookPoints.media.image.original.md5.origImageMd5Class
    val origImageMd5Method: String?
        get() = hookPoints.media.image.original.md5.origImageMd5Method
    val origImagePrimaryReadyMethod: String?
        get() = hookPoints.media.image.original.trigger.origImagePrimaryReadyMethod
    val origImageTriggerMethod: String?
        get() = hookPoints.media.image.original.trigger.origImageTriggerMethod
    val origImageDirectStartMethod: String?
        get() = hookPoints.media.image.original.trigger.origImageDirectStartMethod
    val shareTrackBuilderClass: String?
        get() = hookPoints.media.image.shareTrack.shareTrackBuilderClass
    val shareTrackBuildUrlMethod: String?
        get() = hookPoints.media.image.shareTrack.shareTrackBuildUrlMethod
    val shareTrackAppendQueryMethod: String?
        get() = hookPoints.media.image.shareTrack.shareTrackAppendQueryMethod
    val imageViewerShareConfigClass: String?
        get() = hookPoints.media.image.viewerShare.config.imageViewerShareConfigClass
    val imageViewerShareIsDialogField: String?
        get() = hookPoints.media.image.viewerShare.config.imageViewerShareIsDialogField
    val imageViewerShareItemField: String?
        get() = hookPoints.media.image.viewerShare.config.imageViewerShareItemField
    val imageViewerShareAddOutsideMethod: String?
        get() = hookPoints.media.image.viewerShare.config.imageViewerShareAddOutsideMethod
    val imageViewerShareGetRequestDataMethod: String?
        get() = hookPoints.media.image.viewerShare.request.imageViewerShareGetRequestDataMethod
    val imageViewerShareSetRequestDataMethod: String?
        get() = hookPoints.media.image.viewerShare.request.imageViewerShareSetRequestDataMethod
    val imageViewerShareGetContextMethod: String?
        get() = hookPoints.media.image.viewerShare.request.imageViewerShareGetContextMethod
    val imageViewerShareItemClass: String?
        get() = hookPoints.media.image.viewerShare.item.imageViewerShareItemClass
    val imageViewerShareItemTitleField: String?
        get() = hookPoints.media.image.viewerShare.item.imageViewerShareItemTitleField
    val imageViewerShareItemContentField: String?
        get() = hookPoints.media.image.viewerShare.item.imageViewerShareItemContentField
    val imageViewerShareItemLinkUrlField: String?
        get() = hookPoints.media.image.viewerShare.item.imageViewerShareItemLinkUrlField
    val imageViewerShareItemImageUriField: String?
        get() = hookPoints.media.image.viewerShare.item.imageViewerShareItemImageUriField
    val imageViewerShareItemImageUrlField: String?
        get() = hookPoints.media.image.viewerShare.item.imageViewerShareItemImageUrlField
    val imageViewerShareItemLocalFileField: String?
        get() = hookPoints.media.image.viewerShare.itemView.imageViewerShareItemLocalFileField
    val imageViewerShareItemViewClass: String?
        get() = hookPoints.media.image.viewerShare.itemView.imageViewerShareItemViewClass
    val imageViewerShareItemNameByResMethod: String?
        get() = hookPoints.media.image.viewerShare.itemView.imageViewerShareItemNameByResMethod
    val imageViewerShareItemNameByTextMethod: String?
        get() = hookPoints.media.image.viewerShare.itemView.imageViewerShareItemNameByTextMethod
    val imageViewerShareIconResId: Int?
        get() = resources.imageViewerShareIconResId
    val homeNativeGlassSubPbNextPageMoreViewId: Int?
        get() = resources.homeNativeGlassSubPbNextPageMoreViewId
    val homeNativeGlassPbReplyTitleDividerViewId: Int?
        get() = resources.homeNativeGlassPbReplyTitleDividerViewId
    val homeNativeGlassDynamicBackgroundColorIds: List<Int>
        get() = resources.homeNativeGlassDynamicBackgroundColorIds
    val homeNativeGlassReadableTextResourceIdsByName: Map<String, Int>
        get() = resources.homeNativeGlassReadableTextResourceIdsByName
    val homeNativeGlassSortSwitchBackgroundPaintField: String?
        get() = hookPoints.primary.home.nativeGlass.sortSwitch.homeNativeGlassSortSwitchBackgroundPaintField
    val homeNativeGlassSortSwitchSlideDrawMethod: String?
        get() = hookPoints.primary.home.nativeGlass.sortSwitch.homeNativeGlassSortSwitchSlideDrawMethod
    val homeNativeGlassSortSwitchSlidePathField: String?
        get() = hookPoints.primary.home.nativeGlass.sortSwitch.homeNativeGlassSortSwitchSlidePathField
    val homeNativeGlassEnterForumCapsuleControllerClass: String?
        get() = hookPoints.primary.home.nativeGlass.enterForumCapsule.homeNativeGlassEnterForumCapsuleControllerClass
    val homeNativeGlassEnterForumCapsuleInitMethod: String?
        get() = hookPoints.primary.home.nativeGlass.enterForumCapsule.homeNativeGlassEnterForumCapsuleInitMethod
    val homeNativeGlassEnterForumCapsuleRefreshMethod: String?
        get() = hookPoints.primary.home.nativeGlass.enterForumCapsule.homeNativeGlassEnterForumCapsuleRefreshMethod
    val homeNativeGlassEnterForumCapsuleViewField: String?
        get() = hookPoints.primary.home.nativeGlass.enterForumCapsule.homeNativeGlassEnterForumCapsuleViewField
    val homeNativeGlassEnterForumCapsuleTitleField: String?
        get() = hookPoints.primary.home.nativeGlass.enterForumCapsule.homeNativeGlassEnterForumCapsuleTitleField
    val pbCommonLayoutPreloaderGetOrDefaultMethod: String?
        get() = hookPoints.primary.pb.misc.commonLayoutPreloader.pbCommonLayoutPreloaderGetOrDefaultMethod
    val feedCardBindMethod: String?
        get() = hookPoints.primary.ad.feedCard.feedCardBindMethod
    val feedCardDataListField: String?
        get() = hookPoints.primary.ad.feedCard.feedCardDataListField
    val feedHeadParamsField: String?
        get() = hookPoints.primary.ad.feedCard.feedHeadParamsField
    val feedRecommendCardNestedDataMethod: String?
        get() = hookPoints.primary.ad.feedCard.feedRecommendCardNestedDataMethod
    val feedRecommendCardNestedDataListField: String?
        get() = hookPoints.primary.ad.feedCard.feedRecommendCardNestedDataListField
    val replyServerResponseClass: String?
        get() = hookPoints.primary.pb.misc.replyServerResponse.replyServerResponseClass
    val replyServerResponseDecodeMethod: String?
        get() = hookPoints.primary.pb.misc.replyServerResponse.replyServerResponseDecodeMethod
    val replyServerResponseResultJsonField: String?
        get() = hookPoints.primary.pb.misc.replyServerResponse.replyServerResponseResultJsonField
    val aiSpriteMemePanControllerClass: String?
        get() = hookPoints.media.ai.spriteMeme.aiSpriteMemePanControllerClass
    val aiSpriteMemeEnableMethod: String?
        get() = hookPoints.media.ai.spriteMeme.aiSpriteMemeEnableMethod
    val aiPbNewInputContainerClass: String?
        get() = hookPoints.media.ai.pbInput.aiPbNewInputContainerClass
    val aiPbNewInputContainerInitSpriteMemeMethod: String?
        get() = hookPoints.media.ai.pbInput.aiPbNewInputContainerInitSpriteMemeMethod
    val aiPbNewInputContainerInitAiWriteMethod: String?
        get() = hookPoints.media.ai.pbInput.aiPbNewInputContainerInitAiWriteMethod
    val aiPbAiEmojiCreationViewBindMethod: String?
        get() = hookPoints.media.ai.emojiCreation.aiPbAiEmojiCreationViewBindMethod
    val aiPbPageBrowserAiEmojiCreationBindMethod: String?
        get() = hookPoints.media.ai.emojiCreation.aiPbPageBrowserAiEmojiCreationBindMethod
    val aiImageViewerJumpButtonOwnerClass: String?
        get() = hookPoints.media.ai.imageViewerJumpButton.aiImageViewerJumpButtonOwnerClass
    val aiImageViewerJumpButtonInitMethod: String?
        get() = hookPoints.media.ai.imageViewerJumpButton.aiImageViewerJumpButtonInitMethod
    val featureStatusMap: Map<String, HookFeatureStatus>
        get() = meta.featureStatusMap
    val scanSupportState: String
        get() = meta.availability.scanSupportState
    val scanTargetVersionCode: Long?
        get() = meta.availability.scanTargetVersionCode
    val scanTargetVersionName: String?
        get() = meta.availability.scanTargetVersionName
    val scanTargetVersionType: String?
        get() = meta.availability.scanTargetVersionType
    val scanErrors: List<String>
        get() = meta.scanErrors
    val source: String
        get() = meta.source
    val createdAt: Long
        get() = meta.createdAt
    val cacheSchemaVersion: Int
        get() = meta.cacheSchemaVersion

    fun withFeatureStatusMap(featureStatusMap: Map<String, HookFeatureStatus>): HookSymbols {
        return withMeta(
            ScanMeta(
                featureStatusMap = featureStatusMap,
                availability = meta.availability,
                scanErrors = meta.scanErrors,
                source = meta.source,
                createdAt = meta.createdAt,
                cacheSchemaVersion = meta.cacheSchemaVersion,
            ),
        )
    }

    fun withScanSupport(
        state: String,
        targetVersionCode: Long? = scanTargetVersionCode,
        targetVersionName: String? = scanTargetVersionName,
        targetVersionType: String? = scanTargetVersionType,
    ): HookSymbols {
        return withMeta(
            ScanMeta(
                featureStatusMap = meta.featureStatusMap,
                availability = ScanAvailabilityMeta(
                    scanSupportState = state,
                    scanTargetVersionCode = targetVersionCode,
                    scanTargetVersionName = targetVersionName,
                    scanTargetVersionType = targetVersionType,
                ),
                scanErrors = meta.scanErrors,
                source = meta.source,
                createdAt = meta.createdAt,
                cacheSchemaVersion = meta.cacheSchemaVersion,
            ),
        )
    }

    private fun withMeta(meta: ScanMeta): HookSymbols {
        return HookSymbols(
            hookPoints = hookPoints,
            resources = resources,
            meta = meta,
        )
    }

    fun toJson(): String {
        return JSONObject().apply {
            fun putStringArray(name: String, values: List<String>?) {
                if (values == null) return
                val array = org.json.JSONArray()
                values.forEach { array.put(it) }
                put(name, array)
            }

            put("homeTabClass", homeTabClass)
            put("homeTabRebuildMethod", homeTabRebuildMethod)
            put("homeTabListField", homeTabListField)
            put("homeTabItemTypeField", homeTabItemTypeField)
            put("homeTabItemCodeField", homeTabItemCodeField)
            put("homeTabItemNameField", homeTabItemNameField)
            put("homeTabItemUrlField", homeTabItemUrlField)
            put("homeTabItemMainSetterMethod", homeTabItemMainSetterMethod)
            put("homeTabItemMainIntField", homeTabItemMainIntField)
            put("homeTabItemMainBooleanField", homeTabItemMainBooleanField)
            put("settingsClass", settingsClass)
            put("settingsInitMethod", settingsInitMethod)
            put("settingsContainerField", settingsContainerField)
            put("feedTemplateKeyMethod", feedTemplateKeyMethod)
            put("feedTemplatePayloadMethod", feedTemplatePayloadMethod)
            put("feedTemplateLoadMoreMethod", feedTemplateLoadMoreMethod)
            put("splashAdHelperClass", splashAdHelperClass)
            put("splashAdHelperMethod", splashAdHelperMethod)
            put("closeAdDataClass", closeAdDataClass)
            put("closeAdDataMethodG1", closeAdDataMethodG1)
            put("closeAdDataMethodJ1", closeAdDataMethodJ1)

            put("zgaClass", zgaClass)
            putStringArray("zgaMethods", zgaMethods)
            put("searchBoxViewClass", searchBoxViewClass)
            put("searchBoxSetHintMethod", searchBoxSetHintMethod)
            put("homeSearchBoxOwnerClass", homeSearchBoxOwnerClass)
            put("homeSearchBoxInitMethod", homeSearchBoxInitMethod)
            put("homeSearchBoxGetterMethod", homeSearchBoxGetterMethod)
            putStringArray("homePersonalizeAnchorClasses", homePersonalizeAnchorClasses)
            put("homeRightSlotClass", homeRightSlotClass)
            putStringArray("homeRightSlotStateMethods", homeRightSlotStateMethods)
            put("pbFallingViewClass", pbFallingViewClass)
            put("pbFallingInitMethod", pbFallingInitMethod)
            put("pbFallingShowMethod", pbFallingShowMethod)
            put("pbFallingClearMethod", pbFallingClearMethod)
            put("pbEarlyAdInsertClass", pbEarlyAdInsertClass)
            putStringArray("pbEarlyAdInsertMethodSpecs", pbEarlyAdInsertMethodSpecs)
            put("pbAdBidCommonRequestModelClass", pbAdBidCommonRequestModelClass)
            putStringArray("pbAdBidCommonRequestStartMethods", pbAdBidCommonRequestStartMethods)
            put("pbAdBidCommonRequestNotifyMethod", pbAdBidCommonRequestNotifyMethod)
            put("pbAdBidPageBrowserRequestModelClass", pbAdBidPageBrowserRequestModelClass)
            put("pbAdBidPageBrowserRequestDataMethod", pbAdBidPageBrowserRequestDataMethod)
            put("typeAdapterSetDataMethod", typeAdapterSetDataMethod)
            put("typeAdapterDataItemClass", typeAdapterDataItemClass)
            put("typeAdapterDataGetTypeMethod", typeAdapterDataGetTypeMethod)
            put("enterForumWebControllerClass", enterForumWebControllerClass)
            put("enterForumWebLoadMethod", enterForumWebLoadMethod)
            put("enterForumInitInfoDataClass", enterForumInitInfoDataClass)
            put("enterForumInitInfoGetUrlMethod", enterForumInitInfoGetUrlMethod)
            put("plainUrlClickableSpanClass", plainUrlClickableSpanClass)
            put("plainUrlClickableSpanOnClickMethod", plainUrlClickableSpanOnClickMethod)
            putStringArray("plainUrlClickableSpanOnClickOwnerClasses", plainUrlClickableSpanOnClickOwnerClasses)
            put("plainUrlClickableSpanTypeField", plainUrlClickableSpanTypeField)
            put("plainUrlClickableSpanUrlField", plainUrlClickableSpanUrlField)
            put("plainUrlClickableSpanTextField", plainUrlClickableSpanTextField)
            put("plainUrlMessageManagerClass", plainUrlMessageManagerClass)
            put("plainUrlMessageDispatchMethod", plainUrlMessageDispatchMethod)
            put("plainUrlResponsedMessageClass", plainUrlResponsedMessageClass)
            put("plainUrlResponsedMessageGetCmdMethod", plainUrlResponsedMessageGetCmdMethod)
            put("plainUrlCustomResponsedMessageClass", plainUrlCustomResponsedMessageClass)
            put("plainUrlCustomResponsedMessageGetDataMethod", plainUrlCustomResponsedMessageGetDataMethod)
            put("plainUrlApplicationClass", plainUrlApplicationClass)
            put("plainUrlApplicationGetInstMethod", plainUrlApplicationGetInstMethod)
            put("privateReadReceiptModelClass", privateReadReceiptModelClass)
            put("privateReadReceiptModelReadDispatchMethod", privateReadReceiptModelReadDispatchMethod)
            put("privateReadReceiptMessageManagerClass", privateReadReceiptMessageManagerClass)
            put("privateReadReceiptMessageManagerGetInstanceMethod", privateReadReceiptMessageManagerGetInstanceMethod)
            put("privateReadReceiptMessageSendMethod", privateReadReceiptMessageSendMethod)
            put("privateReadReceiptMessageBaseClass", privateReadReceiptMessageBaseClass)
            put("privateReadReceiptRequestClass", privateReadReceiptRequestClass)
            put("privateReadReceiptModelBaseClass", privateReadReceiptModelBaseClass)
            put("privateReadReceiptCommitResponseClass", privateReadReceiptCommitResponseClass)
            put("privateReadReceiptProcessAckMethod", privateReadReceiptProcessAckMethod)
            put("privateReadReceiptResponseErrorMethod", privateReadReceiptResponseErrorMethod)
            put("privateReadReceiptRequestMsgIdField", privateReadReceiptRequestMsgIdField)
            put("privateReadReceiptRequestToUidField", privateReadReceiptRequestToUidField)
            put("privateReadReceiptModelDataField", privateReadReceiptModelDataField)
            put("privateReadReceiptPageDataClass", privateReadReceiptPageDataClass)
            put("privateReadReceiptPageDataChatListMethod", privateReadReceiptPageDataChatListMethod)
            put("privateReadReceiptChatMessageClass", privateReadReceiptChatMessageClass)
            put("privateReadReceiptChatMessageMsgIdMethod", privateReadReceiptChatMessageMsgIdMethod)
            put("privateReadReceiptChatMessageUserIdMethod", privateReadReceiptChatMessageUserIdMethod)
            put("privateReadReceiptChatMessageLocalDataMethod", privateReadReceiptChatMessageLocalDataMethod)
            put("privateReadReceiptLocalDataClass", privateReadReceiptLocalDataClass)
            put("privateReadReceiptLocalDataStatusMethod", privateReadReceiptLocalDataStatusMethod)
            put("privateReadReceiptAccountClass", privateReadReceiptAccountClass)
            put("privateReadReceiptCurrentAccountMethod", privateReadReceiptCurrentAccountMethod)
            put("mountCardLinkLayoutClass", mountCardLinkLayoutClass)
            put("mountCardLinkLayoutOnClickMethod", mountCardLinkLayoutOnClickMethod)
            put("mountCardLinkLayoutDataField", mountCardLinkLayoutDataField)
            put("mountCardLinkInfoDataClass", mountCardLinkInfoDataClass)
            put("mountCardLinkInfoGetUrlMethod", mountCardLinkInfoGetUrlMethod)
            put("forumBottomSheetViewClass", forumBottomSheetViewClass)
            put("forumBottomSheetInitScrollMethod", forumBottomSheetInitScrollMethod)
            put("autoRefreshTriggerMethod", autoRefreshTriggerMethod)
            put("autoLoadMoreConfigClass", autoLoadMoreConfigClass)
            put("autoLoadMoreConfigMethod", autoLoadMoreConfigMethod)
            put("pbCommentScrollListenerClass", pbCommentScrollListenerClass)
            put("pbCommentScrollMethod", pbCommentScrollMethod)
            put("pbCommentScrollFragmentField", pbCommentScrollFragmentField)
            put("pbCommentScrollBottomListenerField", pbCommentScrollBottomListenerField)
            put("pbCommentScrollBottomMethod", pbCommentScrollBottomMethod)
            put("pbCommentBottomListScrollClass", pbCommentBottomListScrollClass)
            put("pbCommentBottomListScrollMethod", pbCommentBottomListScrollMethod)
            put("pbCommentBottomListOwnerField", pbCommentBottomListOwnerField)
            put("pbCommentBottomRecyclerScrollClass", pbCommentBottomRecyclerScrollClass)
            put("pbCommentBottomRecyclerScrollMethod", pbCommentBottomRecyclerScrollMethod)
            put("pbCommentBottomRecyclerOwnerField", pbCommentBottomRecyclerOwnerField)
            put("pbGestureScaleManagerClass", pbGestureScaleManagerClass)
            put("pbGestureScaleDispatchMethod", pbGestureScaleDispatchMethod)
            put("pbGestureScaleListenerSetterMethod", pbGestureScaleListenerSetterMethod)
            put("pbGestureScaleListenerClass", pbGestureScaleListenerClass)
            put("pbGestureScaleListenerOnScaleMethod", pbGestureScaleListenerOnScaleMethod)
            put("pbLikeAutoReplyAgreeViewClass", pbLikeAutoReplyAgreeViewClass)
            put("pbLikeAutoReplyAgreeClickMethod", pbLikeAutoReplyAgreeClickMethod)
            put("pbLikeAutoReplyAgreeViewGetDataMethod", pbLikeAutoReplyAgreeViewGetDataMethod)
            put("pbLikeAutoReplyAgreeDataClass", pbLikeAutoReplyAgreeDataClass)
            put("pbLikeAutoReplyAgreeDataHasAgreeField", pbLikeAutoReplyAgreeDataHasAgreeField)
            put("pbLikeAutoReplyAgreeDataAgreeTypeField", pbLikeAutoReplyAgreeDataAgreeTypeField)
            put("pbLikeAutoReplyAgreeDataIsInThreadField", pbLikeAutoReplyAgreeDataIsInThreadField)
            put("pbLikeAutoReplyInputContainerClass", pbLikeAutoReplyInputContainerClass)
            put("pbLikeAutoReplyInputContainerGetInputViewMethod", pbLikeAutoReplyInputContainerGetInputViewMethod)
            put("pbLikeAutoReplyInputContainerGetSendViewMethod", pbLikeAutoReplyInputContainerGetSendViewMethod)

            put("collectionPresenterField", collectionPresenterField)
            put("collectionPresenterListSetterMethod", collectionPresenterListSetterMethod)
            put("collectionPresenterAdapterField", collectionPresenterAdapterField)
            put("collectionModelField", collectionModelField)
            put("collectionModelListGetterMethod", collectionModelListGetterMethod)
            put("collectionModelParseMethod", collectionModelParseMethod)
            put("collectionModelListField", collectionModelListField)
            put("collectionFragmentDisplayListField", collectionFragmentDisplayListField)
            put("collectionActivityNavControllerField", collectionActivityNavControllerField)
            put("collectionNavBarField", collectionNavBarField)
            put("collectionAdapterShowFooterMethod", collectionAdapterShowFooterMethod)
            put("collectionAdapterLoadingMethod", collectionAdapterLoadingMethod)
            put("collectionAdapterHasMoreMethod", collectionAdapterHasMoreMethod)
            put("collectionEditModeMethod", collectionEditModeMethod)

            put("historyAdapterField", historyAdapterField)
            put("historyAdapterSetListMethod", historyAdapterSetListMethod)
            put("historyListField", historyListField)
            put("historyActivityListUpdateMethod", historyActivityListUpdateMethod)
            put("historyActivityNavBarField", historyActivityNavBarField)
            put("historyThreadNameMethod", historyThreadNameMethod)
            put("historyForumNameMethod", historyForumNameMethod)
            put("historyUserNameMethod", historyUserNameMethod)
            put("historyDescriptionMethod", historyDescriptionMethod)
            put("historyThreadIdMethod", historyThreadIdMethod)
            put("historyPostIdMethod", historyPostIdMethod)
            put("historyLiveIdMethod", historyLiveIdMethod)

            put("msgTabLocateToTabMethod", msgTabLocateToTabMethod)
            put("msgTabContainerSelectMethod", msgTabContainerSelectMethod)
            put("msgTabContainerExtDataField", msgTabContainerExtDataField)
            put("freeCopyPopupMenuClass", freeCopyPopupMenuClass)
            put("freeCopyPopupContentViewMethod", freeCopyPopupContentViewMethod)
            put("freeCopyPopupTextField", freeCopyPopupTextField)

            put("mainTabDataClass", mainTabDataClass)
            put("mainTabAddMethod", mainTabAddMethod)
            put("mainTabGetListMethod", mainTabGetListMethod)
            put("mainTabDelegateGetStructureMethod", mainTabDelegateGetStructureMethod)
            put("mainTabStructureTypeField", mainTabStructureTypeField)
            put("mainTabStructureDynamicIconField", mainTabStructureDynamicIconField)
            put("mainTabStructureFragmentField", mainTabStructureFragmentField)

            put("origImagePagerAdapterClass", origImagePagerAdapterClass)
            put("origImageUrlDragImageViewClass", origImageUrlDragImageViewClass)
            put("origImageDataClass", origImageDataClass)
            put("origImageSetPrimaryItemMethod", origImageSetPrimaryItemMethod)
            put("origImageSetAssistUrlMethod", origImageSetAssistUrlMethod)
            put("origImageAssistDataMethod", origImageAssistDataMethod)
            put("origImageOriginTextMethod", origImageOriginTextMethod)
            put("origImageShowButtonField", origImageShowButtonField)
            put("origImageBlockedField", origImageBlockedField)
            put("origImageOriginalProcessField", origImageOriginalProcessField)
            put("origImageOriginalUrlField", origImageOriginalUrlField)
            put("origImageSharedPrefHelperClass", origImageSharedPrefHelperClass)
            put("origImageSharedPrefGetInstanceMethod", origImageSharedPrefGetInstanceMethod)
            put("origImageSharedPrefPutBooleanMethod", origImageSharedPrefPutBooleanMethod)
            put("origImageMd5Class", origImageMd5Class)
            put("origImageMd5Method", origImageMd5Method)
            put("origImagePrimaryReadyMethod", origImagePrimaryReadyMethod)
            put("origImageTriggerMethod", origImageTriggerMethod)
            put("origImageDirectStartMethod", origImageDirectStartMethod)
            put("shareTrackBuilderClass", shareTrackBuilderClass)
            put("shareTrackBuildUrlMethod", shareTrackBuildUrlMethod)
            put("shareTrackAppendQueryMethod", shareTrackAppendQueryMethod)
            put("imageViewerShareConfigClass", imageViewerShareConfigClass)
            put("imageViewerShareIsDialogField", imageViewerShareIsDialogField)
            put("imageViewerShareItemField", imageViewerShareItemField)
            put("imageViewerShareAddOutsideMethod", imageViewerShareAddOutsideMethod)
            put("imageViewerShareGetRequestDataMethod", imageViewerShareGetRequestDataMethod)
            put("imageViewerShareSetRequestDataMethod", imageViewerShareSetRequestDataMethod)
            put("imageViewerShareGetContextMethod", imageViewerShareGetContextMethod)
            put("imageViewerShareItemClass", imageViewerShareItemClass)
            put("imageViewerShareItemTitleField", imageViewerShareItemTitleField)
            put("imageViewerShareItemContentField", imageViewerShareItemContentField)
            put("imageViewerShareItemLinkUrlField", imageViewerShareItemLinkUrlField)
            put("imageViewerShareItemImageUriField", imageViewerShareItemImageUriField)
            put("imageViewerShareItemImageUrlField", imageViewerShareItemImageUrlField)
            put("imageViewerShareItemLocalFileField", imageViewerShareItemLocalFileField)
            put("imageViewerShareItemViewClass", imageViewerShareItemViewClass)
            put("imageViewerShareItemNameByResMethod", imageViewerShareItemNameByResMethod)
            put("imageViewerShareItemNameByTextMethod", imageViewerShareItemNameByTextMethod)
            put("imageViewerShareIconResId", imageViewerShareIconResId)
            put("homeNativeGlassSubPbNextPageMoreViewId", homeNativeGlassSubPbNextPageMoreViewId)
            put("homeNativeGlassPbReplyTitleDividerViewId", homeNativeGlassPbReplyTitleDividerViewId)
            if (homeNativeGlassDynamicBackgroundColorIds.isNotEmpty()) {
                val array = org.json.JSONArray()
                homeNativeGlassDynamicBackgroundColorIds.forEach { array.put(it) }
                put("homeNativeGlassDynamicBackgroundColorIds", array)
            }
            if (homeNativeGlassReadableTextResourceIdsByName.isNotEmpty()) {
                val resourceIds = JSONObject()
                homeNativeGlassReadableTextResourceIdsByName.forEach { (name, id) ->
                    if (name.isNotBlank() && id != 0) resourceIds.put(name, id)
                }
                if (resourceIds.length() > 0) {
                    put("homeNativeGlassReadableTextResourceIdsByName", resourceIds)
                }
            }
            put(
                "homeNativeGlassSortSwitchBackgroundPaintField",
                homeNativeGlassSortSwitchBackgroundPaintField,
            )
            put(
                "homeNativeGlassSortSwitchSlideDrawMethod",
                homeNativeGlassSortSwitchSlideDrawMethod,
            )
            put(
                "homeNativeGlassSortSwitchSlidePathField",
                homeNativeGlassSortSwitchSlidePathField,
            )
            put(
                "homeNativeGlassEnterForumCapsuleControllerClass",
                homeNativeGlassEnterForumCapsuleControllerClass,
            )
            put(
                "homeNativeGlassEnterForumCapsuleInitMethod",
                homeNativeGlassEnterForumCapsuleInitMethod,
            )
            put(
                "homeNativeGlassEnterForumCapsuleRefreshMethod",
                homeNativeGlassEnterForumCapsuleRefreshMethod,
            )
            put(
                "homeNativeGlassEnterForumCapsuleViewField",
                homeNativeGlassEnterForumCapsuleViewField,
            )
            put(
                "homeNativeGlassEnterForumCapsuleTitleField",
                homeNativeGlassEnterForumCapsuleTitleField,
            )
            put("pbCommonLayoutPreloaderGetOrDefaultMethod", pbCommonLayoutPreloaderGetOrDefaultMethod)

            put("feedCardBindMethod", feedCardBindMethod)
            put("feedCardDataListField", feedCardDataListField)
            put("feedHeadParamsField", feedHeadParamsField)
            put("feedRecommendCardNestedDataMethod", feedRecommendCardNestedDataMethod)
            put("feedRecommendCardNestedDataListField", feedRecommendCardNestedDataListField)
            put("replyServerResponseClass", replyServerResponseClass)
            put("replyServerResponseDecodeMethod", replyServerResponseDecodeMethod)
            put("replyServerResponseResultJsonField", replyServerResponseResultJsonField)
            put("aiSpriteMemePanControllerClass", aiSpriteMemePanControllerClass)
            put("aiSpriteMemeEnableMethod", aiSpriteMemeEnableMethod)
            put("aiPbNewInputContainerClass", aiPbNewInputContainerClass)
            put("aiPbNewInputContainerInitSpriteMemeMethod", aiPbNewInputContainerInitSpriteMemeMethod)
            put("aiPbNewInputContainerInitAiWriteMethod", aiPbNewInputContainerInitAiWriteMethod)
            put("aiPbAiEmojiCreationViewBindMethod", aiPbAiEmojiCreationViewBindMethod)
            put("aiPbPageBrowserAiEmojiCreationBindMethod", aiPbPageBrowserAiEmojiCreationBindMethod)
            put("aiImageViewerJumpButtonOwnerClass", aiImageViewerJumpButtonOwnerClass)
            put("aiImageViewerJumpButtonInitMethod", aiImageViewerJumpButtonInitMethod)

            val statusObj = JSONObject()
            for ((featureKey, featureStatus) in featureStatusMap) {
                statusObj.put(
                    featureKey,
                    JSONObject().apply {
                        put("state", featureStatus.state)
                        val critical = org.json.JSONArray()
                        featureStatus.missingCritical.forEach { critical.put(it) }
                        put("missingCritical", critical)
                        val optional = org.json.JSONArray()
                        featureStatus.missingOptional.forEach { optional.put(it) }
                        put("missingOptional", optional)
                    },
                )
            }
            put("featureStatusMap", statusObj)

            put("scanSupportState", scanSupportState)
            put("scanTargetVersionCode", scanTargetVersionCode)
            put("scanTargetVersionName", scanTargetVersionName)
            put("scanTargetVersionType", scanTargetVersionType)
            if (scanErrors.isNotEmpty()) {
                val array = org.json.JSONArray()
                scanErrors.forEach { array.put(it) }
                put("scanErrors", array)
            }

            put("source", source)
            put("createdAt", createdAt)
            put("cacheSchemaVersion", cacheSchemaVersion)
        }.toString()
    }

    companion object {
        const val CACHE_SCHEMA_VERSION = 2

        fun unsupported(
            featureStatusMap: Map<String, HookFeatureStatus> = emptyMap(),
            scanErrors: List<String> = emptyList(),
            createdAt: Long = 0L,
        ): HookSymbols {
            return buildHookSymbols {
                this.featureStatusMap = featureStatusMap
                this.scanErrors = scanErrors
                source = "unsupported"
                this.createdAt = createdAt
            }
        }


        fun fromJson(json: String?): HookSymbols? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val zgaMethodsArray = obj.optJSONArray("zgaMethods")
                val zgaMethodsList = if (zgaMethodsArray != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until zgaMethodsArray.length()) {
                        list.add(zgaMethodsArray.getString(i))
                    }
                    list
                } else null

                val anchorClassesArray = obj.optJSONArray("homePersonalizeAnchorClasses")
                val anchorClassesList = if (anchorClassesArray != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until anchorClassesArray.length()) {
                        list.add(anchorClassesArray.getString(i))
                    }
                    list
                } else null

                val featureStatusMap = mutableMapOf<String, HookFeatureStatus>()
                val statusObj = obj.optJSONObject("featureStatusMap")
                if (statusObj != null) {
                    val names = statusObj.keys()
                    while (names.hasNext()) {
                        val featureKey = names.next()
                        val statusValue = statusObj.optJSONObject(featureKey) ?: continue
                        val state = statusValue.optString("state", HookFeatureState.DISABLED)
                        val missingCritical = statusValue.optStringArray("missingCritical")
                        val missingOptional = statusValue.optStringArray("missingOptional")
                        featureStatusMap[featureKey] = HookFeatureStatus(
                            state = state,
                            missingCritical = missingCritical,
                            missingOptional = missingOptional,
                        )
                    }
                }

                buildHookSymbols {
                    homeTabClass = obj.optStringOrNull("homeTabClass")
                    homeTabRebuildMethod = obj.optStringOrNull("homeTabRebuildMethod")
                    homeTabListField = obj.optStringOrNull("homeTabListField")
                    homeTabItemTypeField = obj.optStringOrNull("homeTabItemTypeField")
                    homeTabItemCodeField = obj.optStringOrNull("homeTabItemCodeField")
                    homeTabItemNameField = obj.optStringOrNull("homeTabItemNameField")
                    homeTabItemUrlField = obj.optStringOrNull("homeTabItemUrlField")
                    homeTabItemMainSetterMethod = obj.optStringOrNull("homeTabItemMainSetterMethod")
                    homeTabItemMainIntField = obj.optStringOrNull("homeTabItemMainIntField")
                    homeTabItemMainBooleanField = obj.optStringOrNull("homeTabItemMainBooleanField")
                    settingsClass = obj.optStringOrNull("settingsClass")
                    settingsInitMethod = obj.optStringOrNull("settingsInitMethod")
                    settingsContainerField = obj.optStringOrNull("settingsContainerField")
                    feedTemplateKeyMethod = obj.optStringOrNull("feedTemplateKeyMethod")
                    feedTemplatePayloadMethod = obj.optStringOrNull("feedTemplatePayloadMethod")
                    feedTemplateLoadMoreMethod = obj.optStringOrNull("feedTemplateLoadMoreMethod")
                    splashAdHelperClass = obj.optStringOrNull("splashAdHelperClass")
                    splashAdHelperMethod = obj.optStringOrNull("splashAdHelperMethod")
                    closeAdDataClass = obj.optStringOrNull("closeAdDataClass")
                    closeAdDataMethodG1 = obj.optStringOrNull("closeAdDataMethodG1")
                    closeAdDataMethodJ1 = obj.optStringOrNull("closeAdDataMethodJ1")

                    zgaClass = obj.optStringOrNull("zgaClass")
                    zgaMethods = zgaMethodsList
                    searchBoxViewClass = obj.optStringOrNull("searchBoxViewClass")
                    searchBoxSetHintMethod = obj.optStringOrNull("searchBoxSetHintMethod")
                    homeSearchBoxOwnerClass = obj.optStringOrNull("homeSearchBoxOwnerClass")
                    homeSearchBoxInitMethod = obj.optStringOrNull("homeSearchBoxInitMethod")
                    homeSearchBoxGetterMethod = obj.optStringOrNull("homeSearchBoxGetterMethod")
                    homePersonalizeAnchorClasses = anchorClassesList
                    homeRightSlotClass = obj.optStringOrNull("homeRightSlotClass")
                    homeRightSlotStateMethods = obj.optStringArray("homeRightSlotStateMethods")
                        .takeIf { it.isNotEmpty() }
                    pbFallingViewClass = obj.optStringOrNull("pbFallingViewClass")
                    pbFallingInitMethod = obj.optStringOrNull("pbFallingInitMethod")
                    pbFallingShowMethod = obj.optStringOrNull("pbFallingShowMethod")
                    pbFallingClearMethod = obj.optStringOrNull("pbFallingClearMethod")
                    pbEarlyAdInsertClass = obj.optStringOrNull("pbEarlyAdInsertClass")
                    pbEarlyAdInsertMethodSpecs = obj.optStringArray("pbEarlyAdInsertMethodSpecs")
                        .takeIf { it.isNotEmpty() }
                    pbAdBidCommonRequestModelClass = obj.optStringOrNull("pbAdBidCommonRequestModelClass")
                    pbAdBidCommonRequestStartMethods = obj.optStringArray("pbAdBidCommonRequestStartMethods")
                        .takeIf { it.isNotEmpty() }
                    pbAdBidCommonRequestNotifyMethod = obj.optStringOrNull("pbAdBidCommonRequestNotifyMethod")
                    pbAdBidPageBrowserRequestModelClass = obj.optStringOrNull("pbAdBidPageBrowserRequestModelClass")
                    pbAdBidPageBrowserRequestDataMethod = obj.optStringOrNull("pbAdBidPageBrowserRequestDataMethod")
                    typeAdapterSetDataMethod = obj.optStringOrNull("typeAdapterSetDataMethod")
                    typeAdapterDataItemClass = obj.optStringOrNull("typeAdapterDataItemClass")
                    typeAdapterDataGetTypeMethod = obj.optStringOrNull("typeAdapterDataGetTypeMethod")
                    enterForumWebControllerClass = obj.optStringOrNull("enterForumWebControllerClass")
                    enterForumWebLoadMethod = obj.optStringOrNull("enterForumWebLoadMethod")
                    enterForumInitInfoDataClass = obj.optStringOrNull("enterForumInitInfoDataClass")
                    enterForumInitInfoGetUrlMethod = obj.optStringOrNull("enterForumInitInfoGetUrlMethod")
                    plainUrlClickableSpanClass = obj.optStringOrNull("plainUrlClickableSpanClass")
                    plainUrlClickableSpanOnClickMethod = obj.optStringOrNull("plainUrlClickableSpanOnClickMethod")
                    plainUrlClickableSpanOnClickOwnerClasses = obj.optStringArray("plainUrlClickableSpanOnClickOwnerClasses")
                        .takeIf { it.isNotEmpty() }
                    plainUrlClickableSpanTypeField = obj.optStringOrNull("plainUrlClickableSpanTypeField")
                    plainUrlClickableSpanUrlField = obj.optStringOrNull("plainUrlClickableSpanUrlField")
                    plainUrlClickableSpanTextField = obj.optStringOrNull("plainUrlClickableSpanTextField")
                    plainUrlMessageManagerClass = obj.optStringOrNull("plainUrlMessageManagerClass")
                    plainUrlMessageDispatchMethod = obj.optStringOrNull("plainUrlMessageDispatchMethod")
                    plainUrlResponsedMessageClass = obj.optStringOrNull("plainUrlResponsedMessageClass")
                    plainUrlResponsedMessageGetCmdMethod = obj.optStringOrNull("plainUrlResponsedMessageGetCmdMethod")
                    plainUrlCustomResponsedMessageClass = obj.optStringOrNull("plainUrlCustomResponsedMessageClass")
                    plainUrlCustomResponsedMessageGetDataMethod = obj.optStringOrNull("plainUrlCustomResponsedMessageGetDataMethod")
                    plainUrlApplicationClass = obj.optStringOrNull("plainUrlApplicationClass")
                    plainUrlApplicationGetInstMethod = obj.optStringOrNull("plainUrlApplicationGetInstMethod")
                    privateReadReceiptModelClass = obj.optStringOrNull("privateReadReceiptModelClass")
                    privateReadReceiptModelReadDispatchMethod = obj.optStringOrNull("privateReadReceiptModelReadDispatchMethod")
                    privateReadReceiptMessageManagerClass = obj.optStringOrNull("privateReadReceiptMessageManagerClass")
                    privateReadReceiptMessageManagerGetInstanceMethod = obj.optStringOrNull("privateReadReceiptMessageManagerGetInstanceMethod")
                    privateReadReceiptMessageSendMethod = obj.optStringOrNull("privateReadReceiptMessageSendMethod")
                    privateReadReceiptMessageBaseClass = obj.optStringOrNull("privateReadReceiptMessageBaseClass")
                    privateReadReceiptRequestClass = obj.optStringOrNull("privateReadReceiptRequestClass")
                    privateReadReceiptModelBaseClass = obj.optStringOrNull("privateReadReceiptModelBaseClass")
                    privateReadReceiptCommitResponseClass = obj.optStringOrNull("privateReadReceiptCommitResponseClass")
                    privateReadReceiptProcessAckMethod = obj.optStringOrNull("privateReadReceiptProcessAckMethod")
                    privateReadReceiptResponseErrorMethod = obj.optStringOrNull("privateReadReceiptResponseErrorMethod")
                    privateReadReceiptRequestMsgIdField = obj.optStringOrNull("privateReadReceiptRequestMsgIdField")
                    privateReadReceiptRequestToUidField = obj.optStringOrNull("privateReadReceiptRequestToUidField")
                    privateReadReceiptModelDataField = obj.optStringOrNull("privateReadReceiptModelDataField")
                    privateReadReceiptPageDataClass = obj.optStringOrNull("privateReadReceiptPageDataClass")
                    privateReadReceiptPageDataChatListMethod = obj.optStringOrNull("privateReadReceiptPageDataChatListMethod")
                    privateReadReceiptChatMessageClass = obj.optStringOrNull("privateReadReceiptChatMessageClass")
                    privateReadReceiptChatMessageMsgIdMethod = obj.optStringOrNull("privateReadReceiptChatMessageMsgIdMethod")
                    privateReadReceiptChatMessageUserIdMethod = obj.optStringOrNull("privateReadReceiptChatMessageUserIdMethod")
                    privateReadReceiptChatMessageLocalDataMethod = obj.optStringOrNull("privateReadReceiptChatMessageLocalDataMethod")
                    privateReadReceiptLocalDataClass = obj.optStringOrNull("privateReadReceiptLocalDataClass")
                    privateReadReceiptLocalDataStatusMethod = obj.optStringOrNull("privateReadReceiptLocalDataStatusMethod")
                    privateReadReceiptAccountClass = obj.optStringOrNull("privateReadReceiptAccountClass")
                    privateReadReceiptCurrentAccountMethod = obj.optStringOrNull("privateReadReceiptCurrentAccountMethod")
                    mountCardLinkLayoutClass = obj.optStringOrNull("mountCardLinkLayoutClass")
                    mountCardLinkLayoutOnClickMethod = obj.optStringOrNull("mountCardLinkLayoutOnClickMethod")
                    mountCardLinkLayoutDataField = obj.optStringOrNull("mountCardLinkLayoutDataField")
                    mountCardLinkInfoDataClass = obj.optStringOrNull("mountCardLinkInfoDataClass")
                    mountCardLinkInfoGetUrlMethod = obj.optStringOrNull("mountCardLinkInfoGetUrlMethod")
                    forumBottomSheetViewClass = obj.optStringOrNull("forumBottomSheetViewClass")
                    forumBottomSheetInitScrollMethod = obj.optStringOrNull("forumBottomSheetInitScrollMethod")
                    autoRefreshTriggerMethod = obj.optStringOrNull("autoRefreshTriggerMethod")
                    autoLoadMoreConfigClass = obj.optStringOrNull("autoLoadMoreConfigClass")
                    autoLoadMoreConfigMethod = obj.optStringOrNull("autoLoadMoreConfigMethod")
                    pbCommentScrollListenerClass = obj.optStringOrNull("pbCommentScrollListenerClass")
                    pbCommentScrollMethod = obj.optStringOrNull("pbCommentScrollMethod")
                    pbCommentScrollFragmentField = obj.optStringOrNull("pbCommentScrollFragmentField")
                    pbCommentScrollBottomListenerField = obj.optStringOrNull("pbCommentScrollBottomListenerField")
                    pbCommentScrollBottomMethod = obj.optStringOrNull("pbCommentScrollBottomMethod")
                    pbCommentBottomListScrollClass = obj.optStringOrNull("pbCommentBottomListScrollClass")
                    pbCommentBottomListScrollMethod = obj.optStringOrNull("pbCommentBottomListScrollMethod")
                    pbCommentBottomListOwnerField = obj.optStringOrNull("pbCommentBottomListOwnerField")
                    pbCommentBottomRecyclerScrollClass = obj.optStringOrNull("pbCommentBottomRecyclerScrollClass")
                    pbCommentBottomRecyclerScrollMethod = obj.optStringOrNull("pbCommentBottomRecyclerScrollMethod")
                    pbCommentBottomRecyclerOwnerField = obj.optStringOrNull("pbCommentBottomRecyclerOwnerField")
                    pbGestureScaleManagerClass = obj.optStringOrNull("pbGestureScaleManagerClass")
                    pbGestureScaleDispatchMethod = obj.optStringOrNull("pbGestureScaleDispatchMethod")
                    pbGestureScaleListenerSetterMethod = obj.optStringOrNull("pbGestureScaleListenerSetterMethod")
                    pbGestureScaleListenerClass = obj.optStringOrNull("pbGestureScaleListenerClass")
                    pbGestureScaleListenerOnScaleMethod = obj.optStringOrNull("pbGestureScaleListenerOnScaleMethod")
                    pbLikeAutoReplyAgreeViewClass = obj.optStringOrNull("pbLikeAutoReplyAgreeViewClass")
                    pbLikeAutoReplyAgreeClickMethod = obj.optStringOrNull("pbLikeAutoReplyAgreeClickMethod")
                    pbLikeAutoReplyAgreeViewGetDataMethod = obj.optStringOrNull("pbLikeAutoReplyAgreeViewGetDataMethod")
                    pbLikeAutoReplyAgreeDataClass = obj.optStringOrNull("pbLikeAutoReplyAgreeDataClass")
                    pbLikeAutoReplyAgreeDataHasAgreeField = obj.optStringOrNull("pbLikeAutoReplyAgreeDataHasAgreeField")
                    pbLikeAutoReplyAgreeDataAgreeTypeField = obj.optStringOrNull("pbLikeAutoReplyAgreeDataAgreeTypeField")
                    pbLikeAutoReplyAgreeDataIsInThreadField =
                        obj.optStringOrNull("pbLikeAutoReplyAgreeDataIsInThreadField")
                    pbLikeAutoReplyInputContainerClass = obj.optStringOrNull("pbLikeAutoReplyInputContainerClass")
                    pbLikeAutoReplyInputContainerGetInputViewMethod =
                        obj.optStringOrNull("pbLikeAutoReplyInputContainerGetInputViewMethod")
                    pbLikeAutoReplyInputContainerGetSendViewMethod =
                        obj.optStringOrNull("pbLikeAutoReplyInputContainerGetSendViewMethod")

                    collectionPresenterField = obj.optStringOrNull("collectionPresenterField")
                    collectionPresenterListSetterMethod = obj.optStringOrNull("collectionPresenterListSetterMethod")
                    collectionPresenterAdapterField = obj.optStringOrNull("collectionPresenterAdapterField")
                    collectionModelField = obj.optStringOrNull("collectionModelField")
                    collectionModelListGetterMethod = obj.optStringOrNull("collectionModelListGetterMethod")
                    collectionModelParseMethod = obj.optStringOrNull("collectionModelParseMethod")
                    collectionModelListField = obj.optStringOrNull("collectionModelListField")
                    collectionFragmentDisplayListField = obj.optStringOrNull("collectionFragmentDisplayListField")
                    collectionActivityNavControllerField = obj.optStringOrNull("collectionActivityNavControllerField")
                    collectionNavBarField = obj.optStringOrNull("collectionNavBarField")
                    collectionAdapterShowFooterMethod = obj.optStringOrNull("collectionAdapterShowFooterMethod")
                    collectionAdapterLoadingMethod = obj.optStringOrNull("collectionAdapterLoadingMethod")
                    collectionAdapterHasMoreMethod = obj.optStringOrNull("collectionAdapterHasMoreMethod")
                    collectionEditModeMethod = obj.optStringOrNull("collectionEditModeMethod")

                    historyAdapterField = obj.optStringOrNull("historyAdapterField")
                    historyAdapterSetListMethod = obj.optStringOrNull("historyAdapterSetListMethod")
                    historyListField = obj.optStringOrNull("historyListField")
                    historyActivityListUpdateMethod = obj.optStringOrNull("historyActivityListUpdateMethod")
                    historyActivityNavBarField = obj.optStringOrNull("historyActivityNavBarField")
                    historyThreadNameMethod = obj.optStringOrNull("historyThreadNameMethod")
                    historyForumNameMethod = obj.optStringOrNull("historyForumNameMethod")
                    historyUserNameMethod = obj.optStringOrNull("historyUserNameMethod")
                    historyDescriptionMethod = obj.optStringOrNull("historyDescriptionMethod")
                    historyThreadIdMethod = obj.optStringOrNull("historyThreadIdMethod")
                    historyPostIdMethod = obj.optStringOrNull("historyPostIdMethod")
                    historyLiveIdMethod = obj.optStringOrNull("historyLiveIdMethod")

                    msgTabLocateToTabMethod = obj.optStringOrNull("msgTabLocateToTabMethod")
                    msgTabContainerSelectMethod = obj.optStringOrNull("msgTabContainerSelectMethod")
                    msgTabContainerExtDataField = obj.optStringOrNull("msgTabContainerExtDataField")
                    freeCopyPopupMenuClass = obj.optStringOrNull("freeCopyPopupMenuClass")
                    freeCopyPopupContentViewMethod = obj.optStringOrNull("freeCopyPopupContentViewMethod")
                    freeCopyPopupTextField = obj.optStringOrNull("freeCopyPopupTextField")

                    mainTabDataClass = obj.optStringOrNull("mainTabDataClass")
                    mainTabAddMethod = obj.optStringOrNull("mainTabAddMethod")
                    mainTabGetListMethod = obj.optStringOrNull("mainTabGetListMethod")
                    mainTabDelegateGetStructureMethod = obj.optStringOrNull("mainTabDelegateGetStructureMethod")
                    mainTabStructureTypeField = obj.optStringOrNull("mainTabStructureTypeField")
                    mainTabStructureDynamicIconField = obj.optStringOrNull("mainTabStructureDynamicIconField")
                    mainTabStructureFragmentField = obj.optStringOrNull("mainTabStructureFragmentField")

                    origImagePagerAdapterClass = obj.optStringOrNull("origImagePagerAdapterClass")
                    origImageUrlDragImageViewClass = obj.optStringOrNull("origImageUrlDragImageViewClass")
                    origImageDataClass = obj.optStringOrNull("origImageDataClass")
                    origImageSetPrimaryItemMethod = obj.optStringOrNull("origImageSetPrimaryItemMethod")
                    origImageSetAssistUrlMethod = obj.optStringOrNull("origImageSetAssistUrlMethod")
                    origImageAssistDataMethod = obj.optStringOrNull("origImageAssistDataMethod")
                    origImageOriginTextMethod = obj.optStringOrNull("origImageOriginTextMethod")
                    origImageShowButtonField = obj.optStringOrNull("origImageShowButtonField")
                    origImageBlockedField = obj.optStringOrNull("origImageBlockedField")
                    origImageOriginalProcessField = obj.optStringOrNull("origImageOriginalProcessField")
                    origImageOriginalUrlField = obj.optStringOrNull("origImageOriginalUrlField")
                    origImageSharedPrefHelperClass = obj.optStringOrNull("origImageSharedPrefHelperClass")
                    origImageSharedPrefGetInstanceMethod = obj.optStringOrNull("origImageSharedPrefGetInstanceMethod")
                    origImageSharedPrefPutBooleanMethod = obj.optStringOrNull("origImageSharedPrefPutBooleanMethod")
                    origImageMd5Class = obj.optStringOrNull("origImageMd5Class")
                    origImageMd5Method = obj.optStringOrNull("origImageMd5Method")
                    origImagePrimaryReadyMethod = obj.optStringOrNull("origImagePrimaryReadyMethod")
                    origImageTriggerMethod = obj.optStringOrNull("origImageTriggerMethod")
                    origImageDirectStartMethod = obj.optStringOrNull("origImageDirectStartMethod")
                    shareTrackBuilderClass = obj.optStringOrNull("shareTrackBuilderClass")
                    shareTrackBuildUrlMethod = obj.optStringOrNull("shareTrackBuildUrlMethod")
                    shareTrackAppendQueryMethod = obj.optStringOrNull("shareTrackAppendQueryMethod")
                    imageViewerShareConfigClass = obj.optStringOrNull("imageViewerShareConfigClass")
                    imageViewerShareIsDialogField = obj.optStringOrNull("imageViewerShareIsDialogField")
                    imageViewerShareItemField = obj.optStringOrNull("imageViewerShareItemField")
                    imageViewerShareAddOutsideMethod = obj.optStringOrNull("imageViewerShareAddOutsideMethod")
                    imageViewerShareGetRequestDataMethod = obj.optStringOrNull("imageViewerShareGetRequestDataMethod")
                    imageViewerShareSetRequestDataMethod = obj.optStringOrNull("imageViewerShareSetRequestDataMethod")
                    imageViewerShareGetContextMethod = obj.optStringOrNull("imageViewerShareGetContextMethod")
                    imageViewerShareItemClass = obj.optStringOrNull("imageViewerShareItemClass")
                    imageViewerShareItemTitleField = obj.optStringOrNull("imageViewerShareItemTitleField")
                    imageViewerShareItemContentField = obj.optStringOrNull("imageViewerShareItemContentField")
                    imageViewerShareItemLinkUrlField = obj.optStringOrNull("imageViewerShareItemLinkUrlField")
                    imageViewerShareItemImageUriField = obj.optStringOrNull("imageViewerShareItemImageUriField")
                    imageViewerShareItemImageUrlField = obj.optStringOrNull("imageViewerShareItemImageUrlField")
                    imageViewerShareItemLocalFileField = obj.optStringOrNull("imageViewerShareItemLocalFileField")
                    imageViewerShareItemViewClass = obj.optStringOrNull("imageViewerShareItemViewClass")
                    imageViewerShareItemNameByResMethod = obj.optStringOrNull("imageViewerShareItemNameByResMethod")
                    imageViewerShareItemNameByTextMethod = obj.optStringOrNull("imageViewerShareItemNameByTextMethod")
                    imageViewerShareIconResId = obj.optIntOrNull("imageViewerShareIconResId")
                    homeNativeGlassSubPbNextPageMoreViewId = obj.optIntOrNull("homeNativeGlassSubPbNextPageMoreViewId")
                    homeNativeGlassPbReplyTitleDividerViewId = obj.optIntOrNull("homeNativeGlassPbReplyTitleDividerViewId")
                    homeNativeGlassDynamicBackgroundColorIds =
                        obj.optIntArray("homeNativeGlassDynamicBackgroundColorIds")
                    homeNativeGlassReadableTextResourceIdsByName =
                        obj.optStringIntMap("homeNativeGlassReadableTextResourceIdsByName")
                    homeNativeGlassSortSwitchBackgroundPaintField =
                        obj.optStringOrNull("homeNativeGlassSortSwitchBackgroundPaintField")
                    homeNativeGlassSortSwitchSlideDrawMethod =
                        obj.optStringOrNull("homeNativeGlassSortSwitchSlideDrawMethod")
                    homeNativeGlassSortSwitchSlidePathField =
                        obj.optStringOrNull("homeNativeGlassSortSwitchSlidePathField")
                    homeNativeGlassEnterForumCapsuleControllerClass =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleControllerClass")
                    homeNativeGlassEnterForumCapsuleInitMethod =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleInitMethod")
                    homeNativeGlassEnterForumCapsuleRefreshMethod =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleRefreshMethod")
                    homeNativeGlassEnterForumCapsuleViewField =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleViewField")
                    homeNativeGlassEnterForumCapsuleTitleField =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleTitleField")
                    pbCommonLayoutPreloaderGetOrDefaultMethod =
                        obj.optStringOrNull("pbCommonLayoutPreloaderGetOrDefaultMethod")

                    feedCardBindMethod = obj.optStringOrNull("feedCardBindMethod")
                    feedCardDataListField = obj.optStringOrNull("feedCardDataListField")
                    feedHeadParamsField = obj.optStringOrNull("feedHeadParamsField")
                    feedRecommendCardNestedDataMethod = obj.optStringOrNull("feedRecommendCardNestedDataMethod")
                    feedRecommendCardNestedDataListField = obj.optStringOrNull("feedRecommendCardNestedDataListField")
                    replyServerResponseClass = obj.optStringOrNull("replyServerResponseClass")
                    replyServerResponseDecodeMethod = obj.optStringOrNull("replyServerResponseDecodeMethod")
                    replyServerResponseResultJsonField =
                        obj.optStringOrNull("replyServerResponseResultJsonField")
                    aiSpriteMemePanControllerClass = obj.optStringOrNull("aiSpriteMemePanControllerClass")
                    aiSpriteMemeEnableMethod = obj.optStringOrNull("aiSpriteMemeEnableMethod")
                    aiPbNewInputContainerClass = obj.optStringOrNull("aiPbNewInputContainerClass")
                    aiPbNewInputContainerInitSpriteMemeMethod = obj.optStringOrNull("aiPbNewInputContainerInitSpriteMemeMethod")
                    aiPbNewInputContainerInitAiWriteMethod = obj.optStringOrNull("aiPbNewInputContainerInitAiWriteMethod")
                    aiPbAiEmojiCreationViewBindMethod = obj.optStringOrNull("aiPbAiEmojiCreationViewBindMethod")
                    aiPbPageBrowserAiEmojiCreationBindMethod = obj.optStringOrNull("aiPbPageBrowserAiEmojiCreationBindMethod")
                    aiImageViewerJumpButtonOwnerClass = obj.optStringOrNull("aiImageViewerJumpButtonOwnerClass")
                    aiImageViewerJumpButtonInitMethod = obj.optStringOrNull("aiImageViewerJumpButtonInitMethod")

                    this.featureStatusMap = featureStatusMap
                    scanSupportState = obj.optString("scanSupportState", ScanSupportState.UNKNOWN)
                    scanTargetVersionCode = obj.optLongOrNull("scanTargetVersionCode")
                    scanTargetVersionName = obj.optStringOrNull("scanTargetVersionName")
                    scanTargetVersionType = obj.optStringOrNull("scanTargetVersionType")
                    scanErrors = obj.optStringArray("scanErrors")

                    source = obj.optString("source", "unsupported")
                    createdAt = obj.optLong("createdAt", 0L)
                    cacheSchemaVersion = obj.optInt("cacheSchemaVersion", 0)
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun JSONObject.optStringOrNull(name: String): String? {
            if (isNull(name)) return null
            val s = optString(name)
            return s.ifEmpty { null }
        }

        private fun JSONObject.optIntOrNull(name: String): Int? {
            if (!has(name) || isNull(name)) return null
            return optInt(name).takeIf { it != 0 }
        }

        private fun JSONObject.optLongOrNull(name: String): Long? {
            if (!has(name) || isNull(name)) return null
            return try {
                getLong(name)
            } catch (_: Throwable) {
                null
            }
        }

        private fun JSONObject.optStringArray(name: String): List<String> {
            val array = optJSONArray(name) ?: return emptyList()
            val out = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) out.add(value)
            }
            return out
        }

        private fun JSONObject.optIntArray(name: String): List<Int> {
            val array = optJSONArray(name) ?: return emptyList()
            val out = ArrayList<Int>(array.length())
            for (i in 0 until array.length()) {
                val value = array.optInt(i, 0)
                if (value != 0) out.add(value)
            }
            return out
        }

        private fun JSONObject.optStringIntMap(name: String): Map<String, Int> {
            val obj = optJSONObject(name) ?: return emptyMap()
            val out = LinkedHashMap<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optInt(key, 0)
                if (key.isNotBlank() && value != 0) out[key] = value
            }
            return out
        }
    }
}
