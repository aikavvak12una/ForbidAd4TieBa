package com.forbidad4tieba.hook.feature.web

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDataSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDispatchSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainUrlMessageHookTest {
    private val dispatch = Manager::class.java.getDeclaredMethod("dispatch", Response::class.java)
    private val settingsField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
    private val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }

    @Test
    fun disabledWrongCommandAndPrivateRoutesPreserveHostWithoutUnnecessaryReads() = withRuntime { api ->
        install { _, _ -> error("private routes must remain in the host") }
        val hostResult = Any()
        setSettings(SettingsSnapshot.bootstrap())
        val disabled = CustomResponse(2001332, Payload(16, "user", "caption"))
        assertSame(hostResult, invoke(api, disabled) { hostResult })
        assertEquals(0, disabled.commandReads)
        assertEquals(0, Application.contextReads)

        setSettings(SettingsSnapshot.bootstrap().copy(isCommentAvatarDirectProfileEnabled = true))
        val wrongCommand = CustomResponse(7, Payload(16, "user", "caption"))
        assertSame(hostResult, invoke(api, wrongCommand) { hostResult })
        assertEquals(0, wrongCommand.dataReads)

        val route = "com.baidu.tieba://tbwebview?url=https%3A%2F%2Fexample.com"
        val privateRoute = CustomResponse(2001332, Payload(2, route, route))
        assertSame(hostResult, invoke(api, privateRoute) { hostResult })
    }

    @Test
    fun exactPayloadClassHandlesWhileLookalikesReachTheHost() = withRuntime { api ->
        val opened = arrayListOf<String?>()
        install { _, destination -> opened += destination; true }
        val hostResult = Any()
        for (payload in listOf(UnknownPayload(), DerivedPayload(), Any())) {
            assertSame(hostResult, invoke(api, CustomResponse(2001332, payload)) { hostResult })
        }
        assertEquals(0, opened.size)
        assertEquals(0, Application.contextReads)
        val handled = CustomResponse(2001332, Payload(16, "message-user", "message-caption"))
        assertNull(invoke(api, handled) { error("exact payload reached host") })
        assertEquals(listOf("message-user"), opened)
        assertEquals(1, Application.contextReads)
    }

    private fun install(openProfile: (Context?, String?) -> Boolean) {
        val data = PlainUrlMessageDataSymbols(
            Payload::class.java, Payload::class.java.getField("category"),
            Payload::class.java.getField("destination"),
            Payload::class.java.getField("caption"),
        )
        val message = PlainUrlMessageDispatchSymbols(
            Manager::class.java, dispatch,
            Response::class.java, Response::class.java.getDeclaredMethod("command"),
            CustomResponse::class.java, CustomResponse::class.java.getDeclaredMethod("data"),
            Application::class.java, Application::class.java.getDeclaredMethod("current"), data,
        )
        PlainUrlDirectBrowserHook.hook(PlainUrlDirectBrowserHook.RuntimeTargets(
            null, message, null, null, null, { it == 2001332 }, openProfile,
        ))
    }

    private fun invoke(api: FakeHookApi, message: Any, original: () -> Any?): Any? =
        requireNotNull(api.callbacks[dispatch]).intercept(Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(XposedInterface.Chain::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getArgs" -> listOf(message)
                "proceed" -> original()
                else -> error("Unexpected callback work: ${method.name}")
            }
        } as XposedInterface.Chain)

    private fun setSettings(settings: SettingsSnapshot) = settingsField.set(ConfigManager, settings)

    private fun withRuntime(test: (FakeHookApi) -> Unit) {
        val oldSettings = settingsField.get(ConfigManager)
        val oldModule = moduleField.get(XposedCompat)
        val api = FakeHookApi(allowLog = true)
        try {
            Application.contextReads = 0
            XposedCompat.attachModule(object : XposedModule() {}.apply { attachFramework(api.value) {} })
            setSettings(SettingsSnapshot.bootstrap().copy(isCommentAvatarDirectProfileEnabled = true))
            test(api)
        } finally {
            (PlainUrlDirectBrowserHook::class.java.getDeclaredField("installed")
                .apply { isAccessible = true }.get(null) as AtomicBoolean).set(false)
            (PlainUrlDirectBrowserHook::class.java.getDeclaredField("installedMethodKeys")
                .apply { isAccessible = true }.get(null) as MutableSet<*>).clear()
            settingsField.set(ConfigManager, oldSettings)
            moduleField.set(XposedCompat, oldModule)
            val hooks = RuntimeHooks::class.java.getDeclaredField("hooks")
                .apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
            assertTrue(hooks.rollback().isEmpty())
        }
    }

    open class Response(private val cmd: Int) {
        var commandReads = 0
        fun command(): Int { commandReads += 1; return cmd }
    }

    class CustomResponse(cmd: Int, private val payload: Any?) : Response(cmd) {
        var dataReads = 0
        fun data(): Any? { dataReads += 1; return payload }
    }

    open class Payload(
        @JvmField val category: Int,
        @JvmField val destination: String?,
        @JvmField val caption: String?,
    )

    class DerivedPayload : Payload(16, "derived-user", "caption")
    class UnknownPayload {
        @JvmField val category = 16
        @JvmField val destination = "unknown-user"
        @JvmField val caption = "caption"
    }

    class Manager { @Suppress("UNUSED_PARAMETER") fun dispatch(response: Response) = Unit }

    object Application {
        var contextReads = 0
        @JvmStatic fun current(): Context? {
            contextReads++
            return null
        }
    }
}
