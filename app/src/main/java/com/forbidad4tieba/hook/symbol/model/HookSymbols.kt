package com.forbidad4tieba.hook.symbol.model

import org.json.JSONObject

data class HookSymbols(
    val homeTabClass: String? = null,
    val homeTabRebuildMethod: String? = null,
    val homeTabListField: String? = null,
    val homeTabItemTypeField: String? = null,
    val homeTabItemCodeField: String? = null,
    val homeTabItemNameField: String? = null,
    val homeTabItemUrlField: String? = null,
    val homeTabItemMainSetterMethod: String? = null,
    val homeTabItemMainIntField: String? = null,
    val homeTabItemMainBooleanField: String? = null,
    val settingsClass: String? = null,
    val settingsInitMethod: String? = null,
    val settingsContainerField: String? = null,
    val feedTemplateKeyMethod: String? = null,
    val feedTemplatePayloadMethod: String? = null,
    val feedTemplateLoadMoreMethod: String? = null,
    val splashAdHelperClass: String? = null,
    val splashAdHelperMethod: String? = null,
    val closeAdDataClass: String? = null,
    val closeAdDataMethodG1: String? = null,
    val closeAdDataMethodJ1: String? = null,

    val zgaClass: String? = null,
    val zgaMethods: List<String>? = null,
    val searchBoxViewClass: String? = null,
    val searchBoxSetHintMethod: String? = null,
    val homeSearchBoxOwnerClass: String? = null,
    val homeSearchBoxInitMethod: String? = null,
    val homeSearchBoxGetterMethod: String? = null,
    val homePersonalizeAnchorClasses: List<String>? = null,
    val homeRightSlotClass: String? = null,
    val homeRightSlotStateMethods: List<String>? = null,
    val pbFallingViewClass: String? = null,
    val pbFallingInitMethod: String? = null,
    val pbFallingShowMethod: String? = null,
    val pbFallingClearMethod: String? = null,
    val pbEarlyAdInsertClass: String? = null,
    val pbEarlyAdInsertMethodSpecs: List<String>? = null,
    val pbAdBidCommonRequestModelClass: String? = null,
    val pbAdBidCommonRequestStartMethods: List<String>? = null,
    val pbAdBidCommonRequestNotifyMethod: String? = null,
    val pbAdBidPageBrowserRequestModelClass: String? = null,
    val pbAdBidPageBrowserRequestDataMethod: String? = null,
    val typeAdapterSetDataMethod: String? = null,
    val typeAdapterDataItemClass: String? = null,
    val typeAdapterDataGetTypeMethod: String? = null,
    val enterForumWebControllerClass: String? = null,
    val enterForumWebLoadMethod: String? = null,
    val enterForumInitInfoDataClass: String? = null,
    val enterForumInitInfoGetUrlMethod: String? = null,
    val plainUrlClickableSpanClass: String? = null,
    val plainUrlClickableSpanOnClickMethod: String? = null,
    val plainUrlClickableSpanOnClickOwnerClasses: List<String>? = null,
    val plainUrlClickableSpanTypeField: String? = null,
    val plainUrlClickableSpanUrlField: String? = null,
    val plainUrlClickableSpanTextField: String? = null,
    val plainUrlMessageManagerClass: String? = null,
    val plainUrlMessageDispatchMethod: String? = null,
    val plainUrlResponsedMessageClass: String? = null,
    val plainUrlResponsedMessageGetCmdMethod: String? = null,
    val plainUrlCustomResponsedMessageClass: String? = null,
    val plainUrlCustomResponsedMessageGetDataMethod: String? = null,
    val plainUrlApplicationClass: String? = null,
    val plainUrlApplicationGetInstMethod: String? = null,
    val privateReadReceiptModelClass: String? = null,
    val privateReadReceiptModelReadDispatchMethod: String? = null,
    val privateReadReceiptMessageManagerClass: String? = null,
    val privateReadReceiptMessageManagerGetInstanceMethod: String? = null,
    val privateReadReceiptMessageSendMethod: String? = null,
    val privateReadReceiptMessageBaseClass: String? = null,
    val privateReadReceiptRequestClass: String? = null,
    val privateReadReceiptModelBaseClass: String? = null,
    val privateReadReceiptCommitResponseClass: String? = null,
    val privateReadReceiptProcessAckMethod: String? = null,
    val privateReadReceiptResponseErrorMethod: String? = null,
    val privateReadReceiptRequestMsgIdField: String? = null,
    val privateReadReceiptRequestToUidField: String? = null,
    val privateReadReceiptModelDataField: String? = null,
    val privateReadReceiptPageDataClass: String? = null,
    val privateReadReceiptPageDataChatListMethod: String? = null,
    val privateReadReceiptChatMessageClass: String? = null,
    val privateReadReceiptChatMessageMsgIdMethod: String? = null,
    val privateReadReceiptChatMessageUserIdMethod: String? = null,
    val privateReadReceiptChatMessageLocalDataMethod: String? = null,
    val privateReadReceiptLocalDataClass: String? = null,
    val privateReadReceiptLocalDataStatusMethod: String? = null,
    val privateReadReceiptAccountClass: String? = null,
    val privateReadReceiptCurrentAccountMethod: String? = null,
    val mountCardLinkLayoutClass: String? = null,
    val mountCardLinkLayoutOnClickMethod: String? = null,
    val mountCardLinkLayoutDataField: String? = null,
    val mountCardLinkInfoDataClass: String? = null,
    val mountCardLinkInfoGetUrlMethod: String? = null,
    val forumBottomSheetViewClass: String? = null,
    val forumBottomSheetInitScrollMethod: String? = null,
    val autoRefreshTriggerMethod: String? = null,
    val autoLoadMoreConfigClass: String? = null,
    val autoLoadMoreConfigMethod: String? = null,
    val pbCommentScrollListenerClass: String? = null,
    val pbCommentScrollMethod: String? = null,
    val pbCommentScrollFragmentField: String? = null,
    val pbCommentScrollBottomListenerField: String? = null,
    val pbCommentScrollBottomMethod: String? = null,
    val pbCommentBottomListScrollClass: String? = null,
    val pbCommentBottomListScrollMethod: String? = null,
    val pbCommentBottomListOwnerField: String? = null,
    val pbCommentBottomRecyclerScrollClass: String? = null,
    val pbCommentBottomRecyclerScrollMethod: String? = null,
    val pbCommentBottomRecyclerOwnerField: String? = null,
    val pbGestureScaleManagerClass: String? = null,
    val pbGestureScaleDispatchMethod: String? = null,
    val pbGestureScaleListenerSetterMethod: String? = null,
    val pbGestureScaleListenerClass: String? = null,
    val pbGestureScaleListenerOnScaleMethod: String? = null,
    val pbLikeAutoReplyAgreeViewClass: String? = null,
    val pbLikeAutoReplyAgreeClickMethod: String? = null,
    val pbLikeAutoReplyAgreeViewGetDataMethod: String? = null,
    val pbLikeAutoReplyAgreeDataClass: String? = null,
    val pbLikeAutoReplyAgreeDataHasAgreeField: String? = null,
    val pbLikeAutoReplyAgreeDataAgreeTypeField: String? = null,
    val pbLikeAutoReplyAgreeDataIsInThreadField: String? = null,
    val pbLikeAutoReplyInputContainerClass: String? = null,
    val pbLikeAutoReplyInputContainerGetInputViewMethod: String? = null,
    val pbLikeAutoReplyInputContainerGetSendViewMethod: String? = null,

    val collectionPresenterField: String? = null,
    val collectionPresenterListSetterMethod: String? = null,
    val collectionPresenterAdapterField: String? = null,
    val collectionModelField: String? = null,
    val collectionModelListGetterMethod: String? = null,
    val collectionModelParseMethod: String? = null,
    val collectionModelListField: String? = null,
    val collectionFragmentDisplayListField: String? = null,
    val collectionActivityNavControllerField: String? = null,
    val collectionNavBarField: String? = null,
    val collectionAdapterShowFooterMethod: String? = null,
    val collectionAdapterLoadingMethod: String? = null,
    val collectionAdapterHasMoreMethod: String? = null,
    val collectionEditModeMethod: String? = null,

    val historyAdapterField: String? = null,
    val historyAdapterSetListMethod: String? = null,
    val historyListField: String? = null,
    val historyActivityListUpdateMethod: String? = null,
    val historyActivityNavBarField: String? = null,
    val historyThreadNameMethod: String? = null,
    val historyForumNameMethod: String? = null,
    val historyUserNameMethod: String? = null,
    val historyDescriptionMethod: String? = null,
    val historyThreadIdMethod: String? = null,
    val historyPostIdMethod: String? = null,
    val historyLiveIdMethod: String? = null,

    val msgTabLocateToTabMethod: String? = null,
    val msgTabContainerSelectMethod: String? = null,
    val msgTabContainerExtDataField: String? = null,
    val freeCopyPopupMenuClass: String? = null,
    val freeCopyPopupContentViewMethod: String? = null,
    val freeCopyPopupTextField: String? = null,

    val mainTabDataClass: String? = null,
    val mainTabAddMethod: String? = null,
    val mainTabGetListMethod: String? = null,
    val mainTabDelegateGetStructureMethod: String? = null,
    val mainTabStructureTypeField: String? = null,
    val mainTabStructureDynamicIconField: String? = null,
    val mainTabStructureFragmentField: String? = null,

    val origImagePagerAdapterClass: String? = null,
    val origImageUrlDragImageViewClass: String? = null,
    val origImageDataClass: String? = null,
    val origImageSetPrimaryItemMethod: String? = null,
    val origImageSetAssistUrlMethod: String? = null,
    val origImageAssistDataMethod: String? = null,
    val origImageOriginTextMethod: String? = null,
    val origImageShowButtonField: String? = null,
    val origImageBlockedField: String? = null,
    val origImageOriginalProcessField: String? = null,
    val origImageOriginalUrlField: String? = null,
    val origImageSharedPrefHelperClass: String? = null,
    val origImageSharedPrefGetInstanceMethod: String? = null,
    val origImageSharedPrefPutBooleanMethod: String? = null,
    val origImageMd5Class: String? = null,
    val origImageMd5Method: String? = null,
    val origImagePrimaryReadyMethod: String? = null,
    val origImageTriggerMethod: String? = null,
    val origImageDirectStartMethod: String? = null,
    val shareTrackBuilderClass: String? = null,
    val shareTrackBuildUrlMethod: String? = null,
    val shareTrackAppendQueryMethod: String? = null,
    val imageViewerShareConfigClass: String? = null,
    val imageViewerShareIsDialogField: String? = null,
    val imageViewerShareItemField: String? = null,
    val imageViewerShareAddOutsideMethod: String? = null,
    val imageViewerShareGetRequestDataMethod: String? = null,
    val imageViewerShareSetRequestDataMethod: String? = null,
    val imageViewerShareGetContextMethod: String? = null,
    val imageViewerShareItemClass: String? = null,
    val imageViewerShareItemTitleField: String? = null,
    val imageViewerShareItemContentField: String? = null,
    val imageViewerShareItemLinkUrlField: String? = null,
    val imageViewerShareItemImageUriField: String? = null,
    val imageViewerShareItemImageUrlField: String? = null,
    val imageViewerShareItemLocalFileField: String? = null,
    val imageViewerShareItemViewClass: String? = null,
    val imageViewerShareItemNameByResMethod: String? = null,
    val imageViewerShareItemNameByTextMethod: String? = null,
    val imageViewerShareIconResId: Int? = null,
    val homeNativeGlassSubPbNextPageMoreViewId: Int? = null,
    val homeNativeGlassPbReplyTitleDividerViewId: Int? = null,
    val homeNativeGlassDynamicBackgroundColorIds: List<Int> = emptyList(),
    val homeNativeGlassReadableTextResourceIdsByName: Map<String, Int> = emptyMap(),
    val homeNativeGlassSortSwitchBackgroundPaintField: String? = null,
    val homeNativeGlassSortSwitchSlideDrawMethod: String? = null,
    val homeNativeGlassSortSwitchSlidePathField: String? = null,
    val homeNativeGlassEnterForumCapsuleControllerClass: String? = null,
    val homeNativeGlassEnterForumCapsuleInitMethod: String? = null,
    val homeNativeGlassEnterForumCapsuleRefreshMethod: String? = null,
    val homeNativeGlassEnterForumCapsuleViewField: String? = null,
    val homeNativeGlassEnterForumCapsuleTitleField: String? = null,
    val pbCommonLayoutPreloaderGetOrDefaultMethod: String? = null,

    val feedCardBindMethod: String? = null,
    val feedCardDataListField: String? = null,
    val feedHeadParamsField: String? = null,
    val feedRecommendCardNestedDataMethod: String? = null,
    val feedRecommendCardNestedDataListField: String? = null,
    val replyServerResponseClass: String? = null,
    val replyServerResponseDecodeMethod: String? = null,
    val replyServerResponseResultJsonField: String? = null,
    val aiSpriteMemePanControllerClass: String? = null,
    val aiSpriteMemeEnableMethod: String? = null,
    val aiPbNewInputContainerClass: String? = null,
    val aiPbNewInputContainerInitSpriteMemeMethod: String? = null,
    val aiPbNewInputContainerInitAiWriteMethod: String? = null,
    val aiPbAiEmojiCreationViewBindMethod: String? = null,
    val aiPbPageBrowserAiEmojiCreationBindMethod: String? = null,
    val aiImageViewerJumpButtonOwnerClass: String? = null,
    val aiImageViewerJumpButtonInitMethod: String? = null,

    val featureStatusMap: Map<String, HookFeatureStatus> = emptyMap(),
    val scanSupportState: String = ScanSupportState.UNKNOWN,
    val scanTargetVersionCode: Long? = null,
    val scanTargetVersionName: String? = null,
    val scanTargetVersionType: String? = null,
    val scanErrors: List<String> = emptyList(),

    val source: String = "unsupported",
    val createdAt: Long = 0L,
    val cacheSchemaVersion: Int = CACHE_SCHEMA_VERSION,
) {
    fun toJson(): String {
        return JSONObject().apply {
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
            if (zgaMethods != null) {
                val array = org.json.JSONArray()
                zgaMethods.forEach { array.put(it) }
                put("zgaMethods", array)
            }
            put("searchBoxViewClass", searchBoxViewClass)
            put("searchBoxSetHintMethod", searchBoxSetHintMethod)
            put("homeSearchBoxOwnerClass", homeSearchBoxOwnerClass)
            put("homeSearchBoxInitMethod", homeSearchBoxInitMethod)
            put("homeSearchBoxGetterMethod", homeSearchBoxGetterMethod)
            if (homePersonalizeAnchorClasses != null) {
                val array = org.json.JSONArray()
                homePersonalizeAnchorClasses.forEach { array.put(it) }
                put("homePersonalizeAnchorClasses", array)
            }
            put("homeRightSlotClass", homeRightSlotClass)
            if (homeRightSlotStateMethods != null) {
                val array = org.json.JSONArray()
                homeRightSlotStateMethods.forEach { array.put(it) }
                put("homeRightSlotStateMethods", array)
            }
            put("pbFallingViewClass", pbFallingViewClass)
            put("pbFallingInitMethod", pbFallingInitMethod)
            put("pbFallingShowMethod", pbFallingShowMethod)
            put("pbFallingClearMethod", pbFallingClearMethod)
            put("pbEarlyAdInsertClass", pbEarlyAdInsertClass)
            if (pbEarlyAdInsertMethodSpecs != null) {
                val array = org.json.JSONArray()
                pbEarlyAdInsertMethodSpecs.forEach { array.put(it) }
                put("pbEarlyAdInsertMethodSpecs", array)
            }
            put("pbAdBidCommonRequestModelClass", pbAdBidCommonRequestModelClass)
            if (pbAdBidCommonRequestStartMethods != null) {
                val array = org.json.JSONArray()
                pbAdBidCommonRequestStartMethods.forEach { array.put(it) }
                put("pbAdBidCommonRequestStartMethods", array)
            }
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
            if (plainUrlClickableSpanOnClickOwnerClasses != null) {
                val array = org.json.JSONArray()
                plainUrlClickableSpanOnClickOwnerClasses.forEach { array.put(it) }
                put("plainUrlClickableSpanOnClickOwnerClasses", array)
            }
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

                HookSymbols(
                    homeTabClass = obj.optStringOrNull("homeTabClass"),
                    homeTabRebuildMethod = obj.optStringOrNull("homeTabRebuildMethod"),
                    homeTabListField = obj.optStringOrNull("homeTabListField"),
                    homeTabItemTypeField = obj.optStringOrNull("homeTabItemTypeField"),
                    homeTabItemCodeField = obj.optStringOrNull("homeTabItemCodeField"),
                    homeTabItemNameField = obj.optStringOrNull("homeTabItemNameField"),
                    homeTabItemUrlField = obj.optStringOrNull("homeTabItemUrlField"),
                    homeTabItemMainSetterMethod = obj.optStringOrNull("homeTabItemMainSetterMethod"),
                    homeTabItemMainIntField = obj.optStringOrNull("homeTabItemMainIntField"),
                    homeTabItemMainBooleanField = obj.optStringOrNull("homeTabItemMainBooleanField"),
                    settingsClass = obj.optStringOrNull("settingsClass"),
                    settingsInitMethod = obj.optStringOrNull("settingsInitMethod"),
                    settingsContainerField = obj.optStringOrNull("settingsContainerField"),
                    feedTemplateKeyMethod = obj.optStringOrNull("feedTemplateKeyMethod"),
                    feedTemplatePayloadMethod = obj.optStringOrNull("feedTemplatePayloadMethod"),
                    feedTemplateLoadMoreMethod = obj.optStringOrNull("feedTemplateLoadMoreMethod"),
                    splashAdHelperClass = obj.optStringOrNull("splashAdHelperClass"),
                    splashAdHelperMethod = obj.optStringOrNull("splashAdHelperMethod"),
                    closeAdDataClass = obj.optStringOrNull("closeAdDataClass"),
                    closeAdDataMethodG1 = obj.optStringOrNull("closeAdDataMethodG1"),
                    closeAdDataMethodJ1 = obj.optStringOrNull("closeAdDataMethodJ1"),

                    zgaClass = obj.optStringOrNull("zgaClass"),
                    zgaMethods = zgaMethodsList,
                    searchBoxViewClass = obj.optStringOrNull("searchBoxViewClass"),
                    searchBoxSetHintMethod = obj.optStringOrNull("searchBoxSetHintMethod"),
                    homeSearchBoxOwnerClass = obj.optStringOrNull("homeSearchBoxOwnerClass"),
                    homeSearchBoxInitMethod = obj.optStringOrNull("homeSearchBoxInitMethod"),
                    homeSearchBoxGetterMethod = obj.optStringOrNull("homeSearchBoxGetterMethod"),
                    homePersonalizeAnchorClasses = anchorClassesList,
                    homeRightSlotClass = obj.optStringOrNull("homeRightSlotClass"),
                    homeRightSlotStateMethods = obj.optStringArray("homeRightSlotStateMethods")
                        .takeIf { it.isNotEmpty() },
                    pbFallingViewClass = obj.optStringOrNull("pbFallingViewClass"),
                    pbFallingInitMethod = obj.optStringOrNull("pbFallingInitMethod"),
                    pbFallingShowMethod = obj.optStringOrNull("pbFallingShowMethod"),
                    pbFallingClearMethod = obj.optStringOrNull("pbFallingClearMethod"),
                    pbEarlyAdInsertClass = obj.optStringOrNull("pbEarlyAdInsertClass"),
                    pbEarlyAdInsertMethodSpecs = obj.optStringArray("pbEarlyAdInsertMethodSpecs")
                        .takeIf { it.isNotEmpty() },
                    pbAdBidCommonRequestModelClass = obj.optStringOrNull("pbAdBidCommonRequestModelClass"),
                    pbAdBidCommonRequestStartMethods = obj.optStringArray("pbAdBidCommonRequestStartMethods")
                        .takeIf { it.isNotEmpty() },
                    pbAdBidCommonRequestNotifyMethod = obj.optStringOrNull("pbAdBidCommonRequestNotifyMethod"),
                    pbAdBidPageBrowserRequestModelClass = obj.optStringOrNull("pbAdBidPageBrowserRequestModelClass"),
                    pbAdBidPageBrowserRequestDataMethod = obj.optStringOrNull("pbAdBidPageBrowserRequestDataMethod"),
                    typeAdapterSetDataMethod = obj.optStringOrNull("typeAdapterSetDataMethod"),
                    typeAdapterDataItemClass = obj.optStringOrNull("typeAdapterDataItemClass"),
                    typeAdapterDataGetTypeMethod = obj.optStringOrNull("typeAdapterDataGetTypeMethod"),
                    enterForumWebControllerClass = obj.optStringOrNull("enterForumWebControllerClass"),
                    enterForumWebLoadMethod = obj.optStringOrNull("enterForumWebLoadMethod"),
                    enterForumInitInfoDataClass = obj.optStringOrNull("enterForumInitInfoDataClass"),
                    enterForumInitInfoGetUrlMethod = obj.optStringOrNull("enterForumInitInfoGetUrlMethod"),
                    plainUrlClickableSpanClass = obj.optStringOrNull("plainUrlClickableSpanClass"),
                    plainUrlClickableSpanOnClickMethod = obj.optStringOrNull("plainUrlClickableSpanOnClickMethod"),
                    plainUrlClickableSpanOnClickOwnerClasses = obj.optStringArray("plainUrlClickableSpanOnClickOwnerClasses")
                        .takeIf { it.isNotEmpty() },
                    plainUrlClickableSpanTypeField = obj.optStringOrNull("plainUrlClickableSpanTypeField"),
                    plainUrlClickableSpanUrlField = obj.optStringOrNull("plainUrlClickableSpanUrlField"),
                    plainUrlClickableSpanTextField = obj.optStringOrNull("plainUrlClickableSpanTextField"),
                    plainUrlMessageManagerClass = obj.optStringOrNull("plainUrlMessageManagerClass"),
                    plainUrlMessageDispatchMethod = obj.optStringOrNull("plainUrlMessageDispatchMethod"),
                    plainUrlResponsedMessageClass = obj.optStringOrNull("plainUrlResponsedMessageClass"),
                    plainUrlResponsedMessageGetCmdMethod = obj.optStringOrNull("plainUrlResponsedMessageGetCmdMethod"),
                    plainUrlCustomResponsedMessageClass = obj.optStringOrNull("plainUrlCustomResponsedMessageClass"),
                    plainUrlCustomResponsedMessageGetDataMethod = obj.optStringOrNull("plainUrlCustomResponsedMessageGetDataMethod"),
                    plainUrlApplicationClass = obj.optStringOrNull("plainUrlApplicationClass"),
                    plainUrlApplicationGetInstMethod = obj.optStringOrNull("plainUrlApplicationGetInstMethod"),
                    privateReadReceiptModelClass = obj.optStringOrNull("privateReadReceiptModelClass"),
                    privateReadReceiptModelReadDispatchMethod = obj.optStringOrNull("privateReadReceiptModelReadDispatchMethod"),
                    privateReadReceiptMessageManagerClass = obj.optStringOrNull("privateReadReceiptMessageManagerClass"),
                    privateReadReceiptMessageManagerGetInstanceMethod = obj.optStringOrNull("privateReadReceiptMessageManagerGetInstanceMethod"),
                    privateReadReceiptMessageSendMethod = obj.optStringOrNull("privateReadReceiptMessageSendMethod"),
                    privateReadReceiptMessageBaseClass = obj.optStringOrNull("privateReadReceiptMessageBaseClass"),
                    privateReadReceiptRequestClass = obj.optStringOrNull("privateReadReceiptRequestClass"),
                    privateReadReceiptModelBaseClass = obj.optStringOrNull("privateReadReceiptModelBaseClass"),
                    privateReadReceiptCommitResponseClass = obj.optStringOrNull("privateReadReceiptCommitResponseClass"),
                    privateReadReceiptProcessAckMethod = obj.optStringOrNull("privateReadReceiptProcessAckMethod"),
                    privateReadReceiptResponseErrorMethod = obj.optStringOrNull("privateReadReceiptResponseErrorMethod"),
                    privateReadReceiptRequestMsgIdField = obj.optStringOrNull("privateReadReceiptRequestMsgIdField"),
                    privateReadReceiptRequestToUidField = obj.optStringOrNull("privateReadReceiptRequestToUidField"),
                    privateReadReceiptModelDataField = obj.optStringOrNull("privateReadReceiptModelDataField"),
                    privateReadReceiptPageDataClass = obj.optStringOrNull("privateReadReceiptPageDataClass"),
                    privateReadReceiptPageDataChatListMethod = obj.optStringOrNull("privateReadReceiptPageDataChatListMethod"),
                    privateReadReceiptChatMessageClass = obj.optStringOrNull("privateReadReceiptChatMessageClass"),
                    privateReadReceiptChatMessageMsgIdMethod = obj.optStringOrNull("privateReadReceiptChatMessageMsgIdMethod"),
                    privateReadReceiptChatMessageUserIdMethod = obj.optStringOrNull("privateReadReceiptChatMessageUserIdMethod"),
                    privateReadReceiptChatMessageLocalDataMethod = obj.optStringOrNull("privateReadReceiptChatMessageLocalDataMethod"),
                    privateReadReceiptLocalDataClass = obj.optStringOrNull("privateReadReceiptLocalDataClass"),
                    privateReadReceiptLocalDataStatusMethod = obj.optStringOrNull("privateReadReceiptLocalDataStatusMethod"),
                    privateReadReceiptAccountClass = obj.optStringOrNull("privateReadReceiptAccountClass"),
                    privateReadReceiptCurrentAccountMethod = obj.optStringOrNull("privateReadReceiptCurrentAccountMethod"),
                    mountCardLinkLayoutClass = obj.optStringOrNull("mountCardLinkLayoutClass"),
                    mountCardLinkLayoutOnClickMethod = obj.optStringOrNull("mountCardLinkLayoutOnClickMethod"),
                    mountCardLinkLayoutDataField = obj.optStringOrNull("mountCardLinkLayoutDataField"),
                    mountCardLinkInfoDataClass = obj.optStringOrNull("mountCardLinkInfoDataClass"),
                    mountCardLinkInfoGetUrlMethod = obj.optStringOrNull("mountCardLinkInfoGetUrlMethod"),
                    forumBottomSheetViewClass = obj.optStringOrNull("forumBottomSheetViewClass"),
                    forumBottomSheetInitScrollMethod = obj.optStringOrNull("forumBottomSheetInitScrollMethod"),
                    autoRefreshTriggerMethod = obj.optStringOrNull("autoRefreshTriggerMethod"),
                    autoLoadMoreConfigClass = obj.optStringOrNull("autoLoadMoreConfigClass"),
                    autoLoadMoreConfigMethod = obj.optStringOrNull("autoLoadMoreConfigMethod"),
                    pbCommentScrollListenerClass = obj.optStringOrNull("pbCommentScrollListenerClass"),
                    pbCommentScrollMethod = obj.optStringOrNull("pbCommentScrollMethod"),
                    pbCommentScrollFragmentField = obj.optStringOrNull("pbCommentScrollFragmentField"),
                    pbCommentScrollBottomListenerField = obj.optStringOrNull("pbCommentScrollBottomListenerField"),
                    pbCommentScrollBottomMethod = obj.optStringOrNull("pbCommentScrollBottomMethod"),
                    pbCommentBottomListScrollClass = obj.optStringOrNull("pbCommentBottomListScrollClass"),
                    pbCommentBottomListScrollMethod = obj.optStringOrNull("pbCommentBottomListScrollMethod"),
                    pbCommentBottomListOwnerField = obj.optStringOrNull("pbCommentBottomListOwnerField"),
                    pbCommentBottomRecyclerScrollClass = obj.optStringOrNull("pbCommentBottomRecyclerScrollClass"),
                    pbCommentBottomRecyclerScrollMethod = obj.optStringOrNull("pbCommentBottomRecyclerScrollMethod"),
                    pbCommentBottomRecyclerOwnerField = obj.optStringOrNull("pbCommentBottomRecyclerOwnerField"),
                    pbGestureScaleManagerClass = obj.optStringOrNull("pbGestureScaleManagerClass"),
                    pbGestureScaleDispatchMethod = obj.optStringOrNull("pbGestureScaleDispatchMethod"),
                    pbGestureScaleListenerSetterMethod = obj.optStringOrNull("pbGestureScaleListenerSetterMethod"),
                    pbGestureScaleListenerClass = obj.optStringOrNull("pbGestureScaleListenerClass"),
                    pbGestureScaleListenerOnScaleMethod = obj.optStringOrNull("pbGestureScaleListenerOnScaleMethod"),
                    pbLikeAutoReplyAgreeViewClass = obj.optStringOrNull("pbLikeAutoReplyAgreeViewClass"),
                    pbLikeAutoReplyAgreeClickMethod = obj.optStringOrNull("pbLikeAutoReplyAgreeClickMethod"),
                    pbLikeAutoReplyAgreeViewGetDataMethod = obj.optStringOrNull("pbLikeAutoReplyAgreeViewGetDataMethod"),
                    pbLikeAutoReplyAgreeDataClass = obj.optStringOrNull("pbLikeAutoReplyAgreeDataClass"),
                    pbLikeAutoReplyAgreeDataHasAgreeField = obj.optStringOrNull("pbLikeAutoReplyAgreeDataHasAgreeField"),
                    pbLikeAutoReplyAgreeDataAgreeTypeField = obj.optStringOrNull("pbLikeAutoReplyAgreeDataAgreeTypeField"),
                    pbLikeAutoReplyAgreeDataIsInThreadField =
                        obj.optStringOrNull("pbLikeAutoReplyAgreeDataIsInThreadField"),
                    pbLikeAutoReplyInputContainerClass = obj.optStringOrNull("pbLikeAutoReplyInputContainerClass"),
                    pbLikeAutoReplyInputContainerGetInputViewMethod =
                        obj.optStringOrNull("pbLikeAutoReplyInputContainerGetInputViewMethod"),
                    pbLikeAutoReplyInputContainerGetSendViewMethod =
                        obj.optStringOrNull("pbLikeAutoReplyInputContainerGetSendViewMethod"),

                    collectionPresenterField = obj.optStringOrNull("collectionPresenterField"),
                    collectionPresenterListSetterMethod = obj.optStringOrNull("collectionPresenterListSetterMethod"),
                    collectionPresenterAdapterField = obj.optStringOrNull("collectionPresenterAdapterField"),
                    collectionModelField = obj.optStringOrNull("collectionModelField"),
                    collectionModelListGetterMethod = obj.optStringOrNull("collectionModelListGetterMethod"),
                    collectionModelParseMethod = obj.optStringOrNull("collectionModelParseMethod"),
                    collectionModelListField = obj.optStringOrNull("collectionModelListField"),
                    collectionFragmentDisplayListField = obj.optStringOrNull("collectionFragmentDisplayListField"),
                    collectionActivityNavControllerField = obj.optStringOrNull("collectionActivityNavControllerField"),
                    collectionNavBarField = obj.optStringOrNull("collectionNavBarField"),
                    collectionAdapterShowFooterMethod = obj.optStringOrNull("collectionAdapterShowFooterMethod"),
                    collectionAdapterLoadingMethod = obj.optStringOrNull("collectionAdapterLoadingMethod"),
                    collectionAdapterHasMoreMethod = obj.optStringOrNull("collectionAdapterHasMoreMethod"),
                    collectionEditModeMethod = obj.optStringOrNull("collectionEditModeMethod"),

                    historyAdapterField = obj.optStringOrNull("historyAdapterField"),
                    historyAdapterSetListMethod = obj.optStringOrNull("historyAdapterSetListMethod"),
                    historyListField = obj.optStringOrNull("historyListField"),
                    historyActivityListUpdateMethod = obj.optStringOrNull("historyActivityListUpdateMethod"),
                    historyActivityNavBarField = obj.optStringOrNull("historyActivityNavBarField"),
                    historyThreadNameMethod = obj.optStringOrNull("historyThreadNameMethod"),
                    historyForumNameMethod = obj.optStringOrNull("historyForumNameMethod"),
                    historyUserNameMethod = obj.optStringOrNull("historyUserNameMethod"),
                    historyDescriptionMethod = obj.optStringOrNull("historyDescriptionMethod"),
                    historyThreadIdMethod = obj.optStringOrNull("historyThreadIdMethod"),
                    historyPostIdMethod = obj.optStringOrNull("historyPostIdMethod"),
                    historyLiveIdMethod = obj.optStringOrNull("historyLiveIdMethod"),

                    msgTabLocateToTabMethod = obj.optStringOrNull("msgTabLocateToTabMethod"),
                    msgTabContainerSelectMethod = obj.optStringOrNull("msgTabContainerSelectMethod"),
                    msgTabContainerExtDataField = obj.optStringOrNull("msgTabContainerExtDataField"),
                    freeCopyPopupMenuClass = obj.optStringOrNull("freeCopyPopupMenuClass"),
                    freeCopyPopupContentViewMethod = obj.optStringOrNull("freeCopyPopupContentViewMethod"),
                    freeCopyPopupTextField = obj.optStringOrNull("freeCopyPopupTextField"),

                    mainTabDataClass = obj.optStringOrNull("mainTabDataClass"),
                    mainTabAddMethod = obj.optStringOrNull("mainTabAddMethod"),
                    mainTabGetListMethod = obj.optStringOrNull("mainTabGetListMethod"),
                    mainTabDelegateGetStructureMethod = obj.optStringOrNull("mainTabDelegateGetStructureMethod"),
                    mainTabStructureTypeField = obj.optStringOrNull("mainTabStructureTypeField"),
                    mainTabStructureDynamicIconField = obj.optStringOrNull("mainTabStructureDynamicIconField"),
                    mainTabStructureFragmentField = obj.optStringOrNull("mainTabStructureFragmentField"),

                    origImagePagerAdapterClass = obj.optStringOrNull("origImagePagerAdapterClass"),
                    origImageUrlDragImageViewClass = obj.optStringOrNull("origImageUrlDragImageViewClass"),
                    origImageDataClass = obj.optStringOrNull("origImageDataClass"),
                    origImageSetPrimaryItemMethod = obj.optStringOrNull("origImageSetPrimaryItemMethod"),
                    origImageSetAssistUrlMethod = obj.optStringOrNull("origImageSetAssistUrlMethod"),
                    origImageAssistDataMethod = obj.optStringOrNull("origImageAssistDataMethod"),
                    origImageOriginTextMethod = obj.optStringOrNull("origImageOriginTextMethod"),
                    origImageShowButtonField = obj.optStringOrNull("origImageShowButtonField"),
                    origImageBlockedField = obj.optStringOrNull("origImageBlockedField"),
                    origImageOriginalProcessField = obj.optStringOrNull("origImageOriginalProcessField"),
                    origImageOriginalUrlField = obj.optStringOrNull("origImageOriginalUrlField"),
                    origImageSharedPrefHelperClass = obj.optStringOrNull("origImageSharedPrefHelperClass"),
                    origImageSharedPrefGetInstanceMethod = obj.optStringOrNull("origImageSharedPrefGetInstanceMethod"),
                    origImageSharedPrefPutBooleanMethod = obj.optStringOrNull("origImageSharedPrefPutBooleanMethod"),
                    origImageMd5Class = obj.optStringOrNull("origImageMd5Class"),
                    origImageMd5Method = obj.optStringOrNull("origImageMd5Method"),
                    origImagePrimaryReadyMethod = obj.optStringOrNull("origImagePrimaryReadyMethod"),
                    origImageTriggerMethod = obj.optStringOrNull("origImageTriggerMethod"),
                    origImageDirectStartMethod = obj.optStringOrNull("origImageDirectStartMethod"),
                    shareTrackBuilderClass = obj.optStringOrNull("shareTrackBuilderClass"),
                    shareTrackBuildUrlMethod = obj.optStringOrNull("shareTrackBuildUrlMethod"),
                    shareTrackAppendQueryMethod = obj.optStringOrNull("shareTrackAppendQueryMethod"),
                    imageViewerShareConfigClass = obj.optStringOrNull("imageViewerShareConfigClass"),
                    imageViewerShareIsDialogField = obj.optStringOrNull("imageViewerShareIsDialogField"),
                    imageViewerShareItemField = obj.optStringOrNull("imageViewerShareItemField"),
                    imageViewerShareAddOutsideMethod = obj.optStringOrNull("imageViewerShareAddOutsideMethod"),
                    imageViewerShareGetRequestDataMethod = obj.optStringOrNull("imageViewerShareGetRequestDataMethod"),
                    imageViewerShareSetRequestDataMethod = obj.optStringOrNull("imageViewerShareSetRequestDataMethod"),
                    imageViewerShareGetContextMethod = obj.optStringOrNull("imageViewerShareGetContextMethod"),
                    imageViewerShareItemClass = obj.optStringOrNull("imageViewerShareItemClass"),
                    imageViewerShareItemTitleField = obj.optStringOrNull("imageViewerShareItemTitleField"),
                    imageViewerShareItemContentField = obj.optStringOrNull("imageViewerShareItemContentField"),
                    imageViewerShareItemLinkUrlField = obj.optStringOrNull("imageViewerShareItemLinkUrlField"),
                    imageViewerShareItemImageUriField = obj.optStringOrNull("imageViewerShareItemImageUriField"),
                    imageViewerShareItemImageUrlField = obj.optStringOrNull("imageViewerShareItemImageUrlField"),
                    imageViewerShareItemLocalFileField = obj.optStringOrNull("imageViewerShareItemLocalFileField"),
                    imageViewerShareItemViewClass = obj.optStringOrNull("imageViewerShareItemViewClass"),
                    imageViewerShareItemNameByResMethod = obj.optStringOrNull("imageViewerShareItemNameByResMethod"),
                    imageViewerShareItemNameByTextMethod = obj.optStringOrNull("imageViewerShareItemNameByTextMethod"),
                    imageViewerShareIconResId = obj.optIntOrNull("imageViewerShareIconResId"),
                    homeNativeGlassSubPbNextPageMoreViewId = obj.optIntOrNull("homeNativeGlassSubPbNextPageMoreViewId"),
                    homeNativeGlassPbReplyTitleDividerViewId = obj.optIntOrNull("homeNativeGlassPbReplyTitleDividerViewId"),
                    homeNativeGlassDynamicBackgroundColorIds =
                        obj.optIntArray("homeNativeGlassDynamicBackgroundColorIds"),
                    homeNativeGlassReadableTextResourceIdsByName =
                        obj.optStringIntMap("homeNativeGlassReadableTextResourceIdsByName"),
                    homeNativeGlassSortSwitchBackgroundPaintField =
                        obj.optStringOrNull("homeNativeGlassSortSwitchBackgroundPaintField"),
                    homeNativeGlassSortSwitchSlideDrawMethod =
                        obj.optStringOrNull("homeNativeGlassSortSwitchSlideDrawMethod"),
                    homeNativeGlassSortSwitchSlidePathField =
                        obj.optStringOrNull("homeNativeGlassSortSwitchSlidePathField"),
                    homeNativeGlassEnterForumCapsuleControllerClass =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleControllerClass"),
                    homeNativeGlassEnterForumCapsuleInitMethod =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleInitMethod"),
                    homeNativeGlassEnterForumCapsuleRefreshMethod =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleRefreshMethod"),
                    homeNativeGlassEnterForumCapsuleViewField =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleViewField"),
                    homeNativeGlassEnterForumCapsuleTitleField =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleTitleField"),
                    pbCommonLayoutPreloaderGetOrDefaultMethod =
                        obj.optStringOrNull("pbCommonLayoutPreloaderGetOrDefaultMethod"),

                    feedCardBindMethod = obj.optStringOrNull("feedCardBindMethod"),
                    feedCardDataListField = obj.optStringOrNull("feedCardDataListField"),
                    feedHeadParamsField = obj.optStringOrNull("feedHeadParamsField"),
                    feedRecommendCardNestedDataMethod = obj.optStringOrNull("feedRecommendCardNestedDataMethod"),
                    feedRecommendCardNestedDataListField = obj.optStringOrNull("feedRecommendCardNestedDataListField"),
                    replyServerResponseClass = obj.optStringOrNull("replyServerResponseClass"),
                    replyServerResponseDecodeMethod = obj.optStringOrNull("replyServerResponseDecodeMethod"),
                    replyServerResponseResultJsonField =
                        obj.optStringOrNull("replyServerResponseResultJsonField"),
                    aiSpriteMemePanControllerClass = obj.optStringOrNull("aiSpriteMemePanControllerClass"),
                    aiSpriteMemeEnableMethod = obj.optStringOrNull("aiSpriteMemeEnableMethod"),
                    aiPbNewInputContainerClass = obj.optStringOrNull("aiPbNewInputContainerClass"),
                    aiPbNewInputContainerInitSpriteMemeMethod = obj.optStringOrNull("aiPbNewInputContainerInitSpriteMemeMethod"),
                    aiPbNewInputContainerInitAiWriteMethod = obj.optStringOrNull("aiPbNewInputContainerInitAiWriteMethod"),
                    aiPbAiEmojiCreationViewBindMethod = obj.optStringOrNull("aiPbAiEmojiCreationViewBindMethod"),
                    aiPbPageBrowserAiEmojiCreationBindMethod = obj.optStringOrNull("aiPbPageBrowserAiEmojiCreationBindMethod"),
                    aiImageViewerJumpButtonOwnerClass = obj.optStringOrNull("aiImageViewerJumpButtonOwnerClass"),
                    aiImageViewerJumpButtonInitMethod = obj.optStringOrNull("aiImageViewerJumpButtonInitMethod"),

                    featureStatusMap = featureStatusMap,
                    scanSupportState = obj.optString("scanSupportState", ScanSupportState.UNKNOWN),
                    scanTargetVersionCode = obj.optLongOrNull("scanTargetVersionCode"),
                    scanTargetVersionName = obj.optStringOrNull("scanTargetVersionName"),
                    scanTargetVersionType = obj.optStringOrNull("scanTargetVersionType"),
                    scanErrors = obj.optStringArray("scanErrors"),

                    source = obj.optString("source", "unsupported"),
                    createdAt = obj.optLong("createdAt", 0L),
                    cacheSchemaVersion = obj.optInt("cacheSchemaVersion", 0),
                )
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
