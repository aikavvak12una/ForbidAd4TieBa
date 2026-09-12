package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.DefaultPopupSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPopupStatusTest {
    private val first = DefaultPopupSymbols(
        firstLikeResponseClass = "host.LikeResponse",
        firstLikeToastMethod = "parseToast",
    )

    @Test
    fun unavailableNotificationDoesNotDisableFirstLikeBlock() {
        val features = DefaultPopupStatus.features(first)
        assertTrue(features.getValue(HookFeatureKey.BLOCK_FIRST_LIKE_POPUP).isSupported())
        assertFalse(features.getValue(HookFeatureKey.BLOCK_NOTIFICATION_GUIDE).isSupported())
        assertEquals(listOf("guideClass", "tryShow"),
            features.getValue(HookFeatureKey.BLOCK_NOTIFICATION_GUIDE).missingCritical)
    }

    @Test
    fun incompleteFirstLikeDoesNotDisableNotificationBlock() {
        val symbols = DefaultPopupSymbols(
            firstLikeResponseClass = "host.LikeResponse",
            notificationGuideClass = "host.PushGuide",
            notificationGuideMethod = "tryShow",
        )
        val features = DefaultPopupStatus.features(symbols)
        assertFalse(features.getValue(HookFeatureKey.BLOCK_FIRST_LIKE_POPUP).isSupported())
        assertTrue(features.getValue(HookFeatureKey.BLOCK_NOTIFICATION_GUIDE).isSupported())
        assertEquals(listOf("toastParser"), features.getValue(HookFeatureKey.BLOCK_FIRST_LIKE_POPUP).missingCritical)
    }

    @Test
    fun mainCacheRoundTripPreservesBothTargets() {
        val targets = first.copy(notificationGuideClass = "host.PushGuide", notificationGuideMethod = "tryShow")
        val symbols = buildHookSymbols { defaultPopups = targets }
        assertEquals(targets, HookSymbols.fromJson(symbols.toJson())?.defaultPopups)
    }

    @Test
    fun absentCacheFieldsStayUnavailable() {
        assertEquals(DefaultPopupSymbols(), DefaultPopupSymbols.fromJson(null))
        assertTrue(DefaultPopupStatus.hookPoints(DefaultPopupSymbols()).all { it.state == HookPointState.MISSING })
    }
}
