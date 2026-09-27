package com.forbidad4tieba.hook.feature.ad

/** Writers serialize updates with their I/O lock; producers only read [disabled]. */
internal class StatsPersistenceState(val failureLimit: Int = 3) {
    var failureCount = 0
        private set
    @Volatile var disabled = false
        private set

    fun failed() {
        failureCount++
        if (failureCount >= failureLimit) disabled = true
    }

    fun succeeded() { failureCount = 0 }

    fun reset() {
        failureCount = 0
        disabled = false
    }
}
