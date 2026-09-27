package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MineTabCapabilityTest {
    @Test fun verifiedSymbolsDetermineCapabilityWithoutHistoricalVersionGate() {
        for (version in listOf(null, 1L, 369491967L, 369885440L, 400000000L)) {
            val symbols = symbols(version)
            val feature = HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.BLOCK_AD_MINE_TAB_WEB)
            assertEquals(HookFeatureState.FULL, feature.state)
            assertEquals(HookPointState.FOUND, point(symbols).state)
            assertFalse(point(symbols).target.contains("minVersion"))
        }
    }

    @Test fun eachMissingDependencyStillDisablesCapabilityAndReportsTheSameMissingSymbol() {
        for (missing in listOf("mineTabWebViewClass", "mineTabWebLoadUrlMethod", "mineTabWebGetUrlMethod",
            "mineTabWebGetInnerWebViewMethod")) {
            val symbols = symbols(369885440L, missing)
            val feature = HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.BLOCK_AD_MINE_TAB_WEB)
            assertEquals(HookFeatureState.DISABLED, feature.state)
            assertEquals(listOf(missing), feature.missingCritical)
            assertEquals(HookPointState.MISSING, point(symbols).state)
            assertEquals(listOf(missing), point(symbols).missing)
        }
    }

    private fun point(symbols: HookSymbols) = HookSymbolStatusFormatter.collectHookPointStatuses(
        symbols).single { it.name == "MineTabWebBlockHook" }

    private fun symbols(version: Long?, missing: String? = null) = buildHookSymbols {
        scanTargetVersionCode = version
        if (missing != "mineTabWebViewClass") this[MineTabWebContract.mineTabWebViewClass] = "verified.WebView"
        if (missing != "mineTabWebLoadUrlMethod") this[MineTabWebContract.mineTabWebLoadUrlMethod] = "loadUrl"
        if (missing != "mineTabWebGetUrlMethod") this[MineTabWebContract.mineTabWebGetUrlMethod] = "getUrl"
        if (missing != "mineTabWebGetInnerWebViewMethod") this[MineTabWebContract.mineTabWebGetInnerWebViewMethod] = "getInnerWebView"
    }
}
