package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsCatalog
import com.forbidad4tieba.hook.config.SimpleToggle
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.ui.SettingsMenuGroupActions
import com.forbidad4tieba.hook.ui.SettingsMenuGroupBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleToggleIntegrationTest {
    @Test
    fun everyIndependentToggleHasOneMenuRowInstallerAndCapabilityOwner() {
        val declared = SimpleToggle.entries
        assertEquals(declared.size, declared.map { it.prefKey }.distinct().size)
        val bound = FeatureCatalog.definitions.mapNotNull { it.toggle }
        assertEquals(declared.toSet(), bound.toSet())
        assertEquals(declared.size, bound.size)

        val menu = SettingsMenuGroupBuilder.build(false, noActions).flatMap { it.items }
        declared.forEach { toggle ->
            val row = menu.single { it.prefKey == toggle.prefKey }
            assertTrue(toggle.capabilityKey in HookFeatureKey.orderedKeys)
            assertEquals(toggle.capabilityKey, SettingsCatalog.capabilityFor(row.prefKey))
        }
    }

    private val noActions = SettingsMenuGroupActions(
        onAdBlock = {},
        onCustomPostFilter = {},
        onCustomPostModelScore = {},
        onCustomPostFilterKeyword = {},
        onCustomPostFilterUserId = {},
        onCommentLevelFilter = {},
        onPbLikeAutoReply = {},
        onFreeCopy = {},
        onPerformanceOptimization = {},
        onAutoSignIn = {},
        onReplyVisibilityProbe = {},
        onDetailedLogSave = {},
        onTabCustomization = {},
        onHomeTopTab = {},
        onHomeNativeGlass = {},
        onBottomTab = {},
        onBottomTabLiquidGlass = {},
    )
}
