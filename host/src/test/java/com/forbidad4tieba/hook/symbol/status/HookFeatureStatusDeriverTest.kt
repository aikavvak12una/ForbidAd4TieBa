package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookFeatureStatusDeriverTest {
    @Test
    fun inputMemeBarFeatureRequiresBothRuntimeSymbols() {
        val missing = HookFeatureStatusDeriver.derive(buildHookSymbols {})
            .getValue(HookFeatureKey.HIDE_INPUT_MEME_BAR)
        val readySymbols = buildHookSymbols {
            this[InputMemeBarContract.inputMemeBarControllerClass] = "com.tieba.SpriteMemePanController"
            this[InputMemeBarContract.inputMemeBarEnableMethod] = "enabled"
        }
        val ready = HookFeatureStatusDeriver.derive(readySymbols)
            .getValue(HookFeatureKey.HIDE_INPUT_MEME_BAR)
        val hookPoint = HookSymbolStatusFormatter.collectHookPointStatuses(
            symbols = readySymbols).single { it.name == "InputMemeBarBlockHook" }

        assertEquals(HookFeatureState.DISABLED, missing.state)
        assertEquals(HookFeatureState.FULL, ready.state)
        assertEquals(HookPointState.FOUND, hookPoint.state)
    }

    @Test
    fun detailedLoggingRemainsAvailableThroughStableHostLogger() {
        val status = HookFeatureStatusDeriver.derive(buildHookSymbols {})
            .getValue(HookFeatureKey.DETAILED_LOGGING)

        assertEquals(HookFeatureState.PARTIAL, status.state)
        assertTrue(status.missingCritical.isEmpty())
        assertTrue(status.missingOptional.contains("ReplyServerResponseLogHook"))
        assertTrue(status.missingOptional.contains("AgreeServerResponseLogHook"))
        assertTrue(status.missingOptional.contains("FeedInfoLogHook.Bind"))
    }

    @Test
    fun hookPointStatusKeepsCompatibleLogFormatAndAvailabilitySemantics() {
        val optional = HookPointStatus(
            name = "OptionalPoint",
            state = HookPointState.OPTIONAL,
            missing = listOf("optionalField"),
            target = "target",
        )
        val partial = optional.copy(state = HookPointState.PARTIAL)
        val error = optional.copy(state = HookPointState.ERROR)

        assertEquals(
            "HookPoint[OptionalPoint] state=OPTIONAL missing=optionalField target=target",
            optional.formatLine(),
        )
        assertFalse(optional.isUnavailable())
        assertTrue(partial.isUnavailable())
        assertTrue(error.isUnavailable())
    }

    @Test
    fun optionalHookPointDoesNotDisableFeatureOrEnterUnavailableStatus() {
        val symbols = buildHookSymbols {
            this[PbAdRequestContract.pbAdBidCommonRequestModelClass] = "com.tieba.CommonRequest"
            this[PbAdRequestContract.pbAdBidCommonRequestStartMethods] = listOf("start")
            this[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod] = "notify"
        }
        val pageBrowserStatus = HookSymbolStatusFormatter.collectHookPointStatuses(
            symbols = symbols).single { it.name == "PbAdRequestBlockHook.AdBid.PageBrowser" }

        assertEquals(HookPointState.OPTIONAL, pageBrowserStatus.state)
        assertFalse(pageBrowserStatus.isUnavailable())

        val featureStatus = HookFeatureStatusDeriver.derive(symbols)
            .getValue(HookFeatureKey.BLOCK_AD_POST_PAGE)
        assertTrue(featureStatus.missingOptional.none { it == pageBrowserStatus.name })
    }

    @Test
    fun missingSymbolCacheKeepsExistingDiagnosticLine() {
        val lines = HookSymbolStatusFormatter.formatHookPointStatusLines(
            symbols = null)

        assertEquals(
            listOf("HookPoint[SymbolCache] state=MISSING missing=symbols target=-"),
            lines,
        )
    }

    @Test
    fun postAdDataFilterReportsBothAdapterPaths() {
        val symbols = buildHookSymbols {
            this[PostAdDataContract.typeAdapterSetDataMethod] = "replaceItems"
            this[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod] = "setData"
            this[PostAdDataContract.typeAdapterDataItemClass] = "com.tieba.PostItem"
            this[PostAdDataContract.typeAdapterDataGetTypeMethod] = "getType"
        }
        val statuses = HookSymbolStatusFormatter.collectHookPointStatuses(
            symbols = symbols)

        assertEquals(
            HookPointState.FOUND,
            statuses.single { it.name == "PostAdHook.DataFilter" }.state,
        )
        assertEquals(
            HookPointState.FOUND,
            statuses.single { it.name == "PostAdHook.DataFilter.TypeAdapter" }.state,
        )
        assertEquals(
            HookPointState.FOUND,
            statuses.single {
                it.name == "PostAdHook.DataFilter.RecyclerViewTypeAdapter"
            }.state,
        )
    }

    @Test
    fun postAdFeatureIsPartialWhenRecyclerAdapterPathIsMissing() {
        val symbols = buildHookSymbols {
            this[PostAdDataContract.typeAdapterSetDataMethod] = "replaceItems"
            this[PostAdDataContract.typeAdapterDataItemClass] = "com.tieba.PostItem"
            this[PostAdDataContract.typeAdapterDataGetTypeMethod] = "getType"
        }

        val featureStatus = HookFeatureStatusDeriver.derive(symbols)
            .getValue(HookFeatureKey.BLOCK_AD_POST_PAGE)
        val recyclerStatus = HookSymbolStatusFormatter.collectHookPointStatuses(
            symbols = symbols).single { it.name == "PostAdHook.DataFilter.RecyclerViewTypeAdapter" }

        assertEquals(HookFeatureState.PARTIAL, featureStatus.state)
        assertTrue(
            featureStatus.missingOptional.contains("recyclerViewTypeAdapterSetDataMethod"),
        )
        assertEquals(HookPointState.MISSING, recyclerStatus.state)
    }

    @Test
    fun firstFloorRecommendInsertReportsIndependentHookPoint() {
        val symbols = buildHookSymbols {
            this[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertClass] = "com.tieba.LegacyHeader"
            this[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertMethod] = "insertRecommend"
        }
        val status = HookSymbolStatusFormatter.collectHookPointStatuses(
            symbols = symbols).single { it.name == "PbFirstFloorRecommendBlockHook" }

        assertEquals(HookPointState.FOUND, status.state)
    }

    @Test
    fun deriveDisablesAutoRefreshWhenTriggerMethodIsMissing() {
        val status = HookFeatureStatusDeriver.derive(buildHookSymbols {})
            .getValue(HookFeatureKey.DISABLE_AUTO_REFRESH)

        assertEquals(HookFeatureState.DISABLED, status.state)
        assertEquals(
            listOf(
                "autoRefreshTriggerMethod",
                "autoRefreshNetRequestMethod",
                "autoRefreshCacheRestoreMethod",
            ),
            status.missingCritical,
        )
    }

    @Test
    fun deriveMarksAutoRefreshFullWhenAllTargetsExist() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                this[AutoRefreshContract.autoRefreshTriggerMethod] = "com.tieba.Feed#triggerRefresh"
                this[AutoRefreshContract.autoRefreshNetRequestMethod] = "com.tieba.FeedModel#sendRefreshRequest"
                this[AutoRefreshContract.autoRefreshCacheRestoreMethod] = "isTaskBlocked"
                this[AutoRefreshContract.autoRefreshPullGestureMethod] = "releaseGesture"
            },
        ).getValue(HookFeatureKey.DISABLE_AUTO_REFRESH)

        assertEquals(HookFeatureState.FULL, status.state)
        assertTrue(status.missingCritical.isEmpty())
        assertTrue(status.missingOptional.isEmpty())
    }

    @Test
    fun deriveDisablesFreeCopyCommentInjectionWhenAnyRequiredTargetIsMissing() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                this[FreeCopyContract.freeCopyPopupMenuClass] = "com.tieba.Popup"
                this[FreeCopyContract.freeCopyPopupContentViewMethod] = "contentView"
            },
        ).getValue(HookFeatureKey.FREE_COPY_COMMENT_INJECTION)

        assertEquals(HookFeatureState.DISABLED, status.state)
        assertEquals(listOf("freeCopyPopupTextField"), status.missingCritical)
    }

    @Test
    fun deriveMarksFreeCopyCommentInjectionFullWhenAllRequiredTargetsExist() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                this[FreeCopyContract.freeCopyPopupMenuClass] = "com.tieba.Popup"
                this[FreeCopyContract.freeCopyPopupContentViewMethod] = "contentView"
                this[FreeCopyContract.freeCopyPopupTextField] = "text"
            },
        ).getValue(HookFeatureKey.FREE_COPY_COMMENT_INJECTION)

        assertEquals(HookFeatureState.FULL, status.state)
    }

    @Test
    fun deriveKeepsFreeCopyLongPressSupportedWhenOnlyWebViewPathExists() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                this[FreeCopyContract.freeCopyPostDataClass] = "com.tieba.PostData"
                this[FreeCopyContract.freeCopyPostCopyMethodSpec] = "copy|void|"
                this[FreeCopyContract.freeCopyPostParseMethodSpec] = "parse|void|com.tieba.Protocol"
                this[FreeCopyContract.freeCopyPostFloorMethodSpec] = "floor|int|"
                this[FreeCopyContract.freeCopyWebViewBindMethodSpec] = "bind|void|com.tieba.PageData"
                this[FreeCopyContract.freeCopyWebViewGetterMethodSpec] = "webView|com.tieba.TbWebView|"
                this[FreeCopyContract.freeCopyInnerWebViewGetterMethodSpec] = "inner|android.webkit.WebView|"
                this[FreeCopyContract.freeCopyWebViewPageDataGetterMethodSpec] = "pageData|com.tieba.AggregateData|"
                this[FreeCopyContract.freeCopyWebViewFirstFloorPostGetterMethodSpec] =
                    "firstFloor|com.tieba.PostData|"
            },
        ).getValue(HookFeatureKey.FREE_COPY_POST_LONG_PRESS)

        assertEquals(HookFeatureState.PARTIAL, status.state)
        assertTrue(status.missingCritical.isEmpty())
        assertTrue(status.missingOptional.contains("freeCopyPostLongPressMethodSpecs"))
        assertTrue(status.missingOptional.contains("freeCopyRichTextViewClass"))
    }

    @Test
    fun deriveDisablesFreeCopyLongPressWhenBothRenderPathsAreIncomplete() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                this[FreeCopyContract.freeCopyPostDataClass] = "com.tieba.PostData"
                this[FreeCopyContract.freeCopyPostCopyMethodSpec] = "copy|void|"
                this[FreeCopyContract.freeCopyPostParseMethodSpec] = "parse|void|com.tieba.Protocol"
                this[FreeCopyContract.freeCopyPostFloorMethodSpec] = "floor|int|"
            },
        ).getValue(HookFeatureKey.FREE_COPY_POST_LONG_PRESS)

        assertEquals(HookFeatureState.DISABLED, status.state)
        assertTrue(status.missingCritical.contains("freeCopyPostLongPressMethodSpecs"))
        assertTrue(status.missingCritical.contains("freeCopyWebViewBindMethodSpec"))
    }

    @Test
    fun deriveDisablesForumTopShiftBlockWhenBottomSheetSymbolsAreMissing() {
        val status = HookFeatureStatusDeriver.derive(buildHookSymbols {})
            .getValue(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT)

        assertEquals(HookFeatureState.DISABLED, status.state)
        assertEquals(
            listOf(
                "forumBottomSheetViewClass",
                "forumBottomSheetInitScrollMethod",
            ),
            status.missingCritical,
        )
    }

    @Test
    fun deriveMarksForumTopShiftBlockFullWhenBottomSheetSymbolsExist() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                this[ForumTopShiftContract.forumBottomSheetViewClass] =
                    StableTiebaHookPoints.FORUM_BOTTOM_SHEET_VIEW_CLASS
                this[ForumTopShiftContract.forumBottomSheetInitScrollMethod] = "d0"
            },
        ).getValue(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT)

        assertEquals(HookFeatureState.FULL, status.state)
        assertTrue(status.missingCritical.isEmpty())
    }

    @Test
    fun deriveMarksHomeNativeGlassPartialWhenOnlyOptionalSymbolsAreMissing() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                this[FeedContract.feedCardBindMethod] = "com.tieba.FeedCard#bind"
                this[FeedContract.feedCardBindMethodSpec] = "com.tieba.FeedCard#bind|void|com.tieba.CardData"
                this[HomeAnchorsContract.homePersonalizeAnchorClasses] = listOf(
                    StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS,
                )
            },
        ).getValue(HookFeatureKey.HOME_NATIVE_GLASS)

        assertEquals(HookFeatureState.PARTIAL, status.state)
        assertTrue(status.missingCritical.isEmpty())
        assertTrue(status.missingOptional.contains("homeNativeGlassSubPbNextPageMoreViewId"))
    }

    @Test
    fun deriveDisablesSearchBoxAdChildWhenItsSymbolsAreMissing() {
        val status = HookFeatureStatusDeriver.derive(buildHookSymbols {})
            .getValue(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT)

        assertEquals(HookFeatureState.DISABLED, status.state)
        assertTrue(status.missingCritical.contains("searchBoxViewClass"))
    }

    @Test
    fun deriveKeepsAdParentPartialWhenOnlySomeAdChildrenAreAvailable() {
        val statuses = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                this[FeedContract.feedTemplateKeyMethod] = "getTemplateKey"
            },
        )

        assertEquals(HookFeatureState.PARTIAL, statuses.getValue(HookFeatureKey.BLOCK_AD_FEED).state)
        assertEquals(
            HookFeatureState.DISABLED,
            statuses.getValue(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT).state,
        )
        assertEquals(HookFeatureState.PARTIAL, statuses.getValue(HookFeatureKey.BLOCK_AD).state)
    }

    @Test
    fun deriveDisablesHomeBottomEasterEggAdWhenParserSymbolsAreMissing() {
        val missing = HookFeatureStatusDeriver.derive(buildHookSymbols {})
            .getValue(HookFeatureKey.BLOCK_AD_HOME_BOTTOM_EASTER_EGG)
        assertEquals(HookFeatureState.DISABLED, missing.state)
        assertTrue(missing.missingCritical.contains("homeBottomEasterEggParserClass"))

        val ready = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                this[HomeBottomEasterEggContract.homeBottomEasterEggParserClass] = "com.tieba.EasterEggParser"
                this[HomeBottomEasterEggContract.homeBottomEasterEggParserMethod] = "parseJson"
            },
        ).getValue(HookFeatureKey.BLOCK_AD_HOME_BOTTOM_EASTER_EGG)
        assertEquals(HookFeatureState.FULL, ready.state)
    }
}
