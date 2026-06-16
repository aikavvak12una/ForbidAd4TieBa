package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HookFeatureStatusDeriverTest {
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
    fun deriveMarksHomeNativeGlassPartialWhenOnlyOptionalSymbolsAreMissing() {
        val status = HookFeatureStatusDeriver.derive(
            buildHookSymbols {
                feedCardBindMethod = "com.tieba.FeedCard#bind"
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
