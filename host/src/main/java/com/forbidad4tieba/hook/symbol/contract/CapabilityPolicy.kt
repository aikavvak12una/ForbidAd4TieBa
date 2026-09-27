package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus

internal object CapabilityPolicy {
    fun combineSubFeatureStatuses(statuses: List<HookFeatureStatus>): HookFeatureStatus {
        val supported = statuses.filter { it.isSupported() }
        val missingCritical = statuses.flatMap { it.missingCritical }.distinct()
        val missingOptional = statuses.flatMap { it.missingOptional }.distinct()
        if (supported.isEmpty()) {
            return HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = missingCritical.ifEmpty { listOf("allSubFeatures") },
                missingOptional = missingOptional,
            )
        }
        val allComplete = statuses.all {
            it.state == HookFeatureState.FULL || it.state == HookFeatureState.HARD_CODED
        }
        return if (allComplete) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingCritical = missingCritical,
                missingOptional = missingOptional,
            )
        }
    }

    fun statusFromMissing(
        critical: List<String>,
        optional: List<String> = emptyList(),
    ): HookFeatureStatus {
        return when {
            critical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = critical,
                missingOptional = optional,
            )
            optional.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = optional,
            )
            else -> HookFeatureStatus(state = HookFeatureState.FULL)
        }
    }
}
