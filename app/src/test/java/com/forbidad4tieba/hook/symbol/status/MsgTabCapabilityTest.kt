package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MsgTabCapabilityTest {
    @Test fun viewModelAloneProvidesTheWholeRuntimeContract() {
        val symbols = buildHookSymbols { msgTabLocateToTabMethod = "locateTab" }
        val feature = HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.DEFAULT_NOTIFY_TAB)
        assertEquals(HookFeatureState.FULL, feature.state)
        assertTrue(feature.missingCritical.isEmpty())
        assertTrue(feature.missingOptional.isEmpty())
        val points = messagePoints(symbols)
        assertEquals(listOf("MsgTabDefaultNotifyHook"), points.map { it.name })
        assertEquals(HookPointState.FOUND, points.single().state)
    }

    @Test fun missingViewModelStillDisablesAndReportsTheRequiredSymbol() {
        for (method in listOf(null, "", " ")) {
            val symbols = buildHookSymbols { msgTabLocateToTabMethod = method }
            val feature = HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.DEFAULT_NOTIFY_TAB)
            assertEquals(HookFeatureState.DISABLED, feature.state)
            assertEquals(listOf("msgTabLocateToTabMethod"), feature.missingCritical)
            val point = messagePoints(symbols).single()
            assertEquals(HookPointState.MISSING, point.state)
            assertEquals(listOf("msgTabLocateToTabMethod"), point.missing)
        }
    }

    private fun messagePoints(symbols: HookSymbols) = HookSymbolStatusFormatter.collectHookPointStatuses(
        symbols, "", "", StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS,
    ).filter { it.name.startsWith("MsgTabDefaultNotifyHook") }
}
