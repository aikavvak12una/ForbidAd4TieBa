package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.*
import org.junit.Test

class CommentFilterSettingsTest {
    @Test fun defaultsBoundsCapabilityAndSavedExceptionAreExplicit() {
        fun derive(values: Map<String, Any>, available: Boolean = true): CommentLevelFilterSettings {
            val states = if (available) mapOf(HookFeatureKey.COMMENT_LEVEL_FILTER to ConfigManager.ScanFeatureAvailabilityState.AVAILABLE) else emptyMap()
            return EffectiveSettingsPolicy.derive(UserSettings(values), HostCapabilities(states), RemoteSettingsPolicy(true)).snapshot.commentLevelFilter
        }
        assertEquals(CommentLevelFilterSettings(false, 5, false, false), derive(emptyMap()))
        val values = mapOf(CommentFilterPreferences.KEY_ENABLE to true, CommentFilterPreferences.KEY_MINIMUM_LEVEL to 11,
            CommentFilterPreferences.KEY_KEEP_WITH_REPLIES to true, CommentFilterPreferences.KEY_SKIP_NESTED to true)
        assertEquals(CommentLevelFilterSettings(true, 11, true, true), derive(values))
        assertFalse(derive(values, false).enabled)
        assertEquals(1, derive(values + (CommentFilterPreferences.KEY_MINIMUM_LEVEL to -1)).minimumLevel)
        assertEquals(18, derive(values + (CommentFilterPreferences.KEY_MINIMUM_LEVEL to Int.MAX_VALUE)).minimumLevel)
        assertEquals(CommentLevelFilterSettings(false, 5, false, false), SettingsSnapshot.bootstrap().commentLevelFilter)
    }
}
