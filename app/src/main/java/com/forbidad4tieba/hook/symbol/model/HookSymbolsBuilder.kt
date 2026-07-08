package com.forbidad4tieba.hook.symbol.model

internal class HookSymbolsBuilder {
    var homeTabClass: String? = null
    var homeTabRebuildMethod: String? = null
    var homeTabListField: String? = null
    var homeTabItemTypeField: String? = null
    var homeTabItemCodeField: String? = null
    var homeTabItemNameField: String? = null
    var homeTabItemUrlField: String? = null
    var homeTabItemMainSetterMethod: String? = null
    var homeTabItemMainIntField: String? = null
    var homeTabItemMainBooleanField: String? = null
    var settingsClass: String? = null
    var settingsInitMethod: String? = null
    var settingsContainerField: String? = null
    var feedTemplateKeyMethod: String? = null
    var feedTemplatePayloadMethod: String? = null
    var feedTemplateLoadMoreMethod: String? = null
    var splashAdHelperClass: String? = null
    var splashAdHelperMethod: String? = null
    var closeAdDataClass: String? = null
    var closeAdDataMethodG1: String? = null
    var closeAdDataMethodJ1: String? = null
    var zgaClass: String? = null
    var zgaMethods: List<String>? = null
    var searchBoxViewClass: String? = null
    var searchBoxSetHintMethod: String? = null
    var homeSearchBoxOwnerClass: String? = null
    var homeSearchBoxInitMethod: String? = null
    var homeSearchBoxGetterMethod: String? = null
    var homePersonalizeAnchorClasses: List<String>? = null
    var homeRightSlotClass: String? = null
    var homeRightSlotStateMethods: List<String>? = null
    var pbFallingViewClass: String? = null
    var pbFallingInitMethod: String? = null
    var pbFallingShowMethod: String? = null
    var pbFallingClearMethod: String? = null
    var pbBottomEnterBarViewClass: String? = null
    var pbBottomEnterBarConstructorCount: Int? = null
    var pbBottomEnterBarRefreshMethodSpecs: List<String>? = null
    var pbEnterFrsAnimationTipViewClass: String? = null
    var pbEnterFrsAnimationTipConstructorCount: Int? = null
    var pbEnterFrsAnimationTipCallerClasses: List<String>? = null
    var pbHotTopicGuideTotalViewMethod: String? = null
    var pbHotTopicGuideRefreshMethodSpecs: List<String>? = null
    var pbEarlyAdInsertClass: String? = null
    var pbEarlyAdInsertMethodSpecs: List<String>? = null
    var pbAdBidCommonRequestModelClass: String? = null
    var pbAdBidCommonRequestStartMethods: List<String>? = null
    var pbAdBidCommonRequestNotifyMethod: String? = null
    var pbAdBidPageBrowserRequestModelClass: String? = null
    var pbAdBidPageBrowserRequestDataMethod: String? = null
    var typeAdapterSetDataMethod: String? = null
    var typeAdapterDataItemClass: String? = null
    var typeAdapterDataGetTypeMethod: String? = null
    var enterForumWebControllerClass: String? = null
    var enterForumWebLoadMethod: String? = null
    var enterForumInitInfoDataClass: String? = null
    var enterForumInitInfoGetUrlMethod: String? = null
    var plainUrlClickableSpanClass: String? = null
    var plainUrlClickableSpanOnClickMethod: String? = null
    var plainUrlClickableSpanOnClickOwnerClasses: List<String>? = null
    var plainUrlClickableSpanTypeField: String? = null
    var plainUrlClickableSpanUrlField: String? = null
    var plainUrlClickableSpanTextField: String? = null
    var plainUrlMessageManagerClass: String? = null
    var plainUrlMessageDispatchMethod: String? = null
    var plainUrlResponsedMessageClass: String? = null
    var plainUrlResponsedMessageGetCmdMethod: String? = null
    var plainUrlCustomResponsedMessageClass: String? = null
    var plainUrlCustomResponsedMessageGetDataMethod: String? = null
    var plainUrlApplicationClass: String? = null
    var plainUrlApplicationGetInstMethod: String? = null
    var plainUrlBrowserHelperClass: String? = null
    var plainUrlBrowserHelperStartWebActivityMethod: String? = null
    var plainUrlWebContainerActivityClass: String? = null
    var plainUrlWebContainerInitDataMethod: String? = null
    var plainUrlWebContainerWebViewClientClass: String? = null
    var plainUrlWebContainerShouldOverrideUrlLoadingMethod: String? = null
    var privateReadReceiptModelClass: String? = null
    var privateReadReceiptModelReadDispatchMethod: String? = null
    var privateReadReceiptMessageManagerClass: String? = null
    var privateReadReceiptMessageManagerGetInstanceMethod: String? = null
    var privateReadReceiptMessageManagerGetSocketClientMethod: String? = null
    var privateReadReceiptMessageSendMethod: String? = null
    var privateReadReceiptMessageBaseClass: String? = null
    var privateReadReceiptSocketClientClass: String? = null
    var privateReadReceiptSocketDuplicateCheckMethod: String? = null
    var privateReadReceiptRequestClass: String? = null
    var privateReadReceiptModelBaseClass: String? = null
    var privateReadReceiptCommitResponseClass: String? = null
    var privateReadReceiptProcessAckMethod: String? = null
    var privateReadReceiptResponseErrorMethod: String? = null
    var privateReadReceiptRequestMsgIdField: String? = null
    var privateReadReceiptRequestToUidField: String? = null
    var privateReadReceiptModelDataField: String? = null
    var privateReadReceiptPageDataClass: String? = null
    var privateReadReceiptPageDataChatListMethod: String? = null
    var privateReadReceiptChatMessageClass: String? = null
    var privateReadReceiptChatMessageMsgIdMethod: String? = null
    var privateReadReceiptChatMessageUserIdMethod: String? = null
    var privateReadReceiptChatMessageLocalDataMethod: String? = null
    var privateReadReceiptLocalDataClass: String? = null
    var privateReadReceiptLocalDataStatusMethod: String? = null
    var privateReadReceiptAccountClass: String? = null
    var privateReadReceiptCurrentAccountMethod: String? = null
    var mountCardLinkLayoutClass: String? = null
    var mountCardLinkLayoutOnClickMethod: String? = null
    var mountCardLinkLayoutDataField: String? = null
    var mountCardLinkInfoDataClass: String? = null
    var mountCardLinkInfoGetUrlMethod: String? = null
    var mineTabWebViewClass: String? = null
    var mineTabWebLoadUrlMethod: String? = null
    var mineTabWebGetUrlMethod: String? = null
    var mineTabWebGetInnerWebViewMethod: String? = null
    var homeSideBarWebViewClass: String? = null
    var homeSideBarTbWebViewClass: String? = null
    var homeSideBarWebGetWebViewMethod: String? = null
    var homeSideBarWebGetUrlMethod: String? = null
    var homeSideBarWebGetInnerWebViewMethod: String? = null
    var homeSideBarWebLoadUrlMethods: List<String>? = null
    var autoSignInNetworkClass: String? = null
    var autoSignInNetworkConstructorSpec: String? = null
    var autoSignInNetworkAddPostDataMethod: String? = null
    var autoSignInNetworkPostNetDataMethod: String? = null
    var autoSignInNetworkSetNeedTbsMethod: String? = null
    var autoSignInNetworkSetNeedSigMethod: String? = null
    var autoSignInTbConfigClass: String? = null
    var autoSignInServerAddressField: String? = null
    var autoSignInCoreApplicationClass: String? = null
    var autoSignInCurrentAccountMethod: String? = null
    var autoSignInHybridProxyClass: String? = null
    var autoSignInHybridProxyConstructorSpec: String? = null
    var autoSignInHybridJsBridgeClass: String? = null
    var autoSignInHybridNativeNetworkProxyMethod: String? = null
    var autoSignInHybridTaskClass: String? = null
    var autoSignInHybridTaskConstructorSpec: String? = null
    var autoSignInHybridTaskDoInBackgroundMethod: String? = null
    var forumBottomSheetViewClass: String? = null
    var forumBottomSheetInitScrollMethod: String? = null
    var autoRefreshTriggerMethod: String? = null
    var autoLoadMoreConfigClass: String? = null
    var autoLoadMoreConfigMethod: String? = null
    var pbCommentScrollListenerClass: String? = null
    var pbCommentScrollMethod: String? = null
    var pbCommentScrollFragmentField: String? = null
    var pbCommentScrollBottomListenerField: String? = null
    var pbCommentScrollBottomMethod: String? = null
    var pbCommentBottomListScrollClass: String? = null
    var pbCommentBottomListScrollMethod: String? = null
    var pbCommentBottomListOwnerField: String? = null
    var pbCommentBottomRecyclerScrollClass: String? = null
    var pbCommentBottomRecyclerScrollMethod: String? = null
    var pbCommentBottomRecyclerOwnerField: String? = null
    var pbGestureScaleManagerClass: String? = null
    var pbGestureScaleDispatchMethod: String? = null
    var pbGestureScaleListenerSetterMethod: String? = null
    var pbGestureScaleListenerClass: String? = null
    var pbGestureScaleListenerOnScaleMethod: String? = null
    var pbLikeAutoReplyAgreeViewClass: String? = null
    var pbLikeAutoReplyAgreeClickMethod: String? = null
    var pbLikeAutoReplyAgreeViewGetDataMethod: String? = null
    var pbLikeAutoReplyAgreeDataClass: String? = null
    var pbLikeAutoReplyAgreeDataHasAgreeField: String? = null
    var pbLikeAutoReplyAgreeDataAgreeTypeField: String? = null
    var pbLikeAutoReplyAgreeDataIsInThreadField: String? = null
    var pbLikeAutoReplyInputContainerClass: String? = null
    var pbLikeAutoReplyInputContainerGetInputViewMethod: String? = null
    var pbLikeAutoReplyInputContainerGetSendViewMethod: String? = null
    var collectionPresenterField: String? = null
    var collectionPresenterListSetterMethod: String? = null
    var collectionPresenterListSetterMethodSpec: String? = null
    var collectionPresenterAdapterField: String? = null
    var collectionModelField: String? = null
    var collectionModelListGetterMethod: String? = null
    var collectionModelListGetterMethodSpec: String? = null
    var collectionModelParseMethod: String? = null
    var collectionModelParseMethodSpec: String? = null
    var collectionModelListField: String? = null
    var collectionFragmentDisplayListField: String? = null
    var collectionActivityNavControllerField: String? = null
    var collectionNavBarField: String? = null
    var collectionAdapterShowFooterMethod: String? = null
    var collectionAdapterLoadingMethod: String? = null
    var collectionAdapterHasMoreMethod: String? = null
    var collectionEditModeMethod: String? = null
    var historyAdapterField: String? = null
    var historyAdapterSetListMethod: String? = null
    var historyAdapterSetListMethodSpec: String? = null
    var historyListField: String? = null
    var historyActivityListUpdateMethod: String? = null
    var historyActivityListUpdateMethodSpec: String? = null
    var historyActivityNavBarField: String? = null
    var historyThreadNameMethod: String? = null
    var historyForumNameMethod: String? = null
    var historyUserNameMethod: String? = null
    var historyDescriptionMethod: String? = null
    var historyThreadIdMethod: String? = null
    var historyPostIdMethod: String? = null
    var historyLiveIdMethod: String? = null
    var msgTabLocateToTabMethod: String? = null
    var msgTabContainerSelectMethod: String? = null
    var msgTabContainerExtDataField: String? = null
    var freeCopyPopupMenuClass: String? = null
    var freeCopyPopupContentViewMethod: String? = null
    var freeCopyPopupTextField: String? = null
    var mainTabDataClass: String? = null
    var mainTabAddMethod: String? = null
    var mainTabGetListMethod: String? = null
    var mainTabDelegateGetStructureMethod: String? = null
    var mainTabStructureTypeField: String? = null
    var mainTabStructureDynamicIconField: String? = null
    var mainTabStructureFragmentField: String? = null
    var origImagePagerAdapterClass: String? = null
    var origImageUrlDragImageViewClass: String? = null
    var origImageDataClass: String? = null
    var origImageSetPrimaryItemMethod: String? = null
    var origImageSetAssistUrlMethod: String? = null
    var origImageAssistDataMethod: String? = null
    var origImageOriginTextMethod: String? = null
    var origImageShowButtonField: String? = null
    var origImageBlockedField: String? = null
    var origImageOriginalProcessField: String? = null
    var origImageOriginalUrlField: String? = null
    var origImageSharedPrefHelperClass: String? = null
    var origImageSharedPrefGetInstanceMethod: String? = null
    var origImageSharedPrefPutBooleanMethod: String? = null
    var origImageMd5Class: String? = null
    var origImageMd5Method: String? = null
    var origImagePrimaryReadyMethod: String? = null
    var origImageTriggerMethod: String? = null
    var origImageDirectStartMethod: String? = null
    var shareTrackBuilderClass: String? = null
    var shareTrackBuildUrlMethod: String? = null
    var shareTrackAppendQueryMethod: String? = null
    var imageViewerShareConfigClass: String? = null
    var imageViewerShareIsDialogField: String? = null
    var imageViewerShareItemField: String? = null
    var imageViewerShareAddOutsideMethod: String? = null
    var imageViewerShareGetRequestDataMethod: String? = null
    var imageViewerShareSetRequestDataMethod: String? = null
    var imageViewerShareGetContextMethod: String? = null
    var imageViewerShareItemClass: String? = null
    var imageViewerShareItemTitleField: String? = null
    var imageViewerShareItemContentField: String? = null
    var imageViewerShareItemLinkUrlField: String? = null
    var imageViewerShareItemImageUriField: String? = null
    var imageViewerShareItemImageUrlField: String? = null
    var imageViewerShareItemLocalFileField: String? = null
    var imageViewerShareItemViewClass: String? = null
    var imageViewerShareItemNameByResMethod: String? = null
    var imageViewerShareItemNameByTextMethod: String? = null
    var imageViewerShareIconResId: Int? = null
    var homeNativeGlassSubPbNextPageMoreViewId: Int? = null
    var homeNativeGlassPbReplyTitleDividerViewId: Int? = null
    var homeNativeGlassDynamicBackgroundColorIds: List<Int> = emptyList()
    var homeNativeGlassTopChromeTabSelectedMethodSpecs: List<String>? = null
    var homeNativeGlassSubPbSetNextPageMethod: String? = null
    var homeNativeGlassSubPbSetNextPageParamType: String? = null
    var homeNativeGlassSortSwitchBackgroundPaintField: String? = null
    var homeNativeGlassSortSwitchSlideDrawMethod: String? = null
    var homeNativeGlassSortSwitchSlidePathField: String? = null
    var homeNativeGlassEnterForumCapsuleControllerClass: String? = null
    var homeNativeGlassEnterForumCapsuleInitMethod: String? = null
    var homeNativeGlassEnterForumCapsuleRefreshMethod: String? = null
    var homeNativeGlassEnterForumCapsuleViewField: String? = null
    var homeNativeGlassEnterForumCapsuleTitleField: String? = null
    var homeNativeGlassHostDarkModeMoreActivityClass: String? = null
    var homeNativeGlassHostDarkModeControllerField: String? = null
    var homeNativeGlassHostDarkModeSwitchGetterMethod: String? = null
    var homeNativeGlassHostDarkModeSwitchStateField: String? = null
    var homeNativeGlassHostDarkModeSwitchSetOnMethod: String? = null
    var homeNativeGlassHostDarkModeSwitchSetOffMethod: String? = null
    var homeNativeGlassHostDarkModeSwitchCallbackMethod: String? = null
    var pbCommonLayoutPreloaderGetOrDefaultMethod: String? = null
    var feedCardBindMethod: String? = null
    var feedCardBindMethodSpec: String? = null
    var feedCardDataListField: String? = null
    var feedHeadParamsField: String? = null
    var feedRecommendCardNestedDataMethod: String? = null
    var feedRecommendCardNestedDataListField: String? = null
    var forumResponseDataClass: String? = null
    var forumResponseParserMethod: String? = null
    var forumResponseAdFields: List<String>? = null
    var forumPageMapperClass: String? = null
    var forumBottomDataMapperMethod: String? = null
    var forumBottomDataClass: String? = null
    var forumBusinessPromotSetterMethod: String? = null
    var forumPrivatePopSetterMethod: String? = null
    var forumSpriteBubbleSetterMethod: String? = null
    var forumMaskPopSetterMethod: String? = null
    var forumBottomGameBarMapperMethod: String? = null
    var forumHeaderDataMapperMethod: String? = null
    var forumHeaderDataClass: String? = null
    var forumRainDataClass: String? = null
    var forumRainSetterMethod: String? = null
    var forumDialogControllerClass: String? = null
    var forumBusinessPromotShowMethod: String? = null
    var forumAnimationShowMethod: String? = null
    var forumGameFloatingBarControllerClass: String? = null
    var forumGameFloatingBarShowMethod: String? = null
    var forumGameFloatingBarField: String? = null
    var forumBusinessPromotBizClass: String? = null
    var forumBusinessPromotJumpMethod: String? = null
    var replyServerResponseClass: String? = null
    var replyServerResponseDecodeMethod: String? = null
    var replyServerResponseResultJsonField: String? = null
    var agreeServerResponseClass: String? = null
    var agreeServerResponseDecodeLogicMethod: String? = null
    var replyVisibilityProbeReplyResponseClass: String? = null
    var replyVisibilityProbeReplyDecodeMethod: String? = null
    var replyVisibilityProbeReplyResultJsonField: String? = null
    var replyVisibilityProbeAddPostRequestClass: String? = null
    var replyVisibilityProbeAddPostRequestDataField: String? = null
    var replyVisibilityProbeResponsedMessageClass: String? = null
    var replyVisibilityProbeGetOriginalMessageMethod: String? = null
    var replyVisibilityProbeMessageClass: String? = null
    var replyVisibilityProbeMessageGetExtraMethod: String? = null
    var replyVisibilityProbeMessageGetTagMethod: String? = null
    var replyVisibilityProbeMessageSetTagMethod: String? = null
    var replyVisibilityProbeHttpMessageClass: String? = null
    var replyVisibilityProbeHttpMessageConstructor: String? = null
    var replyVisibilityProbeHttpMessageAddParamMethod: String? = null
    var replyVisibilityProbeHttpMessageAddHeaderMethod: String? = null
    var replyVisibilityProbeMessageManagerClass: String? = null
    var replyVisibilityProbeMessageManagerGetInstanceMethod: String? = null
    var replyVisibilityProbeMessageManagerFindTaskMethod: String? = null
    var replyVisibilityProbeMessageManagerRegisterTaskMethod: String? = null
    var replyVisibilityProbeMessageManagerSendMethod: String? = null
    var replyVisibilityProbeTbHttpMessageTaskClass: String? = null
    var replyVisibilityProbeTbHttpMessageTaskConstructor: String? = null
    var replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod: String? = null
    var replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod: String? = null
    var replyVisibilityProbeBdUniqueIdClass: String? = null
    var replyVisibilityProbeBdUniqueIdGenMethod: String? = null
    var replyVisibilityProbeTbadkCoreApplicationClass: String? = null
    var replyVisibilityProbeTbadkCoreApplicationGetInstMethod: String? = null
    var replyVisibilityProbeTbadkCoreApplicationGetZidMethod: String? = null
    var replyVisibilityProbeTbConfigClass: String? = null
    var replyVisibilityProbeTbConfigServerAddressField: String? = null
    var replyVisibilityProbeTbConfigPbFloorAgreeUrlField: String? = null
    var replyVisibilityProbeCmdConfigHttpClass: String? = null
    var replyVisibilityProbeCmdPbFloorAgreeField: String? = null
    var replyVisibilityProbeAgreeResponseClass: String? = null
    var replyVisibilityProbeAgreeDecodeLogicMethod: String? = null
    var aiSpriteMemePanControllerClass: String? = null
    var aiSpriteMemeEnableMethod: String? = null
    var aiPbNewInputContainerClass: String? = null
    var aiPbNewInputContainerInitSpriteMemeMethod: String? = null
    var aiPbNewInputContainerInitAiWriteMethod: String? = null
    var aiPbAiEmojiCreationViewBindMethod: String? = null
    var aiPbPageBrowserAiEmojiCreationViewClass: String? = null
    var aiPbPageBrowserAiEmojiCreationBindMethod: String? = null
    var aiImageViewerJumpButtonOwnerClass: String? = null
    var aiImageViewerJumpButtonInitMethod: String? = null
    var featureStatusMap: Map<String, HookFeatureStatus> = emptyMap()
    var scanSupportState: String = ScanSupportState.UNKNOWN
    var scanTargetVersionCode: Long? = null
    var scanTargetVersionName: String? = null
    var scanTargetVersionType: String? = null
    var scanErrors: List<String> = emptyList()
    var source: String = "unsupported"
    var createdAt: Long = 0L
    var cacheSchemaVersion: Int = HookSymbols.CACHE_SCHEMA_VERSION
    var dexKitRuleVersion: Int = HookSymbols.DEXKIT_RULE_VERSION

