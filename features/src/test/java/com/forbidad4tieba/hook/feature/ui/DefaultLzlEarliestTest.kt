package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.contract.LzlSortContract
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DefaultLzlEarliestTest {
    @Test fun onlyTheHotDefaultChangesAndDisabledSettingsPreserveEveryInput() {
        for (original in listOf(null, "0", "1", "2", "", "unknown")) {
            assertEquals(original, DefaultLzlEarliestHook.defaultSort(original, false))
            assertEquals(if (original == null || original == "2") "0" else original,
                DefaultLzlEarliestHook.defaultSort(original, true))
        }
    }

    @Test fun installationRequiresAnEnabledSettingAResolvedTargetAndTheMainProcess() {
        val found = checkNotNull(HookSymbols.fromJson(
            JSONObject().put(LzlSortContract.defaultSortSetter.cacheKey, "fixture").toString()))
        val enabled = SettingsSnapshot(isDefaultLzlEarliestEnabled = true)
        fun entries(process: String, settings: SettingsSnapshot, missing: Boolean = false) =
            DefaultLzlEarliestFeature.entries(HookInstallContext(process,
                if (missing) HookSymbols.unsupported() else found), settings)
        assertEquals(1, entries("com.baidu.tieba", enabled).size)
        assertTrue(entries("com.baidu.tieba", SettingsSnapshot()).isEmpty())
        assertTrue(entries("com.baidu.tieba", enabled, missing = true).isEmpty())
        assertTrue(entries("com.baidu.tieba:remote", enabled).isEmpty())
        assertTrue(entries("com.android.systemui", enabled).isEmpty())
    }
}
