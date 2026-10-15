package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.*
import org.junit.Test

class PostUserIdFilterSettingsTest {
    @Test fun idInputNormalizesOnePositiveIntegerWithoutLosingPrecision() {
        assertEquals("123", PostFilterPreferences.normalizeUserId(" 00123 "))
        assertEquals("9007199254740993", PostFilterPreferences.normalizeUserId("9007199254740993"))
        assertNull(PostFilterPreferences.normalizeUserId("123,456"))
        assertNull(PostFilterPreferences.normalizeUserId("123\n456"))
    }

    @Test fun invalidTokensNeverBecomePartialUidMatches() {
        for (raw in listOf(null, "", "  ", "0", "000", "-123", "+123", "123.0", "1e3", "１２３", "uid=123", "123 456", "#123#", "https://example.com/123")) {
            assertNull("raw=$raw", PostFilterPreferences.normalizeUserId(raw))
            assertTrue("raw=$raw", PostFilterPreferences.parseUserIdList(raw).isEmpty())
        }
        assertTrue(PostFilterPreferences.parseUserIdList("123,456").isEmpty())
    }

    @Test fun uidFilterDefaultsToOffAndDoesNotActivateAnEmptyList() {
        val defaults = derive(emptyMap(), ConfigManager.ScanFeatureAvailabilityState.AVAILABLE)
        assertFalse(defaults.isPostUserIdFilterEnabled)
        assertTrue(defaults.postUserIdList.isEmpty())
        assertNull(defaults.customPostRules)
        val enabledWithoutUsers = derive(mapOf(
            PostFilterPreferences.KEY_ENABLE_CUSTOM_POST_FILTER to true,
            PostFilterPreferences.KEY_FILTER_POST_USER_ID to true,
            PostFilterPreferences.KEY_FILTER_POST_USER_ID_LIST to "invalid,0",
        ), ConfigManager.ScanFeatureAvailabilityState.AVAILABLE)
        assertTrue(enabledWithoutUsers.isPostUserIdFilterEnabled)
        assertTrue(enabledWithoutUsers.postUserIdList.isEmpty())
        assertNull(enabledWithoutUsers.customPostRules)
    }

    @Test fun parentSwitchChildSwitchAndHostCapabilityGateUidRulesIndependentlyOfUnlock() {
        for (state in ConfigManager.ScanFeatureAvailabilityState.entries) {
            for (master in listOf(false, true)) for (child in listOf(false, true)) {
                for (locked in listOf(false, true)) {
                    val settings = derive(mapOf(
                        PostFilterPreferences.KEY_ENABLE_CUSTOM_POST_FILTER to master,
                        PostFilterPreferences.KEY_FILTER_POST_USER_ID to child,
                        PostFilterPreferences.KEY_FILTER_POST_USER_ID_LIST to PostUserFilterSettings.encode(listOf(
                            BlockedPostUser("123", "123000", "first"), BlockedPostUser("456", "456000", "second"))),
                    ), state, locked)
                    val enabled = master && child && state in setOf(
                        ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
                        ConfigManager.ScanFeatureAvailabilityState.PARTIAL,
                    )
                    assertEquals("$state/$master/$child/$locked", enabled, settings.isPostUserIdFilterEnabled)
                    assertEquals(if (enabled) setOf("123", "456") else emptySet<String>(), settings.postUserIdList)
                    assertEquals(enabled, settings.customPostRules?.userId == true)
                    assertEquals(enabled, settings.customPostRules?.needsFeedHeadParamsCheck == true)
                }
            }
        }
    }

    private fun derive(
        values: Map<String, Any>,
        state: ConfigManager.ScanFeatureAvailabilityState,
        locked: Boolean = true,
    ): SettingsSnapshot = EffectiveSettingsPolicy.derive(
        UserSettings(values),
        HostCapabilities(mapOf(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER to state)),
        RemoteSettingsPolicy(locked),
    ).snapshot
}
