package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.YyBackgroundSymbols
import com.forbidad4tieba.hook.symbol.model.YyBackgroundTarget
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class BackgroundPreloadHooksTest {
    private val api = FakeHookApi()
    private fun method(name: String, vararg params: Class<*>) = Fixture::class.java.getDeclaredMethod(name, *params)
    private val symbols = YyBackgroundSymbols(YyBackgroundTarget.entries.associateWith { target ->
        when (target) {
            YyBackgroundTarget.CONSTRUCTOR -> Fixture::class.java.getDeclaredConstructor()
            YyBackgroundTarget.CALLBACK_RESULT -> method("result", Boolean::class.javaPrimitiveType!!)
            YyBackgroundTarget.CALLBACK_EVENT -> method("event", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            else -> method(target.name)
        }
    }, method("ready"))

    @After fun release() {
        api.failUnhook.clear()
        YyBackgroundHook.install(api.value, null) { true }
        val hooks = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
        assertTrue(hooks.rollback().isEmpty())
    }

    private fun invoke(member: Executable, args: Array<Any?> = emptyArray(), original: () -> Any?): Any? {
        val callback = api.callbacks[member] ?: return original()
        val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, called, _ ->
            when (called.name) { "proceed" -> original(); "getArgs" -> args.toList(); else -> error(called.name) }
        } as XposedInterface.Chain
        return callback.intercept(chain)
    }

    @Test fun yyConstructorDefersOnlyItsPreparationAndRestoresScopeAfterFailure() {
        var enabled = true
        assertEquals(InstallState.INSTALLED, YyBackgroundHook.install(api.value, symbols) { enabled }.state)
        assertEquals(6, api.created)
        var actualChecks = 0
        fun ready() = invoke(symbols.readiness) { actualChecks++; false }
        assertEquals(false, ready())
        invoke(symbols.constructor) { assertEquals(true, ready()); null }
        assertEquals(1, actualChecks)
        assertThrows(IllegalStateException::class.java) { invoke(symbols.constructor) { throw IllegalStateException("original constructor failed") } }
        assertEquals(false, ready())
        enabled = false
        invoke(symbols.constructor) { assertEquals(false, ready()); null }
        assertEquals(3, actualChecks)
        enabled = true
        assertEquals(InstallState.ALREADY_INSTALLED, YyBackgroundHook.install(api.value, symbols) { enabled }.state)
        assertEquals(6, api.created)
    }

    @Test fun yyPreloadReportsRejectionAndOffPassesTheOriginal() {
        var enabled = true
        YyBackgroundHook.install(api.value, symbols) { enabled }
        val callback = Fixture()
        val target = symbols.method(YyBackgroundTarget.PRELOAD_CALLBACK)
        invoke(target, arrayOf(callback)) { error("preload ran") }
        assertEquals(listOf(false), callback.results)
        assertEquals(listOf(false to 42), callback.events)
        assertEquals(false, invoke(symbols.method(YyBackgroundTarget.INSERT_SUPPORT)) { true })
        enabled = false
        var calls = 0
        invoke(target, arrayOf(callback)) { calls++; null }
        assertEquals(1, calls)
        assertEquals(1, callback.results.size)
    }

    @Test fun yyPartialInstallationNeverActivatesAndCanRetry() {
        api.failInstall += symbols.method(YyBackgroundTarget.PRELOAD_CALLBACK)
        assertEquals(InstallState.ROLLED_BACK, YyBackgroundHook.install(api.value, symbols) { true }.state)
        assertTrue(api.callbacks.isEmpty())
        api.failInstall.clear()
        assertEquals(InstallState.INSTALLED, YyBackgroundHook.install(api.value, symbols) { true }.state)
        assertEquals(InstallState.SKIPPED, YyBackgroundHook.install(api.value, null) { true }.state)
        assertTrue(api.callbacks.isEmpty())
    }

    @Test fun swanOnlyBlocksTheTwoProducersAndItsSettingIsIndependent() {
        val targets = listOf(method("PRELOAD"), method("SUBPLUGINS"))
        var enabled = false
        SwanPreloadHook.install(api.value, targets) { enabled }
        assertEquals(0, api.created)
        enabled = true
        SwanPreloadHook.install(api.value, targets) { enabled }
        for (target in targets) assertNull(invoke(target) { error("background work ran") })
        var explicitLaunch = 0
        invoke(method("launch")) { explicitLaunch++; null }
        assertEquals(1, explicitLaunch)
        enabled = false
        var restored = 0
        for (target in targets) invoke(target) { restored++; null }
        assertEquals(2, restored)
    }

    @Test fun plansAreIndependentAndMainProcessOnly() {
        for (yy in listOf(false, true)) for (swan in listOf(false, true)) for (video in listOf(false, true)) {
            val settings = SettingsSnapshot.bootstrap().copy(isYyBackgroundDisabled = yy, isSwanPreloadDisabled = swan, isVideoPreloadDisabled = video)
            val context = HookInstallContext("com.baidu.tieba", HookSymbols.unsupported())
            assertEquals(yy, YyBackgroundFeature.entries(context, settings).isNotEmpty())
            assertEquals(swan, SwanPreloadFeature.entries(context, settings).isNotEmpty())
            for (name in listOf("com.baidu.tieba:remote", "com.baidu.tieba:swan0", "com.baidu.tieba:media", "com.other.app")) {
                val other = HookInstallContext(name, null)
                assertTrue(YyBackgroundFeature.entries(other, settings).isEmpty())
                assertTrue(SwanPreloadFeature.entries(other, settings).isEmpty())
            }
        }
    }

    class Fixture {
        val results = ArrayList<Boolean>()
        val events = ArrayList<Pair<Boolean, Int>>()
        fun ready() = false
        fun PRELOAD() = Unit
        fun SUBPLUGINS() = Unit
        fun INSERT_SUPPORT() = true
        fun PRELOAD_CALLBACK() = Unit
        fun launch() = Unit
        fun result(value: Boolean) { results += value }
        fun event(value: Boolean, code: Int) { events += value to code }
    }
}