    fun build(): HookSymbols {
        return HookSymbols(
            hookPoints = HookPointSymbols(
                primary = PrimaryHookPointSymbols(
                    settings = SettingsSymbols(settingsClass, settingsInitMethod, settingsContainerField),
                    home = buildHomeSymbols(),
                    ad = buildAdSymbols(),
                    pb = buildPbSymbols(),
                ),
                web = buildWebSymbols(),
                signIn = buildSignInSymbols(),
                privateMessage = buildPrivateMessageSymbols(),
                collectionHistory = buildCollectionHistorySymbols(),
                media = buildMediaSymbols(),
            ),
            resources = buildResourceSymbols(),
            meta = buildScanMeta(),
        )
    }

    private fun buildHomeSymbols(): HomeSymbols {
        return HomeSymbols(
            tab = HomeTabSymbolsGroup(
                homeTabClass,
                homeTabRebuildMethod,
                homeTabListField,
                item = HomeTabItemSymbolsGroup(
                    homeTabItemTypeField,
                    homeTabItemCodeField,
                    homeTabItemNameField,
                    homeTabItemUrlField,
                ),
                itemMain = HomeTabItemMainSymbolsGroup(
                    homeTabItemMainSetterMethod,
                    homeTabItemMainIntField,
                    homeTabItemMainBooleanField,
                ),
            ),
            search = HomeSearchSymbolsGroup(
                searchBoxViewClass,
                searchBoxSetHintMethod,
                homeSearchBoxOwnerClass,
                homeSearchBoxInitMethod,
                homeSearchBoxGetterMethod,
            ),
            personalization = HomePersonalizationSymbolsGroup(homePersonalizeAnchorClasses),
            rightSlot = HomeRightSlotSymbolsGroup(homeRightSlotClass, homeRightSlotStateMethods),
            mainTab = MainTabSymbolsGroup(
                mainTabDataClass,
                mainTabAddMethod,
                mainTabGetListMethod,
                mainTabDelegateGetStructureMethod,
                structure = MainTabStructureSymbolsGroup(
                    mainTabStructureTypeField,
                    mainTabStructureDynamicIconField,
                    mainTabStructureFragmentField,
                ),
            ),
            nativeGlass = HomeNativeGlassSymbolsGroup(
                topChrome = HomeNativeGlassTopChromeSymbolsGroup(
                    homeNativeGlassTopChromeTabSelectedMethodSpecs,
                ),
                subPbNextPage = HomeNativeGlassSubPbNextPageSymbolsGroup(
                    homeNativeGlassSubPbSetNextPageMethod,
                    homeNativeGlassSubPbSetNextPageParamType,
                ),
                sortSwitch = HomeNativeGlassSortSwitchSymbolsGroup(
                    homeNativeGlassSortSwitchBackgroundPaintField,
                    homeNativeGlassSortSwitchSlideDrawMethod,
                    homeNativeGlassSortSwitchSlidePathField,
                ),
                enterForumCapsule = HomeNativeGlassEnterForumCapsuleSymbolsGroup(
                    homeNativeGlassEnterForumCapsuleControllerClass,
                    homeNativeGlassEnterForumCapsuleInitMethod,
                    homeNativeGlassEnterForumCapsuleRefreshMethod,
                    homeNativeGlassEnterForumCapsuleViewField,
                    homeNativeGlassEnterForumCapsuleTitleField,
                ),
                hostDarkModeSwitch = HomeNativeGlassHostDarkModeSwitchSymbolsGroup(
                    homeNativeGlassHostDarkModeMoreActivityClass,
                    homeNativeGlassHostDarkModeControllerField,
                    homeNativeGlassHostDarkModeSwitchGetterMethod,
                    homeNativeGlassHostDarkModeSwitchStateField,
                    homeNativeGlassHostDarkModeSwitchSetOnMethod,
                    homeNativeGlassHostDarkModeSwitchSetOffMethod,
                    homeNativeGlassHostDarkModeSwitchCallbackMethod,
                ),
            ),
        )
    }

