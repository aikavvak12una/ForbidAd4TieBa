package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.core.XposedCompat

internal enum class InstallState { DISPATCHED, INSTALLED, ALREADY_INSTALLED, PARTIAL, SKIPPED, FAILED, ROLLED_BACK, ROLLBACK_FAILED }

internal data class InstallOutcome(
    val state: InstallState,
    val installedCount: Int = 0,
    val reason: String? = null,
) {
    companion object {
        fun skipped(reason: String) = InstallOutcome(InstallState.SKIPPED, reason = reason)
    }
}

internal data class HookInstallEntry(
    val id: String,
    val install: (ClassLoader) -> InstallOutcome,
) {
    companion object {
        /** Legacy entries report dispatch, never success inferred from the absence of an exception. */
        fun dispatched(id: String, action: (ClassLoader) -> Unit) = HookInstallEntry(id) { cl ->
            action(cl)
            InstallOutcome(InstallState.DISPATCHED)
        }
    }
}

internal data class HookInstallRecord(
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

internal object HookInstaller {
    private val records = LinkedHashMap<String, HookInstallRecord>()

    fun snapshot(): List<HookInstallRecord> = synchronized(records) { records.values.toList() }

    fun install(plan: HookInstallPlan, cl: ClassLoader) {
        for (entry in plan.entries) {
            val start = System.nanoTime()
            val outcome = try {
                entry.install(cl)
            } catch (failure: Throwable) {
                XposedCompat.log(failure)
                InstallOutcome(InstallState.FAILED, reason = "${failure.javaClass.simpleName}: ${failure.message}")
            }
            val record = HookInstallRecord(plan.processName, plan.phase, entry.id, outcome, System.nanoTime() - start)
            synchronized(records) { records["${plan.phase}:${entry.id}"] = record }
            when (outcome.state) {
                InstallState.FAILED, InstallState.PARTIAL, InstallState.ROLLED_BACK, InstallState.ROLLBACK_FAILED ->
                    XposedCompat.logW(record.formatLine())
                else -> XposedCompat.logD { record.formatLine() }
            }
        }
    }
}
