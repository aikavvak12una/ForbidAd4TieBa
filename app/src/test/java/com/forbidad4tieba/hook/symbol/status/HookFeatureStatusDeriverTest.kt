package com.forbidad4tieba.hook.symbol.status

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
            pbAdBidCommonRequestModelClass = "com.tieba.CommonRequest"
            pbAdBidCommonRequestStartMethods = listOf("start")
            pbAdBidCommonRequestNotifyMethod = "notify"
        }
        val pageBrowserStatus = HookSymbolStatusFormatter.collectHookPointStatuses(
            symbols = symbols,
            aiPbAiEmojiCreationViewClass = "com.baidu.tieba.pb.view.PbAiEmojiCreationView",
            aiPbAiEmojiCreationPageBrowserViewClass =
                "com.baidu.tieba.pb.pagebrowser.CommentFloorAiEmojiCreationView",
            msgTabViewModelClass = StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS,
            msgTabContainerViewClass = "com.baidu.tieba.msg.TabContainer",
        ).single { it.name == "PbAdRequestBlockHook.AdBid.PageBrowser" }

        assertEquals(HookPointState.OPTIONAL, pageBrowserStatus.state)
        assertFalse(pageBrowserStatus.isUnavailable())

        val featureStatus = HookFeatureStatusDeriver.derive(symbols)
            .getValue(HookFeatureKey.BLOCK_AD_POST_PAGE)
        assertTrue(featureStatus.missingOptional.none { it == pageBrowserStatus.name })
    }

    @Test
    fun missingSymbolCacheKeepsExistingDiagnosticLine() {
        val lines = HookSymbolStatusFormatter.formatHookPointStatusLines(
            symbols = null,
            aiPbAiEmojiCreationViewClass = "unused",
            aiPbAiEmojiCreationPageBrowserViewClass = "unused",
            msgTabViewModelClass = "unused",
            msgTabContainerViewClass = "unused",
        )

        assertEquals(
            listOf("HookPoint[SymbolCache] state=MISSING missing=symbols target=-"),
            lines,
        )
    }

    @Test
    fun deriveDisablesAutoRefreshWhenTriggerMethodIsMissing() {
        val status = HookFeatureStatusDeriver.derive(buildHookSymbols {})
            .getValue(HookFeatureKey.DISABLE_AUTO_REFRESH)

        assertEquals(HookFeatureState.DISABLED, status.state)
        assertEquals(listOf("autoRefreshTriggerMethod"), status.missingCritical)
    }

    @Test
    fun deriveMarksAutoRefreshFullWhenTriggerMethodExists() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                autoRefreshTriggerMethod = "com.tieba.Feed#triggerRefresh"
            },
        ).getValue(HookFeatureKey.DISABLE_AUTO_REFRESH)

        assertEquals(HookFeatureState.FULL, status.state)
        assertTrue(status.missingCritical.isEmpty())
        assertTrue(status.missingOptional.isEmpty())
    }

    @Test
    fun deriveDisablesFreeCopyWhenAnyRequiredTargetIsMissing() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                freeCopyPopupMenuClass = "com.tieba.Popup"
                freeCopyPopupContentViewMethod = "contentView"
            },
        ).getValue(HookFeatureKey.FREE_COPY)

        assertEquals(HookFeatureState.DISABLED, status.state)
        assertEquals(listOf("freeCopyPopupTextField"), status.missingCritical)
    }

    @Test
    fun deriveMarksFreeCopyFullWhenAllRequiredTargetsExist() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                freeCopyPopupMenuClass = "com.tieba.Popup"
                freeCopyPopupContentViewMethod = "contentView"
                freeCopyPopupTextField = "text"
            },
        ).getValue(HookFeatureKey.FREE_COPY)

        assertEquals(HookFeatureState.FULL, status.state)
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
                forumBottomSheetViewClass =
                    StableTiebaHookPoints.FORUM_BOTTOM_SHEET_VIEW_CLASS
                forumBottomSheetInitScrollMethod = "d0"
            },
        ).getValue(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT)

        assertEquals(HookFeatureState.FULL, status.state)
        assertTrue(status.missingCritical.isEmpty())
    }

    @Test
    fun deriveMarksHomeNativeGlassPartialWhenOnlyOptionalSymbolsAreMissing() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                feedCardBindMethod = "com.tieba.FeedCard#bind"
                feedCardBindMethodSpec = "com.tieba.FeedCard#bind|void|com.tieba.CardData"
                homePersonalizeAnchorClasses = listOf(
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
                feedTemplateKeyMethod = "getTemplateKey"
            },
        )

        assertEquals(HookFeatureState.PARTIAL, statuses.getValue(HookFeatureKey.BLOCK_AD_FEED).state)
        assertEquals(
            HookFeatureState.DISABLED,
            statuses.getValue(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT).state,
        )
        assertEquals(HookFeatureState.PARTIAL, statuses.getValue(HookFeatureKey.BLOCK_AD).state)
    }
}