    private fun buildAdSymbols(): AdSymbols {
        return AdSymbols(
            feedTemplate = FeedTemplateSymbolsGroup(
                feedTemplateKeyMethod,
                feedTemplatePayloadMethod,
                feedTemplateLoadMoreMethod,
            ),
            splash = SplashAdSymbolsGroup(splashAdHelperClass, splashAdHelperMethod),
            closeAd = CloseAdSymbolsGroup(closeAdDataClass, closeAdDataMethodG1, closeAdDataMethodJ1),
            zga = ZgaSymbolsGroup(zgaClass, zgaMethods),
            typeAdapter = TypeAdapterSymbolsGroup(
                typeAdapterSetDataMethod,
                typeAdapterDataItemClass,
                typeAdapterDataGetTypeMethod,
            ),
            feedCard = FeedCardSymbolsGroup(
                feedCardBindMethod,
                feedCardBindMethodSpec,
                feedCardDataListField,
                feedHeadParamsField,
                feedRecommendCardNestedDataMethod,
                feedRecommendCardNestedDataListField,
            ),
            forumPage = ForumPageAdSymbolsGroup(
                forumResponseDataClass,
                forumResponseParserMethod,
                forumResponseAdFields,
                forumPageMapperClass,
                forumBottomDataMapperMethod,
                forumBottomDataClass,
                forumBusinessPromotSetterMethod,
                forumPrivatePopSetterMethod,
                forumSpriteBubbleSetterMethod,
                forumMaskPopSetterMethod,
                forumBottomGameBarMapperMethod,
                forumHeaderDataMapperMethod,
                forumHeaderDataClass,
                forumRainDataClass,
                forumRainSetterMethod,
                forumDialogControllerClass,
                forumBusinessPromotShowMethod,
                forumAnimationShowMethod,
                forumGameFloatingBarControllerClass,
                forumGameFloatingBarShowMethod,
                forumGameFloatingBarField,
                forumBusinessPromotBizClass,
                forumBusinessPromotJumpMethod,
            ),
        )
    }

