package com.forbidad4tieba.hook.feature.web

import android.content.Context
import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PlainUrlClickableSpanSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDataSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDispatchSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class PlainUrlMessageHookTest {
    private val dispatch = Manager::class.java.getDeclaredMethod("dispatch", Response::class.java)

    @Test fun unknownAndDerivedPayloadTypesPreserveHostDispatchOnEveryCall() = withRuntime { api ->
        var profiles = 0
        install { _, _ -> profiles++; true }
        for (data in listOf(UnknownPayload(), DerivedPayload(), Any())) {
            val message = CustomResponse(2001332, data)
            repeat(3) {
                val expected = Any()
                assertSame(expected, invoke(api, message) { expected })
            }
            assertEquals(3, message.dataReads)
        }
        assertEquals(0, profiles)
        assertEquals(0, Application.contextReads)
    }

    @Test fun messageRemainsInstalledAlongsideDirectAndUsesItsOwnPayloadContext() = withRuntime { api ->
        val opened = arrayListOf<String?>()
        val span = PlainUrlClickableSpanSymbols(Span::class.java,
            listOf(Span::class.java.getDeclaredMethod("onClick", View::class.java)),
            Span::class.java.getField("category"), Span::class.java.getField("destination"),
            Span::class.java.getField("directCaption"))
        install(span) { _, destination -> opened += destination; true }
        assertEquals(2, api.created)
        assertTrue(api.callbacks.containsKey(dispatch))
        assertNull(invoke(api, CustomResponse(2001332, Payload(16, "message-user", "message-caption"))) {
            error("Handled message must not dispatch to the host")
        })
        assertEquals(listOf("message-user"), opened)
        assertEquals(1, Application.contextReads)
    }

    @Test fun callbackUsesOnePublishedSnapshotAndRetainsProfileFailureFallback() = withRuntime { api ->
        var profiles = 0
        var handled = true
        install { _, _ -> profiles++; handled }
        val message = CustomResponse(2001332, Payload(16, "user", "caption")).apply {
            afterRead = { setSettings(SettingsSnapshot.bootstrap()) }
        }
        assertNull(invoke(api, message) { error("Initial snapshot enabled profile handling") })
        assertEquals(1, profiles)
        assertEquals(1, message.dataReads)
        setSettings(SettingsSnapshot.bootstrap().copy(isCommentAvatarDirectProfileEnabled = true))
        handled = false
        val expected = Any()
        assertSame(expected, invoke(api, CustomResponse(2001332, Payload(16, "user", "caption"))) { expected })
        assertEquals(2, profiles)
    }

    @Test fun disabledSettingsOtherCommandsAndPrivateRoutesPreserveHostHandling() = withRuntime { api ->
        install { _, _ -> error("These messages must not open a profile") }
        val expected = Any()
        val wrongCommand = CustomResponse(7, Payload(16, "user", "caption"))
        assertSame(expected, invoke(api, wrongCommand) { expected })
        assertEquals(0, wrongCommand.dataReads)
        setSettings(SettingsSnapshot.bootstrap())
        val disabled = CustomResponse(2001332, Payload(16, "user", "caption"))
        assertSame(expected, invoke(api, disabled) { expected })
        assertEquals(0, disabled.commandReads)
        assertEquals(0, disabled.dataReads)
        setSettings(SettingsSnapshot.bootstrap().copy(isOpenWebLinkInSystemBrowserEnabled = true))
        val route = "com.baidu.tieba://tbwebview?url=https%3A%2F%2Fexample.com"
        assertSame(expected, invoke(api, CustomResponse(2001332, Payload(2, route, route))) { expected })
        assertEquals(0, Application.contextReads)
    }

    private fun install(
        span: PlainUrlClickableSpanSymbols? = null,
        profile: (Context?, String?) -> Boolean,
    ) {
        val data = PlainUrlMessageDataSymbols(Payload::class.java, Payload::class.java.getField("category"),
            Payload::class.java.getField("destination"), Payload::class.java.getField("caption"))
        val message = PlainUrlMessageDispatchSymbols(Manager::class.java, dispatch, Response::class.java,
            Response::class.java.getDeclaredMethod("command"), CustomResponse::class.java,
            CustomResponse::class.java.getDeclaredMethod("data"), Application::class.java,
            Application::class.java.getDeclaredMethod("current"), data)
        val targets = PlainUrlDirectBrowserHook.RuntimeTargets(span, message, null, null, null, { it == 2001332 }, profile)
        RuntimeHooks.measure("PlainUrlDirectBrowserHook") {
            PlainUrlDirectBrowserHook.hook(targets)
            null
        }
    }

    private fun invoke(api: FakeHookApi, message: Any, original: () -> Any?): Any? {
        val callback = requireNotNull(api.callbacks[dispatch])
        val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
            when (method.name) {
                "getArgs" -> listOf(message)
                "proceed" -> { assertTrue(args.isNullOrEmpty()); original() }
                else -> error("Unexpected callback work: ${method.name}")
            }
        } as XposedInterface.Chain
        return callback.intercept(chain)
    }

    private fun setSettings(settings: SettingsSnapshot) {
        ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }.set(ConfigManager, settings)
    }

    private fun withRuntime(test: (FakeHookApi) -> Unit) {
        val snapshot = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val originalSettings = snapshot.get(ConfigManager)
        val originalModule = moduleField.get(XposedCompat)
        val api = FakeHookApi(allowLog = true)
        val module = object : XposedModule() {}.apply { attachFramework(api.value) {} }
        try {
            Application.contextReads = 0
            XposedCompat.attachModule(module)
            setSettings(SettingsSnapshot.bootstrap().copy(isCommentAvatarDirectProfileEnabled = true))
            test(api)
        } finally {
            val hooks = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
            try { assertTrue(hooks.rollback().isEmpty()) } finally {
                (PlainUrlDirectBrowserHook::class.java.getDeclaredField("installed").apply { isAccessible = true }.get(null) as AtomicBoolean).set(false)
                (PlainUrlDirectBrowserHook::class.java.getDeclaredField("installedMethodKeys").apply { isAccessible = true }.get(null) as MutableSet<*>).clear()
                snapshot.set(ConfigManager, originalSettings)
                moduleField.set(XposedCompat, originalModule)
            }
        }
    }

    open class Response(private val cmd: Int) {
        var commandReads = 0
        fun command(): Int { commandReads++; return cmd }
    }
    class CustomResponse(cmd: Int, private val payload: Any?) : Response(cmd) {
        var dataReads = 0
        var afterRead: () -> Unit = {}
        fun data(): Any? { dataReads++; afterRead(); return payload }
    }
    open class Payload(@JvmField val category: Int, @JvmField val destination: String?, @JvmField val caption: String?)
    class DerivedPayload : Payload(16, "derived-user", "caption")
    class UnknownPayload {
        @JvmField val category: Int = 16
        @JvmField val destination: String = "unknown-user"
        @JvmField val caption: String = "caption"
    }
    class Manager { @Suppress("UNUSED_PARAMETER") fun dispatch(response: Response) = Unit }
    object Application {
        var contextReads = 0
        @JvmStatic fun current(): Context? { contextReads++; return null }
    }
    class Span {
        @JvmField val category: Int = 16
        @JvmField val destination: String = "span-user"
        @JvmField val directCaption: String = "span-caption"
        @Suppress("UNUSED_PARAMETER") fun onClick(view: View) = Unit
    }
}
