package com.forbidad4tieba.hook.core

import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class RuntimeHooksTest {
    private val member = String::class.java.getMethod("trim")

    @Test fun missingTargetsAreSkippedAndFailuresAreNotReportedAsSuccess() {
        assertEquals(InstallState.SKIPPED, RuntimeHooks.measure("missing") { null }.state)
        val failed = RuntimeHooks.measure("failed") { error("restoration failed") }
        assertEquals(InstallState.FAILED, failed.state)
        assertEquals(0, failed.installedCount)
        assertTrue(failed.reason!!.contains("restoration failed"))
    }

    @Test fun independentPointFailureRetainsSuccessfulHandlesAndReportsPartial() {
        val api = FakeApi()
        lateinit var handle: XposedInterface.HookHandle
        val outcome = RuntimeHooks.measure("independent") {
            handle = install(api, "independent", "first")
            api.failInstall = true
            runCatching { install(api, "independent", "second") }
            null
        }
        assertEquals(InstallState.PARTIAL, outcome.state)
        assertEquals(1, outcome.installedCount)
        handle.unhook()
    }

    @Test fun sameSiteDeduplicatesButDifferentPurposesOnSameMethodSurvive() {
        val api = FakeApi()
        lateinit var first: XposedInterface.HookHandle
        lateinit var second: XposedInterface.HookHandle
        val initial = RuntimeHooks.measure("shared") {
            first = install(api, "shared", "background")
            second = install(api, "shared", "refresh")
            null
        }
        val duplicate = RuntimeHooks.measure("shared") {
            assertSame(first, install(api, "shared", "background"))
            null
        }
        assertEquals(InstallState.INSTALLED, initial.state)
        assertEquals(InstallState.ALREADY_INSTALLED, duplicate.state)
        assertEquals(2, duplicate.installedCount)
        assertEquals(2, api.handles.size)
        first.unhook()
        second.unhook()
    }

    @Test fun rollbackFailureRetainsOwnershipAndInvalidatesOldCallbacks() {
        val api = FakeApi()
        lateinit var handle: XposedInterface.HookHandle
        RuntimeHooks.measure("rollback") { handle = install(api, "rollback", "group"); null }
        val captured = api.handles.single().callback
        assertEquals("intercepted", captured.intercept(chain()))
        api.failUninstall = true
        val rollback = RuntimeHooks.measure("rollback") {
            assertThrows(IllegalStateException::class.java) { handle.unhook() }
            InstallOutcome(InstallState.ROLLBACK_FAILED)
        }
        assertEquals(InstallState.ROLLBACK_FAILED, rollback.state)
        assertEquals(1, rollback.installedCount)
        assertEquals("original", captured.intercept(chain()))
        assertSame(handle, install(api, "rollback", "group"))
        assertEquals(1, api.handles.size)
        assertThrows(IllegalStateException::class.java) { handle.replaceHook { "resurrected" } }
        api.failUninstall = false
        handle.unhook()
        assertEquals(InstallState.SKIPPED, RuntimeHooks.measure("rollback") { null }.state)
    }

    @Test fun completeRollbackReportsNoRetainedHandles() {
        val api = FakeApi()
        val outcome = RuntimeHooks.measure("atomic") {
            val first = install(api, "atomic", "one")
            api.failInstall = true
            assertThrows(IllegalStateException::class.java) { install(api, "atomic", "two") }
            first.unhook()
            InstallOutcome(InstallState.ROLLED_BACK)
        }
        assertEquals(InstallState.ROLLED_BACK, outcome.state)
        assertEquals(0, outcome.installedCount)
    }

    @Test fun oldHandleCannotUninstallNewRegistrationAtSameSite() {
        val api = FakeApi()
        val old = install(api, "stale", "same")
        old.unhook()
        val replacement = install(api, "stale", "same")
        old.unhook()
        assertFalse(api.handles.last().released)
        assertEquals("intercepted", api.handles.last().callback.intercept(chain()))
        replacement.unhook()
    }

    @Test fun replacementPreservesOptionsAndInvalidatesPreviousCallbackSnapshot() {
        val api = FakeApi()
        val handle = RuntimeHooks.builder(api.value, member, "replace", "same")
            .setId("stable-id").setPriority(19).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { "old" }
        val oldCallback = api.handles.single().callback
        assertEquals(19, api.priority)
        assertEquals("stable-id", handle.id)
        assertEquals(XposedInterface.ExceptionMode.PROTECTIVE, api.mode)
        assertSame(handle, handle.replaceHook { "new" })
        assertEquals("original", oldCallback.intercept(chain()))
        assertEquals("new", api.handles.last().callback.intercept(chain()))
        handle.unhook()
    }

    private fun install(api: FakeApi, owner: String, purpose: String) =
        RuntimeHooks.builder(api.value, member, owner, purpose).intercept { "intercepted" }

    private fun chain(): XposedInterface.Chain = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "proceed" -> "original"
            else -> error("Unexpected chain call: ${method.name}")
        }
    } as XposedInterface.Chain

    private class FakeApi {
        var failInstall = false
        var failUninstall = false
        var priority: Int? = null
        var mode: XposedInterface.ExceptionMode? = null
        val handles = ArrayList<Handle>()
        val value = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(XposedInterface::class.java),
        ) { _, method, args ->
            check(method.name == "hook") { "Unexpected API call: ${method.name}" }
            builder(args!![0] as Executable)
        } as XposedInterface

        private fun builder(member: Executable) = object : XposedInterface.HookBuilder {
            var id: String? = null
            override fun setId(id: String?) = apply { this.id = id }
            override fun setPriority(priority: Int) = apply { this@FakeApi.priority = priority }
            override fun setExceptionMode(mode: XposedInterface.ExceptionMode) = apply { this@FakeApi.mode = mode }
            override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                check(!failInstall) { "install failed" }
                return Handle(member, id, hooker).also { handles += it }
            }
        }

        inner class Handle(
            private val member: Executable,
            private val hookId: String?,
            val callback: XposedInterface.Hooker,
        ) : XposedInterface.HookHandle {
            var released = false
            override fun getExecutable(): Executable = member
            override fun getId(): String? = hookId
            override fun unhook() { check(!failUninstall) { "uninstall failed" }; released = true }
            override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle =
                Handle(member, hookId, hooker).also { handles += it }
        }
    }
}