    private fun buildSignInSymbols(): SignInSymbols {
        return SignInSymbols(
            autoSignIn = AutoSignInSymbolsGroup(
                nativeNetwork = AutoSignInNativeNetworkSymbolsGroup(
                    autoSignInNetworkClass,
                    autoSignInNetworkConstructorSpec,
                    autoSignInNetworkAddPostDataMethod,
                    autoSignInNetworkPostNetDataMethod,
                    autoSignInNetworkSetNeedTbsMethod,
                    autoSignInNetworkSetNeedSigMethod,
                    autoSignInTbConfigClass,
                    autoSignInServerAddressField,
                    autoSignInCoreApplicationClass,
                    autoSignInCurrentAccountMethod,
                ),
                hybridNativeProxy = AutoSignInHybridNativeProxySymbolsGroup(
                    autoSignInHybridProxyClass,
                    autoSignInHybridProxyConstructorSpec,
                    autoSignInHybridJsBridgeClass,
                    autoSignInHybridNativeNetworkProxyMethod,
                    autoSignInHybridTaskClass,
                    autoSignInHybridTaskConstructorSpec,
                    autoSignInHybridTaskDoInBackgroundMethod,
                ),
            ),
        )
    }

    private fun buildPbSymbols(): PbSymbols {
        return PbSymbols(
            ad = PbAdSymbolsGroup(
                earlyInsert = PbEarlyAdInsertSymbolsGroup(pbEarlyAdInsertClass, pbEarlyAdInsertMethodSpecs),
                bid = PbAdBidSymbolsGroup(
                    pbAdBidCommonRequestModelClass,
                    pbAdBidCommonRequestStartMethods,
                    pbAdBidCommonRequestNotifyMethod,
                    pbAdBidPageBrowserRequestModelClass,
                    pbAdBidPageBrowserRequestDataMethod,
                ),
            ),
            falling = PbFallingSymbolsGroup(
                pbFallingViewClass,
                pbFallingInitMethod,
                pbFallingShowMethod,
                pbFallingClearMethod,
            ),
            bottomEnterBar = PbBottomEnterBarSymbolsGroup(
                pbBottomEnterBarViewClass,
                pbBottomEnterBarConstructorCount,
                pbBottomEnterBarRefreshMethodSpecs,
                pbEnterFrsAnimationTipViewClass,
                pbEnterFrsAnimationTipConstructorCount,
                pbEnterFrsAnimationTipCallerClasses,
                pbHotTopicGuideTotalViewMethod,
                pbHotTopicGuideRefreshMethodSpecs,
            ),
            comment = PbCommentSymbolsGroup(
                bottomSheet = ForumBottomSheetSymbolsGroup(forumBottomSheetViewClass, forumBottomSheetInitScrollMethod),
                autoLoad = AutoLoadSymbolsGroup(autoRefreshTriggerMethod, autoLoadMoreConfigClass, autoLoadMoreConfigMethod),
                scroll = PbCommentScrollSymbolsGroup(
                    pbCommentScrollListenerClass,
                    pbCommentScrollMethod,
                    pbCommentScrollFragmentField,
                    pbCommentScrollBottomListenerField,
                    pbCommentScrollBottomMethod,
                ),
                bottomList = PbCommentBottomListSymbolsGroup(
                    pbCommentBottomListScrollClass,
                    pbCommentBottomListScrollMethod,
                    pbCommentBottomListOwnerField,
                ),
                bottomRecycler = PbCommentBottomRecyclerSymbolsGroup(
                    pbCommentBottomRecyclerScrollClass,
                    pbCommentBottomRecyclerScrollMethod,
                    pbCommentBottomRecyclerOwnerField,
                ),
            ),
            gestureScale = PbGestureScaleSymbolsGroup(
                pbGestureScaleManagerClass,
                pbGestureScaleDispatchMethod,
                pbGestureScaleListenerSetterMethod,
                pbGestureScaleListenerClass,
                pbGestureScaleListenerOnScaleMethod,
            ),
            likeAutoReply = PbLikeAutoReplySymbolsGroup(
                agreeView = PbLikeAutoReplyAgreeViewSymbolsGroup(
                    pbLikeAutoReplyAgreeViewClass,
                    pbLikeAutoReplyAgreeClickMethod,
                    pbLikeAutoReplyAgreeViewGetDataMethod,
                ),
                agreeData = PbLikeAutoReplyAgreeDataSymbolsGroup(
                    pbLikeAutoReplyAgreeDataClass,
                    pbLikeAutoReplyAgreeDataHasAgreeField,
                    pbLikeAutoReplyAgreeDataAgreeTypeField,
                    pbLikeAutoReplyAgreeDataIsInThreadField,
                ),
                inputContainer = PbLikeAutoReplyInputContainerSymbolsGroup(
                    pbLikeAutoReplyInputContainerClass,
                    pbLikeAutoReplyInputContainerGetInputViewMethod,
                    pbLikeAutoReplyInputContainerGetSendViewMethod,
                ),
            ),
            misc = PbMiscSymbolsGroup(
                commonLayoutPreloader = PbCommonLayoutPreloaderSymbolsGroup(pbCommonLayoutPreloaderGetOrDefaultMethod),
                replyServerResponse = ReplyServerResponseSymbolsGroup(
                    replyServerResponseClass,
                    replyServerResponseDecodeMethod,
                    replyServerResponseResultJsonField,
                ),
                agreeServerResponse = AgreeServerResponseSymbolsGroup(
                    agreeServerResponseClass,
                    agreeServerResponseDecodeLogicMethod,
                ),
                replyVisibilityProbe = ReplyVisibilityProbeSymbolsGroup(
                    replyVisibilityProbeReplyResponseClass,
                    replyVisibilityProbeReplyDecodeMethod,
                    replyVisibilityProbeReplyResultJsonField,
                    replyVisibilityProbeAddPostRequestClass,
                    replyVisibilityProbeAddPostRequestDataField,
                    replyVisibilityProbeResponsedMessageClass,
                    replyVisibilityProbeGetOriginalMessageMethod,
                    replyVisibilityProbeMessageClass,
                    replyVisibilityProbeMessageGetExtraMethod,
                    replyVisibilityProbeMessageGetTagMethod,
                    replyVisibilityProbeMessageSetTagMethod,
                    replyVisibilityProbeHttpMessageClass,
                    replyVisibilityProbeHttpMessageConstructor,
                    replyVisibilityProbeHttpMessageAddParamMethod,
                    replyVisibilityProbeHttpMessageAddHeaderMethod,
                    replyVisibilityProbeMessageManagerClass,
                    replyVisibilityProbeMessageManagerGetInstanceMethod,
                    replyVisibilityProbeMessageManagerFindTaskMethod,
                    replyVisibilityProbeMessageManagerRegisterTaskMethod,
                    replyVisibilityProbeMessageManagerSendMethod,
                    replyVisibilityProbeTbHttpMessageTaskClass,
                    replyVisibilityProbeTbHttpMessageTaskConstructor,
                    replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod,
                    replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod,
                    replyVisibilityProbeBdUniqueIdClass,
                    replyVisibilityProbeBdUniqueIdGenMethod,
                    replyVisibilityProbeTbadkCoreApplicationClass,
                    replyVisibilityProbeTbadkCoreApplicationGetInstMethod,
                    replyVisibilityProbeTbadkCoreApplicationGetZidMethod,
                    replyVisibilityProbeTbConfigClass,
                    replyVisibilityProbeTbConfigServerAddressField,
                    replyVisibilityProbeTbConfigPbFloorAgreeUrlField,
                    replyVisibilityProbeCmdConfigHttpClass,
                    replyVisibilityProbeCmdPbFloorAgreeField,
                    replyVisibilityProbeAgreeResponseClass,
                    replyVisibilityProbeAgreeDecodeLogicMethod,
                ),
            ),
        )
    }

