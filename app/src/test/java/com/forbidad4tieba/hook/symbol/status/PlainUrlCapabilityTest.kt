package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlainUrlCapabilityTest {
    @Test fun retiredContainerFieldsCannotEnableBrowserNavigation() {
        val json = JSONObject(buildHookSymbols {}.toJson())
            .put("plainUrlWebContainerActivityClass", "retired.Activity")
            .put("plainUrlWebContainerInitDataMethod", "initData")
        val restored = requireNotNull(HookSymbols.fromJson(json.toString()))
        assertEquals(HookFeatureState.DISABLED,
            HookFeatureStatusDeriver.derive(restored).getValue(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER).state)
        assertFalse(JSONObject(restored.toJson()).has("plainUrlWebContainerActivityClass"))
    }

    @Test fun installedNavigationCapabilitiesDoNotDependOnARetiredContainer() {
        val symbols = buildHookSymbols {
            plainUrlBrowserHelperClass = "browser.Helper"
            plainUrlBrowserHelperStartWebActivityMethod = "open"
            plainUrlMessageManagerClass = "message.Manager"
            plainUrlMessageDispatchMethod = "dispatch"
            plainUrlResponsedMessageClass = "message.Response"
            plainUrlResponsedMessageGetCmdMethod = "getCmd"
            plainUrlCustomResponsedMessageClass = "message.Custom"
            plainUrlCustomResponsedMessageGetDataMethod = "getData"
            plainUrlApplicationClass = "application.App"
            plainUrlApplicationGetInstMethod = "getInst"
            mountCardLinkLayoutClass = "card.Link"
            mountCardLinkLayoutOnClickMethod = "onClick"
            mountCardLinkLayoutDataField = "data"
            mountCardLinkInfoDataClass = "card.Data"
            mountCardLinkInfoGetUrlMethod = "getUrl"
        }
        val state = HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER)
        assertEquals(HookFeatureState.FULL, state.state)
        assertTrue(state.missingCritical.isEmpty())
    }
}
