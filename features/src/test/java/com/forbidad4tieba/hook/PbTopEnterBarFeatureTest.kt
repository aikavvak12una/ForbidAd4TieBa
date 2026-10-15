package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.EffectiveSettingsPolicy
import com.forbidad4tieba.hook.config.ExtensionPreferences
import com.forbidad4tieba.hook.config.HostCapabilities
import com.forbidad4tieba.hook.config.RemoteSettingsPolicy
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.config.UserSettings
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PbTopEnterBarFeatureTest {
    private val fields = listOf("pbTopEnterBarControllerClass", "pbTopEnterBarInitializeMethod",
        "pbTopEnterBarContainerField", "pbTopEnterBarNavigationBarField")
    private val feature get() = FeatureCatalog.definitions.single { it.id == "PbTopEnterBarBlockHook" }

    @Test fun onlyAnEnabledMainProcessWithCompleteTargetsPlansTheHook() {
        fun planned(enabled: Boolean, process: String = "com.baidu.tieba", included: List<String> = fields): Boolean {
            val json = JSONObject().apply { included.forEach { put(it, "fixture") } }
            val symbols = HookSymbols.fromJson(json.toString())!!
            return feature.entries(HookInstallContext(process, symbols),
                SettingsSnapshot.bootstrap().copy(isPbTopEnterBarHidden = enabled)).isNotEmpty()
        }
        assertTrue(planned(true))
        assertFalse(planned(false))
        assertFalse(planned(true, "com.baidu.tieba:extra"))
        fields.forEach { assertFalse(planned(true, included = fields - it)) }
    }

    @Test fun defaultsToOffAndRequiresItsOwnCapabilityWithoutAnUnlockRequirement() {
        for (state in ConfigManager.ScanFeatureAvailabilityState.entries) {
            for (choice in listOf(false, true)) for (locked in listOf(false, true)) {
                val settings = EffectiveSettingsPolicy.derive(
                    UserSettings(mapOf(ExtensionPreferences.KEY_HIDE_PB_TOP_ENTER_BAR to choice)),
                    HostCapabilities(mapOf(HookFeatureKey.HIDE_PB_TOP_ENTER_BAR to state)),
                    RemoteSettingsPolicy(locked),
                ).snapshot
                val available = state == ConfigManager.ScanFeatureAvailabilityState.AVAILABLE ||
                    state == ConfigManager.ScanFeatureAvailabilityState.PARTIAL
                assertEquals("$state/$choice/$locked", choice && available, settings.isPbTopEnterBarHidden)
                assertFalse(settings.isInputMemeBarHidden)
            }
        }
        val defaults = EffectiveSettingsPolicy.derive(UserSettings(emptyMap<String, Any>()), HostCapabilities(emptyMap()),
            RemoteSettingsPolicy(false)).snapshot
        assertFalse(defaults.isPbTopEnterBarHidden)
    }
}
