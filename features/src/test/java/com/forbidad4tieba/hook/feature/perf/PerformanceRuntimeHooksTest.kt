package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceRuntimeHooksTest {
    @Test
    fun cookieHooksOnlyVerifiedAbAndDisablingRestoresTheHostCallback() = withRuntime { api ->
        val cookie = method("cookie")
        val abMethods = mapOf(
            PerformanceAbTarget.COOKIE_REPEATED.methodName to cookie,
            "isPbCommentFunAdABTest" to method("unrelatedAdAb"),
        )
        setEnabled(false)
        CookieSyncDedupHook.hook(abMethods)
        assertTrue(api.callbacks.isEmpty())

        setEnabled(true)
        CookieSyncDedupHook.hook(emptyMap())
        assertTrue(api.callbacks.isEmpty())
        CookieSyncDedupHook.hook(abMethods)
        assertEquals(setOf(cookie), api.callbacks.keys)

        var originalCalls = 0
        assertEquals(true, api.callbacks.getValue(cookie).call { originalCalls++; false })
        assertEquals(0, originalCalls)
        setEnabled(false)
        assertEquals(false, api.callbacks.getValue(cookie).call { originalCalls++; false })
        assertEquals(1, originalCalls)
    }

    private fun setEnabled(enabled: Boolean) {
        ConfigManager::class.java.getDeclaredField("settingsSnapshot")
            .apply { isAccessible = true }
            .set(ConfigManager, SettingsSnapshot.bootstrap().copy(isCookieSyncDedupEnabled = enabled))
    }

    private fun withRuntime(test: (FakeHookApi) -> Unit) {
        val settingsField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val oldSettings = settingsField.get(ConfigManager)
        val oldModule = moduleField.get(XposedCompat)
        val api = FakeHookApi(allowLog = true)
        try {
            XposedCompat.attachModule(object : XposedModule() {}.apply { attachFramework(api.value) {} })
            test(api)
        } finally {
            settingsField.set(ConfigManager, oldSettings)
            moduleField.set(XposedCompat, oldModule)
            val hooks = RuntimeHooks::class.java.getDeclaredField("hooks")
                .apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
            assertTrue(hooks.rollback().isEmpty())
        }
    }

    private fun method(name: String): Method = Fixture::class.java.getDeclaredMethod(name)

    private fun XposedInterface.Hooker.call(original: () -> Any?): Any? =
        intercept(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "proceed" -> original()
                else -> error("Unexpected callback work: ${method.name}")
            }
        } as XposedInterface.Chain)

    class Fixture {
        fun cookie() = false
        fun unrelatedAdAb() = true
    }
}
