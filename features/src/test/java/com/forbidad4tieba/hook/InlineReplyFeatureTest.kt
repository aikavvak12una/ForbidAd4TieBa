package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.ui.UiText
import org.junit.Assert.*
import org.junit.Test

class InlineReplyFeatureTest {
    @Test fun defaultFeatureRequiresHostCapabilityButNoLevelFilterOrIndependentToggle() {
        val feature = FeatureCatalog.definitions.single { it.id == "InlineReplyRepair" }
        val symbols = requireNotNull(HookSymbols.fromJson("{\"inlineReplyRepairTargets\":\"fixture\"}"))
        val settings = SettingsSnapshot.bootstrap()
        assertNull(feature.toggle)
        assertFalse(settings.commentLevelFilter.enabled)
        assertEquals(listOf("InlineReplyRepair"), feature.entries(HookInstallContext("com.baidu.tieba", symbols), settings).map { it.id })
        assertTrue(feature.entries(HookInstallContext("com.baidu.tieba", HookSymbols.unsupported()), settings).isEmpty())
        assertTrue(feature.entries(HookInstallContext("com.android.systemui", symbols), settings).isEmpty())
        assertTrue(UiText.Settings.DEFAULT_ENABLED_FEATURES.contains(UiText.Settings.INLINE_REPLY_REPAIR_LABEL))
    }
}