    private fun buildWebSymbols(): WebSymbols {
        return WebSymbols(
            enterForum = EnterForumWebSymbolsGroup(
                enterForumWebControllerClass,
                enterForumWebLoadMethod,
                enterForumInitInfoDataClass,
                enterForumInitInfoGetUrlMethod,
            ),
            plainUrl = PlainUrlSymbolsGroup(
                clickableSpan = PlainUrlClickableSpanSymbolsGroup(
                    plainUrlClickableSpanClass,
                    plainUrlClickableSpanOnClickMethod,
                    plainUrlClickableSpanOnClickOwnerClasses,
                    plainUrlClickableSpanTypeField,
                    plainUrlClickableSpanUrlField,
                    plainUrlClickableSpanTextField,
                ),
                message = PlainUrlMessageSymbolsGroup(
                    manager = PlainUrlMessageManagerSymbolsGroup(
                        plainUrlMessageManagerClass,
                        plainUrlMessageDispatchMethod,
                    ),
                    response = PlainUrlMessageResponseSymbolsGroup(
                        plainUrlResponsedMessageClass,
                        plainUrlResponsedMessageGetCmdMethod,
                        plainUrlCustomResponsedMessageClass,
                        plainUrlCustomResponsedMessageGetDataMethod,
                    ),
                    application = PlainUrlApplicationSymbolsGroup(
                        plainUrlApplicationClass,
                        plainUrlApplicationGetInstMethod,
                    ),
                ),
                browserHelper = PlainUrlBrowserHelperSymbolsGroup(
                    plainUrlBrowserHelperClass,
                    plainUrlBrowserHelperStartWebActivityMethod,
                ),
                webContainer = PlainUrlWebContainerSymbolsGroup(
                    plainUrlWebContainerActivityClass,
                    plainUrlWebContainerInitDataMethod,
                    plainUrlWebContainerWebViewClientClass,
                    plainUrlWebContainerShouldOverrideUrlLoadingMethod,
                ),
            ),
            mountCard = MountCardLinkSymbolsGroup(
                mountCardLinkLayoutClass,
                mountCardLinkLayoutOnClickMethod,
                mountCardLinkLayoutDataField,
                mountCardLinkInfoDataClass,
                mountCardLinkInfoGetUrlMethod,
            ),
            adBlock = WebAdBlockSymbolsGroup(
                mineTab = MineTabWebBlockSymbolsGroup(
                    mineTabWebViewClass,
                    mineTabWebLoadUrlMethod,
                    mineTabWebGetUrlMethod,
                    mineTabWebGetInnerWebViewMethod,
                ),
                homeSideBar = HomeSideBarWebBlockSymbolsGroup(
                    homeSideBarWebViewClass,
                    homeSideBarTbWebViewClass,
                    homeSideBarWebGetWebViewMethod,
                    homeSideBarWebGetUrlMethod,
                    homeSideBarWebGetInnerWebViewMethod,
                    homeSideBarWebLoadUrlMethods,
                ),
            ),
        )
    }

