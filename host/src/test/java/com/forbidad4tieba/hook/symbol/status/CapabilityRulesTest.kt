package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.textSymbols
import org.junit.Assert.*
import org.junit.Test

class CapabilityRulesTest {
    private data class RequiredRule(val feature: String, val point: String?, val keys: List<String>)

    // These facts are intentionally not read from SymbolDependencies or capability output.
    private val requiredRules = listOf(
        RequiredRule(HookFeatureKey.HIDE_INPUT_MEME_BAR, "InputMemeBarBlockHook",
            listOf("inputMemeBarControllerClass", "inputMemeBarEnableMethod")),
        RequiredRule(HookFeatureKey.DISABLE_AUTO_REFRESH, null,
            listOf("autoRefreshFlow", "autoRefreshCacheRestoreMethod")),
        RequiredRule(HookFeatureKey.FREE_COPY_COMMENT_INJECTION, "FreeCopyHook.Popup",
            listOf("freeCopyPopupMenuClass", "freeCopyPopupContentViewMethod", "freeCopyPopupTextField")),
        RequiredRule(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT, null,
            listOf("forumBottomSheetViewClass", "forumBottomSheetInitScrollMethod")),
        RequiredRule(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT, null,
            listOf("searchBoxViewClass", "homeSearchBoxOwnerClass", "homeSearchBoxInitMethod",
                "homeSearchBoxGetterMethod", "searchBoxSetHintMethod")),
        RequiredRule(HookFeatureKey.BLOCK_AD_HOME_BOTTOM_EASTER_EGG, null,
            listOf("homeBottomEasterEggParserClass", "homeBottomEasterEggParserMethod")),
        RequiredRule(HookFeatureKey.BLOCK_AD_MINE_TAB_WEB, "MineTabWebBlockHook",
            listOf("mineTabWebViewClass", "mineTabWebLoadUrlMethod", "mineTabWebGetUrlMethod",
                "mineTabWebGetInnerWebViewMethod")),
        RequiredRule(HookFeatureKey.DEFAULT_NOTIFY_TAB, "MsgTabDefaultNotifyHook",
            listOf("msgTabLocateToTabMethod")),
        RequiredRule(HookFeatureKey.FILTER_ENTER_FORUM_WEB, "EnterForumWebHook",
            listOf("enterForumWebControllerClass", "enterForumWebLoadMethod", "enterForumWebViewFieldOwnerClass",
                "enterForumWebViewField", "enterForumWebSetForceCommonMethod")),
        RequiredRule(HookFeatureKey.COMMENT_LEVEL_FILTER, null,
            listOf("commentFilterPageNative", "commentFilterPageJson", "commentFilterFloorNative", "commentFilterFloorJson", "commentFilterProtocol")),
        RequiredRule(HookFeatureKey.COMMENT_SHORTCUT, "CommentShortcut", listOf("commentShortcutTargets")),
        RequiredRule(HookFeatureKey.DEFAULT_LZL_EARLIEST, "DefaultLzlEarliestHook", listOf("lzlDefaultSortSetter")),
        RequiredRule(HookFeatureKey.HIDE_PB_TOP_ENTER_BAR, "PbTopEnterBarBlockHook",
            listOf("pbTopEnterBarControllerClass", "pbTopEnterBarInitializeMethod", "pbTopEnterBarContainerField", "pbTopEnterBarNavigationBarField")),
        RequiredRule(HookFeatureKey.FIXED_HOME_TOP_TABS, "HomeTabHook.Fixed",
            listOf("homeTabClass", "homeTabRebuildMethod", "homeTabListField", "homeTabParseMethod",
                "homeTabItemTypeField", "homeTabItemCodeField", "homeTabItemNameField", "homeTabItemUrlField")),
    )

    @Test fun requiredFactsGateCapabilitiesAndDiagnosticPoints() {
        for (rule in requiredRules) {
            val ready = textSymbols(rule.keys)
            assertEquals(rule.feature, HookFeatureState.FULL, status(ready, rule.feature).state)
            rule.point?.let { assertEquals(it, HookPointState.FOUND, point(ready, it).state) }
            for (missing in rule.keys) {
                val incomplete = textSymbols(rule.keys, setOf(missing))
                val state = status(incomplete, rule.feature)
                assertEquals("${rule.feature}: $missing", HookFeatureState.DISABLED, state.state)
                assertEquals(listOf(missing), state.missingCritical)
                rule.point?.let { assertEquals(listOf(missing), point(incomplete, it).missing) }
            }
        }
    }

    @Test fun structureDeterminesMineCapabilityAndOptionalForumSourceIsNotRequired() {
        val mine = requiredRules.single { it.feature == HookFeatureKey.BLOCK_AD_MINE_TAB_WEB }
        val oldVersion = textSymbols(mine.keys) { put("scanTargetVersionCode", 1L) }
        assertEquals(HookFeatureState.FULL, status(oldVersion, mine.feature).state)
        assertFalse(point(oldVersion, mine.point!!).target.contains("minVersion"))
        val sourceOnly = textSymbols(listOf("enterForumInitInfoDataClass", "enterForumInitInfoGetUrlMethod"))
        assertEquals(HookFeatureState.DISABLED, status(sourceOnly, HookFeatureKey.FILTER_ENTER_FORUM_WEB).state)
    }

    @Test fun missingHintKeepsRecommendationHidingAvailable() {
        val home = listOf("searchBoxViewClass", "homeSearchBoxOwnerClass", "homeSearchBoxInitMethod", "homeSearchBoxGetterMethod")
        val symbols = textSymbols(home)
        assertEquals(HookFeatureState.FULL, status(symbols, HookFeatureKey.HIDE_RECOMMEND_SEARCH_BOX).state)
        assertEquals(HookFeatureState.DISABLED, status(symbols, HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT).state)
    }

