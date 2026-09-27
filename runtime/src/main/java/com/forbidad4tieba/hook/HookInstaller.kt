package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.core.RuntimeHooks

object HookInstaller {
    private val records = LinkedHashMap<String, HookInstallRecord>()

    fun snapshot(): List<HookInstallRecord> = synchronized(records) { records.values.toList() }

    fun install(plan: HookInstallPlan, cl: ClassLoader) {
        for (entry in plan.entries) {
            val start = System.nanoTime()
            val outcome = try {
                RuntimeHooks.measure(entry.id) { entry.install(cl) }
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
