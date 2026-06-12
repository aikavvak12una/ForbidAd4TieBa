package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.core.StableTiebaHookPoints

internal object HookSymbolStatusFormatter {
    fun formatFeatureStatusLines(
        statusMap: Map<String, HookFeatureStatus>,
        featureKeys: List<String>,
    ): List<String> {
        return featureKeys.map { key ->
            val status = statusMap[key] ?: HookFeatureStatus()
            val critical = if (status.missingCritical.isEmpty()) "-" else status.missingCritical.joinToString(",")
            val optional = if (status.missingOptional.isEmpty()) "-" else status.missingOptional.joinToString(",")
            "Feature[$key] state=${status.state} critical=$critical optional=$optional"
        }
    }

    fun formatHookPointStatusLines(
        symbols: HookSymbols?,
        aiPbAiEmojiCreationViewClass: String,
        aiPbAiEmojiCreationPageBrowserViewClass: String,
        msgTabViewModelClass: String,
        msgTabContainerViewClass: String,
    ): List<String> {
        if (symbols == null) {
            return listOf("HookPoint[SymbolCache] state=MISSING missing=symbols target=-")
        }

        val out = ArrayList<String>(48)

        fun has(value: String?): Boolean = !value.isNullOrBlank()
        fun has(value: Int?): Boolean = value != null && value != 0
        fun hasList(values: List<String>?): Boolean = values.orEmpty().any { it.isNotBlank() }
        fun hasIntList(values: List<Int>?): Boolean = values.orEmpty().any { it != 0 }
        fun hasHomeNativeGlassPageClass(): Boolean {
            return symbols.homePersonalizeAnchorClasses
                .orEmpty()
                .contains(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS)
        }
        fun listTarget(values: List<String>?): String {
            return values.orEmpty().filter { it.isNotBlank() }.joinToString(",").ifBlank { "-" }
        }
        fun resourceListTarget(values: List<Int>?): String {
            val ids = values.orEmpty().filter { it != 0 }
            return if (ids.isEmpty()) "-" else "count=${ids.size}"
        }
        fun add(name: String, target: String, checks: List<Pair<String, Boolean>>) {
            val missing = checks.asSequence()
                .filter { !it.second }
                .map { it.first }
                .toList()
            val state = if (missing.isEmpty()) "FOUND" else "MISSING"
            val missingText = if (missing.isEmpty()) "-" else missing.joinToString(",")
            out.add("HookPoint[$name] state=$state missing=$missingText target=$target")
        }
        fun addOptional(name: String, target: String, checks: List<Pair<String, Boolean>>) {
            val missing = checks.asSequence()
                .filter { !it.second }
                .map { it.first }
                .toList()
            val state = if (missing.isEmpty()) "FOUND" else "PARTIAL"
            val missingText = if (missing.isEmpty()) "-" else missing.joinToString(",")
            out.add("HookPoint[$name] state=$state missing=$missingText target=$target")
        }

        add(
            "SettingsMenuHook",
            "${symbols.settingsClass}.${symbols.settingsInitMethod}[${symbols.settingsContainerField}]",
            listOf(
                "settingsClass" to has(symbols.settingsClass),
                "settingsInitMethod" to has(symbols.settingsInitMethod),
                "settingsContainerField" to has(symbols.settingsContainerField),
            ),
        )
        add(
            "HomeTabHook",
            "${symbols.homeTabClass}.${symbols.homeTabRebuildMethod}[${symbols.homeTabListField}]",
            listOf(
                "homeTabClass" to has(symbols.homeTabClass),
                "homeTabRebuildMethod" to has(symbols.homeTabRebuildMethod),
                "homeTabListField" to has(symbols.homeTabListField),
                "homeTabItemTypeField" to has(symbols.homeTabItemTypeField),
                "homeTabItemCodeField" to has(symbols.homeTabItemCodeField),
                "homeTabItemNameField" to has(symbols.homeTabItemNameField),
                "homeTabItemUrlField" to has(symbols.homeTabItemUrlField),
                "homeTabItemMainSetterMethod" to has(symbols.homeTabItemMainSetterMethod),
                "homeTabItemMainIntField" to has(symbols.homeTabItemMainIntField),
                "homeTabItemMainBooleanField" to has(symbols.homeTabItemMainBooleanField),
            ),
        )
        add(
            "StrategyAdHook.HomePersonalizeAnchors",
            "anchors={${listTarget(symbols.homePersonalizeAnchorClasses)}}",
            listOf("homePersonalizeAnchorClasses" to hasList(symbols.homePersonalizeAnchorClasses)),
        )
        add(
            "FeedAdHook.TemplateKey",
            "Feed item.${symbols.feedTemplateKeyMethod}()",
            listOf("feedTemplateKeyMethod" to has(symbols.feedTemplateKeyMethod)),
        )
        add(
            "CustomPostCardBlockHook.Payload",
            "Feed item.${symbols.feedTemplatePayloadMethod}()",
            listOf("feedTemplatePayloadMethod" to has(symbols.feedTemplatePayloadMethod)),
        )
        add(
            "FeedAdHook.LoadMore",
            "com.baidu.tieba.feed.list.FeedTemplateAdapter.${symbols.feedTemplateLoadMoreMethod}(List)",
            listOf("feedTemplateLoadMoreMethod" to has(symbols.feedTemplateLoadMoreMethod)),
        )
        add(
            "CustomPostCardBlockHook",
            "com.baidu.tieba.feed.list.TemplateAdapter.setList / FeedTemplateAdapter.${symbols.feedTemplateLoadMoreMethod}",
            listOf(
                "feedTemplateKeyMethod" to has(symbols.feedTemplateKeyMethod),
                "feedTemplatePayloadMethod" to has(symbols.feedTemplatePayloadMethod),
                "feedTemplateLoadMoreMethod" to has(symbols.feedTemplateLoadMoreMethod),
                "feedCardDataListField" to has(symbols.feedCardDataListField),
            ),
        )
        add(
            "CustomPostCardBlockHook.HeadParams",
            "Feed head params.${symbols.feedHeadParamsField}",
            listOf("feedHeadParamsField" to has(symbols.feedHeadParamsField)),
        )
        add(
            "CustomPostCardBlockHook.RecommendCard",
            "RecommendCardUiState.${symbols.feedRecommendCardNestedDataMethod}()[NestedData.${symbols.feedRecommendCardNestedDataListField}]",
            listOf(
                "feedRecommendCardNestedDataMethod" to has(symbols.feedRecommendCardNestedDataMethod),
                "feedRecommendCardNestedDataListField" to has(symbols.feedRecommendCardNestedDataListField),
            ),
        )
        add(
            "FeedInfoLogHook.Bind",
            "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.${symbols.feedCardBindMethod}",
            listOf("feedCardBindMethod" to has(symbols.feedCardBindMethod)),
        )
        add(
            "ReplyServerResponseLogHook",
            "${symbols.replyServerResponseClass}.${symbols.replyServerResponseDecodeMethod}" +
                "[${symbols.replyServerResponseResultJsonField}]",
            listOf(
                "replyServerResponseClass" to has(symbols.replyServerResponseClass),
                "replyServerResponseDecodeMethod" to has(symbols.replyServerResponseDecodeMethod),
                "replyServerResponseResultJsonField" to has(symbols.replyServerResponseResultJsonField),
            ),
        )
        add(
            "AgreeServerResponseLogHook",
            "${symbols.agreeServerResponseClass}.${symbols.agreeServerResponseDecodeLogicMethod}",
            listOf(
                "agreeServerResponseClass" to has(symbols.agreeServerResponseClass),
                "agreeServerResponseDecodeLogicMethod" to has(symbols.agreeServerResponseDecodeLogicMethod),
            ),
        )
        add(
            "ReplyVisibilityProbeHook",
            "${symbols.replyVisibilityProbeReplyResponseClass}.${symbols.replyVisibilityProbeReplyDecodeMethod}" +
                " -> ${symbols.replyVisibilityProbeHttpMessageClass} / " +
                "${symbols.replyVisibilityProbeAgreeResponseClass}.${symbols.replyVisibilityProbeAgreeDecodeLogicMethod}",
            listOf(
                "replyVisibilityProbeReplyResponseClass" to has(symbols.replyVisibilityProbeReplyResponseClass),
                "replyVisibilityProbeReplyDecodeMethod" to has(symbols.replyVisibilityProbeReplyDecodeMethod),
                "replyVisibilityProbeReplyResultJsonField" to has(symbols.replyVisibilityProbeReplyResultJsonField),
                "replyVisibilityProbeAddPostRequestClass" to has(symbols.replyVisibilityProbeAddPostRequestClass),
                "replyVisibilityProbeAddPostRequestDataField" to has(
                    symbols.replyVisibilityProbeAddPostRequestDataField,
                ),
                "replyVisibilityProbeResponsedMessageClass" to has(symbols.replyVisibilityProbeResponsedMessageClass),
                "replyVisibilityProbeGetOriginalMessageMethod" to has(
                    symbols.replyVisibilityProbeGetOriginalMessageMethod,
                ),
                "replyVisibilityProbeMessageClass" to has(symbols.replyVisibilityProbeMessageClass),
                "replyVisibilityProbeMessageGetExtraMethod" to has(symbols.replyVisibilityProbeMessageGetExtraMethod),
                "replyVisibilityProbeMessageGetTagMethod" to has(symbols.replyVisibilityProbeMessageGetTagMethod),
                "replyVisibilityProbeMessageSetTagMethod" to has(symbols.replyVisibilityProbeMessageSetTagMethod),
                "replyVisibilityProbeHttpMessageClass" to has(symbols.replyVisibilityProbeHttpMessageClass),
                "replyVisibilityProbeHttpMessageConstructor" to has(
                    symbols.replyVisibilityProbeHttpMessageConstructor,
                ),
                "replyVisibilityProbeHttpMessageAddParamMethod" to has(
                    symbols.replyVisibilityProbeHttpMessageAddParamMethod,
                ),
                "replyVisibilityProbeHttpMessageAddHeaderMethod" to has(
                    symbols.replyVisibilityProbeHttpMessageAddHeaderMethod,
                ),
                "replyVisibilityProbeMessageManagerClass" to has(symbols.replyVisibilityProbeMessageManagerClass),
                "replyVisibilityProbeMessageManagerGetInstanceMethod" to has(
                    symbols.replyVisibilityProbeMessageManagerGetInstanceMethod,
                ),
                "replyVisibilityProbeMessageManagerFindTaskMethod" to has(
                    symbols.replyVisibilityProbeMessageManagerFindTaskMethod,
                ),
                "replyVisibilityProbeMessageManagerRegisterTaskMethod" to has(
                    symbols.replyVisibilityProbeMessageManagerRegisterTaskMethod,
                ),
                "replyVisibilityProbeMessageManagerSendMethod" to has(
                    symbols.replyVisibilityProbeMessageManagerSendMethod,
                ),
                "replyVisibilityProbeTbHttpMessageTaskClass" to has(
                    symbols.replyVisibilityProbeTbHttpMessageTaskClass,
                ),
                "replyVisibilityProbeTbHttpMessageTaskConstructor" to has(
                    symbols.replyVisibilityProbeTbHttpMessageTaskConstructor,
                ),
                "replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod" to has(
                    symbols.replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod,
                ),
                "replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod" to has(
                    symbols.replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod,
                ),
                "replyVisibilityProbeBdUniqueIdClass" to has(symbols.replyVisibilityProbeBdUniqueIdClass),
                "replyVisibilityProbeBdUniqueIdGenMethod" to has(symbols.replyVisibilityProbeBdUniqueIdGenMethod),
                "replyVisibilityProbeTbadkCoreApplicationClass" to has(
                    symbols.replyVisibilityProbeTbadkCoreApplicationClass,
                ),
                "replyVisibilityProbeTbadkCoreApplicationGetInstMethod" to has(
                    symbols.replyVisibilityProbeTbadkCoreApplicationGetInstMethod,
                ),
                "replyVisibilityProbeTbadkCoreApplicationGetZidMethod" to has(
                    symbols.replyVisibilityProbeTbadkCoreApplicationGetZidMethod,
                ),
                "replyVisibilityProbeTbConfigClass" to has(symbols.replyVisibilityProbeTbConfigClass),
                "replyVisibilityProbeTbConfigServerAddressField" to has(
                    symbols.replyVisibilityProbeTbConfigServerAddressField,
                ),
                "replyVisibilityProbeTbConfigPbFloorAgreeUrlField" to has(
                    symbols.replyVisibilityProbeTbConfigPbFloorAgreeUrlField,
                ),
                "replyVisibilityProbeCmdConfigHttpClass" to has(symbols.replyVisibilityProbeCmdConfigHttpClass),
                "replyVisibilityProbeCmdPbFloorAgreeField" to has(symbols.replyVisibilityProbeCmdPbFloorAgreeField),
                "replyVisibilityProbeAgreeResponseClass" to has(symbols.replyVisibilityProbeAgreeResponseClass),
                "replyVisibilityProbeAgreeDecodeLogicMethod" to has(
                    symbols.replyVisibilityProbeAgreeDecodeLogicMethod,
                ),
            ),
        )
        add(
            "HomeNativeGlassHook",
            "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.<init> / " +
                "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.${symbols.feedCardBindMethod}",
            listOf(
                "homeNativeGlassPageClass" to hasHomeNativeGlassPageClass(),
                "feedCardBindMethod" to has(symbols.feedCardBindMethod),
            ),
        )
        add(
            "HomeNativeGlassHook.Resources",
            "subPbNext=${formatResourceId(symbols.homeNativeGlassSubPbNextPageMoreViewId)}, " +
                "titleDivider=${formatResourceId(symbols.homeNativeGlassPbReplyTitleDividerViewId)}",
            listOf(
                "homeNativeGlassSubPbNextPageMoreViewId" to has(symbols.homeNativeGlassSubPbNextPageMoreViewId),
                "homeNativeGlassPbReplyTitleDividerViewId" to has(symbols.homeNativeGlassPbReplyTitleDividerViewId),
            ),
        )
        add(
            "HomeNativeGlassHook.DynamicBackgroundColors",
            resourceListTarget(symbols.homeNativeGlassDynamicBackgroundColorIds),
            listOf(
                "homeNativeGlassDynamicBackgroundColorIds" to
                    hasIntList(symbols.homeNativeGlassDynamicBackgroundColorIds),
            ),
        )
        add(
            "HomeNativeGlassHook.SortSwitchBackground",
            "${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}.${symbols.homeNativeGlassSortSwitchBackgroundPaintField}",
            listOf(
                "homeNativeGlassSortSwitchBackgroundPaintField" to
                    has(symbols.homeNativeGlassSortSwitchBackgroundPaintField),
            ),
        )
        add(
            "HomeNativeGlassHook.SortSwitchSelectedSlide",
            "${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}." +
                "${symbols.homeNativeGlassSortSwitchSlideDrawMethod}(Canvas)" +
                "[${symbols.homeNativeGlassSortSwitchSlidePathField}]",
            listOf(
                "homeNativeGlassSortSwitchSlideDrawMethod" to
                    has(symbols.homeNativeGlassSortSwitchSlideDrawMethod),
                "homeNativeGlassSortSwitchSlidePathField" to
                    has(symbols.homeNativeGlassSortSwitchSlidePathField),
            ),
        )
        add(
            "HomeNativeGlassHook.EnterForumCapsule",
            "${symbols.homeNativeGlassEnterForumCapsuleControllerClass}." +
                "${symbols.homeNativeGlassEnterForumCapsuleInitMethod}/" +
                "${symbols.homeNativeGlassEnterForumCapsuleRefreshMethod}" +
                "[${symbols.homeNativeGlassEnterForumCapsuleViewField}]",
            listOf(
                "homeNativeGlassEnterForumCapsuleControllerClass" to
                    has(symbols.homeNativeGlassEnterForumCapsuleControllerClass),
                "homeNativeGlassEnterForumCapsuleInitMethod" to
                    has(symbols.homeNativeGlassEnterForumCapsuleInitMethod),
                "homeNativeGlassEnterForumCapsuleRefreshMethod" to
                    has(symbols.homeNativeGlassEnterForumCapsuleRefreshMethod),
                "homeNativeGlassEnterForumCapsuleViewField" to
                    has(symbols.homeNativeGlassEnterForumCapsuleViewField),
                "homeNativeGlassEnterForumCapsuleTitleField" to
                    has(symbols.homeNativeGlassEnterForumCapsuleTitleField),
            ),
        )
        add(
            "HomeNativeGlassHook.HostDarkModeSwitch",
            "${symbols.homeNativeGlassHostDarkModeMoreActivityClass}" +
                "[${symbols.homeNativeGlassHostDarkModeControllerField}]." +
                "${symbols.homeNativeGlassHostDarkModeSwitchGetterMethod}/" +
                "${symbols.homeNativeGlassHostDarkModeSwitchStateField}/" +
                "${symbols.homeNativeGlassHostDarkModeSwitchSetOnMethod}/" +
                "${symbols.homeNativeGlassHostDarkModeSwitchSetOffMethod}/" +
                "${symbols.homeNativeGlassHostDarkModeSwitchCallbackMethod}",
            listOf(
                "homeNativeGlassHostDarkModeMoreActivityClass" to
                    has(symbols.homeNativeGlassHostDarkModeMoreActivityClass),
                "homeNativeGlassHostDarkModeControllerField" to
                    has(symbols.homeNativeGlassHostDarkModeControllerField),
                "homeNativeGlassHostDarkModeSwitchGetterMethod" to
                    has(symbols.homeNativeGlassHostDarkModeSwitchGetterMethod),
                "homeNativeGlassHostDarkModeSwitchStateField" to
                    has(symbols.homeNativeGlassHostDarkModeSwitchStateField),
                "homeNativeGlassHostDarkModeSwitchSetOnMethod" to
                    has(symbols.homeNativeGlassHostDarkModeSwitchSetOnMethod),
                "homeNativeGlassHostDarkModeSwitchSetOffMethod" to
                    has(symbols.homeNativeGlassHostDarkModeSwitchSetOffMethod),
                "homeNativeGlassHostDarkModeSwitchCallbackMethod" to
                    has(symbols.homeNativeGlassHostDarkModeSwitchCallbackMethod),
            ),
        )
        add(
            "HomeNativeGlassHook.CommonLayoutPreloader",
            "${StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS}.${symbols.pbCommonLayoutPreloaderGetOrDefaultMethod}",
            listOf(
                "pbCommonLayoutPreloaderGetOrDefaultMethod" to
                    has(symbols.pbCommonLayoutPreloaderGetOrDefaultMethod),
            ),
        )
        add(
            "StrategyAdHook.Splash",
            "${symbols.splashAdHelperClass}.${symbols.splashAdHelperMethod}",
            listOf(
                "splashAdHelperClass" to has(symbols.splashAdHelperClass),
                "splashAdHelperMethod" to has(symbols.splashAdHelperMethod),
            ),
        )
        add(
            "StrategyAdHook.CloseAd",
            "${symbols.closeAdDataClass}.{${symbols.closeAdDataMethodG1},${symbols.closeAdDataMethodJ1}}",
            listOf(
                "closeAdDataClass" to has(symbols.closeAdDataClass),
                "closeAdDataMethodG1" to has(symbols.closeAdDataMethodG1),
                "closeAdDataMethodJ1" to has(symbols.closeAdDataMethodJ1),
            ),
        )
        add(
            "StrategyAdHook.Zga",
            "${symbols.zgaClass}.{${listTarget(symbols.zgaMethods)}}",
            listOf(
                "zgaClass" to has(symbols.zgaClass),
                "zgaMethods" to hasList(symbols.zgaMethods),
            ),
        )
        add(
            "SearchBoxTextAdHook.Hint",
            "${symbols.searchBoxViewClass}.${symbols.searchBoxSetHintMethod}",
            listOf(
                "searchBoxViewClass" to has(symbols.searchBoxViewClass),
                "searchBoxSetHintMethod" to has(symbols.searchBoxSetHintMethod),
            ),
        )
        add(
            "SearchBoxTextAdHook.Owner",
            "${symbols.homeSearchBoxOwnerClass}.{${symbols.homeSearchBoxInitMethod},${symbols.homeSearchBoxGetterMethod}}",
            listOf(
                "homeSearchBoxOwnerClass" to has(symbols.homeSearchBoxOwnerClass),
                "homeSearchBoxInitMethod" to has(symbols.homeSearchBoxInitMethod),
                "homeSearchBoxGetterMethod" to has(symbols.homeSearchBoxGetterMethod),
            ),
        )
        add(
            "HomeTopBarRightSlotHook",
            "${symbols.homeRightSlotClass}.{${listTarget(symbols.homeRightSlotStateMethods)}}",
            listOf(
                "homeRightSlotClass" to has(symbols.homeRightSlotClass),
                "homeRightSlotStateMethods" to hasList(symbols.homeRightSlotStateMethods),
            ),
        )
        add(
            "PbFallingAdHook",
            "${symbols.pbFallingViewClass}.{${symbols.pbFallingInitMethod},${symbols.pbFallingShowMethod},${symbols.pbFallingClearMethod}}",
            listOf(
                "pbFallingViewClass" to has(symbols.pbFallingViewClass),
                "pbFallingInitMethod" to has(symbols.pbFallingInitMethod),
                "pbFallingShowMethod" to has(symbols.pbFallingShowMethod),
                "pbFallingClearMethod" to has(symbols.pbFallingClearMethod),
            ),
        )
        add(
            "PbEarlyAdBlockHook",
            "${symbols.pbEarlyAdInsertClass}.{${listTarget(symbols.pbEarlyAdInsertMethodSpecs)}}",
            listOf(
                "pbEarlyAdInsertClass" to has(symbols.pbEarlyAdInsertClass),
                "pbEarlyAdInsertMethodSpecs" to hasList(symbols.pbEarlyAdInsertMethodSpecs),
            ),
        )
        add(
            "PbAdRequestBlockHook.AdBid.Common",
            "common=${symbols.pbAdBidCommonRequestModelClass}.{${listTarget(symbols.pbAdBidCommonRequestStartMethods)}}#${symbols.pbAdBidCommonRequestNotifyMethod}",
            listOf(
                "pbAdBidCommonRequestModelClass" to has(symbols.pbAdBidCommonRequestModelClass),
                "pbAdBidCommonRequestStartMethods" to hasList(symbols.pbAdBidCommonRequestStartMethods),
                "pbAdBidCommonRequestNotifyMethod" to has(symbols.pbAdBidCommonRequestNotifyMethod),
            ),
        )
        addOptional(
            "PbAdRequestBlockHook.AdBid.PageBrowser",
            "pageBrowser=" + if (
                has(symbols.pbAdBidPageBrowserRequestModelClass) &&
                has(symbols.pbAdBidPageBrowserRequestDataMethod)
            ) {
                "${symbols.pbAdBidPageBrowserRequestModelClass}.${symbols.pbAdBidPageBrowserRequestDataMethod}"
            } else {
                "optional-absent"
            },
            listOf(
                "pbAdBidPageBrowserRequestModelClass" to has(symbols.pbAdBidPageBrowserRequestModelClass),
                "pbAdBidPageBrowserRequestDataMethod" to has(symbols.pbAdBidPageBrowserRequestDataMethod),
            ),
        )
        add(
            "PostAdHook.DataFilter",
            "${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}.${symbols.typeAdapterSetDataMethod}(List) " +
                "item=${symbols.typeAdapterDataItemClass}.${symbols.typeAdapterDataGetTypeMethod}()",
            listOf(
                "typeAdapterSetDataMethod" to has(symbols.typeAdapterSetDataMethod),
                "typeAdapterDataItemClass" to has(symbols.typeAdapterDataItemClass),
                "typeAdapterDataGetTypeMethod" to has(symbols.typeAdapterDataGetTypeMethod),
            ),
        )
        val enterForumInitInfoReady =
            has(symbols.enterForumInitInfoDataClass) &&
                has(symbols.enterForumInitInfoGetUrlMethod)
        val enterForumWebLoadReady =
            has(symbols.enterForumWebControllerClass) &&
                has(symbols.enterForumWebLoadMethod)
        add(
            "EnterForumWebHook",
            "source=${symbols.enterForumInitInfoDataClass}.${symbols.enterForumInitInfoGetUrlMethod} " +
                "webLoad=${symbols.enterForumWebControllerClass}.${symbols.enterForumWebLoadMethod}(String)",
            listOf(
                "enterForumUrlSourceOrWebLoadTarget" to (enterForumInitInfoReady || enterForumWebLoadReady),
            ),
        )
        addOptional(
            "EnterForumWebHook.InitInfoData",
            "${symbols.enterForumInitInfoDataClass}.${symbols.enterForumInitInfoGetUrlMethod}()",
            listOf(
                "enterForumInitInfoDataClass" to has(symbols.enterForumInitInfoDataClass),
                "enterForumInitInfoGetUrlMethod" to has(symbols.enterForumInitInfoGetUrlMethod),
            ),
        )
        addOptional(
            "EnterForumWebHook.WebLoad",
            "${symbols.enterForumWebControllerClass}.${symbols.enterForumWebLoadMethod}(String)",
            listOf(
                "enterForumWebControllerClass" to has(symbols.enterForumWebControllerClass),
                "enterForumWebLoadMethod" to has(symbols.enterForumWebLoadMethod),
            ),
        )
        addOptional(
            "PlainUrlDirectBrowserHook.Direct",
            "${listTarget(symbols.plainUrlClickableSpanOnClickOwnerClasses)}.${symbols.plainUrlClickableSpanOnClickMethod}(View)[${symbols.plainUrlClickableSpanTypeField},${symbols.plainUrlClickableSpanUrlField},${symbols.plainUrlClickableSpanTextField}]",
            listOf(
                "plainUrlClickableSpanClass" to has(symbols.plainUrlClickableSpanClass),
                "plainUrlClickableSpanOnClickMethod" to has(symbols.plainUrlClickableSpanOnClickMethod),
                "plainUrlClickableSpanOnClickOwnerClasses" to hasList(symbols.plainUrlClickableSpanOnClickOwnerClasses),
                "plainUrlClickableSpanTypeField" to has(symbols.plainUrlClickableSpanTypeField),
                "plainUrlClickableSpanUrlField" to has(symbols.plainUrlClickableSpanUrlField),
                "plainUrlClickableSpanTextField" to has(symbols.plainUrlClickableSpanTextField),
            ),
        )
        addOptional(
            "PlainUrlDirectBrowserHook.Message",
            "${symbols.plainUrlMessageManagerClass}.${symbols.plainUrlMessageDispatchMethod}(${symbols.plainUrlResponsedMessageClass})",
            listOf(
                "plainUrlMessageManagerClass" to has(symbols.plainUrlMessageManagerClass),
                "plainUrlMessageDispatchMethod" to has(symbols.plainUrlMessageDispatchMethod),
                "plainUrlResponsedMessageClass" to has(symbols.plainUrlResponsedMessageClass),
                "plainUrlResponsedMessageGetCmdMethod" to has(symbols.plainUrlResponsedMessageGetCmdMethod),
                "plainUrlCustomResponsedMessageClass" to has(symbols.plainUrlCustomResponsedMessageClass),
                "plainUrlCustomResponsedMessageGetDataMethod" to has(symbols.plainUrlCustomResponsedMessageGetDataMethod),
                "plainUrlApplicationClass" to has(symbols.plainUrlApplicationClass),
                "plainUrlApplicationGetInstMethod" to has(symbols.plainUrlApplicationGetInstMethod),
            ),
        )
        add(
            "PlainUrlDirectBrowserHook.MountCard",
            "${symbols.mountCardLinkLayoutClass}.${symbols.mountCardLinkLayoutOnClickMethod}(View)[${symbols.mountCardLinkLayoutDataField}->${symbols.mountCardLinkInfoDataClass}.${symbols.mountCardLinkInfoGetUrlMethod}]",
            listOf(
                "mountCardLinkLayoutClass" to has(symbols.mountCardLinkLayoutClass),
                "mountCardLinkLayoutOnClickMethod" to has(symbols.mountCardLinkLayoutOnClickMethod),
                "mountCardLinkLayoutDataField" to has(symbols.mountCardLinkLayoutDataField),
                "mountCardLinkInfoDataClass" to has(symbols.mountCardLinkInfoDataClass),
                "mountCardLinkInfoGetUrlMethod" to has(symbols.mountCardLinkInfoGetUrlMethod),
            ),
        )
        add(
            "ForumNativeTopShiftBlockHook",
            "${symbols.forumBottomSheetViewClass}.${symbols.forumBottomSheetInitScrollMethod}",
            listOf(
                "forumBottomSheetViewClass" to has(symbols.forumBottomSheetViewClass),
                "forumBottomSheetInitScrollMethod" to has(symbols.forumBottomSheetInitScrollMethod),
            ),
        )
        add(
            "AutoRefreshHook",
            "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.${symbols.autoRefreshTriggerMethod}",
            listOf("autoRefreshTriggerMethod" to has(symbols.autoRefreshTriggerMethod)),
        )
        add(
            "AutoLoadMoreHook.Config",
            "${symbols.autoLoadMoreConfigClass}.${symbols.autoLoadMoreConfigMethod}",
            listOf(
                "autoLoadMoreConfigClass" to has(symbols.autoLoadMoreConfigClass),
                "autoLoadMoreConfigMethod" to has(symbols.autoLoadMoreConfigMethod),
            ),
        )
        add(
            "PbScrollCoalesceHook",
            "${symbols.pbCommentScrollListenerClass}.${symbols.pbCommentScrollMethod}",
            listOf(
                "pbCommentScrollListenerClass" to has(symbols.pbCommentScrollListenerClass),
                "pbCommentScrollMethod" to has(symbols.pbCommentScrollMethod),
            ),
        )
        run {
            val listChecks = listOf(
                "pbCommentBottomListScrollClass" to has(symbols.pbCommentBottomListScrollClass),
                "pbCommentBottomListScrollMethod" to has(symbols.pbCommentBottomListScrollMethod),
                "pbCommentBottomListOwnerField" to has(symbols.pbCommentBottomListOwnerField),
            )
            val recyclerChecks = listOf(
                "pbCommentBottomRecyclerScrollClass" to has(symbols.pbCommentBottomRecyclerScrollClass),
                "pbCommentBottomRecyclerScrollMethod" to has(symbols.pbCommentBottomRecyclerScrollMethod),
                "pbCommentBottomRecyclerOwnerField" to has(symbols.pbCommentBottomRecyclerOwnerField),
            )
            val listFound = listChecks.all { it.second }
            val recyclerFound = recyclerChecks.all { it.second }
            val anyBottomSymbol = (listChecks + recyclerChecks).any { it.second }
            val missing = when {
                listFound || recyclerFound -> emptyList()
                anyBottomSymbol -> (listChecks + recyclerChecks).filter { !it.second }.map { it.first }
                else -> listOf("pbCommentBottomMechanism")
            }
            val state = when {
                listFound || recyclerFound -> "FOUND"
                anyBottomSymbol -> "PARTIAL"
                else -> "MISSING"
            }
            val missingText = if (missing.isEmpty()) "-" else missing.joinToString(",")
            out.add(
                "HookPoint[PbCommentAutoLoadHook] state=$state missing=$missingText target=" +
                    "${symbols.pbCommentBottomListScrollClass}.${symbols.pbCommentBottomListScrollMethod} / " +
                    "${symbols.pbCommentBottomRecyclerScrollClass}.${symbols.pbCommentBottomRecyclerScrollMethod}",
            )
        }
        add(
            "PbDisableGestureFontScaleHook.Manager",
            "${symbols.pbGestureScaleManagerClass}.${symbols.pbGestureScaleDispatchMethod}/${symbols.pbGestureScaleListenerSetterMethod}",
            listOf(
                "pbGestureScaleManagerClass" to has(symbols.pbGestureScaleManagerClass),
                "pbGestureScaleDispatchMethod" to has(symbols.pbGestureScaleDispatchMethod),
                "pbGestureScaleListenerSetterMethod" to has(symbols.pbGestureScaleListenerSetterMethod),
            ),
        )
        add(
            "PbDisableGestureFontScaleHook.Listener",
            "${symbols.pbGestureScaleListenerClass}.${symbols.pbGestureScaleListenerOnScaleMethod}",
            listOf(
                "pbGestureScaleListenerClass" to has(symbols.pbGestureScaleListenerClass),
                "pbGestureScaleListenerOnScaleMethod" to has(symbols.pbGestureScaleListenerOnScaleMethod),
            ),
        )
        add(
            "PbLikeAutoReplyHook",
            "${symbols.pbLikeAutoReplyAgreeViewClass}.${symbols.pbLikeAutoReplyAgreeClickMethod}(View) / " +
                "${symbols.pbLikeAutoReplyAgreeViewGetDataMethod} -> " +
                "${symbols.pbLikeAutoReplyInputContainerClass}.{${symbols.pbLikeAutoReplyInputContainerGetInputViewMethod}," +
                "${symbols.pbLikeAutoReplyInputContainerGetSendViewMethod}}",
            listOf(
                "pbLikeAutoReplyAgreeViewClass" to has(symbols.pbLikeAutoReplyAgreeViewClass),
                "pbLikeAutoReplyAgreeClickMethod" to has(symbols.pbLikeAutoReplyAgreeClickMethod),
                "pbLikeAutoReplyAgreeViewGetDataMethod" to has(symbols.pbLikeAutoReplyAgreeViewGetDataMethod),
                "pbLikeAutoReplyAgreeDataClass" to has(symbols.pbLikeAutoReplyAgreeDataClass),
                "pbLikeAutoReplyAgreeDataHasAgreeField" to has(symbols.pbLikeAutoReplyAgreeDataHasAgreeField),
                "pbLikeAutoReplyAgreeDataAgreeTypeField" to has(symbols.pbLikeAutoReplyAgreeDataAgreeTypeField),
                "pbLikeAutoReplyAgreeDataIsInThreadField" to has(symbols.pbLikeAutoReplyAgreeDataIsInThreadField),
                "pbLikeAutoReplyInputContainerClass" to has(symbols.pbLikeAutoReplyInputContainerClass),
                "pbLikeAutoReplyInputContainerGetInputViewMethod" to
                    has(symbols.pbLikeAutoReplyInputContainerGetInputViewMethod),
                "pbLikeAutoReplyInputContainerGetSendViewMethod" to
                    has(symbols.pbLikeAutoReplyInputContainerGetSendViewMethod),
            ),
        )
        add(
            "AiComponentDisableHook.SpriteMeme",
            "${symbols.aiSpriteMemePanControllerClass}.${symbols.aiSpriteMemeEnableMethod}",
            listOf(
                "aiSpriteMemePanControllerClass" to has(symbols.aiSpriteMemePanControllerClass),
                "aiSpriteMemeEnableMethod" to has(symbols.aiSpriteMemeEnableMethod),
            ),
        )
        add(
            "AiComponentDisableHook.PbNewInputContainer",
            "${symbols.aiPbNewInputContainerClass}.{${symbols.aiPbNewInputContainerInitSpriteMemeMethod},${symbols.aiPbNewInputContainerInitAiWriteMethod}}",
            listOf(
                "aiPbNewInputContainerClass" to has(symbols.aiPbNewInputContainerClass),
                "aiPbNewInputContainerInitSpriteMemeMethod" to has(symbols.aiPbNewInputContainerInitSpriteMemeMethod),
                "aiPbNewInputContainerInitAiWriteMethod" to has(symbols.aiPbNewInputContainerInitAiWriteMethod),
            ),
        )
        add(
            "AiComponentDisableHook.PbAiEmojiCreation",
            "$aiPbAiEmojiCreationViewClass.${symbols.aiPbAiEmojiCreationViewBindMethod}",
            listOf(
                "aiPbAiEmojiCreationViewBindMethod" to has(symbols.aiPbAiEmojiCreationViewBindMethod),
            ),
        )
        if (has(symbols.aiPbPageBrowserAiEmojiCreationBindMethod)) {
            add(
                "AiComponentDisableHook.PbPageBrowserAiEmojiCreation",
                "$aiPbAiEmojiCreationPageBrowserViewClass.${symbols.aiPbPageBrowserAiEmojiCreationBindMethod}",
                listOf(
                    "aiPbPageBrowserAiEmojiCreationBindMethod" to true,
                ),
            )
        }
        add(
            "AiComponentDisableHook.ImageViewerJumpButton",
            "${symbols.aiImageViewerJumpButtonOwnerClass}.${symbols.aiImageViewerJumpButtonInitMethod}",
            listOf(
                "aiImageViewerJumpButtonOwnerClass" to has(symbols.aiImageViewerJumpButtonOwnerClass),
                "aiImageViewerJumpButtonInitMethod" to has(symbols.aiImageViewerJumpButtonInitMethod),
            ),
        )
        add(
            "MsgTabDefaultNotifyHook",
            "$msgTabViewModelClass.${symbols.msgTabLocateToTabMethod}(long,String)",
            listOf("msgTabLocateToTabMethod" to has(symbols.msgTabLocateToTabMethod)),
        )
        add(
            "MsgTabDefaultNotifyHook.Container",
            "${msgTabContainerViewClass}.${symbols.msgTabContainerSelectMethod}[${symbols.msgTabContainerExtDataField}]",
            listOf(
                "msgTabContainerSelectMethod" to has(symbols.msgTabContainerSelectMethod),
                "msgTabContainerExtDataField" to has(symbols.msgTabContainerExtDataField),
            ),
        )
        add(
            "PrivateReadReceiptBlockHook",
            "${symbols.privateReadReceiptModelClass}.${symbols.privateReadReceiptModelReadDispatchMethod} / " +
                "${symbols.privateReadReceiptMessageManagerClass}.${symbols.privateReadReceiptMessageSendMethod}" +
                "(${symbols.privateReadReceiptMessageBaseClass}) -> " +
                "${symbols.privateReadReceiptRequestClass}[${symbols.privateReadReceiptRequestMsgIdField},${symbols.privateReadReceiptRequestToUidField}]",
            listOf(
                "privateReadReceiptModelClass" to has(symbols.privateReadReceiptModelClass),
                "privateReadReceiptModelReadDispatchMethod" to has(symbols.privateReadReceiptModelReadDispatchMethod),
                "privateReadReceiptMessageManagerClass" to has(symbols.privateReadReceiptMessageManagerClass),
                "privateReadReceiptMessageManagerGetInstanceMethod" to has(symbols.privateReadReceiptMessageManagerGetInstanceMethod),
                "privateReadReceiptMessageSendMethod" to has(symbols.privateReadReceiptMessageSendMethod),
                "privateReadReceiptMessageBaseClass" to has(symbols.privateReadReceiptMessageBaseClass),
                "privateReadReceiptRequestClass" to has(symbols.privateReadReceiptRequestClass),
                "privateReadReceiptModelBaseClass" to has(symbols.privateReadReceiptModelBaseClass),
                "privateReadReceiptCommitResponseClass" to has(symbols.privateReadReceiptCommitResponseClass),
                "privateReadReceiptProcessAckMethod" to has(symbols.privateReadReceiptProcessAckMethod),
                "privateReadReceiptResponseErrorMethod" to has(symbols.privateReadReceiptResponseErrorMethod),
                "privateReadReceiptRequestMsgIdField" to has(symbols.privateReadReceiptRequestMsgIdField),
                "privateReadReceiptRequestToUidField" to has(symbols.privateReadReceiptRequestToUidField),
                "privateReadReceiptModelDataField" to has(symbols.privateReadReceiptModelDataField),
                "privateReadReceiptPageDataClass" to has(symbols.privateReadReceiptPageDataClass),
                "privateReadReceiptPageDataChatListMethod" to has(symbols.privateReadReceiptPageDataChatListMethod),
                "privateReadReceiptChatMessageClass" to has(symbols.privateReadReceiptChatMessageClass),
                "privateReadReceiptChatMessageMsgIdMethod" to has(symbols.privateReadReceiptChatMessageMsgIdMethod),
                "privateReadReceiptChatMessageUserIdMethod" to has(symbols.privateReadReceiptChatMessageUserIdMethod),
                "privateReadReceiptChatMessageLocalDataMethod" to has(symbols.privateReadReceiptChatMessageLocalDataMethod),
                "privateReadReceiptLocalDataClass" to has(symbols.privateReadReceiptLocalDataClass),
                "privateReadReceiptLocalDataStatusMethod" to has(symbols.privateReadReceiptLocalDataStatusMethod),
                "privateReadReceiptAccountClass" to has(symbols.privateReadReceiptAccountClass),
                "privateReadReceiptCurrentAccountMethod" to has(symbols.privateReadReceiptCurrentAccountMethod),
            ),
        )
        add(
            "FreeCopyHook.Popup",
            "${symbols.freeCopyPopupMenuClass}.${symbols.freeCopyPopupContentViewMethod}[${symbols.freeCopyPopupTextField}]",
            listOf(
                "freeCopyPopupMenuClass" to has(symbols.freeCopyPopupMenuClass),
                "freeCopyPopupContentViewMethod" to has(symbols.freeCopyPopupContentViewMethod),
                "freeCopyPopupTextField" to has(symbols.freeCopyPopupTextField),
            ),
        )
        add(
            "CollectionSearchHook.Core",
            "${symbols.collectionPresenterField}.${symbols.collectionPresenterListSetterMethod}",
            listOf(
                "collectionPresenterField" to has(symbols.collectionPresenterField),
                "collectionPresenterListSetterMethod" to has(symbols.collectionPresenterListSetterMethod),
                "collectionPresenterAdapterField" to has(symbols.collectionPresenterAdapterField),
                "collectionModelField" to has(symbols.collectionModelField),
                "collectionModelListGetterMethod" to has(symbols.collectionModelListGetterMethod),
                "collectionModelParseMethod" to has(symbols.collectionModelParseMethod),
            ),
        )
        add(
            "CollectionSearchHook.List",
            "${symbols.collectionModelListField}/${symbols.collectionFragmentDisplayListField}",
            listOf(
                "collectionModelListField" to has(symbols.collectionModelListField),
                "collectionFragmentDisplayListField" to has(symbols.collectionFragmentDisplayListField),
                "collectionActivityNavControllerField" to has(symbols.collectionActivityNavControllerField),
                "collectionNavBarField" to has(symbols.collectionNavBarField),
            ),
        )
        add(
            "CollectionSearchHook.Adapter",
            "${symbols.collectionPresenterAdapterField}.{${symbols.collectionAdapterShowFooterMethod},${symbols.collectionAdapterLoadingMethod},${symbols.collectionAdapterHasMoreMethod}}",
            listOf(
                "collectionAdapterShowFooterMethod" to has(symbols.collectionAdapterShowFooterMethod),
                "collectionAdapterLoadingMethod" to has(symbols.collectionAdapterLoadingMethod),
                "collectionAdapterHasMoreMethod" to has(symbols.collectionAdapterHasMoreMethod),
                "collectionEditModeMethod" to has(symbols.collectionEditModeMethod),
            ),
        )
        add(
            "HistorySearchHook",
            "${symbols.historyAdapterField}.${symbols.historyAdapterSetListMethod}[${symbols.historyListField}]",
            listOf(
                "historyAdapterField" to has(symbols.historyAdapterField),
                "historyAdapterSetListMethod" to has(symbols.historyAdapterSetListMethod),
                "historyListField" to has(symbols.historyListField),
                "historyActivityListUpdateMethod" to has(symbols.historyActivityListUpdateMethod),
                "historyActivityNavBarField" to has(symbols.historyActivityNavBarField),
                "historyThreadNameMethod" to has(symbols.historyThreadNameMethod),
                "historyForumNameMethod" to has(symbols.historyForumNameMethod),
                "historyUserNameMethod" to has(symbols.historyUserNameMethod),
                "historyDescriptionMethod" to has(symbols.historyDescriptionMethod),
                "historyThreadIdMethod" to has(symbols.historyThreadIdMethod),
                "historyPostIdMethod" to has(symbols.historyPostIdMethod),
                "historyLiveIdMethod" to has(symbols.historyLiveIdMethod),
            ),
        )
        add(
            "MainTabBottomHook",
            "${symbols.mainTabDataClass}.${symbols.mainTabAddMethod}/${symbols.mainTabGetListMethod}",
            listOf(
                "mainTabDataClass" to has(symbols.mainTabDataClass),
                "mainTabAddMethod" to has(symbols.mainTabAddMethod),
                "mainTabGetListMethod" to has(symbols.mainTabGetListMethod),
                "mainTabDelegateGetStructureMethod" to has(symbols.mainTabDelegateGetStructureMethod),
                "mainTabStructureTypeField" to has(symbols.mainTabStructureTypeField),
            ),
        )
        add(
            "MainTabBottomHook.StructureFields",
            "${symbols.mainTabDelegateGetStructureMethod}[${symbols.mainTabStructureDynamicIconField},${symbols.mainTabStructureFragmentField}]",
            listOf(
                "mainTabStructureDynamicIconField" to has(symbols.mainTabStructureDynamicIconField),
                "mainTabStructureFragmentField" to has(symbols.mainTabStructureFragmentField),
            ),
        )
        add(
            "DefaultOriginalImageHook",
            "${symbols.origImageUrlDragImageViewClass}.${symbols.origImageTriggerMethod}",
            listOf(
                "origImageUrlDragImageViewClass" to has(symbols.origImageUrlDragImageViewClass),
                "origImageDataClass" to has(symbols.origImageDataClass),
                "origImageTriggerMethod" to has(symbols.origImageTriggerMethod),
                "origImageAssistDataMethod" to has(symbols.origImageAssistDataMethod),
                "origImageShowButtonField" to has(symbols.origImageShowButtonField),
                "origImageBlockedField" to has(symbols.origImageBlockedField),
                "origImageOriginalProcessField" to has(symbols.origImageOriginalProcessField),
                "origImageOriginalUrlField" to has(symbols.origImageOriginalUrlField),
            ),
        )
        add(
            "DefaultOriginalImageHook.PrimaryItem",
            "${symbols.origImagePagerAdapterClass}.${symbols.origImageSetPrimaryItemMethod}",
            listOf(
                "origImagePagerAdapterClass" to has(symbols.origImagePagerAdapterClass),
                "origImageSetPrimaryItemMethod" to has(symbols.origImageSetPrimaryItemMethod),
            ),
        )
        add(
            "DefaultOriginalImageHook.UrlDragExtra",
            "${symbols.origImageUrlDragImageViewClass}.{${symbols.origImageSetAssistUrlMethod},${symbols.origImagePrimaryReadyMethod},${symbols.origImageDirectStartMethod},${symbols.origImageOriginTextMethod}}",
            listOf(
                "origImageSetAssistUrlMethod" to has(symbols.origImageSetAssistUrlMethod),
                "origImagePrimaryReadyMethod" to has(symbols.origImagePrimaryReadyMethod),
                "origImageDirectStartMethod" to has(symbols.origImageDirectStartMethod),
                "origImageOriginTextMethod" to has(symbols.origImageOriginTextMethod),
            ),
        )
        add(
            "DefaultOriginalImageHook.SharedPrefs",
            "${symbols.origImageSharedPrefHelperClass}.{${symbols.origImageSharedPrefGetInstanceMethod},${symbols.origImageSharedPrefPutBooleanMethod}}",
            listOf(
                "origImageSharedPrefHelperClass" to has(symbols.origImageSharedPrefHelperClass),
                "origImageSharedPrefGetInstanceMethod" to has(symbols.origImageSharedPrefGetInstanceMethod),
                "origImageSharedPrefPutBooleanMethod" to has(symbols.origImageSharedPrefPutBooleanMethod),
            ),
        )
        add(
            "DefaultOriginalImageHook.Md5",
            "${symbols.origImageMd5Class}.${symbols.origImageMd5Method}",
            listOf(
                "origImageMd5Class" to has(symbols.origImageMd5Class),
                "origImageMd5Method" to has(symbols.origImageMd5Method),
            ),
        )
        add(
            "ShareTrackingParamCleanerHook",
            "${symbols.shareTrackBuilderClass}.${symbols.shareTrackBuildUrlMethod}",
            listOf(
                "shareTrackBuilderClass" to has(symbols.shareTrackBuilderClass),
                "shareTrackBuildUrlMethod" to has(symbols.shareTrackBuildUrlMethod),
            ),
        )
        add(
            "ShareTrackingParamCleanerHook.AppendQuery",
            "${symbols.shareTrackBuilderClass}.${symbols.shareTrackAppendQueryMethod}",
            listOf("shareTrackAppendQueryMethod" to has(symbols.shareTrackAppendQueryMethod)),
        )
        add(
            "ImageViewerNativeShareHook",
            "${symbols.imageViewerShareConfigClass}.${symbols.imageViewerShareAddOutsideMethod}",
            listOf(
                "imageViewerShareConfigClass" to has(symbols.imageViewerShareConfigClass),
                "imageViewerShareIsDialogField" to has(symbols.imageViewerShareIsDialogField),
                "imageViewerShareItemField" to has(symbols.imageViewerShareItemField),
                "imageViewerShareAddOutsideMethod" to has(symbols.imageViewerShareAddOutsideMethod),
                "imageViewerShareGetRequestDataMethod" to has(symbols.imageViewerShareGetRequestDataMethod),
                "imageViewerShareSetRequestDataMethod" to has(symbols.imageViewerShareSetRequestDataMethod),
                "imageViewerShareGetContextMethod" to has(symbols.imageViewerShareGetContextMethod),
                "imageViewerShareItemClass" to has(symbols.imageViewerShareItemClass),
                "imageViewerShareItemImageUriField" to has(symbols.imageViewerShareItemImageUriField),
                "imageViewerShareItemViewClass" to has(symbols.imageViewerShareItemViewClass),
                "imageViewerShareItemNameByResMethod" to has(symbols.imageViewerShareItemNameByResMethod),
                "imageViewerShareItemNameByTextMethod" to has(symbols.imageViewerShareItemNameByTextMethod),
                "imageViewerShareIconResId" to has(symbols.imageViewerShareIconResId),
            ),
        )
        add(
            "ImageViewerNativeShareHook.ItemTextFields",
            "${symbols.imageViewerShareItemClass}[${symbols.imageViewerShareItemTitleField},${symbols.imageViewerShareItemContentField},${symbols.imageViewerShareItemLinkUrlField},${symbols.imageViewerShareItemImageUrlField},${symbols.imageViewerShareItemLocalFileField}]",
            listOf(
                "imageViewerShareItemTitleField" to has(symbols.imageViewerShareItemTitleField),
                "imageViewerShareItemContentField" to has(symbols.imageViewerShareItemContentField),
                "imageViewerShareItemLinkUrlField" to has(symbols.imageViewerShareItemLinkUrlField),
                "imageViewerShareItemImageUrlField" to has(symbols.imageViewerShareItemImageUrlField),
                "imageViewerShareItemLocalFileField" to has(symbols.imageViewerShareItemLocalFileField),
            ),
        )
        symbols.scanErrors.forEach { error ->
            val (name, detail) = splitScanError(error)
            out.add("HookPoint[$name] state=ERROR missing=exception target=$detail")
        }

        return out
    }


    private fun formatResourceId(value: Int?): String {
        return if (value != null && value != 0) {
            "0x${Integer.toHexString(value)}"
        } else {
            "-"
        }
    }

    private fun splitScanError(error: String): Pair<String, String> {
        val separator = " :: "
        val idx = error.indexOf(separator)
        if (idx <= 0) return "ScanException" to sanitizeScanStatusText(error)
        val tag = error.substring(0, idx).trim().ifEmpty { "ScanException" }
        val detail = error.substring(idx + separator.length).trim().ifEmpty { "unknown" }
        return tag to sanitizeScanStatusText(detail)
    }

    private fun sanitizeScanStatusText(raw: String): String {
        return raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(240)
            .ifBlank { "unknown" }
    }
}
