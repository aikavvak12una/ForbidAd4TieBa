package com.forbidad4tieba.hook.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigManagerTest {
    @Test
    fun parseModelScoreThresholdsAcceptsSupportedSeparatorsAndKeepsLastDuplicateValue() {
        val thresholds = ConfigManager.parseModelScoreThresholds(
            """
            alpha=0.25
            beta:1.5; alpha = 0.75
            """.trimIndent(),
        )

        assertEquals(listOf("alpha", "beta"), thresholds.map { it.key })
        assertEquals(0.75, thresholds[0].threshold, 0.0)
        assertEquals(1.5, thresholds[1].threshold, 0.0)
    }

    @Test
    fun parseModelScoreThresholdsIgnoresInvalidValuesAndBlankKeys() {
        val thresholds = ConfigManager.parseModelScoreThresholds(
            """
            =0.1
            negative=-1
            nan=NaN
            infinite=Infinity
            bad=text
            ok=0
            spaced : 2.5
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ConfigManager.ModelScoreThreshold("ok", 0.0),
                ConfigManager.ModelScoreThreshold("spaced", 2.5),
            ),
            thresholds,
        )
    }

    @Test
    fun parseModelScoreThresholdsReturnsEmptyListForBlankInput() {
        assertTrue(ConfigManager.parseModelScoreThresholds(null).isEmpty())
        assertTrue(ConfigManager.parseModelScoreThresholds("   ").isEmpty())
    }

    @Test
    fun nonScanPreferencesAreAvailableWithoutScanState() {
        assertNull(ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION))
        assertNull(ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_FORCE_FEED_UI_OPT))
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION),
        )
        assertTrue(ConfigManager.isScanFeatureAvailable(ConfigManager.KEY_FORCE_FEED_UI_OPT))
    }

    @Test
    fun scanIndependentFeaturesAreAvailableBeforeScanStateIsApplied() {
        assertEquals(
            ConfigManager.KEY_ENABLE_AUTO_SIGN_IN,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN),
        )
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.AVAILABLE,
            ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN),
        )
        assertTrue(ConfigManager.isScanFeatureAvailable(ConfigManager.KEY_ENABLE_AUTO_SIGN_IN))
    }

    @Test
    fun scanBackedPreferencesRemainUnknownUntilScanStateIsApplied() {
        assertEquals(
            ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS,
            ConfigManager.scanFeatureKeyForPrefKeyOrNull(ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS),
        )
        assertEquals(
            ConfigManager.ScanFeatureAvailabilityState.UNKNOWN,
            ConfigManager.getScanFeatureAvailabilityState(ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS),
        )
    }
}