    @Test fun stableLoggingAndOptionalPointsDoNotDisableTheirConsumers() {
        val logging = status(buildHookSymbols {}, HookFeatureKey.DETAILED_LOGGING)
        assertEquals(HookFeatureState.PARTIAL, logging.state)
        assertTrue(logging.missingCritical.isEmpty())
        assertTrue(logging.missingOptional.containsAll(listOf(
            "ReplyServerResponseLogHook", "AgreeServerResponseLogHook", "FeedInfoLogHook.Bind")))
        val ad = buildHookSymbols {
            this[PbAdRequestContract.pbAdBidCommonRequestModelClass] = "fixture.Request"
            this[PbAdRequestContract.pbAdBidCommonRequestStartMethods] = listOf("start")
            this[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod] = "notify"
        }
        val optional = point(ad, "PbAdRequestBlockHook.AdBid.PageBrowser")
        assertEquals(HookPointState.OPTIONAL, optional.state)
        assertFalse(optional.isUnavailable())
        assertFalse(status(ad, HookFeatureKey.BLOCK_AD_POST_PAGE).missingOptional.contains(optional.name))
    }

    @Test fun diagnosticsKeepAvailabilitySemanticsAndAbsentCacheLine() {
        val optional = HookPointStatus("OptionalPoint", HookPointState.OPTIONAL,
            missing = listOf("optionalField"), target = "target")
        assertEquals("HookPoint[OptionalPoint] state=OPTIONAL missing=optionalField target=target", optional.formatLine())
        assertFalse(optional.isUnavailable())
        assertTrue(optional.copy(state = HookPointState.PARTIAL).isUnavailable())
        assertTrue(optional.copy(state = HookPointState.ERROR).isUnavailable())
        assertEquals(listOf("HookPoint[SymbolCache] state=MISSING missing=symbols target=-"),
            HookSymbolStatusFormatter.formatHookPointStatusLines(null))
    }

    @Test fun missingRecyclerAdapterRetainsTheOtherPostAdPathAndPartialParent() {
        val symbols = textSymbols(listOf("typeAdapterSetDataMethod", "typeAdapterDataItemClass", "typeAdapterDataGetTypeMethod"))
        assertEquals(HookPointState.FOUND, point(symbols, "PostAdHook.DataFilter.TypeAdapter").state)
        assertEquals(HookPointState.MISSING, point(symbols, "PostAdHook.DataFilter.RecyclerViewTypeAdapter").state)
        assertEquals(HookFeatureState.PARTIAL, status(symbols, HookFeatureKey.BLOCK_AD_POST_PAGE).state)
        assertTrue(status(symbols, HookFeatureKey.BLOCK_AD_POST_PAGE).missingOptional.contains("recyclerViewTypeAdapterSetDataMethod"))
        assertEquals(HookFeatureState.PARTIAL, status(symbols, HookFeatureKey.BLOCK_AD).state)
    }

    @Test fun freeCopyNeedsOneCompleteRenderRouteInsteadOfEveryRoute() {
        val common = listOf("freeCopyPostDataClass", "freeCopyPostCopyMethodSpec",
            "freeCopyPostParseMethodSpec", "freeCopyPostFloorMethodSpec")
        val web = listOf("freeCopyWebViewBindMethodSpec", "freeCopyWebViewGetterMethodSpec", "freeCopyInnerWebViewGetterMethodSpec",
            "freeCopyWebViewPageDataGetterMethodSpec", "freeCopyWebViewFirstFloorPostGetterMethodSpec")
        val missing = status(textSymbols(common), HookFeatureKey.FREE_COPY_POST_LONG_PRESS)
        assertEquals(HookFeatureState.DISABLED, missing.state)
        assertTrue(missing.missingCritical.containsAll(listOf("freeCopyPostLongPressMethodSpecs", "freeCopyWebViewBindMethodSpec")))
        val supported = status(textSymbols(common + web), HookFeatureKey.FREE_COPY_POST_LONG_PRESS)
        assertEquals(HookFeatureState.PARTIAL, supported.state)
        assertTrue(supported.missingCritical.isEmpty())
        assertTrue(supported.missingOptional.contains("freeCopyPostLongPressMethodSpecs"))
    }

    @Test fun defaultPopupRoutesRemainIndependent() {
        val like = DefaultPopupSymbols(firstLikeResponseClass = "fixture.Like", firstLikeToastMethod = "parse")
        val guide = DefaultPopupSymbols(firstLikeResponseClass = "fixture.Like",
            notificationGuideClass = "fixture.Guide", notificationGuideMethod = "show")
        assertTrue(DefaultPopupStatus.features(like).getValue(HookFeatureKey.BLOCK_FIRST_LIKE_POPUP).isSupported())
        assertEquals(listOf("guideClass", "tryShow"), DefaultPopupStatus.features(like)
            .getValue(HookFeatureKey.BLOCK_NOTIFICATION_GUIDE).missingCritical)
        assertTrue(DefaultPopupStatus.features(guide).getValue(HookFeatureKey.BLOCK_NOTIFICATION_GUIDE).isSupported())
        assertEquals(listOf("toastParser"), DefaultPopupStatus.features(guide)
            .getValue(HookFeatureKey.BLOCK_FIRST_LIKE_POPUP).missingCritical)
    }

    private fun status(symbols: HookSymbols, key: String) = HookFeatureStatusDeriver.derive(symbols).getValue(key)
    private fun point(symbols: HookSymbols, name: String) = HookSymbolStatusFormatter.collectHookPointStatuses(symbols)
        .single { it.name == name }
}
