package com.forbidad4tieba.hook

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
            !symbols.imageViewerShareConfigClass.isNullOrBlank() &&
            !symbols.imageViewerShareIsDialogField.isNullOrBlank() &&
            !symbols.imageViewerShareItemField.isNullOrBlank() &&
            !symbols.imageViewerShareAddOutsideMethod.isNullOrBlank() &&
            !symbols.imageViewerShareGetRequestDataMethod.isNullOrBlank() &&
            !symbols.imageViewerShareSetRequestDataMethod.isNullOrBlank() &&
            !symbols.imageViewerShareGetContextMethod.isNullOrBlank() &&
            !symbols.imageViewerShareItemClass.isNullOrBlank() &&
            !symbols.imageViewerShareItemImageUriField.isNullOrBlank() &&
            !symbols.imageViewerShareItemViewClass.isNullOrBlank() &&
            !symbols.imageViewerShareItemNameByResMethod.isNullOrBlank() &&
            !symbols.imageViewerShareItemNameByTextMethod.isNullOrBlank() &&
            symbols.imageViewerShareIconResId != null
        ) {
            "${symbols.imageViewerShareConfigClass}[${symbols.imageViewerShareIsDialogField},${symbols.imageViewerShareItemField}].{${symbols.imageViewerShareAddOutsideMethod},${symbols.imageViewerShareGetRequestDataMethod},${symbols.imageViewerShareSetRequestDataMethod},${symbols.imageViewerShareGetContextMethod}} / ${symbols.imageViewerShareItemClass}[${symbols.imageViewerShareItemTitleField},${symbols.imageViewerShareItemContentField},${symbols.imageViewerShareItemLinkUrlField},${symbols.imageViewerShareItemImageUriField},${symbols.imageViewerShareItemImageUrlField},${symbols.imageViewerShareItemLocalFileField}] / ${symbols.imageViewerShareItemViewClass}.{${symbols.imageViewerShareItemNameByResMethod},${symbols.imageViewerShareItemNameByTextMethod}} icon=${symbols.imageViewerShareIconResId}"
        } else {
            null
        }

        val originalImageValue = if (
            !symbols.origImageUrlDragImageViewClass.isNullOrBlank() &&
            !symbols.origImageDataClass.isNullOrBlank() &&
            !symbols.origImageTriggerMethod.isNullOrBlank() &&
            !symbols.origImageAssistDataMethod.isNullOrBlank() &&
            !symbols.origImageShowButtonField.isNullOrBlank() &&
            !symbols.origImageBlockedField.isNullOrBlank() &&
            !symbols.origImageOriginalProcessField.isNullOrBlank() &&
            !symbols.origImageOriginalUrlField.isNullOrBlank()
        ) {
            "${symbols.origImageUrlDragImageViewClass}.{${symbols.origImagePrimaryReadyMethod},${symbols.origImageTriggerMethod},${symbols.origImageDirectStartMethod}} assist=${symbols.origImageSetAssistUrlMethod}/${symbols.origImageAssistDataMethod}/${symbols.origImageOriginTextMethod} data=${symbols.origImageDataClass}[${symbols.origImageShowButtonField},${symbols.origImageBlockedField},${symbols.origImageOriginalProcessField},${symbols.origImageOriginalUrlField}] primary=${symbols.origImagePagerAdapterClass}.${symbols.origImageSetPrimaryItemMethod}"
        } else {
            null
        }

        val plainUrlClickableSpanOnClickOwnerClasses = symbols.plainUrlClickableSpanOnClickOwnerClasses
        val plainUrlValue = when {
            !plainUrlClickableSpanOnClickOwnerClasses.isNullOrEmpty() &&
                !symbols.plainUrlClickableSpanOnClickMethod.isNullOrBlank() &&
                !symbols.plainUrlClickableSpanTypeField.isNullOrBlank() &&
                !symbols.plainUrlClickableSpanUrlField.isNullOrBlank() &&
                !symbols.plainUrlClickableSpanTextField.isNullOrBlank() -> {
                "${plainUrlClickableSpanOnClickOwnerClasses.joinToString(",")}.${symbols.plainUrlClickableSpanOnClickMethod}[${symbols.plainUrlClickableSpanTypeField},${symbols.plainUrlClickableSpanUrlField},${symbols.plainUrlClickableSpanTextField}]"
            }
            !symbols.plainUrlMessageManagerClass.isNullOrBlank() &&
                !symbols.plainUrlMessageDispatchMethod.isNullOrBlank() &&
                !symbols.plainUrlResponsedMessageClass.isNullOrBlank() &&
                !symbols.plainUrlResponsedMessageGetCmdMethod.isNullOrBlank() &&
                !symbols.plainUrlCustomResponsedMessageClass.isNullOrBlank() &&
                !symbols.plainUrlCustomResponsedMessageGetDataMethod.isNullOrBlank() &&
                !symbols.plainUrlApplicationClass.isNullOrBlank() &&
                !symbols.plainUrlApplicationGetInstMethod.isNullOrBlank() -> {
                "${symbols.plainUrlMessageManagerClass}.${symbols.plainUrlMessageDispatchMethod}(${symbols.plainUrlResponsedMessageClass}) cmd=${symbols.plainUrlResponsedMessageGetCmdMethod} data=${symbols.plainUrlCustomResponsedMessageClass}.${symbols.plainUrlCustomResponsedMessageGetDataMethod} app=${symbols.plainUrlApplicationClass}.${symbols.plainUrlApplicationGetInstMethod}"
            }
            else -> null
        }

        val aiComponentValue = if (
            !symbols.aiSpriteMemePanControllerClass.isNullOrBlank() &&
            !symbols.aiSpriteMemeEnableMethod.isNullOrBlank() &&
            !symbols.aiPbNewInputContainerClass.isNullOrBlank() &&
            !symbols.aiPbNewInputContainerInitSpriteMemeMethod.isNullOrBlank() &&
            !symbols.aiPbNewInputContainerInitAiWriteMethod.isNullOrBlank()
        ) {
            val imageViewerJumpButton = if (
                !symbols.aiImageViewerJumpButtonOwnerClass.isNullOrBlank() &&
                !symbols.aiImageViewerJumpButtonInitMethod.isNullOrBlank()
            ) {
                " / ${symbols.aiImageViewerJumpButtonOwnerClass}.${symbols.aiImageViewerJumpButtonInitMethod}"
            } else {
                ""
            }
            "${symbols.aiSpriteMemePanControllerClass}.${symbols.aiSpriteMemeEnableMethod} / " +
                "${symbols.aiPbNewInputContainerClass}.{${symbols.aiPbNewInputContainerInitSpriteMemeMethod},${symbols.aiPbNewInputContainerInitAiWriteMethod}}" +
                imageViewerJumpButton
        } else {
            null
        }

        val pbAdBidCommonReady =
            !symbols.pbAdBidCommonRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidCommonRequestStartMethods.isNullOrEmpty() &&
                !symbols.pbAdBidCommonRequestNotifyMethod.isNullOrBlank()
        val pbAdBidPageBrowserReady =
            !symbols.pbAdBidPageBrowserRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidPageBrowserRequestDataMethod.isNullOrBlank()
        val pbAdBidValue = when {
            pbAdBidCommonReady || pbAdBidPageBrowserReady -> buildString {
                if (pbAdBidCommonReady) {
                    append("common=")
                    append(symbols.pbAdBidCommonRequestModelClass)
                    append(".{")
                    append(symbols.pbAdBidCommonRequestStartMethods?.joinToString(";"))
                    append("}#")
                    append(symbols.pbAdBidCommonRequestNotifyMethod)
                } else {
                    append("common=optional-absent")
                }
                append(" pageBrowser=")
                if (pbAdBidPageBrowserReady) {
                    append(symbols.pbAdBidPageBrowserRequestModelClass)
                    append(".")
                    append(symbols.pbAdBidPageBrowserRequestDataMethod)
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
            ${fmt("Home", "${symbols.homeTabClass}.${symbols.homeTabRebuildMethod}[${symbols.homeTabListField}] item={${symbols.homeTabItemTypeField},${symbols.homeTabItemCodeField},${symbols.homeTabItemNameField},${symbols.homeTabItemUrlField}} main={${symbols.homeTabItemMainSetterMethod},${symbols.homeTabItemMainIntField},${symbols.homeTabItemMainBooleanField}}")}
            ${fmt("Settings", "${symbols.settingsClass}.${symbols.settingsInitMethod}[${symbols.settingsContainerField}]")}
            ${fmt("Zga", symbols.zgaClass)}
            ${fmt("Splash", "${symbols.splashAdHelperClass}.${symbols.splashAdHelperMethod}")}
            ${fmt("FeedKey", symbols.feedTemplateKeyMethod)}
            ${fmt("FeedPayload", symbols.feedTemplatePayloadMethod)}
            ${fmt("FeedLoadMore", symbols.feedTemplateLoadMoreMethod)}
            ${fmt("SearchBoxHint", "${symbols.searchBoxViewClass}.${symbols.searchBoxSetHintMethod} owner=${symbols.homeSearchBoxOwnerClass}.{${symbols.homeSearchBoxInitMethod},${symbols.homeSearchBoxGetterMethod}}")}
            ${fmt("HomeRightSlot", "${symbols.homeRightSlotClass}.{${symbols.homeRightSlotStateMethods?.joinToString(",")}}")}
            ${fmt("PbFalling", "${symbols.pbFallingViewClass}.{${symbols.pbFallingInitMethod},${symbols.pbFallingShowMethod},${symbols.pbFallingClearMethod}}")}
            ${fmt("PbEarlyAdInsert", "${symbols.pbEarlyAdInsertClass}.{${symbols.pbEarlyAdInsertMethodSpecs?.joinToString(";")}}")}
            ${fmt("EnterForumWeb", "source=${symbols.enterForumInitInfoDataClass}.${symbols.enterForumInitInfoGetUrlMethod} webLoad=${symbols.enterForumWebControllerClass}.${symbols.enterForumWebLoadMethod}")}
            ${fmt("PlainUrlClickableSpan", plainUrlValue)}
            ${fmt("MountCardLink", "${symbols.mountCardLinkLayoutClass}.${symbols.mountCardLinkLayoutOnClickMethod}[${symbols.mountCardLinkLayoutDataField}->${symbols.mountCardLinkInfoDataClass}.${symbols.mountCardLinkInfoGetUrlMethod}]")}
            ${fmt("ForumTopShift", "${symbols.forumBottomSheetViewClass}.${symbols.forumBottomSheetInitScrollMethod}")}
            ${fmt("AutoRefresh", "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.${symbols.autoRefreshTriggerMethod}")}
            ${fmt("AutoLoadMore", "${symbols.autoLoadMoreConfigClass}.${symbols.autoLoadMoreConfigMethod}")}
            ${fmt("PbCommentBottom", "${symbols.pbCommentBottomListScrollClass}.${symbols.pbCommentBottomListScrollMethod}[${symbols.pbCommentBottomListOwnerField}] / ${symbols.pbCommentBottomRecyclerScrollClass}.${symbols.pbCommentBottomRecyclerScrollMethod}[${symbols.pbCommentBottomRecyclerOwnerField}]")}
            ${fmt("PbGestureScale", "${symbols.pbGestureScaleManagerClass}.${symbols.pbGestureScaleDispatchMethod}/${symbols.pbGestureScaleListenerSetterMethod} -> ${symbols.pbGestureScaleListenerClass}.${symbols.pbGestureScaleListenerOnScaleMethod}")}
            ${fmt("PbLikeAutoReply", "${symbols.pbLikeAutoReplyAgreeViewClass}.${symbols.pbLikeAutoReplyAgreeClickMethod}/${symbols.pbLikeAutoReplyAgreeViewGetDataMethod}[${symbols.pbLikeAutoReplyAgreeDataHasAgreeField},${symbols.pbLikeAutoReplyAgreeDataAgreeTypeField},${symbols.pbLikeAutoReplyAgreeDataIsInThreadField}] -> ${symbols.pbLikeAutoReplyInputContainerClass}.{${symbols.pbLikeAutoReplyInputContainerGetInputViewMethod},${symbols.pbLikeAutoReplyInputContainerGetSendViewMethod}}")}
            ${fmt("CollectionCore", "${symbols.collectionPresenterField}.${symbols.collectionPresenterListSetterMethod} / ${symbols.collectionModelField}.${symbols.collectionModelListGetterMethod}/${symbols.collectionModelParseMethod}")}
            ${fmt("CollectionAdapter", "${symbols.collectionPresenterAdapterField}.{${symbols.collectionAdapterShowFooterMethod},${symbols.collectionAdapterLoadingMethod},${symbols.collectionAdapterHasMoreMethod}}")}
            ${fmt("CollectionListField", "${symbols.collectionModelListField}/${symbols.collectionFragmentDisplayListField} nav={${symbols.collectionActivityNavControllerField},${symbols.collectionNavBarField}}")}
            ${fmt("CollectionEditMode", symbols.collectionEditModeMethod)}
            ${fmt("HistorySearch", "${symbols.historyAdapterField}.${symbols.historyAdapterSetListMethod}[${symbols.historyListField}]#${symbols.historyActivityListUpdateMethod} nav=${symbols.historyActivityNavBarField} getters={${symbols.historyThreadNameMethod},${symbols.historyForumNameMethod},${symbols.historyUserNameMethod},${symbols.historyDescriptionMethod},${symbols.historyThreadIdMethod},${symbols.historyPostIdMethod},${symbols.historyLiveIdMethod}}")}
            ${fmt("MsgTabNotify", "${symbols.msgTabLocateToTabMethod}/${symbols.msgTabContainerSelectMethod}[${symbols.msgTabContainerExtDataField}]")}
            ${fmt("PrivateReadReceipt", "${symbols.privateReadReceiptModelClass}.${symbols.privateReadReceiptModelReadDispatchMethod}[${symbols.privateReadReceiptModelDataField}->${symbols.privateReadReceiptPageDataClass}.${symbols.privateReadReceiptPageDataChatListMethod}] ack=${symbols.privateReadReceiptModelBaseClass}.${symbols.privateReadReceiptProcessAckMethod}(${symbols.privateReadReceiptCommitResponseClass}).${symbols.privateReadReceiptResponseErrorMethod} / ${symbols.privateReadReceiptMessageManagerClass}.${symbols.privateReadReceiptMessageManagerGetInstanceMethod}.${symbols.privateReadReceiptMessageSendMethod}(${symbols.privateReadReceiptMessageBaseClass}) request=${symbols.privateReadReceiptRequestClass}[${symbols.privateReadReceiptRequestMsgIdField},${symbols.privateReadReceiptRequestToUidField}]")}
            ${fmt("FreeCopyPopup", "${symbols.freeCopyPopupMenuClass}.${symbols.freeCopyPopupContentViewMethod}[${symbols.freeCopyPopupTextField}]")}
            ${fmt("BottomTab", "${symbols.mainTabDataClass}.${symbols.mainTabAddMethod}/${symbols.mainTabGetListMethod}->${symbols.mainTabDelegateGetStructureMethod}[${symbols.mainTabStructureTypeField},${symbols.mainTabStructureDynamicIconField},${symbols.mainTabStructureFragmentField}]")}
            ${fmt("DefaultOriginalImage", originalImageValue)}
            ${fmt("ImageViewerShare", imageViewerShareValue)}
            ${fmt("FeedCardBind", "FeedCardView.${symbols.feedCardBindMethod}")}
            ${fmt("FeedCardDataList", symbols.feedCardDataListField)}
            ${fmt("FeedHeadParams", symbols.feedHeadParamsField)}
            ${fmt("RecommendCardNestedData", "${symbols.feedRecommendCardNestedDataMethod}[${symbols.feedRecommendCardNestedDataListField}]")}
            ${fmt("PbAdBid", pbAdBidValue)}
            ${fmt("PostAdDataFilter", "${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}.${symbols.typeAdapterSetDataMethod} item=${symbols.typeAdapterDataItemClass}.${symbols.typeAdapterDataGetTypeMethod}")}
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

    fun runtimeModuleVersionName(): String {
        return readRuntimeBuildConfigField("VERSION_NAME") as? String ?: BuildConfig.VERSION_NAME
    }

    fun runtimeModuleVersionCodeLabel(): String {
        return "${runtimeModuleVersionName()}:${runtimeModuleVersionCode()}"
    }

    private fun runtimeModuleVersionCode(): Long {
        return when (val value = readRuntimeBuildConfigField("VERSION_CODE")) {
            is Int -> value.toLong()
            is Long -> value
            is Number -> value.toLong()
            else -> BuildConfig.VERSION_CODE.toLong()
        }
    }

    private fun readRuntimeBuildConfigField(fieldName: String): Any? {
        return try {
            val buildConfigClass = Class.forName(
                "$MODULE_PACKAGE_NAME.BuildConfig",
                false,
                HookSymbolDiagnostics::class.java.classLoader,
            )
            buildConfigClass.getField(fieldName).get(null)
        } catch (_: Throwable) {
            null
        }
    }
}

private const val MODULE_PACKAGE_NAME = "com.forbidad4tieba.hook"