    private fun buildPrivateMessageSymbols(): PrivateMessageSymbols {
        return PrivateMessageSymbols(
            readReceipt = PrivateReadReceiptSymbolsGroup(
                model = PrivateReadReceiptModelSymbolsGroup(
                    privateReadReceiptModelClass,
                    privateReadReceiptModelReadDispatchMethod,
                    privateReadReceiptModelBaseClass,
                    privateReadReceiptModelDataField,
                ),
                message = PrivateReadReceiptMessageSymbolsGroup(
                    privateReadReceiptMessageManagerClass,
                    privateReadReceiptMessageManagerGetInstanceMethod,
                    privateReadReceiptMessageManagerGetSocketClientMethod,
                    privateReadReceiptMessageSendMethod,
                    privateReadReceiptMessageBaseClass,
                    privateReadReceiptSocketClientClass,
                    privateReadReceiptSocketDuplicateCheckMethod,
                ),
                request = PrivateReadReceiptRequestSymbolsGroup(
                    privateReadReceiptRequestClass,
                    privateReadReceiptRequestMsgIdField,
                    privateReadReceiptRequestToUidField,
                ),
                response = PrivateReadReceiptResponseSymbolsGroup(
                    privateReadReceiptCommitResponseClass,
                    privateReadReceiptProcessAckMethod,
                    privateReadReceiptResponseErrorMethod,
                ),
                page = PrivateReadReceiptPageSymbolsGroup(
                    privateReadReceiptPageDataClass,
                    privateReadReceiptPageDataChatListMethod,
                    privateReadReceiptChatMessageClass,
                    privateReadReceiptChatMessageMsgIdMethod,
                    privateReadReceiptChatMessageUserIdMethod,
                    privateReadReceiptChatMessageLocalDataMethod,
                ),
                localAccount = PrivateReadReceiptLocalAccountSymbolsGroup(
                    privateReadReceiptLocalDataClass,
                    privateReadReceiptLocalDataStatusMethod,
                    privateReadReceiptAccountClass,
                    privateReadReceiptCurrentAccountMethod,
                ),
            ),
            tab = MessageTabSymbolsGroup(
                msgTabLocateToTabMethod,
                msgTabContainerSelectMethod,
                msgTabContainerExtDataField,
            ),
        )
    }

