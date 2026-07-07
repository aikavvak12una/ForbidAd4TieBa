package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.core.StableTiebaHookPoints

internal object HookFeatureStatusDeriver {
    private const val PB_EARLY_AD_INSERT_MIN_METHOD_COUNT = 2

    val featureKeys: List<String> = HookFeatureKey.orderedKeys

    fun deriveWithOverrides(symbols: HookSymbols?): Map<String, HookFeatureStatus> {
        val source = symbols ?: HookSymbols.unsupported()
        if (source.featureStatusMap.isEmpty()) return derive(source)
        val merged = LinkedHashMap<String, HookFeatureStatus>(derive(source)).apply {
            putAll(source.featureStatusMap)
        }
        return withHookPointAvailability(source, merged)
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

        val pbBottomEnterBarReady =
            !symbols.pbBottomEnterBarViewClass.isNullOrBlank() &&
                (symbols.pbBottomEnterBarConstructorCount ?: 0) > 0
        val pbBottomEnterBarComplete =
            pbBottomEnterBarReady &&
                !symbols.pbBottomEnterBarRefreshMethodSpecs.isNullOrEmpty()
        val pbEnterFrsAnimationTipReady =
            !symbols.pbEnterFrsAnimationTipViewClass.isNullOrBlank() &&
                (symbols.pbEnterFrsAnimationTipConstructorCount ?: 0) > 0
        val pbEnterFrsAnimationTipComplete =
            pbEnterFrsAnimationTipReady &&
                symbols.pbEnterFrsAnimationTipCallerClasses.orEmpty().containsAll(
                    listOf(
                        StableTiebaHookPoints.PB_VIEW_UTIL_KT_CLASS,
                        StableTiebaHookPoints.SPRITE_ANIMATION_TIP_MANAGER_CLASS,
                    ),
                )
        val pbHotTopicGuideReady =
            !symbols.pbHotTopicGuideTotalViewMethod.isNullOrBlank() &&
                !symbols.pbHotTopicGuideRefreshMethodSpecs.isNullOrEmpty()
        val pbBottomBannerMissing = ArrayList<String>(3)
        if (!pbBottomEnterBarComplete) {
            pbBottomBannerMissing.add("PbBottomEnterBarHook.BottomEnterBarView")
        }
        if (!pbEnterFrsAnimationTipComplete) {
            pbBottomBannerMissing.add("PbBottomEnterBarHook.AnimationTip")
        }
        if (!pbHotTopicGuideReady) {
            pbBottomBannerMissing.add("PbBottomEnterBarHook.HotTopicGuide")
        }
        out[HookFeatureKey.HIDE_PB_BOTTOM_BANNER] = when {
            pbBottomBannerMissing.isEmpty() -> HookFeatureStatus(state = HookFeatureState.FULL)
            pbBottomEnterBarReady || pbEnterFrsAnimationTipReady || pbHotTopicGuideReady -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = pbBottomBannerMissing,
            )
            else -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = pbBottomBannerMissing,
            )
        }
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

        val autoSignInCritical = ArrayList<String>(10)
        if (symbols.autoSignInNetworkClass.isNullOrBlank()) autoSignInCritical.add("autoSignInNetworkClass")
        if (symbols.autoSignInNetworkConstructorSpec.isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkConstructorSpec")
        }
        if (symbols.autoSignInNetworkAddPostDataMethod.isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkAddPostDataMethod")
        }
        if (symbols.autoSignInNetworkPostNetDataMethod.isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkPostNetDataMethod")
        }
        if (symbols.autoSignInNetworkSetNeedTbsMethod.isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkSetNeedTbsMethod")
        }
        if (symbols.autoSignInNetworkSetNeedSigMethod.isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkSetNeedSigMethod")
        }
        if (symbols.autoSignInTbConfigClass.isNullOrBlank()) autoSignInCritical.add("autoSignInTbConfigClass")
        if (symbols.autoSignInServerAddressField.isNullOrBlank()) {
            autoSignInCritical.add("autoSignInServerAddressField")
        }
        if (symbols.autoSignInCoreApplicationClass.isNullOrBlank()) {
            autoSignInCritical.add("autoSignInCoreApplicationClass")
        }
        if (symbols.autoSignInCurrentAccountMethod.isNullOrBlank()) {
            autoSignInCritical.add("autoSignInCurrentAccountMethod")
        }
        val autoSignInOptional = ArrayList<String>(7)
        if (symbols.autoSignInHybridProxyClass.isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridProxyClass")
        }
        if (symbols.autoSignInHybridProxyConstructorSpec.isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridProxyConstructorSpec")
        }
        if (symbols.autoSignInHybridJsBridgeClass.isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridJsBridgeClass")
        }
        if (symbols.autoSignInHybridNativeNetworkProxyMethod.isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridNativeNetworkProxyMethod")
        }
        if (symbols.autoSignInHybridTaskClass.isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridTaskClass")
        }
        if (symbols.autoSignInHybridTaskConstructorSpec.isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridTaskConstructorSpec")
        }
        if (symbols.autoSignInHybridTaskDoInBackgroundMethod.isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridTaskDoInBackgroundMethod")
        }
        out[HookFeatureKey.AUTO_SIGN_IN] = when {
            autoSignInCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = autoSignInCritical,
                missingOptional = autoSignInOptional,
            )
            autoSignInOptional.isEmpty() -> HookFeatureStatus(state = HookFeatureState.FULL)
            else -> HookFeatureStatus(state = HookFeatureState.PARTIAL, missingOptional = autoSignInOptional)
        }

        val privateReadReceiptCritical = ArrayList<String>(27)
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
        if (symbols.privateReadReceiptMessageManagerGetSocketClientMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageManagerGetSocketClientMethod")
        }
        if (symbols.privateReadReceiptMessageSendMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageSendMethod")
        }
        if (symbols.privateReadReceiptMessageBaseClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageBaseClass")
        }
        if (symbols.privateReadReceiptSocketClientClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptSocketClientClass")
        }
        if (symbols.privateReadReceiptSocketDuplicateCheckMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptSocketDuplicateCheckMethod")
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
        if (symbols.feedCardBindMethodSpec.isNullOrBlank()) homeNativeGlassCritical.add("feedCardBindMethodSpec")
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
        val hasHomeNativeGlassTopChromeTabSelectedSpec = symbols.homeNativeGlassTopChromeTabSelectedMethodSpecs
            .orEmpty()
            .any { spec ->
                val sep = spec.indexOf('#')
                sep > 0 && sep < spec.lastIndex
            }
        if (!hasHomeNativeGlassTopChromeTabSelectedSpec) {
            homeNativeGlassOptional.add("homeNativeGlassTopChromeTabSelectedMethodSpecs")
        }
        if (symbols.homeNativeGlassSubPbSetNextPageMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSubPbSetNextPageMethod")
        }
        if (symbols.homeNativeGlassSubPbSetNextPageParamType.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSubPbSetNextPageParamType")
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

        val replyServerLogReady =
            !symbols.replyServerResponseClass.isNullOrBlank() &&
                !symbols.replyServerResponseDecodeMethod.isNullOrBlank() &&
                !symbols.replyServerResponseResultJsonField.isNullOrBlank()
        val agreeServerLogReady =
            !symbols.replyVisibilityProbeAgreeResponseClass.isNullOrBlank() &&
                !symbols.replyVisibilityProbeAgreeDecodeLogicMethod.isNullOrBlank()
        val feedInfoLogReady = !symbols.feedCardBindMethodSpec.isNullOrBlank()
        val detailedLoggingMissing = buildList {
            if (!replyServerLogReady) add("ReplyServerResponseLogHook")
            if (!agreeServerLogReady) add("AgreeServerResponseLogHook")
            if (!feedInfoLogReady) add("FeedInfoLogHook.Bind")
        }
        out[HookFeatureKey.DETAILED_LOGGING] = when {
            detailedLoggingMissing.isEmpty() -> HookFeatureStatus(state = HookFeatureState.FULL)
            replyServerLogReady || agreeServerLogReady || feedInfoLogReady -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = detailedLoggingMissing,
            )
            else -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = listOf("detailedLoggingHookPoints"),
                missingOptional = detailedLoggingMissing,
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
        if (symbols.aiPbPageBrowserAiEmojiCreationViewClass.isNullOrBlank()) {
            aiComponentOptional.add("aiPbPageBrowserAiEmojiCreationViewClass")
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

        fun statusFromMissing(
            critical: List<String>,
            optional: List<String> = emptyList(),
        ): HookFeatureStatus {
            return when {
                critical.isNotEmpty() -> HookFeatureStatus(
                    state = HookFeatureState.DISABLED,
                    missingCritical = critical,
                    missingOptional = optional,
                )
                optional.isNotEmpty() -> HookFeatureStatus(
                    state = HookFeatureState.PARTIAL,
                    missingOptional = optional,
                )
                else -> HookFeatureStatus(state = HookFeatureState.FULL)
            }
        }

        val feedAdCritical = ArrayList<String>(1)
        val feedAdOptional = ArrayList<String>(1)
        if (symbols.feedTemplateKeyMethod.isNullOrBlank()) feedAdCritical.add("feedTemplateKeyMethod")
        if (symbols.feedTemplateLoadMoreMethod.isNullOrBlank()) feedAdOptional.add("feedTemplateLoadMoreMethod")
        out[HookFeatureKey.BLOCK_AD_FEED] = statusFromMissing(feedAdCritical, feedAdOptional)

        val postDataCritical = ArrayList<String>(3)
        if (symbols.typeAdapterSetDataMethod.isNullOrBlank()) postDataCritical.add("typeAdapterSetDataMethod")
        if (symbols.typeAdapterDataItemClass.isNullOrBlank()) postDataCritical.add("typeAdapterDataItemClass")
        if (symbols.typeAdapterDataGetTypeMethod.isNullOrBlank()) {
            postDataCritical.add("typeAdapterDataGetTypeMethod")
        }
        val postDataStatus = statusFromMissing(postDataCritical)

        val pbEarlyCritical = ArrayList<String>(2)
        if (symbols.pbEarlyAdInsertClass.isNullOrBlank()) pbEarlyCritical.add("pbEarlyAdInsertClass")
        if (symbols.pbEarlyAdInsertMethodSpecs.orEmpty().isEmpty()) {
            pbEarlyCritical.add("pbEarlyAdInsertMethodSpecs")
        }
        val pbEarlyOptional = if (
            symbols.pbEarlyAdInsertMethodSpecs.orEmpty().size in 1 until PB_EARLY_AD_INSERT_MIN_METHOD_COUNT
        ) {
            listOf("pbEarlyAdInsertMethodSpecs")
        } else {
            emptyList()
        }
        val pbEarlyStatus = statusFromMissing(pbEarlyCritical, pbEarlyOptional)

        val pbFallingCritical = ArrayList<String>(2)
        val pbFallingOptional = ArrayList<String>(3)
        if (symbols.pbFallingViewClass.isNullOrBlank()) pbFallingCritical.add("pbFallingViewClass")
        val pbFallingMethodNames = listOf(
            "pbFallingInitMethod" to symbols.pbFallingInitMethod,
            "pbFallingShowMethod" to symbols.pbFallingShowMethod,
            "pbFallingClearMethod" to symbols.pbFallingClearMethod,
        )
        val missingPbFallingMethods = pbFallingMethodNames
            .filter { (_, value) -> value.isNullOrBlank() }
            .map { (name, _) -> name }
        if (missingPbFallingMethods.size == pbFallingMethodNames.size) {
            pbFallingCritical.add("pbFallingMethods")
        } else {
            pbFallingOptional.addAll(missingPbFallingMethods)
        }
        val pbFallingStatus = statusFromMissing(pbFallingCritical, pbFallingOptional)

        val hasPbAdBidCommonPath =
            !symbols.pbAdBidCommonRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidCommonRequestStartMethods.isNullOrEmpty() &&
                !symbols.pbAdBidCommonRequestNotifyMethod.isNullOrBlank()
        val hasPbAdBidPageBrowserPath =
            !symbols.pbAdBidPageBrowserRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidPageBrowserRequestDataMethod.isNullOrBlank()
        val pbAdRequestOptional = ArrayList<String>(5)
        if (!hasPbAdBidCommonPath && !hasPbAdBidPageBrowserPath) {
            if (symbols.pbAdBidCommonRequestModelClass.isNullOrBlank()) {
                pbAdRequestOptional.add("pbAdBidCommonRequestModelClass")
            }
            if (symbols.pbAdBidCommonRequestStartMethods.isNullOrEmpty()) {
                pbAdRequestOptional.add("pbAdBidCommonRequestStartMethods")
            }
            if (symbols.pbAdBidCommonRequestNotifyMethod.isNullOrBlank()) {
                pbAdRequestOptional.add("pbAdBidCommonRequestNotifyMethod")
            }
            if (symbols.pbAdBidPageBrowserRequestModelClass.isNullOrBlank()) {
                pbAdRequestOptional.add("pbAdBidPageBrowserRequestModelClass")
            }
            if (symbols.pbAdBidPageBrowserRequestDataMethod.isNullOrBlank()) {
                pbAdRequestOptional.add("pbAdBidPageBrowserRequestDataMethod")
            }
        }
        val pbRequestStatus = HookFeatureStatus(
            state = if (pbAdRequestOptional.isEmpty()) HookFeatureState.FULL else HookFeatureState.PARTIAL,
            missingOptional = pbAdRequestOptional,
        )
        out[HookFeatureKey.BLOCK_AD_POST_PAGE] = combineSubFeatureStatuses(
            listOf(postDataStatus, pbEarlyStatus, pbFallingStatus, pbRequestStatus),
        )

        out[HookFeatureKey.BLOCK_AD_FORUM_PAGE] = if (ForumPageAdSymbolReadiness.evaluate(symbols).any) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = listOf("forumPageAdBlock"),
            )
        }

        val strategyOptional = ArrayList<String>(8)
        if (symbols.splashAdHelperClass.isNullOrBlank()) strategyOptional.add("splashAdHelperClass")
        if (symbols.splashAdHelperMethod.isNullOrBlank()) strategyOptional.add("splashAdHelperMethod")
        if (symbols.closeAdDataClass.isNullOrBlank()) strategyOptional.add("closeAdDataClass")
        if (symbols.closeAdDataMethodG1.isNullOrBlank()) strategyOptional.add("closeAdDataMethodG1")
        if (symbols.closeAdDataMethodJ1.isNullOrBlank()) strategyOptional.add("closeAdDataMethodJ1")
        if (symbols.zgaClass.isNullOrBlank()) strategyOptional.add("zgaClass")
        if (symbols.zgaMethods.isNullOrEmpty()) strategyOptional.add("zgaMethods")
        out[HookFeatureKey.BLOCK_AD_STRATEGY] = HookFeatureStatus(
            state = if (strategyOptional.isEmpty()) HookFeatureState.FULL else HookFeatureState.PARTIAL,
            missingOptional = strategyOptional,
        )

        out[HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT] = statusFromMissing(
            listOfNotNull(
                "searchBoxViewClass".takeIf { symbols.searchBoxViewClass.isNullOrBlank() },
                "searchBoxSetHintMethod".takeIf { symbols.searchBoxSetHintMethod.isNullOrBlank() },
                "homeSearchBoxOwnerClass".takeIf { symbols.homeSearchBoxOwnerClass.isNullOrBlank() },
                "homeSearchBoxInitMethod".takeIf { symbols.homeSearchBoxInitMethod.isNullOrBlank() },
                "homeSearchBoxGetterMethod".takeIf { symbols.homeSearchBoxGetterMethod.isNullOrBlank() },
            ),
        )

        out[HookFeatureKey.BLOCK_AD_HOME_TOP_BAR] = statusFromMissing(
            listOfNotNull(
                "homePersonalizeAnchorClasses".takeIf { symbols.homePersonalizeAnchorClasses.isNullOrEmpty() },
                "homeRightSlotClass".takeIf { symbols.homeRightSlotClass.isNullOrBlank() },
                "homeRightSlotStateMethods".takeIf { symbols.homeRightSlotStateMethods.isNullOrEmpty() },
            ),
        )

        val mineTabWebCritical = ArrayList<String>(5)
        val mineTabTargetVersionCode = symbols.scanTargetVersionCode
        when {
            mineTabTargetVersionCode == null -> mineTabWebCritical.add("scanTargetVersionCode")
            mineTabTargetVersionCode < WebAdBlockConstraints.MINE_TAB_MIN_VERSION_CODE -> {
                mineTabWebCritical.add(
                    "scanTargetVersionCode<${WebAdBlockConstraints.MINE_TAB_MIN_VERSION_CODE}",
                )
            }
        }
        if (symbols.mineTabWebViewClass.isNullOrBlank()) mineTabWebCritical.add("mineTabWebViewClass")
        if (symbols.mineTabWebLoadUrlMethod.isNullOrBlank()) mineTabWebCritical.add("mineTabWebLoadUrlMethod")
        if (symbols.mineTabWebGetUrlMethod.isNullOrBlank()) mineTabWebCritical.add("mineTabWebGetUrlMethod")
        if (symbols.mineTabWebGetInnerWebViewMethod.isNullOrBlank()) {
            mineTabWebCritical.add("mineTabWebGetInnerWebViewMethod")
        }
        out[HookFeatureKey.BLOCK_AD_MINE_TAB_WEB] = statusFromMissing(mineTabWebCritical)

        out[HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB] = statusFromMissing(
            listOfNotNull(
                "homeSideBarWebViewClass".takeIf { symbols.homeSideBarWebViewClass.isNullOrBlank() },
                "homeSideBarTbWebViewClass".takeIf { symbols.homeSideBarTbWebViewClass.isNullOrBlank() },
                "homeSideBarWebGetWebViewMethod".takeIf {
                    symbols.homeSideBarWebGetWebViewMethod.isNullOrBlank()
                },
                "homeSideBarWebGetUrlMethod".takeIf { symbols.homeSideBarWebGetUrlMethod.isNullOrBlank() },
                "homeSideBarWebGetInnerWebViewMethod".takeIf {
                    symbols.homeSideBarWebGetInnerWebViewMethod.isNullOrBlank()
                },
                "homeSideBarWebLoadUrlMethods".takeIf { symbols.homeSideBarWebLoadUrlMethods.isNullOrEmpty() },
            ),
        )
        out[HookFeatureKey.BLOCK_AD] = combineSubFeatureStatuses(
            listOf(
                out.getValue(HookFeatureKey.BLOCK_AD_FEED),
                out.getValue(HookFeatureKey.BLOCK_AD_POST_PAGE),
                out.getValue(HookFeatureKey.BLOCK_AD_FORUM_PAGE),
                out.getValue(HookFeatureKey.BLOCK_AD_STRATEGY),
                out.getValue(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT),
                out.getValue(HookFeatureKey.BLOCK_AD_HOME_TOP_BAR),
                out.getValue(HookFeatureKey.BLOCK_AD_MINE_TAB_WEB),
                out.getValue(HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB),
            ),
        )

        for (key in featureKeys) {
            if (!out.containsKey(key)) out[key] = HookFeatureStatus()
        }
        return withHookPointAvailability(symbols, out)
    }

    private fun combineSubFeatureStatuses(statuses: List<HookFeatureStatus>): HookFeatureStatus {
        val supported = statuses.filter { it.isSupported() }
        val missingCritical = statuses.flatMap { it.missingCritical }.distinct()
        val missingOptional = statuses.flatMap { it.missingOptional }.distinct()
        if (supported.isEmpty()) {
            return HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = missingCritical.ifEmpty { listOf("allSubFeatures") },
                missingOptional = missingOptional,
            )
        }
        val allComplete = statuses.all {
            it.state == HookFeatureState.FULL || it.state == HookFeatureState.HARD_CODED
        }
        return if (allComplete) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingCritical = missingCritical,
                missingOptional = missingOptional,
            )
        }
    }

    private fun withHookPointAvailability(
        symbols: HookSymbols,
        statusMap: LinkedHashMap<String, HookFeatureStatus>,
    ): LinkedHashMap<String, HookFeatureStatus> {
        val missingHookPointsByFeature = LinkedHashMap<String, MutableList<String>>()
        HookSymbolStatusFormatter.formatHookPointStatusLines(
            symbols = symbols,
            aiPbAiEmojiCreationViewClass = AI_PB_AI_EMOJI_CREATION_VIEW_CLASS,
            aiPbAiEmojiCreationPageBrowserViewClass =
                symbols.aiPbPageBrowserAiEmojiCreationViewClass
                    ?: AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS,
            msgTabViewModelClass = StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS,
            msgTabContainerViewClass = MSG_TAB_CONTAINER_VIEW_CLASS,
        ).forEach { line ->
            val match = HOOK_POINT_STATUS_PATTERN.matchEntire(line) ?: return@forEach
            val hookPoint = match.groupValues[1]
            val state = match.groupValues[2]
            if (state == "FOUND") return@forEach
            val affectedFeatures = featureKeysForHookPoint(hookPoint)
            if (affectedFeatures.isEmpty()) return@forEach
            affectedFeatures.forEach { featureKey ->
                missingHookPointsByFeature.getOrPut(featureKey) { ArrayList() }.add(hookPoint)
            }
        }

        missingHookPointsByFeature.forEach { (featureKey, hookPoints) ->
            val current = statusMap[featureKey] ?: HookFeatureStatus()
            val missingOptional = (current.missingOptional + hookPoints).distinct()
            val state = when (current.state) {
                HookFeatureState.DISABLED -> HookFeatureState.DISABLED
                else -> HookFeatureState.PARTIAL
            }
            statusMap[featureKey] = current.copy(
                state = state,
                missingOptional = missingOptional,
            )
        }
        refreshAdBlockParentStatus(statusMap)
        return statusMap
    }

    private fun refreshAdBlockParentStatus(statusMap: LinkedHashMap<String, HookFeatureStatus>) {
        statusMap[HookFeatureKey.BLOCK_AD] = combineSubFeatureStatuses(
            listOf(
                statusMap.getValue(HookFeatureKey.BLOCK_AD_FEED),
                statusMap.getValue(HookFeatureKey.BLOCK_AD_POST_PAGE),
                statusMap.getValue(HookFeatureKey.BLOCK_AD_FORUM_PAGE),
                statusMap.getValue(HookFeatureKey.BLOCK_AD_STRATEGY),
                statusMap.getValue(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT),
                statusMap.getValue(HookFeatureKey.BLOCK_AD_HOME_TOP_BAR),
                statusMap.getValue(HookFeatureKey.BLOCK_AD_MINE_TAB_WEB),
                statusMap.getValue(HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB),
            ),
        )
    }

    private fun featureKeysForHookPoint(name: String): List<String> {
        return when {
            name == "HomeTabHook" -> features(HookFeatureKey.SIMPLIFY_HOME_TOP_TABS)
            name.startsWith("HomeNativeGlassHook") -> features(HookFeatureKey.HOME_NATIVE_GLASS)
            name == "StrategyAdHook.HomePersonalizeAnchors" -> features(
                HookFeatureKey.BLOCK_AD_HOME_TOP_BAR,
                HookFeatureKey.HOME_NATIVE_GLASS,
            )
            name == "FeedInfoLogHook.Bind" -> features(
                HookFeatureKey.HOME_NATIVE_GLASS,
                HookFeatureKey.DETAILED_LOGGING,
            )
            name == "FeedAdHook.TemplateKey" -> features(
                HookFeatureKey.BLOCK_AD_FEED,
                HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
            )
            name == "FeedAdHook.LoadMore" -> features(
                HookFeatureKey.BLOCK_AD_FEED,
                HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,
            )
            name.startsWith("CustomPostCardBlockHook") -> features(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER)
            name == "ReplyVisibilityProbeHook" -> features(HookFeatureKey.VERIFY_REPLY_AFTER_POST)
            name == "ReplyServerResponseLogHook" -> features(HookFeatureKey.DETAILED_LOGGING)
            name == "AgreeServerResponseLogHook" -> features(HookFeatureKey.DETAILED_LOGGING)
            name.startsWith("StrategyAdHook.") -> features(HookFeatureKey.BLOCK_AD_STRATEGY)
            name.startsWith("SearchBoxTextAdHook.") -> features(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT)
            name == "HomeTopBarRightSlotHook" -> features(HookFeatureKey.BLOCK_AD_HOME_TOP_BAR)
            name == "PbBottomEnterBarHook" ||
                name.startsWith("PbBottomEnterBarHook.") -> features(HookFeatureKey.HIDE_PB_BOTTOM_BANNER)
            name == "PbFallingAdHook" -> features(HookFeatureKey.BLOCK_AD_POST_PAGE)
            name == "PbEarlyAdBlockHook" -> features(HookFeatureKey.BLOCK_AD_POST_PAGE)
            name.startsWith("PbAdRequestBlockHook.") -> features(HookFeatureKey.BLOCK_AD_POST_PAGE)
            name == "PostAdHook.DataFilter" -> features(HookFeatureKey.BLOCK_AD_POST_PAGE)
            name.startsWith("ForumPageAdBlockHook") -> features(HookFeatureKey.BLOCK_AD_FORUM_PAGE)
            name.startsWith("EnterForumWebHook") -> features(HookFeatureKey.FILTER_ENTER_FORUM_WEB)
            name.startsWith("PlainUrlDirectBrowserHook.") -> features(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER)
            name == "MineTabWebBlockHook" -> features(HookFeatureKey.BLOCK_AD_MINE_TAB_WEB)
            name == "HomeSideBarWebBlockHook" -> features(HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB)
            name == "ForumNativeTopShiftBlockHook" -> features(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT)
            name == "AutoRefreshHook" -> features(HookFeatureKey.DISABLE_AUTO_REFRESH)
            name == "AutoLoadMoreHook.Config" -> features(HookFeatureKey.AUTO_LOAD_MORE)
            name == "PbCommentAutoLoadHook" -> features(HookFeatureKey.AUTO_LOAD_MORE)
            name.startsWith("PbScrollCoalesceHook") -> features(HookFeatureKey.ENABLE_PB_SCROLL_COALESCE)
            name.startsWith("PbDisableGestureFontScaleHook.") -> features(
                HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE,
            )
            name == "PbLikeAutoReplyHook" -> features(HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY)
            name.startsWith("AiComponentDisableHook.") -> features(HookFeatureKey.DISABLE_AI_COMPONENTS)
            name.startsWith("MsgTabDefaultNotifyHook") -> features(HookFeatureKey.DEFAULT_NOTIFY_TAB)
            name == "PrivateReadReceiptBlockHook" -> features(HookFeatureKey.PRIVATE_READ_RECEIPT_INVISIBLE)
            name == "FreeCopyHook.Popup" -> features(HookFeatureKey.FREE_COPY)
            name.startsWith("MainTabBottomHook") -> features(HookFeatureKey.SIMPLIFY_BOTTOM_TABS)
            name.startsWith("DefaultOriginalImageHook") -> features(HookFeatureKey.DEFAULT_ORIGINAL_IMAGE)
            name.startsWith("ShareTrackingParamCleanerHook") -> features(HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS)
            else -> emptyList()
        }
    }

    private fun features(vararg featureKeys: String): List<String> = featureKeys.asList()

    private val HOOK_POINT_STATUS_PATTERN = Regex("""^HookPoint\[([^]]+)] state=([^ ]+) missing=([^ ]+) target=.*$""")

    private const val AI_PB_AI_EMOJI_CREATION_VIEW_CLASS =
        "com.baidu.tieba.pb.view.PbAiEmojiCreationView"
    private const val AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.meme.CommentFloorAiEmojiCreationView"
    private const val MSG_TAB_CONTAINER_VIEW_CLASS =
        "com.baidu.tieba.immessagecenter.msgtab.ui.view.MsgCenterContainerView"

}
