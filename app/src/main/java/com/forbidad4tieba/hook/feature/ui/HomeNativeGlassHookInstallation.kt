package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/** A site is part of the identity: intentional callbacks sharing a method must all survive. */
internal class HomeNativeGlassHookInstallation {
    private data class Site(val member: Executable, val purpose: String)
    private val hooks = OwnedHookSet<Site, XposedInterface.HookHandle> { it.unhook() }
    private var installation = Any()
    @Volatile private var activeInstallation: Any? = null
    private var failures = 0

    fun intercept(mod: XposedModule, member: Executable, purpose: String, callback: XposedInterface.Hooker) {
        try {
            hooks.install(Site(member, purpose)) {
                val capturedInstallation = installation
                mod.hook(member).intercept { chain ->
                    if (activeInstallation === capturedInstallation) callback.intercept(chain) else chain.proceed()
                }
            }
        } catch (failure: Throwable) {
            failures++
            throw failure
        }
    }

    fun activate(): InstallOutcome {
        activeInstallation = installation
        return InstallOutcome(if (failures == 0) InstallState.INSTALLED else InstallState.PARTIAL, hooks.size())
    }

    fun currentOutcome(): InstallOutcome = InstallOutcome(
        if (activeInstallation != null) InstallState.ALREADY_INSTALLED else InstallState.ROLLBACK_FAILED,
        hooks.size(),
        if (activeInstallation != null) null else "inactive handles retained after failed rollback",
    )

    fun rollback(reason: String): InstallOutcome {
        activeInstallation = null
        // Retained callback snapshots belong to the attempt that has just ended.
        installation = Any()
        val remaining = hooks.rollback()
        for ((site, error) in remaining) {
            XposedCompat.logW("[HomeNativeGlassHook] rollback failed: ${site.purpose} ${site.member}: ${error.message}")
        }
        failures = 0
        return InstallOutcome(
            if (remaining.isEmpty()) InstallState.ROLLED_BACK else InstallState.ROLLBACK_FAILED,
            hooks.size(), reason,
        )
    }
}
