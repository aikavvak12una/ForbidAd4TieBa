package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.core.StableTiebaHookPoints

internal object HookFeatureStatusDeriver {
    private const val PB_EARLY_AD_INSERT_MIN_METHOD_COUNT = 2

    val featureKeys: List<String> = HookFeatureKey.orderedKeys

    fun deriveWithOverrides(symbols: HookSymbols?): Map<String, HookFeatureStatus> {
        val source = symbols ?: HookSymbols.unsupported()
        if (source.featureStatusMap.isEmpty()) return derive(source)
        return LinkedHashMap<String, HookFeatureStatus>(derive(source)).apply {
            putAll(source.featureStatusMap)
        }
    }

    fun derive(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = LinkedHashMap<String, HookFeatureStatus>(featureKeys.size)
        val feedTemplateKeyMissing = symbols.feedTemplateKeyMethod.isNullOrBlank()
        val customPostCritical = ArrayList<String>(4)
        val customPostOptional = ArrayList<String>(1)
        if (feedTemplateKeyMissing) customPostCritical.add("feedTemplateKeyMethod")
        if (symbols.feedTemplatePayloadMethod.isNullOrBlank()) customPostCritical.add("feedTemplatePayloadMethod")
        if (symbols.feedTemplateLoadMoreMethod.isNullOrBlank()) customPostCritical.add("feedTemplateLoadMoreMethod")
        if (symbols.feedCardDataListField.isNullOrBlank()) customPostCritical.add("feedCardDataListField")
        if (symbols.feedHeadParamsField.isNullOrBlank()) customPostOptional.add("feedHeadParamsField")
        if (symbols.feedRecommendCardNestedDataMethod.isNullOrBlank()) {
            customPostOptional.add("feedRecommendCardNestedDataMethod")
        }
        if (symbols.feedRecommendCardNestedDataListField.isNullOrBlank()) {
            customPostOptional.add("feedRecommendCardNestedDataListField")
        }
        out[HookFeatureKey.ENABLE_CUSTOM_POST_FILTER] = if (customPostCritical.isNotEmpty()) {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = customPostCritical,
                missingOptional = customPostOptional,
            )
        } else if (customPostOptional.isNotEmpty()) {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = customPostOptional,
            )
        } else {
            HookFeatureStatus(state = HookFeatureState.FULL)
        }

        out[HookFeatureKey.HIDE_PB_BOTTOM_BANNER] = HookFeatureStatus(state = HookFeatureState.HARD_CODED)
        val freeCopyOptional = ArrayList<String>(3)
        if (symbols.freeCopyPopupMenuClass.isNullOrBlank()) freeCopyOptional.add("freeCopyPopupMenuClass")
        if (symbols.freeCopyPopupContentViewMethod.isNullOrBlank()) {
            freeCopyOptional.add("freeCopyPopupContentViewMethod")
        }
        if (symbols.freeCopyPopupTextField.isNullOrBlank()) freeCopyOptional.add("freeCopyPopupTextField")
        out[HookFeatureKey.FREE_COPY] = if (freeCopyOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.PARTIAL, missingOptional = freeCopyOptional)
        }
        out[HookFeatureKey.AUTO_SIGN_IN] = HookFeatureStatus(state = HookFeatureState.HARD_CODED)

        val privateReadReceiptCritical = ArrayList<String>(19)
        if (symbols.privateReadReceiptModelClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelClass")
        }
        if (symbols.privateReadReceiptModelReadDispatchMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelReadDispatchMethod")
        }
        if (symbols.privateReadReceiptMessageManagerClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageManagerClass")
        }
        if (symbols.privateReadReceiptMessageManagerGetInstanceMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageManagerGetInstanceMethod")
        }
        if (symbols.privateReadReceiptMessageSendMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageSendMethod")
        }
        if (symbols.privateReadReceiptMessageBaseClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageBaseClass")
        }
        if (symbols.privateReadReceiptRequestClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptRequestClass")
        }
        if (symbols.privateReadReceiptModelBaseClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelBaseClass")
        }
        if (symbols.privateReadReceiptCommitResponseClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptCommitResponseClass")
        }
        if (symbols.privateReadReceiptProcessAckMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptProcessAckMethod")
        }
        if (symbols.privateReadReceiptResponseErrorMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptResponseErrorMethod")
        }
        if (symbols.privateReadReceiptRequestMsgIdField.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptRequestMsgIdField")
        }
        if (symbols.privateReadReceiptRequestToUidField.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptRequestToUidField")
        }
        if (symbols.privateReadReceiptModelDataField.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelDataField")
        }
        if (symbols.privateReadReceiptPageDataClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptPageDataClass")
        }
        if (symbols.privateReadReceiptPageDataChatListMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptPageDataChatListMethod")
        }
        if (symbols.privateReadReceiptChatMessageClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageClass")
        }
        if (symbols.privateReadReceiptChatMessageMsgIdMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageMsgIdMethod")
        }
        if (symbols.privateReadReceiptChatMessageUserIdMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageUserIdMethod")
        }
        if (symbols.privateReadReceiptChatMessageLocalDataMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageLocalDataMethod")
        }
        if (symbols.privateReadReceiptLocalDataClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptLocalDataClass")
        }
        if (symbols.privateReadReceiptLocalDataStatusMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptLocalDataStatusMethod")
        }
        if (symbols.privateReadReceiptAccountClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptAccountClass")
        }
        if (symbols.privateReadReceiptCurrentAccountMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptCurrentAccountMethod")
        }
        out[HookFeatureKey.PRIVATE_READ_RECEIPT_INVISIBLE] = if (privateReadReceiptCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = privateReadReceiptCritical,
            )
        }

        val enterForumInitInfoReady =
            !symbols.enterForumInitInfoDataClass.isNullOrBlank() &&
                !symbols.enterForumInitInfoGetUrlMethod.isNullOrBlank()
        val enterForumWebLoadReady =
            !symbols.enterForumWebControllerClass.isNullOrBlank() &&
                !symbols.enterForumWebLoadMethod.isNullOrBlank()
        out[HookFeatureKey.FILTER_ENTER_FORUM_WEB] = if (enterForumInitInfoReady || enterForumWebLoadReady) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = listOf("enterForumInitInfoData", "enterForumWebLoadTarget"),
            )
        }

        val homeNativeGlassCritical = ArrayList<String>(1)
        val homeNativeGlassOptional = ArrayList<String>(12)
        if (symbols.feedCardBindMethod.isNullOrBlank()) homeNativeGlassCritical.add("feedCardBindMethod")
        if (!symbols.homePersonalizeAnchorClasses.orEmpty().contains(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS)) {
            homeNativeGlassOptional.add("homeNativeGlassPageClass")
        }
        if (symbols.homeNativeGlassSubPbNextPageMoreViewId == null || symbols.homeNativeGlassSubPbNextPageMoreViewId == 0) {
            homeNativeGlassOptional.add("homeNativeGlassSubPbNextPageMoreViewId")
        }
        if (symbols.homeNativeGlassPbReplyTitleDividerViewId == null || symbols.homeNativeGlassPbReplyTitleDividerViewId == 0) {
            homeNativeGlassOptional.add("homeNativeGlassPbReplyTitleDividerViewId")
        }
        if (symbols.pbCommonLayoutPreloaderGetOrDefaultMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("pbCommonLayoutPreloaderGetOrDefaultMethod")
        }
        if (symbols.homeNativeGlassDynamicBackgroundColorIds.isEmpty()) {
            homeNativeGlassOptional.add("homeNativeGlassDynamicBackgroundColorIds")
        }
        if (symbols.homeNativeGlassSortSwitchBackgroundPaintField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSortSwitchBackgroundPaintField")
        }
        if (symbols.homeNativeGlassSortSwitchSlideDrawMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSortSwitchSlideDrawMethod")
        }
        if (symbols.homeNativeGlassSortSwitchSlidePathField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSortSwitchSlidePathField")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleControllerClass.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleControllerClass")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleInitMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleInitMethod")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleRefreshMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleRefreshMethod")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleViewField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleViewField")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleTitleField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleTitleField")
        }
        if (symbols.homeNativeGlassHostDarkModeMoreActivityClass.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeMoreActivityClass")
        }
        if (symbols.homeNativeGlassHostDarkModeControllerField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeControllerField")
        }
        if (symbols.homeNativeGlassHostDarkModeSwitchGetterMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchGetterMethod")
        }
        if (symbols.homeNativeGlassHostDarkModeSwitchStateField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchStateField")
        }
        if (symbols.homeNativeGlassHostDarkModeSwitchSetOnMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchSetOnMethod")
        }
        if (symbols.homeNativeGlassHostDarkModeSwitchSetOffMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchSetOffMethod")
        }
        if (symbols.homeNativeGlassHostDarkModeSwitchCallbackMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchCallbackMethod")
        }
        out[HookFeatureKey.HOME_NATIVE_GLASS] = if (homeNativeGlassCritical.isEmpty()) {
            if (homeNativeGlassOptional.isEmpty()) {
                HookFeatureStatus(state = HookFeatureState.FULL)
            } else {
                HookFeatureStatus(
                    state = HookFeatureState.PARTIAL,
                    missingOptional = homeNativeGlassOptional,
                )
            }
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = homeNativeGlassCritical,
                missingOptional = homeNativeGlassOptional,
            )
        }

        val plainUrlDirectMissing = ArrayList<String>(6)
        if (symbols.plainUrlClickableSpanClass.isNullOrBlank()) plainUrlDirectMissing.add("plainUrlClickableSpanClass")
        if (symbols.plainUrlClickableSpanOnClickMethod.isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanOnClickMethod")
        }
        if (symbols.plainUrlClickableSpanOnClickOwnerClasses.isNullOrEmpty()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanOnClickOwnerClasses")
        }
        if (symbols.plainUrlClickableSpanTypeField.isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanTypeField")
        }
        if (symbols.plainUrlClickableSpanUrlField.isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanUrlField")
        }
        if (symbols.plainUrlClickableSpanTextField.isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanTextField")
        }
        val plainUrlMessageMissing = ArrayList<String>(8)
        if (symbols.plainUrlMessageManagerClass.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlMessageManagerClass")
        }
        if (symbols.plainUrlMessageDispatchMethod.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlMessageDispatchMethod")
        }
        if (symbols.plainUrlResponsedMessageClass.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlResponsedMessageClass")
        }
        if (symbols.plainUrlResponsedMessageGetCmdMethod.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlResponsedMessageGetCmdMethod")
        }
        if (symbols.plainUrlCustomResponsedMessageClass.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlCustomResponsedMessageClass")
        }
        if (symbols.plainUrlCustomResponsedMessageGetDataMethod.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlCustomResponsedMessageGetDataMethod")
        }
        if (symbols.plainUrlApplicationClass.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlApplicationClass")
        }
        if (symbols.plainUrlApplicationGetInstMethod.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlApplicationGetInstMethod")
        }
        val mountCardMissing = ArrayList<String>(5)
        if (symbols.mountCardLinkLayoutClass.isNullOrBlank()) mountCardMissing.add("mountCardLinkLayoutClass")
        if (symbols.mountCardLinkLayoutOnClickMethod.isNullOrBlank()) {
            mountCardMissing.add("mountCardLinkLayoutOnClickMethod")
        }
        if (symbols.mountCardLinkLayoutDataField.isNullOrBlank()) mountCardMissing.add("mountCardLinkLayoutDataField")
        if (symbols.mountCardLinkInfoDataClass.isNullOrBlank()) mountCardMissing.add("mountCardLinkInfoDataClass")
        if (symbols.mountCardLinkInfoGetUrlMethod.isNullOrBlank()) {
            mountCardMissing.add("mountCardLinkInfoGetUrlMethod")
        }
        val plainUrlDirectReady = plainUrlDirectMissing.isEmpty()
        val plainUrlMessageReady = plainUrlMessageMissing.isEmpty()
        val generalPlainUrlReady = plainUrlMessageReady || plainUrlDirectReady
        val mountCardReady = mountCardMissing.isEmpty()
        val plainUrlOptionalMissing = buildList {
            if (!plainUrlMessageReady) addAll(plainUrlMessageMissing)
            if (!mountCardReady) addAll(mountCardMissing)
        }.distinct()
        out[HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER] = when {
            plainUrlMessageReady && mountCardReady -> HookFeatureStatus(state = HookFeatureState.FULL)
            generalPlainUrlReady || mountCardReady -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = plainUrlOptionalMissing,
            )
            else -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = (plainUrlMessageMissing + mountCardMissing).distinct(),
                missingOptional = plainUrlDirectMissing,
            )
        }

        val autoRefreshCritical = ArrayList<String>(1)
        if (symbols.autoRefreshTriggerMethod.isNullOrBlank()) {
            autoRefreshCritical.add("autoRefreshTriggerMethod")
        }
        out[HookFeatureKey.DISABLE_AUTO_REFRESH] = if (autoRefreshCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = autoRefreshCritical)
        }

        val pbGestureScaleCritical = ArrayList<String>(5)
        if (symbols.pbGestureScaleManagerClass.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleManagerClass")
        }
        if (symbols.pbGestureScaleDispatchMethod.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleDispatchMethod")
        }
        if (symbols.pbGestureScaleListenerSetterMethod.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleListenerSetterMethod")
        }
        if (symbols.pbGestureScaleListenerClass.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleListenerClass")
        }
        if (symbols.pbGestureScaleListenerOnScaleMethod.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleListenerOnScaleMethod")
        }
        out[HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE] = if (pbGestureScaleCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = pbGestureScaleCritical)
        }

        val forumTopShiftCritical = ArrayList<String>(2)
        if (symbols.forumBottomSheetViewClass.isNullOrBlank()) {
            forumTopShiftCritical.add("forumBottomSheetViewClass")
        }
        if (symbols.forumBottomSheetInitScrollMethod.isNullOrBlank()) {
            forumTopShiftCritical.add("forumBottomSheetInitScrollMethod")
        }
        out[HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT] = if (forumTopShiftCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = forumTopShiftCritical)
        }

        val autoLoadMoreOptional = ArrayList<String>(8)
        if (symbols.autoLoadMoreConfigClass.isNullOrBlank()) {
            autoLoadMoreOptional.add("autoLoadMoreConfigClass")
        }
        if (symbols.autoLoadMoreConfigMethod.isNullOrBlank()) {
            autoLoadMoreOptional.add("autoLoadMoreConfigMethod")
        }
        val hasListBottomMechanism =
            !symbols.pbCommentBottomListScrollClass.isNullOrBlank() &&
                !symbols.pbCommentBottomListScrollMethod.isNullOrBlank() &&
                !symbols.pbCommentBottomListOwnerField.isNullOrBlank()
        val hasRecyclerBottomMechanism =
            !symbols.pbCommentBottomRecyclerScrollClass.isNullOrBlank() &&
                !symbols.pbCommentBottomRecyclerScrollMethod.isNullOrBlank() &&
                !symbols.pbCommentBottomRecyclerOwnerField.isNullOrBlank()
        if (!hasListBottomMechanism && !hasRecyclerBottomMechanism) {
            autoLoadMoreOptional.add("pbCommentBottomMechanism")
        }
        out[HookFeatureKey.AUTO_LOAD_MORE] = if (autoLoadMoreOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = autoLoadMoreOptional,
            )
        }

        val pbLikeAutoReplyCritical = ArrayList<String>(8)
        if (symbols.pbLikeAutoReplyAgreeViewClass.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeViewClass")
        }
        if (symbols.pbLikeAutoReplyAgreeClickMethod.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeClickMethod")
        }
        if (symbols.pbLikeAutoReplyAgreeViewGetDataMethod.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeViewGetDataMethod")
        }
        if (symbols.pbLikeAutoReplyAgreeDataClass.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeDataClass")
        }
        if (symbols.pbLikeAutoReplyAgreeDataHasAgreeField.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeDataHasAgreeField")
        }
        if (symbols.pbLikeAutoReplyAgreeDataAgreeTypeField.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeDataAgreeTypeField")
        }
        if (symbols.pbLikeAutoReplyAgreeDataIsInThreadField.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeDataIsInThreadField")
        }
        if (symbols.pbLikeAutoReplyInputContainerClass.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyInputContainerClass")
        }
        if (symbols.pbLikeAutoReplyInputContainerGetInputViewMethod.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyInputContainerGetInputViewMethod")
        }
        if (symbols.pbLikeAutoReplyInputContainerGetSendViewMethod.isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyInputContainerGetSendViewMethod")
        }
        out[HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY] = if (pbLikeAutoReplyCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = pbLikeAutoReplyCritical,
            )
        }

        val replyVisibilityProbeCritical = ArrayList<String>(36)
        if (symbols.replyVisibilityProbeReplyResponseClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeReplyResponseClass")
        }
        if (symbols.replyVisibilityProbeReplyDecodeMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeReplyDecodeMethod")
        }
        if (symbols.replyVisibilityProbeReplyResultJsonField.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeReplyResultJsonField")
        }
        if (symbols.replyVisibilityProbeAddPostRequestClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeAddPostRequestClass")
        }
        if (symbols.replyVisibilityProbeAddPostRequestDataField.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeAddPostRequestDataField")
        }
        if (symbols.replyVisibilityProbeResponsedMessageClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeResponsedMessageClass")
        }
        if (symbols.replyVisibilityProbeGetOriginalMessageMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeGetOriginalMessageMethod")
        }
        if (symbols.replyVisibilityProbeMessageClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageClass")
        }
        if (symbols.replyVisibilityProbeMessageGetExtraMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageGetExtraMethod")
        }
        if (symbols.replyVisibilityProbeMessageGetTagMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageGetTagMethod")
        }
        if (symbols.replyVisibilityProbeMessageSetTagMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageSetTagMethod")
        }
        if (symbols.replyVisibilityProbeHttpMessageClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageClass")
        }
        if (symbols.replyVisibilityProbeHttpMessageConstructor.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageConstructor")
        }
        if (symbols.replyVisibilityProbeHttpMessageAddParamMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageAddParamMethod")
        }
        if (symbols.replyVisibilityProbeHttpMessageAddHeaderMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageAddHeaderMethod")
        }
        if (symbols.replyVisibilityProbeMessageManagerClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerClass")
        }
        if (symbols.replyVisibilityProbeMessageManagerGetInstanceMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerGetInstanceMethod")
        }
        if (symbols.replyVisibilityProbeMessageManagerFindTaskMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerFindTaskMethod")
        }
        if (symbols.replyVisibilityProbeMessageManagerRegisterTaskMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerRegisterTaskMethod")
        }
        if (symbols.replyVisibilityProbeMessageManagerSendMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerSendMethod")
        }
        if (symbols.replyVisibilityProbeTbHttpMessageTaskClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbHttpMessageTaskClass")
        }
        if (symbols.replyVisibilityProbeTbHttpMessageTaskConstructor.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbHttpMessageTaskConstructor")
        }
        if (symbols.replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod")
        }
        if (symbols.replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod")
        }
        if (symbols.replyVisibilityProbeBdUniqueIdClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeBdUniqueIdClass")
        }
        if (symbols.replyVisibilityProbeBdUniqueIdGenMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeBdUniqueIdGenMethod")
        }
        if (symbols.replyVisibilityProbeTbadkCoreApplicationClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbadkCoreApplicationClass")
        }
        if (symbols.replyVisibilityProbeTbadkCoreApplicationGetInstMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbadkCoreApplicationGetInstMethod")
        }
        if (symbols.replyVisibilityProbeTbadkCoreApplicationGetZidMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbadkCoreApplicationGetZidMethod")
        }
        if (symbols.replyVisibilityProbeTbConfigClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbConfigClass")
        }
        if (symbols.replyVisibilityProbeTbConfigServerAddressField.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbConfigServerAddressField")
        }
        if (symbols.replyVisibilityProbeTbConfigPbFloorAgreeUrlField.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbConfigPbFloorAgreeUrlField")
        }
        if (symbols.replyVisibilityProbeCmdConfigHttpClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeCmdConfigHttpClass")
        }
        if (symbols.replyVisibilityProbeCmdPbFloorAgreeField.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeCmdPbFloorAgreeField")
        }
        if (symbols.replyVisibilityProbeAgreeResponseClass.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeAgreeResponseClass")
        }
        if (symbols.replyVisibilityProbeAgreeDecodeLogicMethod.isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeAgreeDecodeLogicMethod")
        }
        out[HookFeatureKey.VERIFY_REPLY_AFTER_POST] = if (replyVisibilityProbeCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = replyVisibilityProbeCritical,
            )
        }

        val pbScrollCoalesceCritical = ArrayList<String>(2)
        if (symbols.pbCommentScrollListenerClass.isNullOrBlank()) {
            pbScrollCoalesceCritical.add("pbCommentScrollListenerClass")
        }
        if (symbols.pbCommentScrollMethod.isNullOrBlank()) {
            pbScrollCoalesceCritical.add("pbCommentScrollMethod")
        }
        val pbScrollCoalesceOptional = ArrayList<String>(3)
        if (symbols.pbCommentScrollFragmentField.isNullOrBlank()) {
            pbScrollCoalesceOptional.add("pbCommentScrollFragmentField")
        }
        if (symbols.pbCommentScrollBottomListenerField.isNullOrBlank()) {
            pbScrollCoalesceOptional.add("pbCommentScrollBottomListenerField")
        }
        if (symbols.pbCommentScrollBottomMethod.isNullOrBlank()) {
            pbScrollCoalesceOptional.add("pbCommentScrollBottomMethod")
        }
        out[HookFeatureKey.ENABLE_PB_SCROLL_COALESCE] = when {
            pbScrollCoalesceCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = pbScrollCoalesceCritical,
                missingOptional = pbScrollCoalesceOptional,
            )
            pbScrollCoalesceOptional.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = pbScrollCoalesceOptional,
            )
            else -> HookFeatureStatus(state = HookFeatureState.FULL)
        }

        val notifyMissingCritical = ArrayList<String>(1)
        if (symbols.msgTabLocateToTabMethod.isNullOrBlank()) {
            notifyMissingCritical.add("msgTabLocateToTabMethod")
        }
        out[HookFeatureKey.DEFAULT_NOTIFY_TAB] = if (notifyMissingCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = notifyMissingCritical,
            )
        }

        val homeCritical = ArrayList<String>(7)
        val homeOptional = ArrayList<String>(3)
        if (symbols.homeTabClass.isNullOrBlank()) homeCritical.add("homeTabClass")
        if (symbols.homeTabRebuildMethod.isNullOrBlank()) homeCritical.add("homeTabRebuildMethod")
        if (symbols.homeTabListField.isNullOrBlank()) homeCritical.add("homeTabListField")
        if (symbols.homeTabItemTypeField.isNullOrBlank()) homeCritical.add("homeTabItemTypeField")
        if (symbols.homeTabItemCodeField.isNullOrBlank()) homeCritical.add("homeTabItemCodeField")
        if (symbols.homeTabItemNameField.isNullOrBlank()) homeCritical.add("homeTabItemNameField")
        if (symbols.homeTabItemUrlField.isNullOrBlank()) homeCritical.add("homeTabItemUrlField")
        if (symbols.homeTabItemMainSetterMethod.isNullOrBlank()) {
            homeOptional.add("homeTabItemMainSetterMethod")
        }
        if (symbols.homeTabItemMainIntField.isNullOrBlank()) {
            homeOptional.add("homeTabItemMainIntField")
        }
        if (symbols.homeTabItemMainBooleanField.isNullOrBlank()) {
            homeOptional.add("homeTabItemMainBooleanField")
        }
        out[HookFeatureKey.SIMPLIFY_HOME_TOP_TABS] = if (homeCritical.isEmpty() && homeOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else if (homeCritical.isEmpty()) {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = homeOptional,
            )
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = homeCritical,
                missingOptional = homeOptional,
            )
        }

        val bottomCritical = ArrayList<String>(5)
        val bottomOptional = ArrayList<String>(2)
        if (symbols.mainTabDataClass.isNullOrBlank()) bottomCritical.add("mainTabDataClass")
        if (symbols.mainTabAddMethod.isNullOrBlank()) bottomCritical.add("mainTabAddMethod")
        if (symbols.mainTabGetListMethod.isNullOrBlank()) bottomCritical.add("mainTabGetListMethod")
        if (symbols.mainTabDelegateGetStructureMethod.isNullOrBlank()) {
            bottomCritical.add("mainTabDelegateGetStructureMethod")
        }
        if (symbols.mainTabStructureTypeField.isNullOrBlank()) bottomCritical.add("mainTabStructureTypeField")
        if (symbols.mainTabStructureDynamicIconField.isNullOrBlank()) {
            bottomOptional.add("mainTabStructureDynamicIconField")
        }
        if (symbols.mainTabStructureFragmentField.isNullOrBlank()) {
            bottomOptional.add("mainTabStructureFragmentField")
        }
        out[HookFeatureKey.SIMPLIFY_BOTTOM_TABS] = when {
            bottomCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = bottomCritical,
                missingOptional = bottomOptional,
            )
            bottomOptional.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = bottomOptional,
            )
            else -> HookFeatureStatus(state = HookFeatureState.FULL)
        }

        val imageCritical = ArrayList<String>(8)
        val imageOptional = ArrayList<String>(10)
        if (symbols.origImageUrlDragImageViewClass.isNullOrBlank()) imageCritical.add("origImageUrlDragImageViewClass")
        if (symbols.origImageDataClass.isNullOrBlank()) imageCritical.add("origImageDataClass")
        if (symbols.origImageTriggerMethod.isNullOrBlank()) imageCritical.add("origImageTriggerMethod")
        if (symbols.origImageAssistDataMethod.isNullOrBlank()) imageCritical.add("origImageAssistDataMethod")
        if (symbols.origImageShowButtonField.isNullOrBlank()) imageCritical.add("origImageShowButtonField")
        if (symbols.origImageBlockedField.isNullOrBlank()) imageCritical.add("origImageBlockedField")
        if (symbols.origImageOriginalProcessField.isNullOrBlank()) imageCritical.add("origImageOriginalProcessField")
        if (symbols.origImageOriginalUrlField.isNullOrBlank()) imageCritical.add("origImageOriginalUrlField")
        if (symbols.origImagePagerAdapterClass.isNullOrBlank()) imageOptional.add("origImagePagerAdapterClass")
        if (symbols.origImageSetPrimaryItemMethod.isNullOrBlank()) imageOptional.add("origImageSetPrimaryItemMethod")
        if (symbols.origImageSetAssistUrlMethod.isNullOrBlank()) imageOptional.add("origImageSetAssistUrlMethod")
        if (symbols.origImagePrimaryReadyMethod.isNullOrBlank()) imageOptional.add("origImagePrimaryReadyMethod")
        if (symbols.origImageDirectStartMethod.isNullOrBlank()) imageOptional.add("origImageDirectStartMethod")
        if (symbols.origImageOriginTextMethod.isNullOrBlank()) imageOptional.add("origImageOriginTextMethod")
        if (symbols.origImageSharedPrefHelperClass.isNullOrBlank()) imageOptional.add("origImageSharedPrefHelperClass")
        if (symbols.origImageSharedPrefGetInstanceMethod.isNullOrBlank()) {
            imageOptional.add("origImageSharedPrefGetInstanceMethod")
        }
        if (symbols.origImageSharedPrefPutBooleanMethod.isNullOrBlank()) {
            imageOptional.add("origImageSharedPrefPutBooleanMethod")
        }
        if (symbols.origImageMd5Class.isNullOrBlank()) imageOptional.add("origImageMd5Class")
        if (symbols.origImageMd5Method.isNullOrBlank()) imageOptional.add("origImageMd5Method")
        out[HookFeatureKey.DEFAULT_ORIGINAL_IMAGE] = when {
            imageCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = imageCritical,
                missingOptional = imageOptional,
            )
            imageOptional.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = imageOptional,
            )
            else -> HookFeatureStatus(state = HookFeatureState.FULL)
        }

        val shareTrackingCritical = ArrayList<String>(2)
        if (symbols.shareTrackBuilderClass.isNullOrBlank()) shareTrackingCritical.add("shareTrackBuilderClass")
        if (symbols.shareTrackBuildUrlMethod.isNullOrBlank()) shareTrackingCritical.add("shareTrackBuildUrlMethod")
        out[HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS] = if (shareTrackingCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = shareTrackingCritical)
        }

        val aiComponentCritical = ArrayList<String>(5)
        if (symbols.aiSpriteMemePanControllerClass.isNullOrBlank()) {
            aiComponentCritical.add("aiSpriteMemePanControllerClass")
        }
        if (symbols.aiSpriteMemeEnableMethod.isNullOrBlank()) {
            aiComponentCritical.add("aiSpriteMemeEnableMethod")
        }
        if (symbols.aiPbNewInputContainerClass.isNullOrBlank()) {
            aiComponentCritical.add("aiPbNewInputContainerClass")
        }
        if (symbols.aiPbNewInputContainerInitSpriteMemeMethod.isNullOrBlank()) {
            aiComponentCritical.add("aiPbNewInputContainerInitSpriteMemeMethod")
        }
        if (symbols.aiPbNewInputContainerInitAiWriteMethod.isNullOrBlank()) {
            aiComponentCritical.add("aiPbNewInputContainerInitAiWriteMethod")
        }
        val aiComponentOptional = ArrayList<String>(4)
        if (symbols.aiPbAiEmojiCreationViewBindMethod.isNullOrBlank()) {
            aiComponentOptional.add("aiPbAiEmojiCreationViewBindMethod")
        }
        if (symbols.aiPbPageBrowserAiEmojiCreationBindMethod.isNullOrBlank()) {
            aiComponentOptional.add("aiPbPageBrowserAiEmojiCreationBindMethod")
        }
        if (symbols.aiImageViewerJumpButtonOwnerClass.isNullOrBlank()) {
            aiComponentOptional.add("aiImageViewerJumpButtonOwnerClass")
        }
        if (symbols.aiImageViewerJumpButtonInitMethod.isNullOrBlank()) {
            aiComponentOptional.add("aiImageViewerJumpButtonInitMethod")
        }
        out[HookFeatureKey.DISABLE_AI_COMPONENTS] = if (aiComponentCritical.isEmpty()) {
            if (aiComponentOptional.isEmpty()) {
                HookFeatureStatus(state = HookFeatureState.FULL)
            } else {
                HookFeatureStatus(state = HookFeatureState.PARTIAL, missingOptional = aiComponentOptional)
            }
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = aiComponentCritical,
                missingOptional = aiComponentOptional,
            )
        }

        val adOptional = ArrayList<String>(18)
        if (symbols.feedTemplateKeyMethod.isNullOrBlank()) adOptional.add("feedTemplateKeyMethod")
        if (symbols.feedTemplateLoadMoreMethod.isNullOrBlank()) adOptional.add("feedTemplateLoadMoreMethod")
        if (symbols.splashAdHelperClass.isNullOrBlank()) adOptional.add("splashAdHelperClass")
        if (symbols.splashAdHelperMethod.isNullOrBlank()) adOptional.add("splashAdHelperMethod")
        if (symbols.closeAdDataClass.isNullOrBlank()) adOptional.add("closeAdDataClass")
        if (symbols.closeAdDataMethodG1.isNullOrBlank()) adOptional.add("closeAdDataMethodG1")
        if (symbols.closeAdDataMethodJ1.isNullOrBlank()) adOptional.add("closeAdDataMethodJ1")
        if (symbols.zgaClass.isNullOrBlank()) adOptional.add("zgaClass")
        if (symbols.zgaMethods.isNullOrEmpty()) adOptional.add("zgaMethods")
        if (symbols.searchBoxViewClass.isNullOrBlank()) adOptional.add("searchBoxViewClass")
        if (symbols.searchBoxSetHintMethod.isNullOrBlank()) adOptional.add("searchBoxSetHintMethod")
        if (symbols.homeSearchBoxOwnerClass.isNullOrBlank()) adOptional.add("homeSearchBoxOwnerClass")
        if (symbols.homeSearchBoxInitMethod.isNullOrBlank()) adOptional.add("homeSearchBoxInitMethod")
        if (symbols.homeSearchBoxGetterMethod.isNullOrBlank()) adOptional.add("homeSearchBoxGetterMethod")
        if (symbols.homePersonalizeAnchorClasses.isNullOrEmpty()) {
            adOptional.add("homePersonalizeAnchorClasses")
        }
        if (symbols.homeRightSlotClass.isNullOrBlank()) adOptional.add("homeRightSlotClass")
        if (symbols.homeRightSlotStateMethods.isNullOrEmpty()) adOptional.add("homeRightSlotStateMethods")
        if (symbols.pbFallingViewClass.isNullOrBlank()) adOptional.add("pbFallingViewClass")
        if (symbols.pbFallingInitMethod.isNullOrBlank()) adOptional.add("pbFallingInitMethod")
        if (symbols.pbFallingShowMethod.isNullOrBlank()) adOptional.add("pbFallingShowMethod")
        if (symbols.pbFallingClearMethod.isNullOrBlank()) adOptional.add("pbFallingClearMethod")
        if (symbols.pbEarlyAdInsertClass.isNullOrBlank()) adOptional.add("pbEarlyAdInsertClass")
        if (symbols.pbEarlyAdInsertMethodSpecs.orEmpty().size < PB_EARLY_AD_INSERT_MIN_METHOD_COUNT) {
            adOptional.add("pbEarlyAdInsertMethodSpecs")
        }
        val hasPbAdBidCommonPath =
            !symbols.pbAdBidCommonRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidCommonRequestStartMethods.isNullOrEmpty() &&
                !symbols.pbAdBidCommonRequestNotifyMethod.isNullOrBlank()
        val hasPbAdBidPageBrowserPath =
            !symbols.pbAdBidPageBrowserRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidPageBrowserRequestDataMethod.isNullOrBlank()
        if (!hasPbAdBidCommonPath && !hasPbAdBidPageBrowserPath) {
            if (symbols.pbAdBidCommonRequestModelClass.isNullOrBlank()) {
                adOptional.add("pbAdBidCommonRequestModelClass")
            }
            if (symbols.pbAdBidCommonRequestStartMethods.isNullOrEmpty()) {
                adOptional.add("pbAdBidCommonRequestStartMethods")
            }
            if (symbols.pbAdBidCommonRequestNotifyMethod.isNullOrBlank()) {
                adOptional.add("pbAdBidCommonRequestNotifyMethod")
            }
            if (symbols.pbAdBidPageBrowserRequestModelClass.isNullOrBlank()) {
                adOptional.add("pbAdBidPageBrowserRequestModelClass")
            }
            if (symbols.pbAdBidPageBrowserRequestDataMethod.isNullOrBlank()) {
                adOptional.add("pbAdBidPageBrowserRequestDataMethod")
            }
        }
        if (symbols.typeAdapterSetDataMethod.isNullOrBlank()) adOptional.add("typeAdapterSetDataMethod")
        if (symbols.typeAdapterDataItemClass.isNullOrBlank()) adOptional.add("typeAdapterDataItemClass")
        if (symbols.typeAdapterDataGetTypeMethod.isNullOrBlank()) {
            adOptional.add("typeAdapterDataGetTypeMethod")
        }
        if (!ForumPageAdSymbolReadiness.evaluate(symbols).any) {
            adOptional.add("forumPageAdBlock")
        }
        out[HookFeatureKey.BLOCK_AD] = if (adOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.PARTIAL, missingOptional = adOptional)
        }

        for (key in featureKeys) {
            if (!out.containsKey(key)) out[key] = HookFeatureStatus()
        }
        return out
    }

}
