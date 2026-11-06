package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.ForumPageAdBlockSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class ForumRainMapperBlockHookTest {
    private val rain = Fixture::class.java.getDeclaredMethod("rain", Any::class.java)
    private val game = Fixture::class.java.getDeclaredMethod("game", Any::class.java)

    @Test fun enabledRainReturnsNullWithoutConversionAndDisabledRainProceeds() = withRuntime { api ->
        val targets = targets()
        setEnabled(false)
        assertEquals(InstallState.SKIPPED, install(targets).state)
        assertEquals(0, api.created)
        setEnabled(true)
        val outcome = install(targets)
        assertEquals(InstallState.PARTIAL, outcome.state)
        assertEquals(1, outcome.installedCount)
        assertEquals(setOf(rain), api.callbacks.keys)

        var conversions = 0
        val hostRain = Any()
        fun header(): Header = Header("ordinary-header", api.callbacks.getValue(rain).invoke {
            conversions++
            hostRain
        })
        val blocked = header()
        assertEquals("ordinary-header", blocked.title)
        assertNull(blocked.rain)
        assertEquals(0, conversions)
        setEnabled(false)
        val original = header()
        assertEquals("ordinary-header", original.title)
        assertSame(hostRain, original.rain)
        assertEquals(1, conversions)
        setEnabled(true)
        assertNull(header().rain)
        assertEquals(1, conversions)
    }

    @Test fun aRainInstallFailureRetainsOtherPointsAndRetriesWithoutDuplicates() = withRuntime { api ->
        setEnabled(true)
        val targets = targets(game)
        api.failInstall += rain
        val partial = install(targets)
        assertEquals(InstallState.PARTIAL, partial.state)
        assertEquals(1, partial.installedCount)
        assertEquals(setOf(game), api.callbacks.keys)
        api.failInstall.clear()
        val recovered = install(targets)
        assertEquals(InstallState.PARTIAL, recovered.state)
        assertEquals(2, recovered.installedCount)
        assertEquals(setOf(game, rain), api.callbacks.keys)
        assertEquals(2, api.created)
        val repeated = install(targets)
        assertEquals(InstallState.PARTIAL, repeated.state)
        assertEquals(2, repeated.installedCount)
        assertEquals(2, api.created)
    }

    @Test fun releasedRainCallbackBecomesInertEvenWhileTheSettingRemainsEnabled() = withRuntime { api ->
        setEnabled(true)
        install(targets())
        val stale = api.callbacks.getValue(rain)
        release()
        assertTrue(api.callbacks.isEmpty())
        var calls = 0
        val original = Any()
        assertSame(original, stale.invoke { calls++; original })
        assertEquals(1, calls)
    }

    private fun targets(gameMapper: Method? = null) = ForumPageAdBlockSymbols(
        responseParserMethod = null,
        responseAdFields = emptyList(),
        bottomDataMapperMethod = null,
        bottomDataSetterMethods = emptyList(),
        bottomGameBarMapperMethod = gameMapper,
        rainMapperMethod = rain,
        businessPromotShowMethod = null,
        gameFloatingBarShowMethod = null,
        gameFloatingBarField = null,
        businessPromotJumpMethod = null,
    )

    private fun install(targets: ForumPageAdBlockSymbols) = RuntimeHooks.measure("ForumPageAdBlockHook") {
        ForumPageAdBlockHook.hook(targets)
    }

    private fun setEnabled(value: Boolean) {
        ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
            .set(ConfigManager, SettingsSnapshot.bootstrap().copy(isForumPageAdBlockEnabled = value))
    }

    private fun release() {
        val installed = ForumPageAdBlockHook::class.java.getDeclaredField("installedMethods\$delegate").apply { isAccessible = true }
            .get(null) as Lazy<*>
        assertTrue((installed.value as OwnedHookSet<*, *>).rollback().isEmpty())
        val runtime = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }
            .get(null) as OwnedHookSet<*, *>
        assertTrue(runtime.rollback().isEmpty())
    }

    private fun withRuntime(test: (FakeHookApi) -> Unit) {
        val snapshotField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val originalSettings = snapshotField.get(ConfigManager)
        val originalModule = moduleField.get(XposedCompat)
        val api = FakeHookApi(allowLog = true)
        val module = object : XposedModule() {}.apply { attachFramework(api.value) {} }
        try {
            XposedCompat.attachModule(module)
            test(api)
        } finally {
            release()
            snapshotField.set(ConfigManager, originalSettings)
            moduleField.set(XposedCompat, originalModule)
        }
    }

    private fun XposedInterface.Hooker.invoke(original: () -> Any?): Any? {
        val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "proceed" -> original()
                else -> error("Unexpected callback work: ${method.name}")
            }
        } as XposedInterface.Chain
        return intercept(chain)
    }

    private data class Header(val title: String, val rain: Any?)

    class Fixture {
        companion object {
            @JvmStatic fun rain(protocol: Any): Any? = null
            @JvmStatic fun game(protocol: Any): Any? = null
        }
    }
}
