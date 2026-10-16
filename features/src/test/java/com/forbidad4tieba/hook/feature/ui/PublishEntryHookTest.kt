package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.RuntimeHooks
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class PublishEntryHookTest {
    private val key = "22.12_publish_button_right_top"
    private val readers = listOf("isHit", "isHitOnce").map {
        Readers::class.java.getDeclaredMethod(it, String::class.java)
    }

    @Test fun bothReadersOverrideOnlyTheExactKeyAndRecheckEitherSwitch() {
        val harness = Harness()
        val defaults = SettingsSnapshot.bootstrap()
        var settings = defaults.copy(isBottomTabsCustomEnabled = true)
        var settingsReads = 0
        val currentSettings = { settingsReads++; settings }
        var handles = emptyList<XposedInterface.HookHandle>()
        try {
            val installed = RuntimeHooks.measure("PublishEntryHook") {
                handles = PublishEntryHook.install(harness.api, readers, currentSettings)
                null
            }
            assertEquals(InstallState.INSTALLED, installed.state)
            assertEquals(2, installed.installedCount)
            val duplicate = RuntimeHooks.measure("PublishEntryHook") {
                val repeated = PublishEntryHook.install(harness.api, readers, currentSettings)
                handles.zip(repeated).forEach { (first, second) -> assertSame(first, second) }
                null
            }
            assertEquals(InstallState.ALREADY_INSTALLED, duplicate.state)
            assertEquals(2, harness.installs)

            for (callback in harness.callbacks.values) {
                settings = defaults.copy(isBottomTabsCustomEnabled = true)
                assertEquals(false, callback.intercept(chain(key) { error("Forced key reached host cache") }))
                settingsReads = 0
                for (otherKey in listOf(null, "", "other_experiment", key + "_extra")) {
                    for (original in listOf(false, true)) {
                        var calls = 0
                        assertEquals(original, callback.intercept(chain(otherKey) { calls++; original }))
                        assertEquals(1, calls)
                    }
                }
                assertEquals(0, settingsReads)

                for ((customTabs, floatingBar, expected) in listOf(
                    Triple(false, false, true), Triple(true, false, false),
                    Triple(true, true, false), Triple(false, true, false), Triple(false, false, true),
                )) {
                    settings = defaults.copy(isBottomTabsCustomEnabled = customTabs, isFloatingBottomBarEnabled = floatingBar)
                    var calls = 0
                    assertEquals(expected, callback.intercept(chain(key) { calls++; true }))
                    assertEquals(if (expected) 1 else 0, calls)
                }

                settings = defaults
                for (original in listOf(false, true)) {
                    var calls = 0
                    assertEquals(original, callback.intercept(chain(key) { calls++; original }))
                    assertEquals(1, calls)
                }
                val failure = IllegalStateException("original reader failure")
                assertSame(failure, assertThrows(IllegalStateException::class.java) {
                    callback.intercept(chain(key) { throw failure })
                })
                settings = defaults.copy(isFloatingBottomBarEnabled = true)
                assertEquals(false, callback.intercept(chain(key) { true }))
            }
        } finally {
            handles.asReversed().forEach { it.unhook() }
        }
    }

    @Test fun aFailedReaderRollsBackTheGroupAndCanBeRetried() {
        val harness = Harness().apply { failOn = readers.last() }
        val settings = SettingsSnapshot.bootstrap().copy(isFloatingBottomBarEnabled = true)
        val failed = RuntimeHooks.measure("PublishEntryHook") {
            PublishEntryHook.install(harness.api, readers) { settings }
            null
        }
        assertEquals(InstallState.FAILED, failed.state)
        assertEquals(0, failed.installedCount)
        assertEquals(listOf(readers.first()), harness.released)
        val stale = harness.callbacks.getValue(readers.first())
        assertEquals(true, stale.intercept(chain(key) { true }))

        harness.failOn = null
        var handles = emptyList<XposedInterface.HookHandle>()
        try {
            val retried = RuntimeHooks.measure("PublishEntryHook") {
                handles = PublishEntryHook.install(harness.api, readers) { settings }
                null
            }
            assertEquals(InstallState.INSTALLED, retried.state)
            assertEquals(2, retried.installedCount)
        } finally {
            handles.asReversed().forEach { it.unhook() }
        }
    }

    private inner class Harness {
        val callbacks = LinkedHashMap<Method, XposedInterface.Hooker>()
        val released = ArrayList<Method>()
        var installs = 0
        var failOn: Method? = null
        val api = proxy<XposedInterface> { method, args ->
            check(method.name == "hook")
            val reader = args!![0] as Method
            installs++
            check(reader != failOn) { "reader installation failed" }
            proxy<XposedInterface.HookBuilder> { builderMethod, builderArgs ->
                check(builderMethod.name == "intercept")
                callbacks[reader] = builderArgs!![0] as XposedInterface.Hooker
                proxy<XposedInterface.HookHandle> { handleMethod, _ ->
                    when (handleMethod.name) {
                        "getExecutable" -> reader
                        "getId" -> null
                        "unhook" -> { released += reader; null }
                        else -> error("Unexpected handle call: $handleMethod")
                    }
                }
            }
        }
    }

    private fun chain(key: String?, original: () -> Any?): XposedInterface.Chain = proxy { method, _ ->
        when (method.name) {
            "getArgs" -> listOf(key)
            "proceed" -> original()
            else -> error("Unexpected chain call: $method")
        }
    }

    private inline fun <reified T> proxy(noinline call: (Method, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method, args) } as T

    class Readers {
        companion object {
            @JvmStatic fun isHit(key: String?) = true
            @JvmStatic fun isHitOnce(key: String?) = true
        }
    }
}
