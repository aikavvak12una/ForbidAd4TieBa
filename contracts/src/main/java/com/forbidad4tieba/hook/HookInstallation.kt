package com.forbidad4tieba.hook


enum class InstallState { INSTALLED, ALREADY_INSTALLED, PARTIAL, SKIPPED, FAILED, ROLLED_BACK, ROLLBACK_FAILED }

data class InstallOutcome(
    val state: InstallState,
    val installedCount: Int = 0,
    val reason: String? = null,
) {
    companion object {
        fun skipped(reason: String) = InstallOutcome(InstallState.SKIPPED, reason = reason)
    }
}

data class HookInstallEntry(
    val id: String,
    val install: (ClassLoader) -> InstallOutcome?,
) {
    companion object {
        /** The runtime observes actual handles; an action need not invent its own success count. */
        fun observed(id: String, action: (ClassLoader) -> Unit) = HookInstallEntry(id) { cl ->
            action(cl)
            null
        }
    }
}

data class HookInstallRecord(
    val process: String,
    val phase: String,
    val id: String,
    val outcome: InstallOutcome,
    val elapsedNanos: Long,
) {
    fun formatLine(): String = "HookInstall[$id] state=${outcome.state} hooks=${outcome.installedCount} " +
        "phase=$phase process=$process elapsedUs=${elapsedNanos / 1000}" +
        (outcome.reason?.let { " reason=${it.replace('\n', ' ').take(240)}" } ?: "")
}

data class HookInstallPlan(
    val processName: String,
    val phase: String,
    val entries: List<HookInstallEntry>,
) {
    fun isEmpty(): Boolean = entries.isEmpty()
}
