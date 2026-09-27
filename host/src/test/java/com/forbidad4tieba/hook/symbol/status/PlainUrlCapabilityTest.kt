package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.contract.*

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
            this[PlainUrlContract.plainUrlBrowserHelperClass] = "browser.Helper"
            this[PlainUrlContract.plainUrlBrowserHelperStartWebActivityMethod] = "open"
            this[PlainUrlContract.plainUrlMessageManagerClass] = "message.Manager"
            this[PlainUrlContract.plainUrlMessageDispatchMethod] = "dispatch"
            this[PlainUrlContract.plainUrlResponsedMessageClass] = "message.Response"
            this[PlainUrlContract.plainUrlResponsedMessageGetCmdMethod] = "getCmd"
            this[PlainUrlContract.plainUrlCustomResponsedMessageClass] = "message.Custom"
            this[PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod] = "getData"
            this[PlainUrlContract.plainUrlApplicationClass] = "application.App"
            this[PlainUrlContract.plainUrlApplicationGetInstMethod] = "getInst"
            this[MountCardContract.mountCardLinkLayoutClass] = "card.Link"
            this[MountCardContract.mountCardLinkLayoutOnClickMethod] = "onClick"
            this[MountCardContract.mountCardLinkLayoutDataField] = "data"
            this[MountCardContract.mountCardLinkInfoDataClass] = "card.Data"
            this[MountCardContract.mountCardLinkInfoGetUrlMethod] = "getUrl"
        }
        val state = HookFeatureStatusDeriver.derive(symbols).getValue(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER)
        assertEquals(HookFeatureState.FULL, state.state)
        assertTrue(state.missingCritical.isEmpty())
    }
}
