package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.ConfigManager.ScanFeatureAvailabilityState
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal class HostCapabilities(states: Map<String, ScanFeatureAvailabilityState>) {
    private val states = states.toMap()

    fun isAvailable(key: String): Boolean = when (availabilityOf(states, key)) {
        ScanFeatureAvailabilityState.AVAILABLE, ScanFeatureAvailabilityState.PARTIAL -> true
        ScanFeatureAvailabilityState.UNKNOWN, ScanFeatureAvailabilityState.DISABLED -> false
    }

    companion object {
        fun availabilityOf(states: Map<String, ScanFeatureAvailabilityState>, key: String): ScanFeatureAvailabilityState {
            val feature = ConfigManager.scanFeatureKeyForPrefKeyOrNull(key)
                ?: return ScanFeatureAvailabilityState.AVAILABLE
            // The stable logging path does not depend on a symbol scan.
            if (feature == HookFeatureKey.DETAILED_LOGGING) return ScanFeatureAvailabilityState.AVAILABLE
            return states[feature] ?: ScanFeatureAvailabilityState.UNKNOWN
        }
    }
}
