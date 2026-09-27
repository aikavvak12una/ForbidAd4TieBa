package com.forbidad4tieba.hook.core

import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/** Owns installed handles. Callback closures capture only their activation flag and restored targets. */
object RuntimeHooks {
    private data class Site(val owner: String, val member: Executable, val purpose: String)
    private class Activation { @Volatile var active = true }
    private class Attempt(val id: String) {
        var created = 0
        val failures = ArrayList<String>()
    }

    private val attempt = ThreadLocal<Attempt?>()
    private val hooks = OwnedHookSet<Site, Registration> { it.release() }

    private class Registration(
        val site: Site,
        val entry: String,
        var activation: Activation,
        var delegate: XposedInterface.HookHandle,
    ) {
        val handle: XposedInterface.HookHandle = object : XposedInterface.HookHandle {
            override fun getExecutable(): Executable = delegate.executable
            override fun getId(): String? = delegate.id
            override fun unhook() = hooks.release(site, this@Registration)
            override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle = synchronized(this@Registration) {
                check(activation.active) { "Cannot replace an inactive hook: $site" }
                val next = Activation()
                val replacement = delegate.replaceHook { chain ->
                    if (next.active) hooker.intercept(chain) else chain.proceed()
                }
                activation.active = false
                activation = next
                delegate = replacement
                handle
            }
        }

        @Synchronized fun release() {
            activation.active = false
            // A failed framework uninstall retains ownership and leaves old callback snapshots inert.
            delegate.unhook()
        }
    }

    fun builder(
        api: XposedInterface,
        member: Executable,
        owner: String,
        purpose: String,
    ): XposedInterface.HookBuilder = object : XposedInterface.HookBuilder {
        private var priority: Int? = null
        private var exceptionMode: XposedInterface.ExceptionMode? = null
        private var hookId: String? = null
        override fun setPriority(priority: Int) = apply { this.priority = priority }
        override fun setExceptionMode(mode: XposedInterface.ExceptionMode) = apply { exceptionMode = mode }
        override fun setId(id: String?) = apply { hookId = id }

        override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            val site = Site(owner, member, purpose)
            try {
                return hooks.install(site) {
                    val active = Activation()
                    val builder = api.hook(member)
                    priority?.let(builder::setPriority)
                    exceptionMode?.let(builder::setExceptionMode)
                    hookId?.let(builder::setId)
                    val handle = builder.intercept { chain ->
                        if (active.active) hooker.intercept(chain) else chain.proceed()
                    }
                    val current = attempt.get()
                    current?.let { it.created++ }
                    Registration(site, current?.id ?: owner, active, handle)
                }.handle
            } catch (failure: Throwable) {
                attempt.get()?.failures?.add("$purpose: ${failure.javaClass.simpleName}: ${failure.message}")
                throw failure
            }
        }
    }

    /** Keeps explicit independent/group/optional failure policy while measuring real retained handles. */
    fun measure(id: String, install: () -> InstallOutcome?): InstallOutcome {
        check(attempt.get() == null) { "Nested feature installation: $id" }
        val current = Attempt(id)
        attempt.set(current)
        val declared = try {
            install()
        } catch (failure: Throwable) {
            current.failures += "${failure.javaClass.simpleName}: ${failure.message}"
            InstallOutcome(InstallState.FAILED, reason = current.failures.last())
        } finally {
            attempt.remove()
        }
        val owned = hooks.values().filter { it.entry == id }
        val count = owned.size
        val inactive = owned.any { !it.activation.active }
        val state = when {
            declared?.state == InstallState.ROLLED_BACK && count == 0 -> InstallState.ROLLED_BACK
            declared?.state == InstallState.ROLLBACK_FAILED || inactive -> InstallState.ROLLBACK_FAILED
            count == 0 && (current.failures.isNotEmpty() || declared?.state == InstallState.FAILED) -> InstallState.FAILED
            count == 0 -> InstallState.SKIPPED
            current.failures.isNotEmpty() || declared?.state == InstallState.PARTIAL || declared?.state == InstallState.FAILED -> InstallState.PARTIAL
            current.created == 0 -> InstallState.ALREADY_INSTALLED
            else -> InstallState.INSTALLED
        }
        return InstallOutcome(
            state, count,
            declared?.reason ?: current.failures.takeIf { it.isNotEmpty() }?.joinToString("; ")
                ?: if (count == 0) "no hook handles installed" else null,
        )
    }
}