    private fun buildCollectionHistorySymbols(): CollectionHistorySymbols {
        return CollectionHistorySymbols(
            collection = CollectionSymbolsGroup(
                presenter = CollectionPresenterSymbolsGroup(
                    collectionPresenterField,
                    collectionPresenterListSetterMethod,
                    collectionPresenterListSetterMethodSpec,
                    collectionPresenterAdapterField,
                ),
                model = CollectionModelSymbolsGroup(
                    collectionModelField,
                    collectionModelListGetterMethod,
                    collectionModelListGetterMethodSpec,
                    collectionModelParseMethod,
                    collectionModelParseMethodSpec,
                    collectionModelListField,
                ),
                fragment = CollectionFragmentSymbolsGroup(
                    collectionFragmentDisplayListField,
                    collectionActivityNavControllerField,
                    collectionNavBarField,
                ),
                adapter = CollectionAdapterSymbolsGroup(
                    collectionAdapterShowFooterMethod,
                    collectionAdapterLoadingMethod,
                    collectionAdapterHasMoreMethod,
                    collectionEditModeMethod,
                ),
            ),
            history = HistorySymbolsGroup(
                activity = HistoryActivitySymbolsGroup(
                    historyAdapterField,
                    historyAdapterSetListMethod,
                    historyAdapterSetListMethodSpec,
                    historyListField,
                    historyActivityListUpdateMethod,
                    historyActivityListUpdateMethodSpec,
                    historyActivityNavBarField,
                ),
                threadData = HistoryThreadDataSymbolsGroup(
                    historyThreadNameMethod,
                    historyForumNameMethod,
                    historyUserNameMethod,
                    historyDescriptionMethod,
                    historyThreadIdMethod,
                    historyPostIdMethod,
                    historyLiveIdMethod,
                ),
            ),
            freeCopy = FreeCopySymbolsGroup(
                freeCopyPopupMenuClass,
                freeCopyPopupContentViewMethod,
                freeCopyPopupTextField,
            ),
        )
    }

