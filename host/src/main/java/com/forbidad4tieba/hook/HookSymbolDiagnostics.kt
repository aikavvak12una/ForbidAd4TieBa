package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.contracts.BuildIdentity
import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal object HookSymbolDiagnostics {
    fun describeSymbols(context: Context, symbols: HookSymbols): String {
        val appMeta = describeAppMetaFull(context)
        val modVersion = runtimeModuleVersionName()
        val scanIssueLines = HookSymbolResolver.formatScanIssueLines(symbols)

        fun fmt(name: String, value: String?): String {
            val isNull = value == null ||
                value == "null" ||
                value.contains("null.") ||
                value.contains(".null") ||
                value.contains("[null]")

            return if (!isNull) {
                "Symbol[$name: $value]"
            } else {
                "Symbol[$name: NOT FOUND]"
            }
        }

        val imageViewerShareValue = if (
            !symbols[ImageSharingContract.imageViewerShareConfigClass].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareIsDialogField].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareItemField].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareAddOutsideMethod].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareGetRequestDataMethod].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareSetRequestDataMethod].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareGetContextMethod].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareItemClass].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareItemImageUriField].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareItemViewClass].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareItemNameByResMethod].isNullOrBlank() &&
            !symbols[ImageSharingContract.imageViewerShareItemNameByTextMethod].isNullOrBlank() &&
            symbols[ImageSharingContract.imageViewerShareIconResId] != null
        ) {
            "${symbols[ImageSharingContract.imageViewerShareConfigClass]}[${symbols[ImageSharingContract.imageViewerShareIsDialogField]},${symbols[ImageSharingContract.imageViewerShareItemField]}].{${symbols[ImageSharingContract.imageViewerShareAddOutsideMethod]},${symbols[ImageSharingContract.imageViewerShareGetRequestDataMethod]},${symbols[ImageSharingContract.imageViewerShareSetRequestDataMethod]},${symbols[ImageSharingContract.imageViewerShareGetContextMethod]}} / ${symbols[ImageSharingContract.imageViewerShareItemClass]}[${symbols[ImageSharingContract.imageViewerShareItemTitleField]},${symbols[ImageSharingContract.imageViewerShareItemContentField]},${symbols[ImageSharingContract.imageViewerShareItemLinkUrlField]},${symbols[ImageSharingContract.imageViewerShareItemImageUriField]},${symbols[ImageSharingContract.imageViewerShareItemImageUrlField]},${symbols[ImageSharingContract.imageViewerShareItemLocalFileField]}] / ${symbols[ImageSharingContract.imageViewerShareItemViewClass]}.{${symbols[ImageSharingContract.imageViewerShareItemNameByResMethod]},${symbols[ImageSharingContract.imageViewerShareItemNameByTextMethod]}} icon=${symbols[ImageSharingContract.imageViewerShareIconResId]}"
        } else {
            null
        }

        val originalImageValue = if (
            !symbols[OriginalImageContract.origImageUrlDragImageViewClass].isNullOrBlank() &&
            !symbols[OriginalImageContract.origImageDataClass].isNullOrBlank() &&
            !symbols[OriginalImageContract.origImageTriggerMethod].isNullOrBlank() &&
            !symbols[OriginalImageContract.origImageAssistDataMethod].isNullOrBlank() &&
            !symbols[OriginalImageContract.origImageShowButtonField].isNullOrBlank() &&
            !symbols[OriginalImageContract.origImageBlockedField].isNullOrBlank() &&
            !symbols[OriginalImageContract.origImageOriginalProcessField].isNullOrBlank() &&
            !symbols[OriginalImageContract.origImageOriginalUrlField].isNullOrBlank()
        ) {
            "${symbols[OriginalImageContract.origImageUrlDragImageViewClass]}.{${symbols[OriginalImageContract.origImagePrimaryReadyMethod]},${symbols[OriginalImageContract.origImageTriggerMethod]},${symbols[OriginalImageContract.origImageDirectStartMethod]}} assist=${symbols[OriginalImageContract.origImageSetAssistUrlMethod]}/${symbols[OriginalImageContract.origImageAssistDataMethod]}/${symbols[OriginalImageContract.origImageOriginTextMethod]} data=${symbols[OriginalImageContract.origImageDataClass]}[${symbols[OriginalImageContract.origImageShowButtonField]},${symbols[OriginalImageContract.origImageBlockedField]},${symbols[OriginalImageContract.origImageOriginalProcessField]},${symbols[OriginalImageContract.origImageOriginalUrlField]}] primary=${symbols[OriginalImageContract.origImagePagerAdapterClass]}.${symbols[OriginalImageContract.origImageSetPrimaryItemMethod]}"
        } else {
            null
        }

        val plainUrlClickableSpanOnClickOwnerClasses = symbols[PlainUrlContract.plainUrlClickableSpanOnClickOwnerClasses]
        val plainUrlValue = when {
            !plainUrlClickableSpanOnClickOwnerClasses.isNullOrEmpty() &&
                !symbols[PlainUrlContract.plainUrlClickableSpanOnClickMethod].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlClickableSpanTypeField].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlClickableSpanUrlField].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlClickableSpanTextField].isNullOrBlank() -> {
                "${plainUrlClickableSpanOnClickOwnerClasses.joinToString(",")}.${symbols[PlainUrlContract.plainUrlClickableSpanOnClickMethod]}[${symbols[PlainUrlContract.plainUrlClickableSpanTypeField]},${symbols[PlainUrlContract.plainUrlClickableSpanUrlField]},${symbols[PlainUrlContract.plainUrlClickableSpanTextField]}]"
            }
            !symbols[PlainUrlContract.plainUrlMessageManagerClass].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlMessageDispatchMethod].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlResponsedMessageClass].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlResponsedMessageGetCmdMethod].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlCustomResponsedMessageClass].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlApplicationClass].isNullOrBlank() &&
                !symbols[PlainUrlContract.plainUrlApplicationGetInstMethod].isNullOrBlank() -> {
                "${symbols[PlainUrlContract.plainUrlMessageManagerClass]}.${symbols[PlainUrlContract.plainUrlMessageDispatchMethod]}(${symbols[PlainUrlContract.plainUrlResponsedMessageClass]}) cmd=${symbols[PlainUrlContract.plainUrlResponsedMessageGetCmdMethod]} data=${symbols[PlainUrlContract.plainUrlCustomResponsedMessageClass]}.${symbols[PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod]} app=${symbols[PlainUrlContract.plainUrlApplicationClass]}.${symbols[PlainUrlContract.plainUrlApplicationGetInstMethod]}"
            }
            else -> null
        }

        val aiComponentValue = if (
            !symbols[AiContract.aiSpriteMemePanControllerClass].isNullOrBlank() &&
            !symbols[AiContract.aiSpriteMemeEnableMethod].isNullOrBlank() &&
            !symbols[AiContract.aiPbNewInputContainerClass].isNullOrBlank() &&
            !symbols[AiContract.aiPbNewInputContainerInitSpriteMemeMethod].isNullOrBlank() &&
            !symbols[AiContract.aiPbNewInputContainerInitAiWriteMethod].isNullOrBlank()
        ) {
            val imageViewerJumpButton = if (
                !symbols[AiContract.aiImageViewerJumpButtonOwnerClass].isNullOrBlank() &&
                !symbols[AiContract.aiImageViewerJumpButtonInitMethod].isNullOrBlank()
            ) {
                " / ${symbols[AiContract.aiImageViewerJumpButtonOwnerClass]}.${symbols[AiContract.aiImageViewerJumpButtonInitMethod]}"
            } else {
                ""
            }
            "${symbols[AiContract.aiSpriteMemePanControllerClass]}.${symbols[AiContract.aiSpriteMemeEnableMethod]} / " +
                "${symbols[AiContract.aiPbNewInputContainerClass]}.{${symbols[AiContract.aiPbNewInputContainerInitSpriteMemeMethod]},${symbols[AiContract.aiPbNewInputContainerInitAiWriteMethod]}}" +
                imageViewerJumpButton
        } else {
            null
        }

        val pbAdBidCommonReady =
            !symbols[PbAdRequestContract.pbAdBidCommonRequestModelClass].isNullOrBlank() &&
                !symbols[PbAdRequestContract.pbAdBidCommonRequestStartMethods].isNullOrEmpty() &&
                !symbols[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod].isNullOrBlank()
        val pbAdBidPageBrowserReady =
            !symbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass].isNullOrBlank() &&
                !symbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod].isNullOrBlank()
        val pbAdBidValue = when {
            pbAdBidCommonReady || pbAdBidPageBrowserReady -> buildString {
                if (pbAdBidCommonReady) {
                    append("common=")
                    append(symbols[PbAdRequestContract.pbAdBidCommonRequestModelClass])
                    append(".{")
                    append(symbols[PbAdRequestContract.pbAdBidCommonRequestStartMethods]?.joinToString(";"))
                    append("}#")
                    append(symbols[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod])
                } else {
                    append("common=optional-absent")
                }
                append(" pageBrowser=")
                if (pbAdBidPageBrowserReady) {
                    append(symbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass])
                    append(".")
                    append(symbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod])
                } else {
                    append("optional-absent")
                }
            }
            else -> null
        }

        return """
            ========== TBHook Match Status ==========
            App Version   : $appMeta
            Module Version: $modVersion
            Match Result  :
            ${fmt("Home", "${symbols[HomeTabsContract.homeTabClass]}.${symbols[HomeTabsContract.homeTabRebuildMethod]}[${symbols[HomeTabsContract.homeTabListField]}] item={${symbols[HomeTabsContract.homeTabItemTypeField]},${symbols[HomeTabsContract.homeTabItemCodeField]},${symbols[HomeTabsContract.homeTabItemNameField]},${symbols[HomeTabsContract.homeTabItemUrlField]}} main={${symbols[HomeTabsContract.homeTabItemMainSetterMethod]},${symbols[HomeTabsContract.homeTabItemMainIntField]},${symbols[HomeTabsContract.homeTabItemMainBooleanField]}}")}
            ${fmt("Settings", "${symbols[SettingsContract.settingsClass]}.${symbols[SettingsContract.settingsInitMethod]}[${symbols[SettingsContract.settingsContainerField]}]")}
            ${fmt("AutoSignInNative", "${symbols[AutoSignInContract.autoSignInNetworkClass]}.${symbols[AutoSignInContract.autoSignInNetworkAddPostDataMethod]}/${symbols[AutoSignInContract.autoSignInNetworkPostNetDataMethod]} tbs=${symbols[AutoSignInContract.autoSignInNetworkSetNeedTbsMethod]} sig=${symbols[AutoSignInContract.autoSignInNetworkSetNeedSigMethod]} config=${symbols[AutoSignInContract.autoSignInTbConfigClass]}[${symbols[AutoSignInContract.autoSignInServerAddressField]}] account=${symbols[AutoSignInContract.autoSignInCoreApplicationClass]}.${symbols[AutoSignInContract.autoSignInCurrentAccountMethod]}")}
            ${fmt("AutoSignInHybrid", "${symbols[AutoSignInContract.autoSignInHybridJsBridgeClass]}.${symbols[AutoSignInContract.autoSignInHybridNativeNetworkProxyMethod]} task=${symbols[AutoSignInContract.autoSignInHybridTaskClass]}.${symbols[AutoSignInContract.autoSignInHybridTaskDoInBackgroundMethod]}")}
            ${fmt("Zga", symbols[StrategyAdContract.zgaClass])}
            ${fmt("Splash", "${symbols[StrategyAdContract.splashAdHelperClass]}.${symbols[StrategyAdContract.splashAdHelperMethod]}")}
            ${fmt("FeedKey", symbols[FeedContract.feedTemplateKeyMethod])}
            ${fmt("FeedPayload", symbols[FeedContract.feedTemplatePayloadMethod])}
            ${fmt("FeedLoadMore", symbols[FeedContract.feedTemplateLoadMoreMethod])}
            ${fmt("SearchBoxHint", "${symbols[SearchBoxContract.searchBoxViewClass]}.${symbols[SearchBoxContract.searchBoxSetHintMethod]} owner=${symbols[SearchBoxContract.homeSearchBoxOwnerClass]}.{${symbols[SearchBoxContract.homeSearchBoxInitMethod]},${symbols[SearchBoxContract.homeSearchBoxGetterMethod]}}")}
            ${fmt("HomeRightSlot", "${symbols[HomeRightSlotContract.homeRightSlotClass]}.{${symbols[HomeRightSlotContract.homeRightSlotStateMethods]?.joinToString(",")}}")}
            ${fmt("PbFalling", "${symbols[PbFallingContract.pbFallingViewClass]}.{${symbols[PbFallingContract.pbFallingInitMethod]},${symbols[PbFallingContract.pbFallingShowMethod]},${symbols[PbFallingContract.pbFallingClearMethod]}}")}
            ${fmt("PbBottomEnterBarView", "${symbols[PbBottomBannerContract.pbBottomEnterBarViewClass]} constructors=${symbols[PbBottomBannerContract.pbBottomEnterBarConstructorCount]} refresh={${symbols[PbBottomBannerContract.pbBottomEnterBarRefreshMethodSpecs]?.joinToString(";")}}")}
            ${fmt("PbEnterFrsAnimationTip", "${symbols[PbBottomBannerContract.pbEnterFrsAnimationTipViewClass]} constructors=${symbols[PbBottomBannerContract.pbEnterFrsAnimationTipConstructorCount]} callers={${symbols[PbBottomBannerContract.pbEnterFrsAnimationTipCallerClasses]?.joinToString(";")}}")}
            ${fmt("PbBottomEnterBarHotTopicGuide", "${symbols[PbBottomBannerContract.pbHotTopicGuideTotalViewMethod]} refresh={${symbols[PbBottomBannerContract.pbHotTopicGuideRefreshMethodSpecs]?.joinToString(";")}}")}
            ${fmt("PbEarlyAdInsert", "${symbols[PbEarlyAdContract.pbEarlyAdInsertClass]}.{${symbols[PbEarlyAdContract.pbEarlyAdInsertMethodSpecs]?.joinToString(";")}}")}
            ${fmt("PbFirstFloorRecommendInsert", "${symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertClass]}.${symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertMethod]}")}
            ${fmt("EnterForumWeb", "source=${symbols[EnterForumContract.enterForumInitInfoDataClass]}.${symbols[EnterForumContract.enterForumInitInfoGetUrlMethod]} webLoad=${symbols[EnterForumContract.enterForumWebControllerClass]}.${symbols[EnterForumContract.enterForumWebLoadMethod]}")}
            ${fmt("PlainUrlClickableSpan", plainUrlValue)}
            ${fmt("MountCardLink", "${symbols[MountCardContract.mountCardLinkLayoutClass]}.${symbols[MountCardContract.mountCardLinkLayoutOnClickMethod]}[${symbols[MountCardContract.mountCardLinkLayoutDataField]}->${symbols[MountCardContract.mountCardLinkInfoDataClass]}.${symbols[MountCardContract.mountCardLinkInfoGetUrlMethod]}]")}
            ${fmt("ForumTopShift", "${symbols[ForumTopShiftContract.forumBottomSheetViewClass]}.{${symbols[ForumTopShiftContract.forumBottomSheetInitScrollMethod]}(Int,Boolean,Function0),${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SMOOTH_INIT_GETTER}(),${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SETUP_METHOD}(Int,Int,Int,Boolean),${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_MAX_SCROLL_GETTER}()}")}
            ${fmt("AutoRefresh", "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.${symbols[AutoRefreshContract.autoRefreshTriggerMethod]}")}
            ${fmt("AutoLoadMore", "${symbols[AutoLoadMoreContract.autoLoadMoreConfigClass]}.${symbols[AutoLoadMoreContract.autoLoadMoreConfigMethod]}")}
            ${fmt("PbCommentPreload", symbols[AutoLoadMoreContract.pbCommentPreloadConfigMethodSpec])}
            ${fmt("PbGestureScale", "${symbols[PbGestureScaleContract.pbGestureScaleManagerClass]}.${symbols[PbGestureScaleContract.pbGestureScaleDispatchMethod]}/${symbols[PbGestureScaleContract.pbGestureScaleListenerSetterMethod]} -> ${symbols[PbGestureScaleContract.pbGestureScaleListenerClass]}.${symbols[PbGestureScaleContract.pbGestureScaleListenerOnScaleMethod]}")}
            ${fmt("PbLikeAutoReply", "${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewClass]}.${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeClickMethod]}/${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewGetDataMethod]}[${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataHasAgreeField]},${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataAgreeTypeField]},${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataIsInThreadField]}] -> ${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerClass]}.{${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetInputViewMethod]},${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetSendViewMethod]}}")}
            ${fmt("CollectionCore", "${symbols[CollectionContract.collectionPresenterField]}.${symbols[CollectionContract.collectionPresenterListSetterMethod]} / ${symbols[CollectionContract.collectionModelField]}.${symbols[CollectionContract.collectionModelListGetterMethod]}/${symbols[CollectionContract.collectionModelParseMethod]}")}
            ${fmt("CollectionAdapter", "${symbols[CollectionContract.collectionPresenterAdapterField]}.{${symbols[CollectionContract.collectionAdapterShowFooterMethod]},${symbols[CollectionContract.collectionAdapterLoadingMethod]},${symbols[CollectionContract.collectionAdapterHasMoreMethod]}}")}
            ${fmt("CollectionListField", "${symbols[CollectionContract.collectionModelListField]}/${symbols[CollectionContract.collectionFragmentDisplayListField]} nav={${symbols[CollectionContract.collectionActivityNavControllerField]},${symbols[CollectionContract.collectionNavBarField]}}")}
            ${fmt("CollectionEditMode", symbols[CollectionContract.collectionEditModeMethod])}
            ${fmt("HistorySearch", "${symbols[HistoryContract.historyAdapterField]}.${symbols[HistoryContract.historyAdapterSetListMethod]}[${symbols[HistoryContract.historyAdapterSetListMethodSpec]}][${symbols[HistoryContract.historyListField]}]#${symbols[HistoryContract.historyActivityListUpdateMethod]}[${symbols[HistoryContract.historyActivityListUpdateMethodSpec]}] nav=${symbols[HistoryContract.historyActivityNavBarField]} getters={${symbols[HistoryContract.historyThreadNameMethod]},${symbols[HistoryContract.historyForumNameMethod]},${symbols[HistoryContract.historyUserNameMethod]},${symbols[HistoryContract.historyDescriptionMethod]},${symbols[HistoryContract.historyThreadIdMethod]},${symbols[HistoryContract.historyPostIdMethod]},${symbols[HistoryContract.historyLiveIdMethod]}}")}
            ${fmt("MsgTabNotify", symbols[MessageTabContract.msgTabLocateToTabMethod])}
            ${fmt("PrivateReadReceipt", "${symbols[PrivateReadReceiptContract.privateReadReceiptModelClass]}.${symbols[PrivateReadReceiptContract.privateReadReceiptModelReadDispatchMethod]}[${symbols[PrivateReadReceiptContract.privateReadReceiptModelDataField]}->${symbols[PrivateReadReceiptContract.privateReadReceiptPageDataClass]}.${symbols[PrivateReadReceiptContract.privateReadReceiptPageDataChatListMethod]}] ack=${symbols[PrivateReadReceiptContract.privateReadReceiptModelBaseClass]}.${symbols[PrivateReadReceiptContract.privateReadReceiptProcessAckMethod]}(${symbols[PrivateReadReceiptContract.privateReadReceiptCommitResponseClass]}).${symbols[PrivateReadReceiptContract.privateReadReceiptResponseErrorMethod]} / ${symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerClass]}.${symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetInstanceMethod]}.${symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetSocketClientMethod]}.${symbols[PrivateReadReceiptContract.privateReadReceiptMessageSendMethod]}(${symbols[PrivateReadReceiptContract.privateReadReceiptMessageBaseClass]}) dup=${symbols[PrivateReadReceiptContract.privateReadReceiptSocketClientClass]}.${symbols[PrivateReadReceiptContract.privateReadReceiptSocketDuplicateCheckMethod]} request=${symbols[PrivateReadReceiptContract.privateReadReceiptRequestClass]}[${symbols[PrivateReadReceiptContract.privateReadReceiptRequestMsgIdField]},${symbols[PrivateReadReceiptContract.privateReadReceiptRequestToUidField]}]")}
            ${fmt("FreeCopyPopup", "${symbols[FreeCopyContract.freeCopyPopupMenuClass]}.${symbols[FreeCopyContract.freeCopyPopupContentViewMethod]}[${symbols[FreeCopyContract.freeCopyPopupTextField]}]")}
            ${fmt("FreeCopyNative", "${symbols[FreeCopyContract.freeCopyPostDataClass]}.${symbols[FreeCopyContract.freeCopyPostCopyMethodSpec]} parse={${symbols[FreeCopyContract.freeCopyPostParseMethodSpec]},${symbols[FreeCopyContract.freeCopySubPostParseMethodSpec]}} floor=${symbols[FreeCopyContract.freeCopyPostFloorMethodSpec]} richText=${symbols[FreeCopyContract.freeCopyRichTextViewClass]} longPress={${symbols[FreeCopyContract.freeCopyPostLongPressMethodSpecs]?.joinToString(";")}} title={${symbols[FreeCopyContract.freeCopyTitleBindMethodSpecs]?.joinToString(";")}}[${symbols[FreeCopyContract.freeCopyTitleContainerField]}:${symbols[FreeCopyContract.freeCopyTitleTextField]}->${symbols[FreeCopyContract.freeCopyTitlePostDataMethodSpec]}]")}
            ${fmt("BottomTab", "${symbols[MainTabsContract.mainTabDataClass]}.${symbols[MainTabsContract.mainTabAddMethod]}/${symbols[MainTabsContract.mainTabGetListMethod]}->${symbols[MainTabsContract.mainTabDelegateGetStructureMethod]}[${symbols[MainTabsContract.mainTabStructureTypeField]},${symbols[MainTabsContract.mainTabStructureDynamicIconField]},${symbols[MainTabsContract.mainTabStructureFragmentField]}]")}
            ${fmt("DefaultOriginalImage", originalImageValue)}
            ${fmt("ImageViewerShare", imageViewerShareValue)}
            ${fmt("FeedCardBind", "FeedCardView.${symbols[FeedContract.feedCardBindMethod]}")}
            ${fmt("FeedCardDataList", symbols[FeedContract.feedCardDataListField])}
            ${fmt("FeedHeadParams", symbols[FeedContract.feedHeadParamsField])}
            ${fmt("RecommendCardNestedData", "${symbols[FeedContract.feedRecommendCardNestedDataMethod]}[${symbols[FeedContract.feedRecommendCardNestedDataListField]}]")}
            ${fmt("PbAdBid", pbAdBidValue)}
            ${fmt("PostAdDataFilter", "type=${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}.${symbols[PostAdDataContract.typeAdapterSetDataMethod]} recycler=${StableTiebaHookPoints.RECYCLER_VIEW_TYPE_ADAPTER_CLASS}.${symbols[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod]} item=${symbols[PostAdDataContract.typeAdapterDataItemClass]}.${symbols[PostAdDataContract.typeAdapterDataGetTypeMethod]}")}
            ${fmt("AiComponents", aiComponentValue)}

            Source        : ${symbols.source}
            Scan Errors   : ${scanIssueLines.joinToString(" | ").ifEmpty { "-" }}
            =========================================
        """.trimIndent()
    }

    fun describeAppMetaFull(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            "${info.versionName} ($vCode)"
        } catch (_: Throwable) {
            "Unknown"
        }
    }

    fun describeAppMeta(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            "pkg=${context.packageName}, verCode=$vCode, update=${info.lastUpdateTime}"
        } catch (_: Throwable) {
            "pkg=${context.packageName}"
        }
    }

    fun runtimeModuleVersionName(): String = BuildIdentity.current.versionName

    fun runtimeModuleVersionCodeLabel(): String = BuildIdentity.current.cacheIdentity
}