    private fun buildMediaSymbols(): MediaHookPointSymbols {
        return MediaHookPointSymbols(
            image = buildImageSymbols(),
            ai = AiSymbols(
                spriteMeme = AiSpriteMemeSymbolsGroup(aiSpriteMemePanControllerClass, aiSpriteMemeEnableMethod),
                pbInput = AiPbInputSymbolsGroup(
                    aiPbNewInputContainerClass,
                    aiPbNewInputContainerInitSpriteMemeMethod,
                    aiPbNewInputContainerInitAiWriteMethod,
                ),
                emojiCreation = AiEmojiCreationSymbolsGroup(
                    aiPbAiEmojiCreationViewBindMethod,
                    aiPbPageBrowserAiEmojiCreationViewClass,
                    aiPbPageBrowserAiEmojiCreationBindMethod,
                ),
                imageViewerJumpButton = AiImageViewerJumpButtonSymbolsGroup(
                    aiImageViewerJumpButtonOwnerClass,
                    aiImageViewerJumpButtonInitMethod,
                ),
            ),
        )
    }

    private fun buildImageSymbols(): ImageSymbols {
        return ImageSymbols(
            original = OriginalImageSymbolsGroup(
                component = OriginalImageComponentSymbolsGroup(
                    origImagePagerAdapterClass,
                    origImageUrlDragImageViewClass,
                    origImageDataClass,
                    origImageSetPrimaryItemMethod,
                    origImageSetAssistUrlMethod,
                ),
                data = OriginalImageDataSymbolsGroup(
                    origImageAssistDataMethod,
                    origImageOriginTextMethod,
                    origImageShowButtonField,
                    origImageBlockedField,
                    origImageOriginalProcessField,
                    origImageOriginalUrlField,
                ),
                sharedPref = OriginalImageSharedPrefSymbolsGroup(
                    origImageSharedPrefHelperClass,
                    origImageSharedPrefGetInstanceMethod,
                    origImageSharedPrefPutBooleanMethod,
                ),
                md5 = OriginalImageMd5SymbolsGroup(origImageMd5Class, origImageMd5Method),
                trigger = OriginalImageTriggerSymbolsGroup(
                    origImagePrimaryReadyMethod,
                    origImageTriggerMethod,
                    origImageDirectStartMethod,
                ),
            ),
            shareTrack = ShareTrackSymbolsGroup(
                shareTrackBuilderClass,
                shareTrackBuildUrlMethod,
                shareTrackAppendQueryMethod,
            ),
            viewerShare = ImageViewerShareSymbolsGroup(
                config = ImageViewerShareConfigSymbolsGroup(
                    imageViewerShareConfigClass,
                    imageViewerShareIsDialogField,
                    imageViewerShareItemField,
                    imageViewerShareAddOutsideMethod,
                ),
                request = ImageViewerShareRequestSymbolsGroup(
                    imageViewerShareGetRequestDataMethod,
                    imageViewerShareSetRequestDataMethod,
                    imageViewerShareGetContextMethod,
                ),
                item = ImageViewerShareItemSymbolsGroup(
                    imageViewerShareItemClass,
                    imageViewerShareItemTitleField,
                    imageViewerShareItemContentField,
                    imageViewerShareItemLinkUrlField,
                    imageViewerShareItemImageUriField,
                    imageViewerShareItemImageUrlField,
                ),
                itemView = ImageViewerShareItemViewSymbolsGroup(
                    imageViewerShareItemLocalFileField,
                    imageViewerShareItemViewClass,
                    imageViewerShareItemNameByResMethod,
                    imageViewerShareItemNameByTextMethod,
                ),
            ),
        )
    }

    private fun buildResourceSymbols(): ResourceSymbols {
        return ResourceSymbols(
            imageViewerShareIconResId,
            homeNativeGlassSubPbNextPageMoreViewId,
            homeNativeGlassPbReplyTitleDividerViewId,
            homeNativeGlassDynamicBackgroundColorIds,
        )
    }

    private fun buildScanMeta(): ScanMeta {
        return ScanMeta(
            featureStatusMap = featureStatusMap,
            availability = ScanAvailabilityMeta(
                scanSupportState,
                scanTargetVersionCode,
                scanTargetVersionName,
                scanTargetVersionType,
            ),
            scanErrors = scanErrors,
            source = source,
            createdAt = createdAt,
            cacheSchemaVersion = cacheSchemaVersion,
            dexKitRuleVersion = dexKitRuleVersion,
        )
    }
}

internal inline fun buildHookSymbols(block: HookSymbolsBuilder.() -> Unit): HookSymbols {
    return HookSymbolsBuilder().apply(block).build()
}
